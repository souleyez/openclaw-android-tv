package com.openclaw.tv.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TvHomeConfigDto(
    val projectKey: String = "openclaw-android-tv",
    val projectLabel: String = "OpenClaw Android TV",
    val customer: TvHomeCustomerDto? = null,
    val distribution: TvHomeDistributionDto? = null,
    val branding: TvHomeBrandingDto? = null,
    val theme: TvHomeThemeDto? = null,
    val homeApps: List<TvHomeAppDto> = emptyList(),
    val hotelServices: List<TvHotelServiceDto> = emptyList(),
    val runtimeManifestPath: String = "/api/me/runtime-manifest",
    val entitlementPath: String = "/api/me/entitlement",
    val resourceSessionBasePath: String = "/api/client/resource-session",
    val manifestPollAfterSeconds: Int = 900,
    val resourceSessionPollAfterSeconds: Int = 15,
    val backgroundDownloadEnabled: Boolean = true,
    val idleDownloadOnly: Boolean = true,
)

@Serializable
data class TvHomeCustomerDto(
    val id: String = "",
    val slug: String = "",
    val displayName: String = "",
    val hotelName: String = "",
)

@Serializable
data class TvHomeDistributionDto(
    val distributionKey: String = "",
    val packageName: String = "",
    val releaseChannel: String = "",
)

@Serializable
data class TvHomeBrandingDto(
    val logoUrl: String = "",
    val intro: String = "",
    val versionLabel: String = "",
)

@Serializable
data class TvHomeThemeDto(
    val defaultMode: String = "night",
    val switcherEnabled: Boolean = false,
    val dayPalette: Map<String, String> = emptyMap(),
    val nightPalette: Map<String, String> = emptyMap(),
)

@Serializable
data class TvHomeAppDto(
    val appId: String = "",
    val title: String = "",
    val packageName: String = "",
    val sortOrder: Int = 0,
)

@Serializable
data class TvHotelServiceDto(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val imageUrl: String = "",
    val actionType: String = "",
    val actionValue: String = "",
    val sortOrder: Int = 0,
)
