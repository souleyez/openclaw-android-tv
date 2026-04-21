package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredResourceSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceSessionPollPolicyTest {

    private val policy = ResourceSessionPollPolicy(
        defaultPollAfterSeconds = 15,
        maxQueuedPollAfterSeconds = 60,
    )

    @Test
    fun queued_session_scales_poll_interval_from_estimated_wait() {
        val nextPollAfterSeconds = policy.nextPollAfterSeconds(
            storedResourceSession(
                queueStatus = "queued",
                estimatedWaitSeconds = 90,
            ),
        )

        assertEquals(45, nextPollAfterSeconds)
    }

    @Test
    fun queued_session_caps_poll_interval_when_estimated_wait_is_large() {
        val nextPollAfterSeconds = policy.nextPollAfterSeconds(
            storedResourceSession(
                queueStatus = "queued",
                estimatedWaitSeconds = 600,
            ),
        )

        assertEquals(60, nextPollAfterSeconds)
    }

    @Test
    fun granted_session_keeps_default_fast_poll_interval() {
        val nextPollAfterSeconds = policy.nextPollAfterSeconds(
            storedResourceSession(
                queueStatus = "granted",
                estimatedWaitSeconds = null,
            ),
        )

        assertEquals(15, nextPollAfterSeconds)
    }

    @Test
    fun terminal_session_does_not_schedule_polling() {
        val nextPollAfterSeconds = policy.nextPollAfterSeconds(
            storedResourceSession(
                queueStatus = "expired",
                estimatedWaitSeconds = null,
            ),
        )

        assertNull(nextPollAfterSeconds)
    }

    private fun storedResourceSession(
        queueStatus: String,
        estimatedWaitSeconds: Int?,
    ): StoredResourceSession {
        return StoredResourceSession(
            resourceSessionId = "rs_001",
            queueStatus = queueStatus,
            priorityClass = "paid_active",
            queuePosition = if (queueStatus == "queued" || queueStatus == "allocating") 2 else null,
            estimatedWaitSeconds = estimatedWaitSeconds,
            appAccountLease = null,
            modelLease = null,
            entitlementSummary = StoredEntitlementSnapshot(
                accountId = "acct_1",
                displayId = "TV-001",
                planCode = "pro-monthly",
                paymentState = "paid",
                priorityClass = "paid_active",
                renewalState = "auto_renewing",
            ),
            expiresAt = "2099-04-21T00:20:00.000Z",
            updatedAt = "2026-04-20T11:45:00.000Z",
            polledAtEpochMs = 1_000L,
        )
    }
}
