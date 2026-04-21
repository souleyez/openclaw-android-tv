package com.openclaw.tv.feature.appdelivery

data class ManifestRefreshDecision(
    val shouldRefresh: Boolean,
    val nextRefreshAtEpochMs: Long? = null,
)

class ManifestRefreshScheduler {

    fun evaluate(
        lastFetchedAtEpochMs: Long?,
        pollAfterSeconds: Int,
        nowEpochMs: Long,
    ): ManifestRefreshDecision {
        if (lastFetchedAtEpochMs == null) {
            return ManifestRefreshDecision(
                shouldRefresh = true,
                nextRefreshAtEpochMs = null,
            )
        }
        val nextRefreshAtEpochMs = nextRefreshAtEpochMs(
            lastFetchedAtEpochMs = lastFetchedAtEpochMs,
            pollAfterSeconds = pollAfterSeconds,
        )
        return ManifestRefreshDecision(
            shouldRefresh = nowEpochMs >= nextRefreshAtEpochMs,
            nextRefreshAtEpochMs = nextRefreshAtEpochMs,
        )
    }

    fun nextRefreshAtEpochMs(
        lastFetchedAtEpochMs: Long,
        pollAfterSeconds: Int,
    ): Long {
        val safePollMs = pollAfterSeconds.coerceAtLeast(1) * 1_000L
        return lastFetchedAtEpochMs + safePollMs
    }
}
