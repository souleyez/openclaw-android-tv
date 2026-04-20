package com.openclaw.tv.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TvRuntimeManifestDto(
    val manifestVersion: String = "",
    val countryCode: String = "GLOBAL",
    val regionCode: String? = null,
    val apps: List<TvRuntimeManifestAppDto> = emptyList(),
    val adSlots: List<TvRuntimeAdSlotDto> = emptyList(),
    val pollAfterSeconds: Int = 900,
    val eventCursor: String = "",
)

@Serializable
data class TvRuntimeManifestAppDto(
    val appId: String = "",
    val title: String = "",
    val packageName: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val versionCode: Long = 0L,
    val versionName: String = "",
    val minClientVersion: String = "",
    val installMode: String = "",
    val visibility: String = "",
    val preloadPolicy: String = "",
    val requiresEntitlement: Boolean = false,
)

@Serializable
data class TvRuntimeAdSlotDto(
    val slotId: String = "",
    val enabled: Boolean = false,
    val creatives: List<TvRuntimeAdCreativeDto> = emptyList(),
)

@Serializable
data class TvRuntimeAdCreativeDto(
    val creativeId: String = "",
    val mediaType: String = "",
    val assetUrl: String = "",
    val altText: String = "",
    val clickActionType: String = "",
    val clickActionValue: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
)
