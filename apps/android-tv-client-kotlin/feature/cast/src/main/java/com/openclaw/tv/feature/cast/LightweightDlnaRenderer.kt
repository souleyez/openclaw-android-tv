package com.openclaw.tv.feature.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.OutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale

internal class LightweightDlnaRenderer(
    private val config: DlnaRendererConfig,
    private val onStateChanged: (DlnaRendererState) -> Unit,
    private val onMediaRequest: (DlnaMediaRequest) -> Unit,
) {
    @Volatile
    private var isRunning = false
    private var serverSocket: ServerSocket? = null
    private var ssdpSocket: MulticastSocket? = null
    private var scope: CoroutineScope? = null
    private var scopeJob: Job? = null
    private var descriptionUrl: String? = null
    private var currentUri: String? = null
    private var activeNetworkInterface: NetworkInterface? = null

    fun start() {
        if (isRunning) {
            return
        }
        runCatching {
            val endpoint = resolveLocalIpv4Endpoint()
                ?: error("No local IPv4 address available for DLNA renderer")
            val httpSocket = ServerSocket(0, 50, endpoint.address)
            val job = SupervisorJob()
            val activeScope = CoroutineScope(job + Dispatchers.IO)
            val location = "http://${endpoint.address.hostAddress}:${httpSocket.localPort}/dlna/description.xml"

            serverSocket = httpSocket
            scopeJob = job
            scope = activeScope
            descriptionUrl = location
            activeNetworkInterface = endpoint.networkInterface
            isRunning = true
            onStateChanged(
                DlnaRendererState(
                    displayName = config.deviceName,
                    isRunning = true,
                    descriptionUrl = location,
                ),
            )
            activeScope.launch {
                runCatching { runHttpServer(httpSocket) }
                    .onFailure { throwable -> failRenderer(throwable.message ?: "DLNA HTTP server stopped") }
            }
            activeScope.launch {
                runCatching { runSsdpResponder(location, endpoint.networkInterface) }
                    .onFailure { throwable -> failRenderer(throwable.message ?: "DLNA SSDP responder stopped") }
            }
        }.onFailure { throwable ->
            isRunning = false
            closeSockets()
            activeNetworkInterface = null
            onStateChanged(
                DlnaRendererState(
                    displayName = config.deviceName,
                    isRunning = false,
                    errorMessage = throwable.message ?: "DLNA renderer failed to start",
                ),
            )
        }
    }

    fun stop() {
        if (!isRunning && serverSocket == null && ssdpSocket == null) {
            return
        }
        val lastUrl = descriptionUrl
        val lastNetworkInterface = activeNetworkInterface
        isRunning = false
        if (lastUrl != null) {
            sendByebye(lastNetworkInterface)
        }
        scopeJob?.cancel()
        scopeJob = null
        scope = null
        closeSockets()
        descriptionUrl = null
        currentUri = null
        activeNetworkInterface = null
        onStateChanged(
            DlnaRendererState(
                displayName = config.deviceName,
                isRunning = false,
            ),
        )
    }

    private suspend fun runHttpServer(socket: ServerSocket) {
        while (isRunning && currentCoroutineContext().isActive) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope?.launch {
                runCatching { handleHttpClient(client) }
            } ?: client.close()
        }
    }

    private fun handleHttpClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 2_500
            val request = readRequest(socket)
            val response = routeRequest(request)
            writeResponse(
                output = socket.getOutputStream(),
                status = response.status,
                contentType = response.contentType,
                body = response.body,
            )
        }
    }

    private fun readRequest(socket: Socket): HttpRequest {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val firstLine = reader.readLine().orEmpty()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) {
                break
            }
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase(Locale.US)] =
                    line.substring(separator + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val body = if (contentLength > 0) {
            val chars = CharArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val count = reader.read(chars, offset, contentLength - offset)
                if (count < 0) {
                    break
                }
                offset += count
            }
            String(chars, 0, offset)
        } else {
            ""
        }
        val parts = firstLine.split(' ')
        return HttpRequest(
            method = parts.getOrNull(0).orEmpty().uppercase(Locale.US),
            path = normalizePath(parts.getOrNull(1).orEmpty()),
            headers = headers,
            body = body,
        )
    }

    private fun routeRequest(request: HttpRequest): HttpResponse {
        return when {
            request.method == "GET" && request.path == "/dlna/description.xml" -> HttpResponse(
                status = "200 OK",
                contentType = "text/xml; charset=\"utf-8\"",
                body = DlnaProtocol.buildDeviceDescriptionXml(config),
            )

            request.method == "GET" && request.path == "/dlna/avtransport/scpd.xml" -> serviceResponse(
                DlnaProtocol.AvTransportServiceType,
            )

            request.method == "GET" && request.path == "/dlna/renderingcontrol/scpd.xml" -> serviceResponse(
                DlnaProtocol.RenderingControlServiceType,
            )

            request.method == "GET" && request.path == "/dlna/connectionmanager/scpd.xml" -> serviceResponse(
                DlnaProtocol.ConnectionManagerServiceType,
            )

            request.method == "POST" && request.path == "/dlna/avtransport/control" -> handleAvTransport(
                request,
            )

            request.method == "POST" && request.path == "/dlna/renderingcontrol/control" -> handleRenderingControl(
                request,
            )

            request.method == "POST" && request.path == "/dlna/connectionmanager/control" -> handleConnectionManager(
                request,
            )

            else -> HttpResponse(
                status = "404 Not Found",
                contentType = "text/plain; charset=\"utf-8\"",
                body = "Not Found",
            )
        }
    }

    private fun serviceResponse(serviceType: String): HttpResponse {
        return HttpResponse(
            status = "200 OK",
            contentType = "text/xml; charset=\"utf-8\"",
            body = DlnaProtocol.serviceScpdXml(serviceType),
        )
    }

    private fun handleAvTransport(request: HttpRequest): HttpResponse {
        val action = DlnaProtocol.extractSoapAction(request.headers["soapaction"])
            ?: return soapFault("Missing SOAP action")
        val body = when (action) {
            "SetAVTransportURI" -> {
                val uri = DlnaProtocol.extractXmlTag(request.body, "CurrentURI")
                val metadata = DlnaProtocol.extractXmlTag(request.body, "CurrentURIMetaData")
                if (!uri.isNullOrBlank()) {
                    currentUri = uri
                    onMediaRequest(DlnaMediaRequest(uri = uri, metadata = metadata))
                }
                DlnaProtocol.soapResponse(action, DlnaProtocol.AvTransportServiceType)
            }

            "GetTransportInfo" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.AvTransportServiceType,
                body = buildString {
                    append("<CurrentTransportState>")
                    append(if (currentUri.isNullOrBlank()) "NO_MEDIA_PRESENT" else "STOPPED")
                    append("</CurrentTransportState>")
                    append("<CurrentTransportStatus>OK</CurrentTransportStatus>")
                    append("<CurrentSpeed>1</CurrentSpeed>")
                },
            )

            "GetMediaInfo" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.AvTransportServiceType,
                body = buildString {
                    append("<NrTracks>").append(if (currentUri.isNullOrBlank()) "0" else "1").append("</NrTracks>")
                    append("<MediaDuration>00:00:00</MediaDuration>")
                    append("<CurrentURI>").append(DlnaProtocol.xmlEscape(currentUri.orEmpty())).append("</CurrentURI>")
                    append("<CurrentURIMetaData></CurrentURIMetaData>")
                    append("<NextURI></NextURI><NextURIMetaData></NextURIMetaData>")
                    append("<PlayMedium>NETWORK</PlayMedium><RecordMedium>NOT_IMPLEMENTED</RecordMedium>")
                    append("<WriteStatus>NOT_IMPLEMENTED</WriteStatus>")
                },
            )

            "GetPositionInfo" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.AvTransportServiceType,
                body = buildString {
                    append("<Track>0</Track><TrackDuration>00:00:00</TrackDuration>")
                    append("<TrackMetaData></TrackMetaData>")
                    append("<TrackURI>").append(DlnaProtocol.xmlEscape(currentUri.orEmpty())).append("</TrackURI>")
                    append("<RelTime>00:00:00</RelTime><AbsTime>00:00:00</AbsTime>")
                    append("<RelCount>0</RelCount><AbsCount>0</AbsCount>")
                },
            )

            "Play",
            "Pause",
            "Stop",
            "Seek",
            -> DlnaProtocol.soapResponse(action, DlnaProtocol.AvTransportServiceType)

            else -> return soapFault("Unsupported AVTransport action: $action")
        }
        return soapOk(body)
    }

    private fun handleRenderingControl(request: HttpRequest): HttpResponse {
        val action = DlnaProtocol.extractSoapAction(request.headers["soapaction"])
            ?: return soapFault("Missing SOAP action")
        val body = when (action) {
            "GetVolume" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.RenderingControlServiceType,
                body = "<CurrentVolume>100</CurrentVolume>",
            )

            "GetMute" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.RenderingControlServiceType,
                body = "<CurrentMute>0</CurrentMute>",
            )

            "SetVolume",
            "SetMute",
            -> DlnaProtocol.soapResponse(action, DlnaProtocol.RenderingControlServiceType)

            else -> return soapFault("Unsupported RenderingControl action: $action")
        }
        return soapOk(body)
    }

    private fun handleConnectionManager(request: HttpRequest): HttpResponse {
        val action = DlnaProtocol.extractSoapAction(request.headers["soapaction"])
            ?: return soapFault("Missing SOAP action")
        val body = when (action) {
            "GetProtocolInfo" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.ConnectionManagerServiceType,
                body = buildString {
                    append("<Source></Source>")
                    append("<Sink>")
                    append("http-get:*:video/mp4:DLNA.ORG_OP=01,http-get:*:video/*:*,")
                    append("http-get:*:audio/mpeg:DLNA.ORG_OP=01,http-get:*:audio/*:*,")
                    append("http-get:*:image/jpeg:DLNA.ORG_OP=01,http-get:*:image/*:*")
                    append("</Sink>")
                },
            )

            "GetCurrentConnectionIDs" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.ConnectionManagerServiceType,
                body = "<ConnectionIDs>0</ConnectionIDs>",
            )

            "GetCurrentConnectionInfo" -> DlnaProtocol.soapResponse(
                action = action,
                serviceType = DlnaProtocol.ConnectionManagerServiceType,
                body = buildString {
                    append("<RcsID>0</RcsID><AVTransportID>0</AVTransportID>")
                    append("<ProtocolInfo></ProtocolInfo><PeerConnectionManager></PeerConnectionManager>")
                    append("<PeerConnectionID>-1</PeerConnectionID><Direction>Input</Direction><Status>OK</Status>")
                },
            )

            else -> return soapFault("Unsupported ConnectionManager action: $action")
        }
        return soapOk(body)
    }

    private fun soapOk(body: String): HttpResponse {
        return HttpResponse(
            status = "200 OK",
            contentType = "text/xml; charset=\"utf-8\"",
            body = body,
        )
    }

    private fun soapFault(message: String): HttpResponse {
        return HttpResponse(
            status = "500 Internal Server Error",
            contentType = "text/xml; charset=\"utf-8\"",
            body = DlnaProtocol.soapFault(message),
        )
    }

    private suspend fun runSsdpResponder(
        location: String,
        networkInterface: NetworkInterface,
    ) {
        val group = InetAddress.getByName(DlnaProtocol.SsdpAddress)
        val socket = MulticastSocket(null).apply {
            reuseAddress = true
            soTimeout = SsdpSocketTimeoutMillis
            bind(InetSocketAddress(DlnaProtocol.SsdpPort))
            setNetworkInterface(networkInterface)
            timeToLive = SsdpTtl
        }
        ssdpSocket = socket
        joinSsdpGroup(socket, group, networkInterface)
        runCatching { sendAlive(socket, group, location) }
        var nextAliveAtMs = System.currentTimeMillis() + SsdpAliveIntervalMillis
        val buffer = ByteArray(4096)
        try {
            while (isRunning && currentCoroutineContext().isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                val receivedPacket = runCatching {
                    socket.receive(packet)
                    packet
                }.getOrNull()
                val nowMs = System.currentTimeMillis()
                if (nowMs >= nextAliveAtMs) {
                    runCatching { sendAlive(socket, group, location) }
                    nextAliveAtMs = nowMs + SsdpAliveIntervalMillis
                }
                if (receivedPacket == null) {
                    continue
                }
                val request = String(
                    receivedPacket.data,
                    receivedPacket.offset,
                    receivedPacket.length,
                    StandardCharsets.UTF_8,
                )
                val responses = DlnaProtocol.buildSearchResponses(config, location, request)
                responses.forEach { response ->
                    sendUdp(
                        socket = socket,
                        message = response,
                        address = receivedPacket.address,
                        port = receivedPacket.port,
                    )
                    delay(SsdpResponseDelayMillis)
                }
            }
        } finally {
            leaveSsdpGroup(socket, group, networkInterface)
            runCatching { socket.close() }
        }
    }

    private suspend fun sendAlive(
        socket: MulticastSocket,
        group: InetAddress,
        location: String,
    ) {
        val messages = DlnaProtocol.buildNotifyAliveMessages(config, location)
        repeat(SsdpAliveBurstCount) { burstIndex ->
            messages.forEach { message ->
                sendUdp(socket, message, group, DlnaProtocol.SsdpPort)
                delay(SsdpNotifyDelayMillis)
            }
            if (burstIndex < SsdpAliveBurstCount - 1) {
                delay(SsdpAliveBurstGapMillis)
            }
        }
    }

    private fun sendByebye(networkInterface: NetworkInterface?) {
        runCatching {
            val group = InetAddress.getByName(DlnaProtocol.SsdpAddress)
            MulticastSocket().use { socket ->
                if (networkInterface != null) {
                    socket.setNetworkInterface(networkInterface)
                }
                socket.timeToLive = SsdpTtl
                DlnaProtocol.buildNotifyByebyeMessages(config).forEach { message ->
                    sendUdp(socket, message, group, DlnaProtocol.SsdpPort)
                }
            }
        }
    }

    private fun sendUdp(
        socket: MulticastSocket,
        message: String,
        address: InetAddress,
        port: Int,
    ) {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        socket.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    private fun joinSsdpGroup(
        socket: MulticastSocket,
        group: InetAddress,
        networkInterface: NetworkInterface,
    ) {
        val socketAddress = InetSocketAddress(group, DlnaProtocol.SsdpPort)
        runCatching { socket.joinGroup(socketAddress, networkInterface) }
            .recoverCatching {
                @Suppress("DEPRECATION")
                socket.joinGroup(group)
            }
            .getOrThrow()
    }

    private fun leaveSsdpGroup(
        socket: MulticastSocket,
        group: InetAddress,
        networkInterface: NetworkInterface,
    ) {
        val socketAddress = InetSocketAddress(group, DlnaProtocol.SsdpPort)
        if (runCatching { socket.leaveGroup(socketAddress, networkInterface) }.isFailure) {
            @Suppress("DEPRECATION")
            runCatching { socket.leaveGroup(group) }
        }
    }

    private fun writeResponse(
        output: OutputStream,
        status: String,
        contentType: String,
        body: String,
    ) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close").append("\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        output.write(header)
        output.write(bodyBytes)
        output.flush()
    }

    private fun closeSockets() {
        runCatching { serverSocket?.close() }
        runCatching { ssdpSocket?.close() }
        serverSocket = null
        ssdpSocket = null
    }

    private fun failRenderer(message: String) {
        if (!isRunning) {
            return
        }
        isRunning = false
        closeSockets()
        activeNetworkInterface = null
        onStateChanged(
            DlnaRendererState(
                displayName = config.deviceName,
                isRunning = false,
                errorMessage = message,
            ),
        )
    }

    private fun normalizePath(rawTarget: String): String {
        val target = rawTarget.substringBefore('?').ifBlank { "/" }
        if (target.startsWith("/")) {
            return target
        }
        val pathPart = target.substringAfter("://", target).substringAfter('/', "")
        return "/$pathPart"
    }

    private fun resolveLocalIpv4Endpoint(): LocalIpv4Endpoint? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses
                        .toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { address -> !address.isLoopbackAddress && !address.isLinkLocalAddress }
                        .map { address -> LocalIpv4Endpoint(networkInterface, address) }
                }
                .sortedWith(
                    compareBy<LocalIpv4Endpoint> { endpoint -> networkInterfaceScore(endpoint.networkInterface) }
                        .thenBy { endpoint -> endpoint.address.hostAddress },
                )
                .firstOrNull()
        }.getOrNull()
    }

    private fun networkInterfaceScore(networkInterface: NetworkInterface): Int {
        val name = networkInterface.name.lowercase(Locale.US)
        val base = when {
            name.startsWith("wlan") -> 0
            name.startsWith("eth") -> 1
            else -> 10
        }
        val pointToPointPenalty = if (runCatching { networkInterface.isPointToPoint }.getOrDefault(false)) {
            50
        } else {
            0
        }
        return base + pointToPointPenalty
    }

    private data class LocalIpv4Endpoint(
        val networkInterface: NetworkInterface,
        val address: Inet4Address,
    )

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private data class HttpResponse(
        val status: String,
        val contentType: String,
        val body: String,
    )

    private companion object {
        private const val SsdpSocketTimeoutMillis = 1_000
        private const val SsdpTtl = 4
        private const val SsdpResponseDelayMillis = 40L
        private const val SsdpNotifyDelayMillis = 80L
        private const val SsdpAliveBurstCount = 2
        private const val SsdpAliveBurstGapMillis = 200L
        private const val SsdpAliveIntervalMillis = 30_000L
    }
}
