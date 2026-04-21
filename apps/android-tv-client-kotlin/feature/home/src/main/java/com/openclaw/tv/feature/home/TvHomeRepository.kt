package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.storage.StoredTvHomeConfig
import com.openclaw.tv.core.storage.TvHomeConfigStore
import kotlinx.coroutines.withTimeout

data class ResolvedTvHomeConfig(
    val projectKey: String,
    val projectLabel: String,
    val runtimeManifestPath: String,
    val entitlementPath: String,
    val resourceSessionBasePath: String,
    val manifestPollAfterSeconds: Int,
    val resourceSessionPollAfterSeconds: Int,
    val backgroundDownloadEnabled: Boolean,
    val idleDownloadOnly: Boolean,
    val source: ConfigSource,
)

enum class ConfigSource {
    REMOTE,
    CACHE,
    FALLBACK,
}

class TvHomeRepository(
    private val platformApi: PlatformApi,
    private val cacheStore: TvHomeConfigStore? = null,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun load(): ResolvedTvHomeConfig {
        return try {
            val response = withTimeout(requestTimeoutMillis) {
                platformApi.getTvHomeConfig()
            }
            val resolved = response.toResolvedConfig(source = ConfigSource.REMOTE)
            cacheStore?.save(
                response.toStoredConfig(
                    cachedAtEpochMs = nowEpochMs(),
                ),
            )
            resolved
        } catch (_: Exception) {
            cacheStore?.read()?.toResolvedConfig(source = ConfigSource.CACHE) ?: fallback()
        }
    }

    companion object {
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L

        fun fallback(): ResolvedTvHomeConfig {
            return ResolvedTvHomeConfig(
                projectKey = "openclaw-android-tv",
                projectLabel = "OpenClaw Android TV",
                runtimeManifestPath = "/api/me/runtime-manifest",
                entitlementPath = "/api/me/entitlement",
                resourceSessionBasePath = "/api/client/resource-session",
                manifestPollAfterSeconds = 900,
                resourceSessionPollAfterSeconds = 15,
                backgroundDownloadEnabled = true,
                idleDownloadOnly = true,
                source = ConfigSource.FALLBACK,
            )
        }
    }
}

internal fun TvHomeConfigDto.toResolvedConfig(source: ConfigSource): ResolvedTvHomeConfig {
    return ResolvedTvHomeConfig(
        projectKey = projectKey.trim().ifBlank { "openclaw-android-tv" },
        projectLabel = projectLabel.trim().ifBlank { "OpenClaw Android TV" },
        runtimeManifestPath = runtimeManifestPath.trim().ifBlank { "/api/me/runtime-manifest" },
        entitlementPath = entitlementPath.trim().ifBlank { "/api/me/entitlement" },
        resourceSessionBasePath = resourceSessionBasePath.trim().ifBlank { "/api/client/resource-session" },
        manifestPollAfterSeconds = manifestPollAfterSeconds.coerceAtLeast(1),
        resourceSessionPollAfterSeconds = resourceSessionPollAfterSeconds.coerceAtLeast(1),
        backgroundDownloadEnabled = backgroundDownloadEnabled,
        idleDownloadOnly = idleDownloadOnly,
        source = source,
    )
}

internal fun TvHomeConfigDto.toStoredConfig(cachedAtEpochMs: Long): StoredTvHomeConfig {
    return StoredTvHomeConfig(
        projectKey = projectKey.trim().ifBlank { "openclaw-android-tv" },
        projectLabel = projectLabel.trim().ifBlank { "OpenClaw Android TV" },
        runtimeManifestPath = runtimeManifestPath.trim().ifBlank { "/api/me/runtime-manifest" },
        entitlementPath = entitlementPath.trim().ifBlank { "/api/me/entitlement" },
        resourceSessionBasePath = resourceSessionBasePath.trim().ifBlank { "/api/client/resource-session" },
        manifestPollAfterSeconds = manifestPollAfterSeconds.coerceAtLeast(1),
        resourceSessionPollAfterSeconds = resourceSessionPollAfterSeconds.coerceAtLeast(1),
        backgroundDownloadEnabled = backgroundDownloadEnabled,
        idleDownloadOnly = idleDownloadOnly,
        cachedAtEpochMs = cachedAtEpochMs,
    )
}

internal fun StoredTvHomeConfig.toResolvedConfig(source: ConfigSource): ResolvedTvHomeConfig {
    return ResolvedTvHomeConfig(
        projectKey = projectKey,
        projectLabel = projectLabel,
        runtimeManifestPath = runtimeManifestPath,
        entitlementPath = entitlementPath,
        resourceSessionBasePath = resourceSessionBasePath,
        manifestPollAfterSeconds = manifestPollAfterSeconds,
        resourceSessionPollAfterSeconds = resourceSessionPollAfterSeconds,
        backgroundDownloadEnabled = backgroundDownloadEnabled,
        idleDownloadOnly = idleDownloadOnly,
        source = source,
    )
}
