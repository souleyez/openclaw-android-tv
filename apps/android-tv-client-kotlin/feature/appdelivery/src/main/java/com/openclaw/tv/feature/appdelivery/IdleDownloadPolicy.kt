package com.openclaw.tv.feature.appdelivery

class IdleDownloadPolicy {

    fun canEnqueue(
        preloadPolicy: String,
        idleDownloadOnly: Boolean,
        deviceIsActive: Boolean,
    ): Boolean {
        val idleRequired = idleDownloadOnly || preloadPolicy.trim().equals("idle_only", ignoreCase = true)
        return !idleRequired || !deviceIsActive
    }
}
