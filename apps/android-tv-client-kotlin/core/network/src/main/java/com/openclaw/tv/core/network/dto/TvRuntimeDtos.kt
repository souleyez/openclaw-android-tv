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
data class OwnApkUpdateManifestDto(
    val status: String = "",
    val checkedAt: String = "",
    val projectKey: String = "",
    val packageName: String = "",
    val current: OwnApkUpdateCurrentDto = OwnApkUpdateCurrentDto(),
    val policy: OwnApkUpdatePolicyDto = OwnApkUpdatePolicyDto(),
    val resources: OwnApkResourceUpdateDto = OwnApkResourceUpdateDto(),
    val fullApk: OwnApkFullUpdateDto = OwnApkFullUpdateDto(),
    val deltaApk: OwnApkDeltaUpdateDto = OwnApkDeltaUpdateDto(),
    val fallback: OwnApkUpdateFallbackDto = OwnApkUpdateFallbackDto(),
)

@Serializable
data class OwnApkUpdateCurrentDto(
    val versionCode: Long = 0L,
    val resourceVersion: String = "",
)

@Serializable
data class OwnApkUpdatePolicyDto(
    val downloadPolicy: String = "",
    val reportPolicy: String = "",
    val reportDelayMinutes: Int = 60,
    val minCheckIntervalSeconds: Int = 21_600,
    val nextCheckAt: String = "",
)

@Serializable
data class OwnApkResourceUpdateDto(
    val available: Boolean = false,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val resourceVersion: String = "",
    val manifestUrl: String = "",
    val sha256: String = "",
    val size: Long = 0L,
    val releaseId: String = "",
)

@Serializable
data class OwnApkFullUpdateDto(
    val available: Boolean = false,
    val id: String = "",
    val updateMode: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val channel: String = "",
    val artifactUrl: String = "",
    val artifactSha256: String = "",
    val artifactSize: Long = 0L,
    val installPolicy: String = "",
    val releaseNotes: String? = null,
    val latestReport: String = "",
)

@Serializable
data class OwnApkDeltaUpdateDto(
    val available: Boolean = false,
    val id: String = "",
    val updateMode: String = "",
    val versionName: String = "",
    val fromVersionCode: Long = 0L,
    val toVersionCode: Long = 0L,
    val patchUrl: String = "",
    val patchSha256: String = "",
    val patchSize: Long = 0L,
    val targetApkSha256: String = "",
    val targetApkSize: Long = 0L,
    val algorithm: String = "",
    val fallbackArtifactUrl: String = "",
    val installPolicy: String = "",
    val releaseNotes: String? = null,
    val latestReport: String = "",
)

@Serializable
data class OwnApkUpdateFallbackDto(
    val fullApkRequired: Boolean = false,
    val fullApkReleaseId: String = "",
)

@Serializable
data class OwnApkUpdateReportRequestDto(
    val releaseId: String,
    val currentVersionCode: Long = 0L,
    val targetVersionCode: Long,
    val status: String,
    val progressPercent: Int? = null,
    val note: String? = null,
)

@Serializable
data class OwnApkUpdateReportEnvelope(
    val status: String = "",
    val report: OwnApkUpdateReportDto = OwnApkUpdateReportDto(),
)

@Serializable
data class OwnApkUpdateReportDto(
    val id: String = "",
    val projectKey: String = "",
    val releaseId: String = "",
    val deviceUuid: String = "",
    val currentVersionCode: Long = 0L,
    val targetVersionCode: Long = 0L,
    val status: String = "",
    val progressPercent: Int = 0,
    val note: String? = null,
    val reportedAt: String = "",
    val updatedAt: String = "",
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
data class TvModelRenewalPaymentOrderEnvelope(
    val status: String = "",
    val order: TvModelRenewalPaymentOrderDto = TvModelRenewalPaymentOrderDto(),
)

@Serializable
data class TvModelRenewalPaymentOrderDto(
    val orderId: String = "",
    val sku: String = "",
    val title: String = "",
    val paymentProvider: String = "",
    val paymentState: String = "unknown",
    val amount: TvModelRenewalPaymentAmountDto = TvModelRenewalPaymentAmountDto(),
    val qr: TvModelRenewalPaymentQrDto = TvModelRenewalPaymentQrDto(),
    val entitlementSummary: TvEntitlementSummaryDto = TvEntitlementSummaryDto(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val paidAt: String? = null,
    val durationSeconds: Long = 0L,
)

@Serializable
data class TvModelRenewalPaymentAmountDto(
    val totalCents: Int = 0,
    val currency: String = "CNY",
    val display: String = "",
)

@Serializable
data class TvModelRenewalPaymentQrDto(
    val codeUrl: String = "",
    val expiresAt: String = "",
)

@Serializable
data class TvModelRenewalPaymentOrderRequestDto(
    val sku: String? = null,
)

@Serializable
data class DeviceTelemetryRequestDto(
    val capturedAt: String = "",
    val appVersion: String = "",
    val openclawVersion: String = "",
    val runtimeVersion: String = "",
    val foregroundState: String = "",
    val castState: String = "",
    val network: Map<String, String> = emptyMap(),
    val memory: Map<String, String> = emptyMap(),
    val storage: Map<String, String> = emptyMap(),
    val resourceSession: Map<String, String> = emptyMap(),
)

@Serializable
data class DeviceTelemetryResponseDto(
    val status: String = "",
    val telemetry: DeviceTelemetryAckDto = DeviceTelemetryAckDto(),
)

@Serializable
data class DeviceTelemetryAckDto(
    val deviceId: String = "",
    val capturedAt: String = "",
    val receivedAt: String = "",
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
