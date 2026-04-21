package com.openclaw.tv.core.storage

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun StoredResourceSession.toClientVisibleResourceSession(
    nowEpochMs: Long,
): StoredResourceSession {
    val normalizedQueueStatus = queueStatus.trim().lowercase().ifBlank { "not_requested" }
    val effectiveQueueStatus = if (
        normalizedQueueStatus in ExpirableQueueStatuses
        && parseUtcIsoToEpochMs(expiresAt)?.let { expiresAtEpochMs -> expiresAtEpochMs <= nowEpochMs } == true
    ) {
        "expired"
    } else {
        normalizedQueueStatus
    }
    val shouldPreserveLeases = effectiveQueueStatus in LeaseBearingQueueStatuses
    return copy(
        queueStatus = effectiveQueueStatus,
        appAccountLease = if (shouldPreserveLeases) appAccountLease else null,
        modelLease = if (shouldPreserveLeases) modelLease else null,
    )
}

private fun parseUtcIsoToEpochMs(value: String?): Long? {
    val normalizedValue = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    return synchronized(IsoUtcFormat) {
        runCatching {
            IsoUtcFormat.parse(normalizedValue)?.time
        }.getOrNull()
    }
}

private val ExpirableQueueStatuses = setOf(
    "granted",
    "degraded",
)

private val LeaseBearingQueueStatuses = setOf(
    "queued",
    "allocating",
    "granted",
    "degraded",
)

private val IsoUtcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
