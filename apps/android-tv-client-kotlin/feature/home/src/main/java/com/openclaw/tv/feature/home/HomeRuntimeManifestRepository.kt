package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.RuntimeManifestStore
import com.openclaw.tv.core.storage.StoredRuntimeAdCreative
import com.openclaw.tv.core.storage.StoredRuntimeAdSlot
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

internal data class ResolvedRuntimeManifest(
    val manifestVersion: String,
    val source: RuntimeManifestSource,
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
            source = RuntimeManifestSource.FALLBACK,
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
            countryCode = countryCode,
            regionCode = regionCode?.trim()?.takeIf(String::isNotBlank),
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
        return ResolvedRuntimeManifest(
            manifestVersion = manifestVersion,
            source = source,
            heroAds = adSlots
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
                .orEmpty(),
        )
    }

    private fun StoredRuntimeManifest.toResolvedRuntimeManifest(
        source: RuntimeManifestSource,
        nowIso: String,
    ): ResolvedRuntimeManifest {
        return ResolvedRuntimeManifest(
            manifestVersion = manifestVersion,
            source = source,
            heroAds = adSlots
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
                .orEmpty(),
        )
    }
}
