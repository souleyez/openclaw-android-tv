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

@Serializable
data class TvEntitlementSummaryDto(
    val accountId: String = "",
    val displayId: String = "",
    val planCode: String = "",
    val paymentState: String = "unknown",
    val priorityClass: String = "",
    val renewalState: String = "",
)

@Serializable
data class TvResourceSessionDto(
    val resourceSessionId: String = "",
    val queueStatus: String = "not_requested",
    val priorityClass: String = "",
    val queuePosition: Int? = null,
    val estimatedWaitSeconds: Int? = null,
    val appAccountLease: TvResourceAppAccountLeaseDto? = null,
    val modelLease: TvResourceModelLeaseDto? = null,
    val entitlementSummary: TvEntitlementSummaryDto = TvEntitlementSummaryDto(),
    val expiresAt: String? = null,
    val updatedAt: String = "",
)

@Serializable
data class TvResourceAppAccountLeaseDto(
    val leaseId: String = "",
    val appId: String = "",
    val accountLabel: String = "",
    val expiresAt: String = "",
)

@Serializable
data class TvResourceModelLeaseDto(
    val leaseId: String = "",
    val providerScope: String = "",
    val leaseMode: String = "",
    val leaseProfile: String = "",
    val expiresAt: String = "",
)

@Serializable
data class TvResourceSessionRequestDto(
    val appId: String? = null,
    val providerScope: String? = null,
    val leaseProfile: String? = null,
)

@Serializable
data class TvResourceSessionReferenceDto(
    val resourceSessionId: String? = null,
)
