package com.openclaw.tv.core.network

import com.openclaw.tv.core.network.dto.BootstrapAuthEnvelope
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LatestReleaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusRequestDto
import com.openclaw.tv.core.network.dto.PolicyEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
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

interface PlatformApi {
    suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope
    suspend fun getTvHomeConfig(): TvHomeConfigDto
    suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto
    suspend fun getPolicy(sessionToken: String, projectKey: String? = null): PolicyEnvelope
    suspend fun getLatestRelease(
        sessionToken: String,
        channel: String? = null,
        projectKey: String? = null,
    ): LatestReleaseEnvelope

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
        return get(
            path = "me/tv-home-config",
            serializer = TvHomeConfigDto.serializer(),
        )
    }

    override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
        return get(
            path = "me/runtime-manifest",
            sessionToken = sessionToken,
            serializer = TvRuntimeManifestDto.serializer(),
        )
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
