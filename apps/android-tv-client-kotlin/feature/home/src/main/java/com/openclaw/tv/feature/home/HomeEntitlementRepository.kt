package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.storage.EntitlementStore
import com.openclaw.tv.core.storage.StoredEntitlementSummary
import kotlinx.coroutines.withTimeout

internal data class ResolvedEntitlementSummary(
    val planCode: String,
    val paymentState: String,
    val priorityClass: String,
    val renewalState: String,
    val source: EntitlementSource,
)

internal enum class EntitlementSource {
    REMOTE,
    CACHE,
}

internal open class HomeEntitlementRepository(
    private val platformApi: PlatformApi,
    private val cacheStore: EntitlementStore? = null,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    open suspend fun load(sessionToken: String): ResolvedEntitlementSummary? {
        return try {
            val summary = withTimeout(requestTimeoutMillis) {
                platformApi.getEntitlement(sessionToken)
            }
            cacheStore?.save(summary.toStoredEntitlementSummary(nowEpochMs()))
            summary.toResolvedEntitlementSummary(source = EntitlementSource.REMOTE)
        } catch (_: Exception) {
            cacheStore?.read()?.toResolvedEntitlementSummary(source = EntitlementSource.CACHE)
        }
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L
    }
}

private fun TvEntitlementSummaryDto.toResolvedEntitlementSummary(
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

private fun StoredEntitlementSummary.toResolvedEntitlementSummary(
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

internal fun String.normalizedPaymentState(): String {
    return trim().lowercase().ifBlank { "unknown" }
}
