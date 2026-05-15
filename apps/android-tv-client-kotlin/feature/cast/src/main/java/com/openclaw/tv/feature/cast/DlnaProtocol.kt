package com.openclaw.tv.feature.cast

import java.util.Locale

internal object DlnaProtocol {
    const val SsdpAddress = "239.255.255.250"
    const val SsdpPort = 1900
    const val MediaRendererDeviceType = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AvTransportServiceType = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RenderingControlServiceType = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val ConnectionManagerServiceType = "urn:schemas-upnp-org:service:ConnectionManager:1"

    private const val LineBreak = "\r\n"

    fun searchTargets(uuid: String): List<String> {
        return listOf(
            "upnp:rootdevice",
            "uuid:$uuid",
            MediaRendererDeviceType,
            AvTransportServiceType,
            RenderingControlServiceType,
            ConnectionManagerServiceType,
        )
    }

    fun matchingSearchTargets(
        request: String,
        uuid: String,
    ): List<String> {
        if (!request.startsWith("M-SEARCH", ignoreCase = true)) {
            return emptyList()
        }
        val discoveryHeader = headerValue(request, "MAN") ?: return emptyList()
        if (!discoveryHeader.contains("ssdp:discover", ignoreCase = true)) {
            return emptyList()
        }
        val searchTarget = headerValue(request, "ST")?.lowercase(Locale.US) ?: return emptyList()
        val targets = searchTargets(uuid)
        return when (searchTarget) {
            "ssdp:all" -> targets
            else -> targets.filter { target -> target.lowercase(Locale.US) == searchTarget }
        }
    }

    fun buildSearchResponses(
        config: DlnaRendererConfig,
        location: String,
        request: String,
    ): List<String> {
        return matchingSearchTargets(request, config.uuid).map { target ->
            buildString {
                append("HTTP/1.1 200 OK").append(LineBreak)
                append("CACHE-CONTROL: max-age=1800").append(LineBreak)
                append("EXT:").append(LineBreak)
                append("LOCATION: ").append(location).append(LineBreak)
                append("SERVER: Android, UPnP/1.0, OpenClawTV/").append(config.modelNumber).append(LineBreak)
                append("ST: ").append(target).append(LineBreak)
                append("USN: ").append(usn(config.uuid, target)).append(LineBreak)
                append(LineBreak)
            }
        }
    }

    fun buildNotifyAliveMessages(
        config: DlnaRendererConfig,
        location: String,
    ): List<String> {
        return searchTargets(config.uuid).map { target ->
            buildString {
                append("NOTIFY * HTTP/1.1").append(LineBreak)
                append("HOST: ").append(SsdpAddress).append(':').append(SsdpPort).append(LineBreak)
                append("CACHE-CONTROL: max-age=1800").append(LineBreak)
                append("LOCATION: ").append(location).append(LineBreak)
                append("NT: ").append(target).append(LineBreak)
                append("NTS: ssdp:alive").append(LineBreak)
                append("SERVER: Android, UPnP/1.0, OpenClawTV/").append(config.modelNumber).append(LineBreak)
                append("USN: ").append(usn(config.uuid, target)).append(LineBreak)
                append(LineBreak)
            }
        }
    }

    fun buildNotifyByebyeMessages(config: DlnaRendererConfig): List<String> {
        return searchTargets(config.uuid).map { target ->
            buildString {
                append("NOTIFY * HTTP/1.1").append(LineBreak)
                append("HOST: ").append(SsdpAddress).append(':').append(SsdpPort).append(LineBreak)
                append("NT: ").append(target).append(LineBreak)
                append("NTS: ssdp:byebye").append(LineBreak)
                append("USN: ").append(usn(config.uuid, target)).append(LineBreak)
                append(LineBreak)
            }
        }
    }

    fun buildDeviceDescriptionXml(config: DlnaRendererConfig): String {
        val friendlyName = xmlEscape(config.deviceName)
        val manufacturer = xmlEscape(config.manufacturer)
        val modelName = xmlEscape(config.modelName)
        val modelNumber = xmlEscape(config.modelNumber)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
              <specVersion>
                <major>1</major>
                <minor>0</minor>
              </specVersion>
              <device>
                <deviceType>$MediaRendererDeviceType</deviceType>
                <dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>
                <friendlyName>$friendlyName</friendlyName>
                <manufacturer>$manufacturer</manufacturer>
                <manufacturerURL>https://souleye.cc</manufacturerURL>
                <modelName>$modelName</modelName>
                <modelDescription>OpenClaw TV DLNA Media Renderer</modelDescription>
                <modelNumber>$modelNumber</modelNumber>
                <serialNumber>${config.uuid.take(8)}</serialNumber>
                <UDN>uuid:${config.uuid}</UDN>
                <presentationURL>/</presentationURL>
                <serviceList>
                  <service>
                    <serviceType>$AvTransportServiceType</serviceType>
                    <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                    <SCPDURL>/dlna/avtransport/scpd.xml</SCPDURL>
                    <controlURL>/dlna/avtransport/control</controlURL>
                    <eventSubURL>/dlna/avtransport/event</eventSubURL>
                  </service>
                  <service>
                    <serviceType>$RenderingControlServiceType</serviceType>
                    <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                    <SCPDURL>/dlna/renderingcontrol/scpd.xml</SCPDURL>
                    <controlURL>/dlna/renderingcontrol/control</controlURL>
                    <eventSubURL>/dlna/renderingcontrol/event</eventSubURL>
                  </service>
                  <service>
                    <serviceType>$ConnectionManagerServiceType</serviceType>
                    <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
                    <SCPDURL>/dlna/connectionmanager/scpd.xml</SCPDURL>
                    <controlURL>/dlna/connectionmanager/control</controlURL>
                    <eventSubURL>/dlna/connectionmanager/event</eventSubURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()
    }

    fun serviceScpdXml(serviceType: String): String {
        val actions = when (serviceType) {
            AvTransportServiceType -> listOf(
                "SetAVTransportURI",
                "GetTransportInfo",
                "GetMediaInfo",
                "GetPositionInfo",
                "Play",
                "Pause",
                "Stop",
                "Seek",
            )
            RenderingControlServiceType -> listOf("GetVolume", "SetVolume", "GetMute", "SetMute")
            ConnectionManagerServiceType -> listOf(
                "GetProtocolInfo",
                "GetCurrentConnectionIDs",
                "GetCurrentConnectionInfo",
            )
            else -> emptyList()
        }
        return buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<scpd xmlns="urn:schemas-upnp-org:service-1-0">""")
            append("<specVersion><major>1</major><minor>0</minor></specVersion>")
            append("<actionList>")
            actions.forEach { action ->
                append("<action><name>").append(action).append("</name></action>")
            }
            append("</actionList>")
            append("<serviceStateTable>")
            append("""<stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>""")
            append("""<stateVariable sendEvents="no"><name>A_ARG_TYPE_URI</name><dataType>string</dataType></stateVariable>""")
            append("""<stateVariable sendEvents="no"><name>TransportState</name><dataType>string</dataType></stateVariable>""")
            append("</serviceStateTable>")
            append("</scpd>")
        }
    }

    fun soapResponse(
        action: String,
        serviceType: String,
        body: String = "",
    ): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:${action}Response xmlns:u="$serviceType">$body</u:${action}Response>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun soapFault(message: String): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <s:Fault>
                  <faultcode>s:Client</faultcode>
                  <faultstring>${xmlEscape(message)}</faultstring>
                </s:Fault>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun extractSoapAction(value: String?): String? {
        return value
            ?.trim()
            ?.trim('"')
            ?.substringAfterLast('#')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun extractXmlTag(
        xml: String,
        tag: String,
    ): String? {
        val pattern = Regex(
            pattern = "<(?:\\w+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$tag>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return pattern.find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::xmlUnescape)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun xmlUnescape(value: String): String {
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun headerValue(
        message: String,
        header: String,
    ): String? {
        val expectedPrefix = "${header.lowercase(Locale.US)}:"
        return message
            .lineSequence()
            .firstOrNull { line -> line.trimStart().lowercase(Locale.US).startsWith(expectedPrefix) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun usn(
        uuid: String,
        target: String,
    ): String {
        return if (target == "uuid:$uuid") {
            "uuid:$uuid"
        } else {
            "uuid:$uuid::$target"
        }
    }
}
