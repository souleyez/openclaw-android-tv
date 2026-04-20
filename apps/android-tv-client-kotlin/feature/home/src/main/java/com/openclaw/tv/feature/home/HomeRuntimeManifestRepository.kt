package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvRuntimeAdCreativeDto
import com.openclaw.tv.core.network.dto.TvRuntimeAdSlotDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestAppDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.RuntimeManifestStore
import com.openclaw.tv.core.storage.StoredRuntimeAdCreative
import com.openclaw.tv.core.storage.StoredRuntimeAdSlot
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HeroAdItem(
    val creativeId: String,
    val imageUrl: String,
    val altText: String,
    val clickActionType: String,
    val clickActionValue: String?,
)

internal data class RuntimeFeaturedApp(
    val appId: String,
    val title: String,
    val packageName: String,
    val summary: String,
    val monogram: String,
    val accentColorHex: String,
    val installMode: String,
    val requiresEntitlement: Boolean,
)

internal data class ResolvedRuntimeManifest(
    val manifestVersion: String,
    val countryCode: String?,
    val regionCode: String?,
    val source: RuntimeManifestSource,
    val featuredApps: List<RuntimeFeaturedApp>,
    val ignoredFeaturedAppIds: List<String>,
    val heroAds: List<HeroAdItem>,
)

internal enum class RuntimeManifestSource {
    REMOTE,
    CACHE,
    FALLBACK,
}

internal open class HomeRuntimeManifestRepository(
    private val platformApi: PlatformApi,
    private val cacheStore: RuntimeManifestStore? = null,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    open suspend fun load(sessionToken: String): ResolvedRuntimeManifest {
        return try {
            val manifest = platformApi.getRuntimeManifest(sessionToken)
            val resolved = manifest.toResolvedRuntimeManifest(
                source = RuntimeManifestSource.REMOTE,
                nowIso = currentUtcIsoString(),
            )
            cacheStore?.save(
                manifest.toStoredRuntimeManifest(
                    cachedAtEpochMs = nowEpochMs(),
                ),
            )
            resolved
        } catch (_: Exception) {
            cacheStore?.read()?.toResolvedRuntimeManifest(
                source = RuntimeManifestSource.CACHE,
                nowIso = currentUtcIsoString(),
            ) ?: fallback()
        }
    }

    open fun fallback(): ResolvedRuntimeManifest {
        return ResolvedRuntimeManifest(
            manifestVersion = "",
            countryCode = null,
            regionCode = null,
            source = RuntimeManifestSource.FALLBACK,
            featuredApps = HomeAppCatalog.defaultFeaturedApps().map { app ->
                RuntimeFeaturedApp(
                    appId = app.id,
                    title = app.title,
                    packageName = app.packageName,
                    summary = app.summary,
                    monogram = app.monogram,
                    accentColorHex = app.accentColorHex,
                    installMode = "prompt",
                    requiresEntitlement = false,
                )
            },
            ignoredFeaturedAppIds = emptyList(),
            heroAds = emptyList(),
        )
    }

    private fun currentUtcIsoString(): String {
        return ISO_UTC_FORMAT.format(Date(nowEpochMs()))
    }

    private companion object {
        const val HOME_HERO_SLOT_ID = "home.hero"

        val ISO_UTC_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun TvRuntimeManifestDto.toStoredRuntimeManifest(
        cachedAtEpochMs: Long,
    ): StoredRuntimeManifest {
        return StoredRuntimeManifest(
            manifestVersion = manifestVersion,
            countryCode = countryCode.trim().ifBlank { "GLOBAL" },
            regionCode = regionCode?.trim()?.takeIf(String::isNotBlank),
            apps = apps.map { app ->
                StoredRuntimeApp(
                    appId = app.appId,
                    title = app.title,
                    packageName = app.packageName,
                    downloadUrl = app.downloadUrl,
                    sha256 = app.sha256,
                    versionCode = app.versionCode,
                    versionName = app.versionName,
                    minClientVersion = app.minClientVersion,
                    installMode = app.installMode,
                    visibility = app.visibility,
                    preloadPolicy = app.preloadPolicy,
                    requiresEntitlement = app.requiresEntitlement,
                )
            },
            adSlots = adSlots.map { slot ->
                StoredRuntimeAdSlot(
                    slotId = slot.slotId,
                    enabled = slot.enabled,
                    creatives = slot.creatives.map { creative ->
                        StoredRuntimeAdCreative(
                            creativeId = creative.creativeId,
                            mediaType = creative.mediaType,
                            assetUrl = creative.assetUrl,
                            altText = creative.altText,
                            clickActionType = creative.clickActionType,
                            clickActionValue = creative.clickActionValue,
                            startsAt = creative.startsAt,
                            endsAt = creative.endsAt,
                        )
                    },
                )
            },
            cachedAtEpochMs = cachedAtEpochMs,
        )
    }

    private fun TvRuntimeManifestDto.toResolvedRuntimeManifest(
        source: RuntimeManifestSource,
        nowIso: String,
    ): ResolvedRuntimeManifest {
        val featuredResolution = resolveFeaturedApps(apps)
        return ResolvedRuntimeManifest(
            manifestVersion = manifestVersion,
            countryCode = countryCode.trim().takeIf(String::isNotBlank),
            regionCode = regionCode?.trim()?.takeIf(String::isNotBlank),
            source = source,
            featuredApps = featuredResolution.featuredApps,
            ignoredFeaturedAppIds = featuredResolution.ignoredIds,
            heroAds = resolveHeroAds(adSlots, nowIso),
        )
    }

    private fun StoredRuntimeManifest.toResolvedRuntimeManifest(
        source: RuntimeManifestSource,
        nowIso: String,
    ): ResolvedRuntimeManifest {
        val featuredResolution = resolveStoredFeaturedApps(apps)
        return ResolvedRuntimeManifest(
            manifestVersion = manifestVersion,
            countryCode = countryCode.trim().takeIf(String::isNotBlank),
            regionCode = regionCode?.trim()?.takeIf(String::isNotBlank),
            source = source,
            featuredApps = featuredResolution.featuredApps,
            ignoredFeaturedAppIds = featuredResolution.ignoredIds,
            heroAds = resolveStoredHeroAds(adSlots, nowIso),
        )
    }

    private fun resolveFeaturedApps(apps: List<TvRuntimeManifestAppDto>): FeaturedAppResolution {
        val featuredApps = mutableListOf<RuntimeFeaturedApp>()
        val ignoredIds = mutableListOf<String>()
        apps.forEach { app ->
            if (!app.visibility.equals("featured", ignoreCase = true)) {
                return@forEach
            }
            val normalizedAppId = app.appId.trim().ifBlank { app.packageName.trim() }
            val normalizedTitle = app.title.trim()
            val normalizedPackageName = app.packageName.trim()
            if (normalizedAppId.isBlank() || normalizedTitle.isBlank() || normalizedPackageName.isBlank()) {
                ignoredIds += normalizedAppId.ifBlank { "(missing-app-id)" }
                return@forEach
            }
            val decoration = HomeAppCatalog.decorationFor(
                appId = normalizedAppId,
                packageName = normalizedPackageName,
            )
            featuredApps += RuntimeFeaturedApp(
                appId = normalizedAppId,
                title = normalizedTitle,
                packageName = normalizedPackageName,
                summary = decoration?.summary ?: HomeAppCatalog.fallbackSummary(
                    title = normalizedTitle,
                    requiresEntitlement = app.requiresEntitlement,
                ),
                monogram = decoration?.monogram ?: HomeAppCatalog.fallbackMonogram(
                    title = normalizedTitle,
                    packageName = normalizedPackageName,
                ),
                accentColorHex = decoration?.accentColorHex ?: HomeAppCatalog.fallbackAccentColor(
                    appId = normalizedAppId,
                    packageName = normalizedPackageName,
                ),
                installMode = app.installMode.trim(),
                requiresEntitlement = app.requiresEntitlement,
            )
        }
        return FeaturedAppResolution(featuredApps = featuredApps, ignoredIds = ignoredIds.distinct())
    }

    private fun resolveStoredFeaturedApps(apps: List<StoredRuntimeApp>): FeaturedAppResolution {
        val featuredApps = mutableListOf<RuntimeFeaturedApp>()
        val ignoredIds = mutableListOf<String>()
        apps.forEach { app ->
            if (!app.visibility.equals("featured", ignoreCase = true)) {
                return@forEach
            }
            val normalizedAppId = app.appId.trim().ifBlank { app.packageName.trim() }
            val normalizedTitle = app.title.trim()
            val normalizedPackageName = app.packageName.trim()
            if (normalizedAppId.isBlank() || normalizedTitle.isBlank() || normalizedPackageName.isBlank()) {
                ignoredIds += normalizedAppId.ifBlank { "(missing-app-id)" }
                return@forEach
            }
            val decoration = HomeAppCatalog.decorationFor(
                appId = normalizedAppId,
                packageName = normalizedPackageName,
            )
            featuredApps += RuntimeFeaturedApp(
                appId = normalizedAppId,
                title = normalizedTitle,
                packageName = normalizedPackageName,
                summary = decoration?.summary ?: HomeAppCatalog.fallbackSummary(
                    title = normalizedTitle,
                    requiresEntitlement = app.requiresEntitlement,
                ),
                monogram = decoration?.monogram ?: HomeAppCatalog.fallbackMonogram(
                    title = normalizedTitle,
                    packageName = normalizedPackageName,
                ),
                accentColorHex = decoration?.accentColorHex ?: HomeAppCatalog.fallbackAccentColor(
                    appId = normalizedAppId,
                    packageName = normalizedPackageName,
                ),
                installMode = app.installMode.trim(),
                requiresEntitlement = app.requiresEntitlement,
            )
        }
        return FeaturedAppResolution(featuredApps = featuredApps, ignoredIds = ignoredIds.distinct())
    }

    private fun resolveHeroAds(
        adSlots: List<TvRuntimeAdSlotDto>,
        nowIso: String,
    ): List<HeroAdItem> {
        return adSlots
            .firstOrNull { it.slotId == HOME_HERO_SLOT_ID && it.enabled }
            ?.creatives
            ?.asSequence()
            ?.filter { it.mediaType.equals("image", ignoreCase = true) }
            ?.filter { creative ->
                val startsAt = creative.startsAt?.takeIf(String::isNotBlank)
                val endsAt = creative.endsAt?.takeIf(String::isNotBlank)
                creative.assetUrl.isNotBlank() &&
                    (startsAt == null || startsAt <= nowIso) &&
                    (endsAt == null || endsAt >= nowIso)
            }
            ?.map { creative ->
                HeroAdItem(
                    creativeId = creative.creativeId,
                    imageUrl = creative.assetUrl,
                    altText = creative.altText.ifBlank { "首页广告" },
                    clickActionType = creative.clickActionType,
                    clickActionValue = creative.clickActionValue,
                )
            }
            ?.toList()
            .orEmpty()
    }

    private fun resolveStoredHeroAds(
        adSlots: List<StoredRuntimeAdSlot>,
        nowIso: String,
    ): List<HeroAdItem> {
        return adSlots
            .firstOrNull { it.slotId == HOME_HERO_SLOT_ID && it.enabled }
            ?.creatives
            ?.asSequence()
            ?.filter { it.mediaType.equals("image", ignoreCase = true) }
            ?.filter { creative ->
                val startsAt = creative.startsAt?.takeIf(String::isNotBlank)
                val endsAt = creative.endsAt?.takeIf(String::isNotBlank)
                creative.assetUrl.isNotBlank() &&
                    (startsAt == null || startsAt <= nowIso) &&
                    (endsAt == null || endsAt >= nowIso)
            }
            ?.map { creative ->
                HeroAdItem(
                    creativeId = creative.creativeId,
                    imageUrl = creative.assetUrl,
                    altText = creative.altText.ifBlank { "首页广告" },
                    clickActionType = creative.clickActionType,
                    clickActionValue = creative.clickActionValue,
                )
            }
            ?.toList()
            .orEmpty()
    }

    private data class FeaturedAppResolution(
        val featuredApps: List<RuntimeFeaturedApp>,
        val ignoredIds: List<String>,
    )
}
