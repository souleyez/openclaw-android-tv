package com.openclaw.tv.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntitlementStoreTest {

    @Test
    fun save_then_read_round_trips_entitlement_summary() = runTest {
        val store = InMemoryEntitlementStore()
        val expected = StoredEntitlementSummary(
            accountId = "acct_001",
            displayId = "TV-001",
            planCode = "pro-monthly",
            paymentState = "paid",
            priorityClass = "paid_active",
            renewalState = "auto_renewing",
            cachedAtEpochMs = 100L,
        )

        store.save(expected)

        assertEquals(expected, store.read())
    }

    @Test
    fun clear_removes_saved_entitlement() = runTest {
        val store = InMemoryEntitlementStore(
            StoredEntitlementSummary(
                accountId = "acct_001",
                displayId = "TV-001",
                planCode = "pro-monthly",
                paymentState = "pending",
                priorityClass = "paid_active",
                renewalState = "manual_review",
                cachedAtEpochMs = 100L,
            ),
        )

        store.clear()

        assertNull(store.read())
    }
}
