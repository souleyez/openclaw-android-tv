package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.storage.StoredResourceSession

class ResourceSessionPollPolicy(
    private val defaultPollAfterSeconds: Int = 15,
) {

    fun nextPollAfterSeconds(resourceSession: StoredResourceSession?): Int? {
        val queueStatus = resourceSession?.queueStatus?.normalizedQueueStatus() ?: return null
        return when (queueStatus) {
            "queued",
            "allocating",
            "granted",
            "degraded",
            "expired" -> defaultPollAfterSeconds

            else -> null
        }
    }
}
