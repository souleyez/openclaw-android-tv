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
    private val refreshScheduler: EntitlementRefreshScheduler = EntitlementRefreshScheduler(),
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    private var activeSessionToken: String? = null
    private var nextAllowedRefreshAtEpochMs: Long? = null

    suspend fun load(
        sessionToken: String,
        pollAfterSeconds: Int,
    ): RuntimeEntitlementSnapshot {
        val cached = entitlementStore.read()
        val normalizedSessionToken = sessionToken.trim()
        val sessionChanged = activeSessionToken != null && activeSessionToken != normalizedSessionToken
        if (sessionChanged) {
            nextAllowedRefreshAtEpochMs = null
        }
        activeSessionToken = normalizedSessionToken
        val now = nowEpochMs()
        val persistedNextRefreshAtEpochMs = if (sessionChanged) {
            null
        } else {
            cached?.cachedAtEpochMs?.let { cachedAtEpochMs ->
                refreshScheduler.nextRefreshAtEpochMs(
                    lastAttemptedAtEpochMs = cachedAtEpochMs,
                    pollAfterSeconds = pollAfterSeconds,
                )
            }
        }
        val refreshDecision = refreshScheduler.evaluate(
            nextAllowedRefreshAtEpochMs = nextAllowedRefreshAtEpochMs ?: persistedNextRefreshAtEpochMs,
            nowEpochMs = now,
        )
        if (!refreshDecision.shouldRefresh) {
            return RuntimeEntitlementSnapshot(
                entitlement = cached,
                source = if (cached != null) RuntimeEntitlementSnapshotSource.CACHE else RuntimeEntitlementSnapshotSource.EMPTY,
                refreshed = false,
                nextRefreshAtEpochMs = refreshDecision.nextRefreshAtEpochMs,
            )
        }

        return try {
            val summary = withTimeout(requestTimeoutMillis) {
                platformApi.getEntitlement(normalizedSessionToken)
            }.toStoredEntitlementSummary(cachedAtEpochMs = now)
            entitlementStore.save(summary)
            val nextRefreshAtEpochMs = refreshScheduler.nextRefreshAtEpochMs(
                lastAttemptedAtEpochMs = now,
                pollAfterSeconds = pollAfterSeconds,
            )
            nextAllowedRefreshAtEpochMs = nextRefreshAtEpochMs
            RuntimeEntitlementSnapshot(
                entitlement = summary,
                source = RuntimeEntitlementSnapshotSource.REMOTE,
                refreshed = true,
                nextRefreshAtEpochMs = nextRefreshAtEpochMs,
            )
        } catch (error: Exception) {
            error.rethrowIfExternalCancellation()
            val nextRefreshAtEpochMs = refreshScheduler.nextRefreshAtEpochMs(
                lastAttemptedAtEpochMs = now,
                pollAfterSeconds = pollAfterSeconds,
            )
            nextAllowedRefreshAtEpochMs = nextRefreshAtEpochMs
            if (cached != null) {
                RuntimeEntitlementSnapshot(
                    entitlement = cached,
                    source = RuntimeEntitlementSnapshotSource.CACHE,
                    refreshed = false,
                    nextRefreshAtEpochMs = nextRefreshAtEpochMs,
                )
            } else {
                RuntimeEntitlementSnapshot(
                    entitlement = null,
                    source = RuntimeEntitlementSnapshotSource.EMPTY,
                    refreshed = false,
                    nextRefreshAtEpochMs = nextRefreshAtEpochMs,
                )
            }
        }
    }

    suspend fun clear() {
        entitlementStore.clear()
        activeSessionToken = null
        nextAllowedRefreshAtEpochMs = null
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
