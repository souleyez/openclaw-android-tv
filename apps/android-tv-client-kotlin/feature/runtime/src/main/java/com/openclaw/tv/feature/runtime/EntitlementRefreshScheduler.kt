package com.openclaw.tv.feature.runtime

data class EntitlementRefreshDecision(
    val shouldRefresh: Boolean,
    val nextRefreshAtEpochMs: Long? = null,
)

class EntitlementRefreshScheduler {

    fun evaluate(
        nextAllowedRefreshAtEpochMs: Long?,
        nowEpochMs: Long,
    ): EntitlementRefreshDecision {
        if (nextAllowedRefreshAtEpochMs == null) {
            return EntitlementRefreshDecision(
                shouldRefresh = true,
                nextRefreshAtEpochMs = null,
            )
        }
        return EntitlementRefreshDecision(
            shouldRefresh = nowEpochMs >= nextAllowedRefreshAtEpochMs,
            nextRefreshAtEpochMs = nextAllowedRefreshAtEpochMs,
        )
    }

    fun nextRefreshAtEpochMs(
        lastAttemptedAtEpochMs: Long,
        pollAfterSeconds: Int,
    ): Long {
        val safePollMs = pollAfterSeconds.coerceAtLeast(1) * 1_000L
        return lastAttemptedAtEpochMs + safePollMs
    }
}
