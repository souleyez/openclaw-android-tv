package com.openclaw.tv.feature.home

import com.openclaw.tv.core.storage.StoredRuntimeAdSlot

internal class HomeAdSlotRegistry {

    fun resolveHeroAds(
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
            ?.take(MAX_HERO_AD_ITEMS)
            ?.toList()
            .orEmpty()
    }

    companion object {
        const val HOME_HERO_SLOT_ID = "home.hero"
        private const val MAX_HERO_AD_ITEMS = 8
    }
}
