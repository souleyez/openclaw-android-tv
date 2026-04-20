package com.openclaw.tv.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceSessionStoreTest {

    @Test
    fun save_then_read_round_trips_resource_session_summary() = runTest {
        val store = InMemoryResourceSessionStore()
        val expected = StoredResourceSession(
            resourceSessionId = "rs_001",
            queueStatus = "granted",
            priorityClass = "paid_active",
            queuePosition = null,
            estimatedWaitSeconds = null,
            appAccountLease = StoredResourceAppAccountLease(
                leaseId = "aal_001",
                appId = "youtube",
                accountLabel = "shared-premium-01",
                expiresAt = "2026-04-20T12:00:00.000Z",
            ),
            modelLease = StoredResourceModelLease(
                leaseId = "ml_001",
                providerScope = "moonshot",
                leaseMode = "proxy",
                leaseProfile = "server_10m",
                expiresAt = "2026-04-20T12:00:00.000Z",
            ),
            entitlementSummary = StoredEntitlementSnapshot(
                accountId = "acct_001",
                displayId = "TV-001",
                planCode = "pro-monthly",
                paymentState = "paid",
                priorityClass = "paid_active",
                renewalState = "auto_renewing",
            ),
            expiresAt = "2026-04-20T12:00:00.000Z",
            updatedAt = "2026-04-20T11:45:00.000Z",
            polledAtEpochMs = 100L,
        )

        store.save(expected)

        assertEquals(expected, store.read())
    }

    @Test
    fun clear_removes_saved_resource_session() = runTest {
        val store = InMemoryResourceSessionStore(
            StoredResourceSession(
                resourceSessionId = "rs_001",
                queueStatus = "queued",
                priorityClass = "paid_active",
                queuePosition = 3,
                estimatedWaitSeconds = 45,
                entitlementSummary = StoredEntitlementSnapshot(
                    accountId = "acct_001",
                    displayId = "TV-001",
                    planCode = "pro-monthly",
                    paymentState = "pending",
                    priorityClass = "paid_active",
                    renewalState = "manual_review",
                ),
                updatedAt = "2026-04-20T11:45:00.000Z",
                polledAtEpochMs = 100L,
            ),
        )

        store.clear()

        assertNull(store.read())
    }
}
