package com.openclaw.tv.core.network

import com.openclaw.tv.core.network.dto.BootstrapAuthEnvelope
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.DeviceTelemetryRequestDto
import com.openclaw.tv.core.network.dto.DeviceTelemetryResponseDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LatestReleaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusRequestDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateManifestDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportEnvelope
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto
import com.openclaw.tv.core.network.dto.PolicyEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import com.openclaw.tv.core.network.dto.ModelProxyChatCompletionEnvelopeDto
import com.openclaw.tv.core.network.dto.ModelProxyChatCompletionRequestDto
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.network.dto.TvModelRenewalPaymentOrderEnvelope
import com.openclaw.tv.core.network.dto.TvModelRenewalPaymentOrderRequestDto
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.network.dto.TvVoiceCommandEnvelopeDto
import com.openclaw.tv.core.network.dto.TvVoiceCommandRequestDto
import com.openclaw.tv.core.network.dto.toModelProxyChatCompletionRequest
import com.openclaw.tv.core.network.dto.toTvVoiceCommandEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

data class TvRuntimeRequestContext(
    val countryCode: String? = null,
    val regionCode: String? = null,
    val distributionKey: String? = null,
    val packageName: String? = null,
)

interface PlatformApi {
    suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope
    suspend fun getTvHomeConfig(): TvHomeConfigDto
    suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto
    suspend fun getEntitlement(sessionToken: String): TvEntitlementSummaryDto
    suspend fun requestResourceSession(sessionToken: String, request: TvResourceSessionRequestDto): TvResourceSessionDto
    suspend fun getResourceSessionStatus(sessionToken: String, resourceSessionId: String? = null): TvResourceSessionDto
    suspend fun renewResourceSession(sessionToken: String, request: TvResourceSessionReferenceDto = TvResourceSessionReferenceDto()): TvResourceSessionDto
    suspend fun releaseResourceSession(sessionToken: String, request: TvResourceSessionReferenceDto = TvResourceSessionReferenceDto()): TvResourceSessionDto
    suspend fun postDeviceTelemetry(
        sessionToken: String,
        request: DeviceTelemetryRequestDto,
    ): DeviceTelemetryResponseDto = error("Device telemetry is not supported")

    suspend fun createModelRenewalPaymentOrder(
        sessionToken: String,
        request: TvModelRenewalPaymentOrderRequestDto = TvModelRenewalPaymentOrderRequestDto(),
    ): TvModelRenewalPaymentOrderEnvelope = error("Model renewal payment orders are not supported")

    suspend fun getModelRenewalPaymentOrderStatus(
        sessionToken: String,
        orderId: String,
    ): TvModelRenewalPaymentOrderEnvelope = error("Model renewal payment order status is not supported")

    suspend fun resolveTvVoiceCommand(
        sessionToken: String,
        request: TvVoiceCommandRequestDto,
    ): TvVoiceCommandEnvelopeDto = error("TV voice command resolution is not supported")

    suspend fun getPolicy(sessionToken: String, projectKey: String? = null): PolicyEnvelope
    suspend fun getLatestRelease(
        sessionToken: String,
        channel: String? = null,
        projectKey: String? = null,
    ): LatestReleaseEnvelope

    suspend fun getOwnApkUpdateManifest(
        sessionToken: String,
        currentVersionCode: Long,
        currentConfigVersion: Long = 0L,
        currentResourceVersion: String? = null,
    ): OwnApkUpdateManifestDto = error("Own APK update manifest is not supported")

    suspend fun postOwnApkUpdateReport(
        sessionToken: String,
        request: OwnApkUpdateReportRequestDto,
    ): OwnApkUpdateReportEnvelope = error("Own APK update reporting is not supported")

    suspend fun issueLease(sessionToken: String, request: IssueLeaseRequestDto): LeaseEnvelope
    suspend fun getLeaseStatus(sessionToken: String, request: LeaseStatusRequestDto): LeaseStatusEnvelope
    suspend fun renewLease(sessionToken: String, request: RenewLeaseRequestDto): LeaseStatusEnvelope
    suspend fun releaseLease(sessionToken: String, request: ReleaseLeaseRequestDto): ReleaseLeaseEnvelope
}

class OkHttpPlatformApi(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
    private val tvRuntimeRequestContextProvider: () -> TvRuntimeRequestContext = { TvRuntimeRequestContext() },
    private val tvRuntimeSessionTokenProvider: suspend () -> String? = { null },
) : PlatformApi {

    private val resolvedBaseUrl: HttpUrl = if (baseUrl.endsWith("/")) {
        baseUrl.toHttpUrl()
    } else {
        "$baseUrl/".toHttpUrl()
    }

    override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
        return post(
            path = "client/bootstrap/auth",
            payload = request,
            serializer = BootstrapAuthEnvelope.serializer(),
        )
    }

    override suspend fun getTvHomeConfig(): TvHomeConfigDto {
        val runtimeQuery = resolveTvRuntimeQuery()
        return get(
            path = "me/tv-home-config",
            sessionToken = tvRuntimeSessionTokenProvider(),
            query = runtimeQuery,
            serializer = TvHomeConfigDto.serializer(),
        )
    }

    override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
        val runtimeQuery = resolveTvRuntimeQuery()
        return get(
            path = "me/runtime-manifest",
            sessionToken = sessionToken,
            query = runtimeQuery,
            serializer = TvRuntimeManifestDto.serializer(),
        )
    }

    override suspend fun getEntitlement(sessionToken: String): TvEntitlementSummaryDto {
        return get(
            path = "me/entitlement",
            sessionToken = sessionToken,
            serializer = TvEntitlementSummaryDto.serializer(),
        )
    }

    override suspend fun requestResourceSession(
        sessionToken: String,
        request: TvResourceSessionRequestDto,
    ): TvResourceSessionDto {
        return post(
            path = "client/resource-session/request",
            payload = request,
            sessionToken = sessionToken,
            serializer = TvResourceSessionDto.serializer(),
        )
    }

    override suspend fun getResourceSessionStatus(
        sessionToken: String,
        resourceSessionId: String?,
    ): TvResourceSessionDto {
        return get(
            path = "client/resource-session/status",
            sessionToken = sessionToken,
            query = listOfNotNull(
                resourceSessionId?.takeIf(String::isNotBlank)?.let { "resourceSessionId" to it },
            ),
            serializer = TvResourceSessionDto.serializer(),
        )
    }

    override suspend fun renewResourceSession(
        sessionToken: String,
        request: TvResourceSessionReferenceDto,
    ): TvResourceSessionDto {
        return post(
            path = "client/resource-session/renew",
            payload = request,
            sessionToken = sessionToken,
            serializer = TvResourceSessionDto.serializer(),
        )
    }

    override suspend fun releaseResourceSession(
        sessionToken: String,
        request: TvResourceSessionReferenceDto,
    ): TvResourceSessionDto {
        return post(
            path = "client/resource-session/release",
            payload = request,
            sessionToken = sessionToken,
            serializer = TvResourceSessionDto.serializer(),
        )
    }

    override suspend fun postDeviceTelemetry(
        sessionToken: String,
        request: DeviceTelemetryRequestDto,
    ): DeviceTelemetryResponseDto {
        return post(
            path = "client/device/telemetry",
            payload = request,
            sessionToken = sessionToken,
            serializer = DeviceTelemetryResponseDto.serializer(),
        )
    }

    override suspend fun createModelRenewalPaymentOrder(
        sessionToken: String,
        request: TvModelRenewalPaymentOrderRequestDto,
    ): TvModelRenewalPaymentOrderEnvelope {
        return post(
            path = "client/model-renewal/orders",
            payload = request,
            sessionToken = sessionToken,
            serializer = TvModelRenewalPaymentOrderEnvelope.serializer(),
        )
    }

    override suspend fun getModelRenewalPaymentOrderStatus(
        sessionToken: String,
        orderId: String,
    ): TvModelRenewalPaymentOrderEnvelope {
        return get(
            path = "client/model-renewal/orders/$orderId",
            sessionToken = sessionToken,
            serializer = TvModelRenewalPaymentOrderEnvelope.serializer(),
        )
    }

    override suspend fun resolveTvVoiceCommand(
        sessionToken: String,
        request: TvVoiceCommandRequestDto,
    ): TvVoiceCommandEnvelopeDto {
        val completion = post(
            path = "model-proxy/chat/completions",
            payload = request.toModelProxyChatCompletionRequest(),
            sessionToken = sessionToken,
            serializer = ModelProxyChatCompletionEnvelopeDto.serializer(),
        )
        return completion.toTvVoiceCommandEnvelope(json)
    }

    override suspend fun getPolicy(sessionToken: String, projectKey: String?): PolicyEnvelope {
        return get(
            path = "client/policy",
            sessionToken = sessionToken,
            projectKey = projectKey,
            serializer = PolicyEnvelope.serializer(),
        )
    }

    override suspend fun getLatestRelease(
        sessionToken: String,
        channel: String?,
        projectKey: String?,
    ): LatestReleaseEnvelope {
        return get(
            path = "client/releases/latest",
            sessionToken = sessionToken,
            projectKey = projectKey,
            query = listOfNotNull(channel?.takeIf(String::isNotBlank)?.let { "channel" to it }),
            serializer = LatestReleaseEnvelope.serializer(),
        )
    }

    override suspend fun getOwnApkUpdateManifest(
        sessionToken: String,
        currentVersionCode: Long,
        currentConfigVersion: Long,
        currentResourceVersion: String?,
    ): OwnApkUpdateManifestDto {
        return get(
            path = "client/updates/manifest",
            sessionToken = sessionToken,
            query = listOfNotNull(
                "currentVersionCode" to currentVersionCode.toString(),
                "currentConfigVersion" to currentConfigVersion.toString(),
                currentResourceVersion?.takeIf(String::isNotBlank)?.let { "currentResourceVersion" to it },
            ),
            serializer = OwnApkUpdateManifestDto.serializer(),
        )
    }

    override suspend fun postOwnApkUpdateReport(
        sessionToken: String,
        request: OwnApkUpdateReportRequestDto,
    ): OwnApkUpdateReportEnvelope {
        return post(
            path = "client/updates/report",
            payload = request,
            sessionToken = sessionToken,
            serializer = OwnApkUpdateReportEnvelope.serializer(),
        )
    }

    override suspend fun issueLease(sessionToken: String, request: IssueLeaseRequestDto): LeaseEnvelope {
        return post(
            path = "client/model-lease",
            payload = request,
            sessionToken = sessionToken,
            serializer = LeaseEnvelope.serializer(),
        )
    }

    override suspend fun getLeaseStatus(sessionToken: String, request: LeaseStatusRequestDto): LeaseStatusEnvelope {
        return get(
            path = "client/model-lease/status",
            sessionToken = sessionToken,
            projectKey = request.projectKey,
            query = listOfNotNull(
                request.providerScope?.takeIf(String::isNotBlank)?.let { "providerScope" to it },
                request.leaseId?.takeIf(String::isNotBlank)?.let { "leaseId" to it },
            ),
            serializer = LeaseStatusEnvelope.serializer(),
        )
    }

    override suspend fun renewLease(sessionToken: String, request: RenewLeaseRequestDto): LeaseStatusEnvelope {
        return post(
            path = "client/model-lease/renew",
            payload = request,
            sessionToken = sessionToken,
            serializer = LeaseStatusEnvelope.serializer(),
        )
    }

    override suspend fun releaseLease(sessionToken: String, request: ReleaseLeaseRequestDto): ReleaseLeaseEnvelope {
        return post(
            path = "client/model-lease/release",
            payload = request,
            sessionToken = sessionToken,
            serializer = ReleaseLeaseEnvelope.serializer(),
        )
    }

    private suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        sessionToken: String? = null,
        projectKey: String? = null,
        query: List<Pair<String, String>> = emptyList(),
    ): T = withContext(Dispatchers.IO) {
        val url = buildUrl(path, projectKey, query)
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        if (!sessionToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $sessionToken")
        }
        execute(requestBuilder.build(), serializer)
    }

    private suspend fun <T> post(
        path: String,
        payload: Any,
        serializer: KSerializer<T>,
        sessionToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val encodedPayload = when (payload) {
            is BootstrapAuthRequestDto -> json.encodeToString(BootstrapAuthRequestDto.serializer(), payload)
            is IssueLeaseRequestDto -> json.encodeToString(IssueLeaseRequestDto.serializer(), payload)
            is RenewLeaseRequestDto -> json.encodeToString(RenewLeaseRequestDto.serializer(), payload)
            is ReleaseLeaseRequestDto -> json.encodeToString(ReleaseLeaseRequestDto.serializer(), payload)
            is TvResourceSessionRequestDto -> json.encodeToString(TvResourceSessionRequestDto.serializer(), payload)
            is TvResourceSessionReferenceDto -> json.encodeToString(TvResourceSessionReferenceDto.serializer(), payload)
            is DeviceTelemetryRequestDto -> json.encodeToString(DeviceTelemetryRequestDto.serializer(), payload)
            is OwnApkUpdateReportRequestDto -> json.encodeToString(OwnApkUpdateReportRequestDto.serializer(), payload)
            is ModelProxyChatCompletionRequestDto ->
                json.encodeToString(ModelProxyChatCompletionRequestDto.serializer(), payload)
            is TvModelRenewalPaymentOrderRequestDto ->
                if (payload.sku.isNullOrBlank()) {
                    "{}"
                } else {
                    json.encodeToString(TvModelRenewalPaymentOrderRequestDto.serializer(), payload)
                }
            else -> throw IllegalArgumentException("Unsupported payload ${payload::class.java.simpleName}")
        }

        val requestBuilder = Request.Builder()
            .url(buildUrl(path))
            .post(encodedPayload.toRequestBody(JsonMediaType))
        if (!sessionToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $sessionToken")
        }
        execute(requestBuilder.build(), serializer)
    }

    private fun buildUrl(
        path: String,
        projectKey: String? = null,
        query: List<Pair<String, String>> = emptyList(),
    ): HttpUrl {
        val builder = resolvedBaseUrl.newBuilder()
        path.split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        if (!projectKey.isNullOrBlank()) {
            builder.addQueryParameter("projectKey", projectKey)
        }
        query.forEach { (name, value) ->
            builder.addQueryParameter(name, value)
        }
        return builder.build()
    }

    private fun resolveTvRuntimeQuery(): List<Pair<String, String>> {
        val context = tvRuntimeRequestContextProvider()
        val countryCode = context.countryCode?.trim()?.takeIf(String::isNotBlank)
        val regionCode = context.regionCode?.trim()?.takeIf(String::isNotBlank)
        val distributionKey = context.distributionKey?.trim()?.takeIf(String::isNotBlank)
        val packageName = context.packageName?.trim()?.takeIf(String::isNotBlank)
        return buildList {
            if (countryCode != null) {
                add("countryCode" to countryCode)
            }
            if (regionCode != null) {
                add("regionCode" to regionCode)
            }
            if (distributionKey != null) {
                add("distributionKey" to distributionKey)
            }
            if (packageName != null) {
                add("packageName" to packageName)
            }
        }
    }

    private fun <T> execute(
        request: Request,
        serializer: KSerializer<T>,
    ): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw PlatformApiException(response.code, body)
            }
            return try {
                json.decodeFromString(serializer, body)
            } catch (error: SerializationException) {
                throw PlatformApiException(response.code, body, error)
            }
        }
    }
}

class PlatformApiException(
    val statusCode: Int,
    val rawBody: String,
    cause: Throwable? = null,
) : RuntimeException("Platform API request failed with HTTP $statusCode", cause)
