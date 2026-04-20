package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvResourceAppAccountLeaseDto
import com.openclaw.tv.core.network.dto.TvResourceModelLeaseDto
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.storage.ResourceSessionStore
import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredResourceAppAccountLease
import com.openclaw.tv.core.storage.StoredResourceModelLease
import com.openclaw.tv.core.storage.StoredResourceSession
import kotlinx.coroutines.withTimeout

internal data class ResolvedResourceSession(
    val resourceSessionId: String,
    val queueStatus: String,
    val priorityClass: String,
    val queuePosition: Int?,
    val estimatedWaitSeconds: Int?,
    val expiresAt: String?,
    val updatedAt: String,
    val hasAppAccountLease: Boolean,
    val hasModelLease: Boolean,
    val entitlementSummary: ResolvedEntitlementSummary?,
    val source: ResourceSessionSource,
)

internal enum class ResourceSessionSource {
    REMOTE,
    CACHE,
}

internal open class HomeResourceSessionRepository(
    private val platformApi: PlatformApi,
    private val cacheStore: ResourceSessionStore? = null,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    open suspend fun load(sessionToken: String): ResolvedResourceSession? {
        return try {
            val session = withTimeout(requestTimeoutMillis) {
                platformApi.getResourceSessionStatus(sessionToken)
            }
            cacheStore?.save(session.toStoredResourceSession(nowEpochMs()))
            session.toResolvedResourceSession(source = ResourceSessionSource.REMOTE)
        } catch (_: Exception) {
            cacheStore?.read()?.toResolvedResourceSession(source = ResourceSessionSource.CACHE)
        }
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L
    }
}

private fun TvResourceSessionDto.toStoredResourceSession(
    polledAtEpochMs: Long,
): StoredResourceSession {
    return StoredResourceSession(
        resourceSessionId = resourceSessionId.trim(),
        queueStatus = queueStatus.normalizedQueueStatus(),
        priorityClass = priorityClass.trim(),
        queuePosition = queuePosition,
        estimatedWaitSeconds = estimatedWaitSeconds,
        appAccountLease = appAccountLease?.toStoredResourceAppAccountLease(),
        modelLease = modelLease?.toStoredResourceModelLease(),
        entitlementSummary = entitlementSummary.toStoredEntitlementSnapshot(),
        expiresAt = expiresAt?.trim()?.takeIf(String::isNotBlank),
        updatedAt = updatedAt.trim(),
        polledAtEpochMs = polledAtEpochMs,
    )
}

private fun StoredResourceSession.toResolvedResourceSession(
    source: ResourceSessionSource,
): ResolvedResourceSession {
    return ResolvedResourceSession(
        resourceSessionId = resourceSessionId.trim(),
        queueStatus = queueStatus.normalizedQueueStatus(),
        priorityClass = priorityClass.trim(),
        queuePosition = queuePosition,
        estimatedWaitSeconds = estimatedWaitSeconds,
        expiresAt = expiresAt?.trim()?.takeIf(String::isNotBlank),
        updatedAt = updatedAt.trim(),
        hasAppAccountLease = appAccountLease != null,
        hasModelLease = modelLease != null,
        entitlementSummary = entitlementSummary.toResolvedEntitlementSummary(
            source = when (source) {
                ResourceSessionSource.REMOTE -> EntitlementSource.REMOTE
                ResourceSessionSource.CACHE -> EntitlementSource.CACHE
            },
        ),
        source = source,
    )
}

private fun TvResourceSessionDto.toResolvedResourceSession(
    source: ResourceSessionSource,
): ResolvedResourceSession {
    return ResolvedResourceSession(
        resourceSessionId = resourceSessionId.trim(),
        queueStatus = queueStatus.normalizedQueueStatus(),
        priorityClass = priorityClass.trim(),
        queuePosition = queuePosition,
        estimatedWaitSeconds = estimatedWaitSeconds,
        expiresAt = expiresAt?.trim()?.takeIf(String::isNotBlank),
        updatedAt = updatedAt.trim(),
        hasAppAccountLease = appAccountLease != null,
        hasModelLease = modelLease != null,
        entitlementSummary = entitlementSummary.toResolvedEmbeddedEntitlementSummary(
            source = when (source) {
                ResourceSessionSource.REMOTE -> EntitlementSource.REMOTE
                ResourceSessionSource.CACHE -> EntitlementSource.CACHE
            },
        ),
        source = source,
    )
}

private fun TvResourceAppAccountLeaseDto.toStoredResourceAppAccountLease(): StoredResourceAppAccountLease {
    return StoredResourceAppAccountLease(
        leaseId = leaseId.trim(),
        appId = appId.trim(),
        accountLabel = accountLabel.trim(),
        expiresAt = expiresAt.trim(),
    )
}

private fun TvResourceModelLeaseDto.toStoredResourceModelLease(): StoredResourceModelLease {
    return StoredResourceModelLease(
        leaseId = leaseId.trim(),
        providerScope = providerScope.trim(),
        leaseMode = leaseMode.trim(),
        leaseProfile = leaseProfile.trim(),
        expiresAt = expiresAt.trim(),
    )
}

private fun com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto.toStoredEntitlementSnapshot(): StoredEntitlementSnapshot {
    return StoredEntitlementSnapshot(
        accountId = accountId.trim(),
        displayId = displayId.trim(),
        planCode = planCode.trim(),
        paymentState = paymentState.normalizedPaymentState(),
        priorityClass = priorityClass.trim(),
        renewalState = renewalState.trim(),
    )
}

private fun com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto.toResolvedEmbeddedEntitlementSummary(
    source: EntitlementSource,
): ResolvedEntitlementSummary {
    return ResolvedEntitlementSummary(
        planCode = planCode.trim(),
        paymentState = paymentState.normalizedPaymentState(),
        priorityClass = priorityClass.trim(),
        renewalState = renewalState.trim(),
        source = source,
    )
}

private fun StoredEntitlementSnapshot.toResolvedEntitlementSummary(
    source: EntitlementSource,
): ResolvedEntitlementSummary {
    return ResolvedEntitlementSummary(
        planCode = planCode.trim(),
        paymentState = paymentState.normalizedPaymentState(),
        priorityClass = priorityClass.trim(),
        renewalState = renewalState.trim(),
        source = source,
    )
}

private fun String.normalizedQueueStatus(): String {
    return trim().lowercase().ifBlank { "not_requested" }
}
