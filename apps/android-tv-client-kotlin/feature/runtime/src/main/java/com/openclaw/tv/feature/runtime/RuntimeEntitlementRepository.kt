package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.storage.EntitlementStore
import com.openclaw.tv.core.storage.StoredEntitlementSummary
import kotlinx.coroutines.withTimeout

enum class RuntimeEntitlementSnapshotSource {
    REMOTE,
    CACHE,
    EMPTY,
}

data class RuntimeEntitlementSnapshot(
    val entitlement: StoredEntitlementSummary?,
    val source: RuntimeEntitlementSnapshotSource,
    val refreshed: Boolean,
)

class RuntimeEntitlementRepository(
    private val platformApi: PlatformApi,
    private val entitlementStore: EntitlementStore,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun load(sessionToken: String): RuntimeEntitlementSnapshot {
        val cached = entitlementStore.read()
        return try {
            val summary = withTimeout(requestTimeoutMillis) {
                platformApi.getEntitlement(sessionToken)
            }.toStoredEntitlementSummary(cachedAtEpochMs = nowEpochMs())
            entitlementStore.save(summary)
            RuntimeEntitlementSnapshot(
                entitlement = summary,
                source = RuntimeEntitlementSnapshotSource.REMOTE,
                refreshed = true,
            )
        } catch (_: Exception) {
            if (cached != null) {
                RuntimeEntitlementSnapshot(
                    entitlement = cached,
                    source = RuntimeEntitlementSnapshotSource.CACHE,
                    refreshed = false,
                )
            } else {
                RuntimeEntitlementSnapshot(
                    entitlement = null,
                    source = RuntimeEntitlementSnapshotSource.EMPTY,
                    refreshed = false,
                )
            }
        }
    }

    suspend fun clear() {
        entitlementStore.clear()
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L
    }
}

private fun TvEntitlementSummaryDto.toStoredEntitlementSummary(
    cachedAtEpochMs: Long,
): StoredEntitlementSummary {
    return StoredEntitlementSummary(
        accountId = accountId.trim(),
        displayId = displayId.trim(),
        planCode = planCode.trim(),
        paymentState = paymentState.normalizedPaymentState(),
        priorityClass = priorityClass.trim(),
        renewalState = renewalState.trim(),
        cachedAtEpochMs = cachedAtEpochMs,
    )
}

private fun String.normalizedPaymentState(): String {
    return trim().lowercase().ifBlank { "unknown" }
}
