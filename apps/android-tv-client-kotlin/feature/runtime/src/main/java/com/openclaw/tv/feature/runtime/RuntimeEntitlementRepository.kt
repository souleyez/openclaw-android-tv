package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.storage.EntitlementStore
import com.openclaw.tv.core.storage.StoredEntitlementSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    val nextRefreshAtEpochMs: Long? = null,
)

class RuntimeEntitlementRepository(
    private val platformApi: PlatformApi,
    private val entitlementStore: EntitlementStore,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    private var activeSessionToken: String? = null
    private var hasAttemptedRemoteLoadForActiveSession = false

    suspend fun load(
        sessionToken: String,
        @Suppress("UNUSED_PARAMETER")
        pollAfterSeconds: Int,
    ): RuntimeEntitlementSnapshot {
        val cached = entitlementStore.read()
        val normalizedSessionToken = sessionToken.trim()
        val sessionChanged = activeSessionToken != null && activeSessionToken != normalizedSessionToken
        if (sessionChanged) {
            hasAttemptedRemoteLoadForActiveSession = false
        }
        activeSessionToken = normalizedSessionToken
        if (hasAttemptedRemoteLoadForActiveSession) {
            return RuntimeEntitlementSnapshot(
                entitlement = cached,
                source = if (cached != null) RuntimeEntitlementSnapshotSource.CACHE else RuntimeEntitlementSnapshotSource.EMPTY,
                refreshed = false,
                nextRefreshAtEpochMs = null,
            )
        }
        val now = nowEpochMs()

        return try {
            val summary = withTimeout(requestTimeoutMillis) {
                platformApi.getEntitlement(normalizedSessionToken)
            }.toStoredEntitlementSummary(cachedAtEpochMs = now)
            entitlementStore.save(summary)
            hasAttemptedRemoteLoadForActiveSession = true
            RuntimeEntitlementSnapshot(
                entitlement = summary,
                source = RuntimeEntitlementSnapshotSource.REMOTE,
                refreshed = true,
                nextRefreshAtEpochMs = null,
            )
        } catch (error: Exception) {
            error.rethrowIfExternalCancellation()
            hasAttemptedRemoteLoadForActiveSession = true
            if (cached != null) {
                RuntimeEntitlementSnapshot(
                    entitlement = cached,
                    source = RuntimeEntitlementSnapshotSource.CACHE,
                    refreshed = false,
                    nextRefreshAtEpochMs = null,
                )
            } else {
                RuntimeEntitlementSnapshot(
                    entitlement = null,
                    source = RuntimeEntitlementSnapshotSource.EMPTY,
                    refreshed = false,
                    nextRefreshAtEpochMs = null,
                )
            }
        }
    }

    suspend fun clear() {
        entitlementStore.clear()
        activeSessionToken = null
        hasAttemptedRemoteLoadForActiveSession = false
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L
    }
}

private fun Throwable.rethrowIfExternalCancellation() {
    if (this is CancellationException && this !is TimeoutCancellationException) {
        throw this
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
