package com.openclaw.tv.runtime

import com.openclaw.tv.core.storage.StoredRuntimeManifest
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshotSource
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.home.TvHomeRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationRuntimeCoordinatorTest {

    @Test
    fun start_primes_runtime_config_and_observes_bootstrap_state() = runTest {
        val configLoader = FakeConfigLoader()
        val manifestLoader = FakeManifestLoader()
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync(hasStoredSession = true)
        val appDeliverySync = FakeAppDeliverySync()
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = flowOf(
                BootstrapRuntimeState(),
                readyState("session_token_1"),
            ),
            configLoader = configLoader,
            manifestLoader = manifestLoader::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = appDeliverySync,
            deviceActivityProvider = DeviceActivityProvider { true },
        )

        coordinator.start()
        advanceUntilIdle()

        assertEquals(1, configLoader.loadCount)
        assertEquals(2, resourceSessionSync.resumeCount)
        assertEquals(1, resourceSessionSync.pollCount)
        assertEquals(listOf("session_token_1"), entitlementSync.loadedSessionTokens)
        assertEquals(listOf("session_token_1"), manifestLoader.loadedSessionTokens)
        assertEquals(1, appDeliverySync.enqueueCount)
    }

    @Test
    fun sync_for_same_session_runs_only_once() = runTest {
        val manifestLoader = FakeManifestLoader()
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync(hasStoredSession = true)
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = manifestLoader::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(readyState("session_token_1"))

        assertEquals(listOf("session_token_1"), manifestLoader.loadedSessionTokens)
        assertEquals(listOf("session_token_1"), entitlementSync.loadedSessionTokens)
        assertEquals(1, resourceSessionSync.pollCount)
    }

    @Test
    fun sync_clears_session_scoped_runtime_state_when_session_changes() = runTest {
        val manifestLoader = FakeManifestLoader()
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync(hasStoredSession = false)
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = manifestLoader::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(readyState("session_token_2"))

        assertEquals(1, entitlementSync.clearCount)
        assertEquals(1, resourceSessionSync.clearCount)
        assertEquals(listOf("session_token_1", "session_token_2"), manifestLoader.loadedSessionTokens)
        assertEquals(listOf("session_token_1", "session_token_2"), entitlementSync.loadedSessionTokens)
    }

    private fun readyState(sessionToken: String): BootstrapRuntimeState {
        return BootstrapRuntimeState(
            phase = BootstrapRuntimePhase.READY,
            session = com.openclaw.tv.core.storage.StoredSession(
                projectKey = "openclaw-android-tv",
                sessionToken = sessionToken,
                expiresAt = "2026-04-21T00:00:00.000Z",
                userId = "user_1",
                deviceId = "device_1",
                principalLabel = "Living Room TV",
            ),
        )
    }

    private class FakeConfigLoader(
        private val config: com.openclaw.tv.feature.home.ResolvedTvHomeConfig =
            TvHomeRepository.fallback().copy(
                manifestPollAfterSeconds = 321,
                backgroundDownloadEnabled = true,
                idleDownloadOnly = true,
            ),
    ) : RuntimeConfigLoader {
        var loadCount = 0

        override suspend fun load(): com.openclaw.tv.feature.home.ResolvedTvHomeConfig {
            loadCount += 1
            return config
        }
    }

    private class FakeManifestLoader(
        private val manifest: StoredRuntimeManifest = StoredRuntimeManifest(
            manifestVersion = "remote-v1",
            countryCode = "CN",
            regionCode = "SH",
            apps = emptyList(),
            adSlots = emptyList(),
            cachedAtEpochMs = 99_000L,
        ),
    ) {
        val loadedSessionTokens = mutableListOf<String>()
        var lastPollAfterSeconds: Int? = null

        suspend fun load(
            sessionToken: String,
            pollAfterSeconds: Int,
        ): RuntimeManifestSnapshot {
            loadedSessionTokens += sessionToken
            lastPollAfterSeconds = pollAfterSeconds
            return RuntimeManifestSnapshot(
                manifest = manifest,
                source = RuntimeManifestSnapshotSource.REMOTE,
                refreshed = true,
                nextRefreshAtEpochMs = null,
            )
        }
    }

    private class FakeEntitlementSync : RuntimeEntitlementSync {
        val loadedSessionTokens = mutableListOf<String>()
        var clearCount = 0

        override suspend fun load(sessionToken: String) {
            loadedSessionTokens += sessionToken
        }

        override suspend fun clear() {
            clearCount += 1
        }
    }

    private class FakeResourceSessionSync(
        private val hasStoredSession: Boolean,
    ) : ResourceSessionRuntimeSync {
        var resumeCount = 0
        var pollCount = 0
        var clearCount = 0

        override suspend fun resume() {
            resumeCount += 1
        }

        override suspend fun hasStoredSession(): Boolean = hasStoredSession

        override suspend fun poll() {
            pollCount += 1
        }

        override suspend fun clear() {
            clearCount += 1
        }
    }

    private class FakeAppDeliverySync : AppDeliveryRuntimeSync {
        var enqueueCount = 0
        var lastDeviceActive = false

        override suspend fun enqueueEligibleDownloads(
            manifest: RuntimeManifestSnapshot,
            config: com.openclaw.tv.feature.home.ResolvedTvHomeConfig,
            deviceIsActive: Boolean,
        ) {
            enqueueCount += 1
            lastDeviceActive = deviceIsActive
            assertTrue(manifest.manifest != null)
            assertEquals(321, config.manifestPollAfterSeconds)
        }
    }
}
