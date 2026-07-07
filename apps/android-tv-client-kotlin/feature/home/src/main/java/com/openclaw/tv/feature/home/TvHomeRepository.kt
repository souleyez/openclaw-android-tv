package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.storage.StoredTvHomeConfig
import com.openclaw.tv.core.storage.StoredTvHomeApp
import com.openclaw.tv.core.storage.StoredTvHomeBranding
import com.openclaw.tv.core.storage.StoredTvHomeCustomer
import com.openclaw.tv.core.storage.StoredTvHomeDistribution
import com.openclaw.tv.core.storage.StoredTvHomeTheme
import com.openclaw.tv.core.storage.StoredTvHotelService
import com.openclaw.tv.core.storage.TvHomeConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class ResolvedTvHomeConfig(
    val projectKey: String,
    val projectLabel: String,
    val customer: ResolvedTvHomeCustomer?,
    val distribution: ResolvedTvHomeDistribution?,
    val branding: ResolvedTvHomeBranding?,
    val theme: ResolvedTvHomeTheme?,
    val homeApps: List<ResolvedTvHomeApp>,
    val hotelServices: List<ResolvedTvHotelService>,
    val runtimeManifestPath: String,
    val entitlementPath: String,
    val resourceSessionBasePath: String,
    val manifestPollAfterSeconds: Int,
    val resourceSessionPollAfterSeconds: Int,
    val backgroundDownloadEnabled: Boolean,
    val idleDownloadOnly: Boolean,
    val source: ConfigSource,
)

data class ResolvedTvHomeCustomer(
    val id: String,
    val slug: String,
    val displayName: String,
    val hotelName: String,
)

data class ResolvedTvHomeDistribution(
    val distributionKey: String,
    val packageName: String,
    val releaseChannel: String,
)

data class ResolvedTvHomeBranding(
    val logoUrl: String,
    val intro: String,
    val versionLabel: String,
)

data class ResolvedTvHomeTheme(
    val defaultMode: String,
    val switcherEnabled: Boolean,
    val dayPalette: Map<String, String>,
    val nightPalette: Map<String, String>,
)

data class ResolvedTvHomeApp(
    val appId: String,
    val title: String,
    val packageName: String,
    val sortOrder: Int,
)

data class ResolvedTvHotelService(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String,
    val actionType: String,
    val actionValue: String,
    val sortOrder: Int,
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
        } catch (error: Exception) {
            error.rethrowIfExternalCancellation()
            cacheStore?.read()?.toResolvedConfig(source = ConfigSource.CACHE) ?: fallback()
        }
    }

    companion object {
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L

        fun fallback(): ResolvedTvHomeConfig {
            return ResolvedTvHomeConfig(
                projectKey = "openclaw-android-tv",
                projectLabel = "OpenClaw Android TV",
                customer = null,
                distribution = null,
                branding = null,
                theme = null,
                homeApps = emptyList(),
                hotelServices = emptyList(),
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

private fun Throwable.rethrowIfExternalCancellation() {
    if (this is CancellationException && this !is TimeoutCancellationException) {
        throw this
    }
}

internal fun TvHomeConfigDto.toResolvedConfig(source: ConfigSource): ResolvedTvHomeConfig {
    return ResolvedTvHomeConfig(
        projectKey = projectKey.trim().ifBlank { "openclaw-android-tv" },
        projectLabel = projectLabel.trim().ifBlank { "OpenClaw Android TV" },
        customer = customer?.let {
            ResolvedTvHomeCustomer(
                id = it.id.trim(),
                slug = it.slug.trim(),
                displayName = it.displayName.trim(),
                hotelName = it.hotelName.trim(),
            )
        },
        distribution = distribution?.let {
            ResolvedTvHomeDistribution(
                distributionKey = it.distributionKey.trim(),
                packageName = it.packageName.trim(),
                releaseChannel = it.releaseChannel.trim(),
            )
        },
        branding = branding?.let {
            ResolvedTvHomeBranding(
                logoUrl = it.logoUrl.trim(),
                intro = it.intro.trim(),
                versionLabel = it.versionLabel.trim(),
            )
        },
        theme = theme?.let {
            ResolvedTvHomeTheme(
                defaultMode = it.defaultMode.trim().lowercase().ifBlank { "night" },
                switcherEnabled = it.switcherEnabled,
                dayPalette = it.dayPalette.filterKeys(String::isNotBlank).filterValues(String::isNotBlank),
                nightPalette = it.nightPalette.filterKeys(String::isNotBlank).filterValues(String::isNotBlank),
            )
        },
        homeApps = homeApps
            .map {
                ResolvedTvHomeApp(
                    appId = it.appId.trim(),
                    title = it.title.trim(),
                    packageName = it.packageName.trim(),
                    sortOrder = it.sortOrder,
                )
            }
            .filter { it.appId.isNotBlank() || it.packageName.isNotBlank() }
            .sortedWith(compareBy<ResolvedTvHomeApp> { it.sortOrder }.thenBy { it.title }),
        hotelServices = hotelServices
            .map {
                ResolvedTvHotelService(
                    id = it.id.trim(),
                    title = it.title.trim(),
                    summary = it.summary.trim(),
                    imageUrl = it.imageUrl.trim(),
                    actionType = it.actionType.trim(),
                    actionValue = it.actionValue.trim(),
                    sortOrder = it.sortOrder,
                )
            }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .sortedWith(compareBy<ResolvedTvHotelService> { it.sortOrder }.thenBy { it.title }),
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
        customer = customer?.let {
            StoredTvHomeCustomer(
                id = it.id.trim(),
                slug = it.slug.trim(),
                displayName = it.displayName.trim(),
                hotelName = it.hotelName.trim(),
            )
        },
        distribution = distribution?.let {
            StoredTvHomeDistribution(
                distributionKey = it.distributionKey.trim(),
                packageName = it.packageName.trim(),
                releaseChannel = it.releaseChannel.trim(),
            )
        },
        branding = branding?.let {
            StoredTvHomeBranding(
                logoUrl = it.logoUrl.trim(),
                intro = it.intro.trim(),
                versionLabel = it.versionLabel.trim(),
            )
        },
        theme = theme?.let {
            StoredTvHomeTheme(
                defaultMode = it.defaultMode.trim().lowercase().ifBlank { "night" },
                switcherEnabled = it.switcherEnabled,
                dayPalette = it.dayPalette.filterKeys(String::isNotBlank).filterValues(String::isNotBlank),
                nightPalette = it.nightPalette.filterKeys(String::isNotBlank).filterValues(String::isNotBlank),
            )
        },
        homeApps = homeApps
            .map {
                StoredTvHomeApp(
                    appId = it.appId.trim(),
                    title = it.title.trim(),
                    packageName = it.packageName.trim(),
                    sortOrder = it.sortOrder,
                )
            }
            .filter { it.appId.isNotBlank() || it.packageName.isNotBlank() },
        hotelServices = hotelServices
            .map {
                StoredTvHotelService(
                    id = it.id.trim(),
                    title = it.title.trim(),
                    summary = it.summary.trim(),
                    imageUrl = it.imageUrl.trim(),
                    actionType = it.actionType.trim(),
                    actionValue = it.actionValue.trim(),
                    sortOrder = it.sortOrder,
                )
            }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() },
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
        customer = customer?.let {
            ResolvedTvHomeCustomer(
                id = it.id,
                slug = it.slug,
                displayName = it.displayName,
                hotelName = it.hotelName,
            )
        },
        distribution = distribution?.let {
            ResolvedTvHomeDistribution(
                distributionKey = it.distributionKey,
                packageName = it.packageName,
                releaseChannel = it.releaseChannel,
            )
        },
        branding = branding?.let {
            ResolvedTvHomeBranding(
                logoUrl = it.logoUrl,
                intro = it.intro,
                versionLabel = it.versionLabel,
            )
        },
        theme = theme?.let {
            ResolvedTvHomeTheme(
                defaultMode = it.defaultMode,
                switcherEnabled = it.switcherEnabled,
                dayPalette = it.dayPalette,
                nightPalette = it.nightPalette,
            )
        },
        homeApps = homeApps.map {
            ResolvedTvHomeApp(
                appId = it.appId,
                title = it.title,
                packageName = it.packageName,
                sortOrder = it.sortOrder,
            )
        }.sortedWith(compareBy<ResolvedTvHomeApp> { it.sortOrder }.thenBy { it.title }),
        hotelServices = hotelServices.map {
            ResolvedTvHotelService(
                id = it.id,
                title = it.title,
                summary = it.summary,
                imageUrl = it.imageUrl,
                actionType = it.actionType,
                actionValue = it.actionValue,
                sortOrder = it.sortOrder,
            )
        }.sortedWith(compareBy<ResolvedTvHotelService> { it.sortOrder }.thenBy { it.title }),
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
