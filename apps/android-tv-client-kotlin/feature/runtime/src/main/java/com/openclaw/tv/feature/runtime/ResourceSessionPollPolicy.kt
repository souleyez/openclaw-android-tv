package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.storage.StoredResourceSession

class ResourceSessionPollPolicy(
    private val defaultPollAfterSeconds: Int = 15,
    private val maxQueuedPollAfterSeconds: Int = 60,
) {

    fun nextPollAfterSeconds(resourceSession: StoredResourceSession?): Int? {
        val queueStatus = resourceSession?.queueStatus?.normalizedQueueStatus() ?: return null
        return when (queueStatus) {
            "queued",
            "allocating" -> queuedPollAfterSeconds(resourceSession.estimatedWaitSeconds)

            "granted",
            "degraded" -> defaultPollAfterSeconds.coerceAtLeast(1)

            else -> null
        }
    }

    private fun queuedPollAfterSeconds(estimatedWaitSeconds: Int?): Int {
        val defaultDelay = defaultPollAfterSeconds.coerceAtLeast(1)
        val maxDelay = maxQueuedPollAfterSeconds.coerceAtLeast(defaultDelay)
        val estimatedDelay = estimatedWaitSeconds
            ?.takeIf { it > 0 }
            ?.let { (it + 1) / 2 }
            ?: return defaultDelay
        return estimatedDelay.coerceIn(defaultDelay, maxDelay)
    }
}
