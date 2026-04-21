package com.openclaw.tv.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeLoopDelayPolicyTest {

    @Test
    fun chooses_smallest_positive_runtime_poll_interval() {
        val policy = RuntimeLoopDelayPolicy(
            defaultDelaySeconds = 15,
            randomDouble = { 0.5 },
        )

        val delayMillis = policy.nextDelayMillis(
            manifestPollAfterSeconds = 120,
            resourceSessionPollAfterSeconds = 30,
            consecutiveFailures = 0,
        )

        assertEquals(30_000L, delayMillis)
    }

    @Test
    fun falls_back_to_default_delay_when_runtime_intervals_are_invalid() {
        val policy = RuntimeLoopDelayPolicy(
            defaultDelaySeconds = 15,
            randomDouble = { 0.5 },
        )

        val delayMillis = policy.nextDelayMillis(
            manifestPollAfterSeconds = 0,
            resourceSessionPollAfterSeconds = null,
            consecutiveFailures = 0,
        )

        assertEquals(15_000L, delayMillis)
    }

    @Test
    fun consecutive_failures_apply_exponential_backoff_until_cap() {
        val policy = RuntimeLoopDelayPolicy(
            defaultDelaySeconds = 15,
            maxDelaySeconds = 300,
            randomDouble = { 0.5 },
        )

        val delayMillis = policy.nextDelayMillis(
            manifestPollAfterSeconds = 30,
            resourceSessionPollAfterSeconds = null,
            consecutiveFailures = 5,
        )

        assertEquals(300_000L, delayMillis)
    }

    @Test
    fun jitter_scales_delay_within_expected_window() {
        val lowerBoundPolicy = RuntimeLoopDelayPolicy(
            defaultDelaySeconds = 15,
            jitterRatio = 0.2,
            randomDouble = { 0.0 },
        )
        val upperBoundPolicy = RuntimeLoopDelayPolicy(
            defaultDelaySeconds = 15,
            jitterRatio = 0.2,
            randomDouble = { 1.0 },
        )

        val lowerDelayMillis = lowerBoundPolicy.nextDelayMillis(
            manifestPollAfterSeconds = 10,
            resourceSessionPollAfterSeconds = null,
            consecutiveFailures = 0,
        )
        val upperDelayMillis = upperBoundPolicy.nextDelayMillis(
            manifestPollAfterSeconds = 10,
            resourceSessionPollAfterSeconds = null,
            consecutiveFailures = 0,
        )

        assertEquals(8_000L, lowerDelayMillis)
        assertEquals(12_000L, upperDelayMillis)
    }
}
