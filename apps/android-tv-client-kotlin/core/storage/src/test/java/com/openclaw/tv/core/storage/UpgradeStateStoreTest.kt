package com.openclaw.tv.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpgradeStateStoreTest {

    @Test
    fun recordLaunch_tracks_first_seen_version_without_success_notice() = runTest {
        val store = InMemoryUpgradeStateStore(nowEpochMs = { 100L })

        val state = store.recordLaunch("0.1.0")

        assertEquals("0.1.0", state.lastSeenVersion)
        assertNull(state.pendingSuccessVersion)
        assertEquals(100L, state.updatedAtEpochMs)
    }

    @Test
    fun recordLaunch_after_version_change_sets_success_notice_and_clears_pending_install() = runTest {
        val store = InMemoryUpgradeStateStore(
            initial = StoredUpgradeState(
                lastSeenVersion = "0.1.0",
                pendingInstallVersion = "0.2.0",
                pendingInstallDownloadId = 42L,
            ),
            nowEpochMs = { 200L },
        )

        val state = store.recordLaunch("0.2.0")

        assertEquals("0.2.0", state.lastSeenVersion)
        assertEquals("0.2.0", state.pendingSuccessVersion)
        assertNull(state.pendingInstallVersion)
        assertNull(state.pendingInstallDownloadId)
        assertEquals(200L, state.updatedAtEpochMs)
    }

    @Test
    fun consumeSuccessVersion_returns_and_clears_pending_notice() = runTest {
        val store = InMemoryUpgradeStateStore(
            initial = StoredUpgradeState(
                lastSeenVersion = "0.2.0",
                pendingSuccessVersion = "0.2.0",
            ),
            nowEpochMs = { 300L },
        )

        val consumed = store.consumeSuccessVersion()
        val persisted = store.read()

        assertEquals("0.2.0", consumed)
        assertNull(persisted?.pendingSuccessVersion)
        assertEquals(300L, persisted?.updatedAtEpochMs)
    }
}
