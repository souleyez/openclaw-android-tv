package com.openclaw.tv.runtime

import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredResourceSession
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshotSource
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.home.ResolvedTvHomeConfig
import com.openclaw.tv.feature.home.TvHomeRepository
import com.openclaw.tv.feature.runtime.ResourceSessionRuntimePhase
import com.openclaw.tv.feature.runtime.ResourceSessionRuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationRuntimeCoordinatorTest {

    @Test
    fun start_primes_runtime_config_and_runs_one_steady_sync_cycle() = runTest {
        val configLoader = FakeConfigLoader()
        val manifestLoader = FakeManifestLoader()
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = activeResourceState(
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_steady",
                    queueStatus = "granted",
                    expiresAt = "2099-04-21T00:20:00.000Z",
                ),
            ),
        )
        val appDeliverySync = FakeAppDeliverySync()
        val delayController = SingleCycleDelayController()
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
            delayFor = delayController::delay,
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.start()
        advanceUntilIdle()

        assertEquals(2, configLoader.loadCount)
        assertEquals(1, resourceSessionSync.resumeCount)
        assertEquals(2, resourceSessionSync.pollCount)
        assertEquals(
            listOf(
                EntitlementLoadRequest("session_token_1", 321),
                EntitlementLoadRequest("session_token_1", 321),
            ),
            entitlementSync.loadRequests,
        )
        assertEquals(listOf("session_token_1", "session_token_1"), manifestLoader.loadedSessionTokens)
        assertEquals(2, appDeliverySync.enqueueCount)
        assertEquals(1, delayController.delayCount)
    }

    @Test
    fun sync_for_same_session_runs_only_once() = runTest {
        val manifestLoader = FakeManifestLoader()
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = activeResourceState(
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_same",
                    queueStatus = "granted",
                    expiresAt = "2099-04-21T00:20:00.000Z",
                ),
            ),
        )
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = manifestLoader::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(readyState("session_token_1"))

        assertEquals(listOf("session_token_1"), manifestLoader.loadedSessionTokens)
        assertEquals(
            listOf(EntitlementLoadRequest("session_token_1", 321)),
            entitlementSync.loadRequests,
        )
        assertEquals(1, resourceSessionSync.pollCount)
    }

    @Test
    fun sync_clears_session_scoped_runtime_state_when_session_changes() = runTest {
        val manifestLoader = FakeManifestLoader()
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = activeResourceState(
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_seed",
                    queueStatus = "queued",
                    expiresAt = null,
                ),
                queueStatus = "queued",
            ),
        )
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = manifestLoader::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(readyState("session_token_2"))

        assertEquals(1, entitlementSync.clearCount)
        assertEquals(1, resourceSessionSync.clearCount)
        assertEquals(
            listOf(ResourceSessionReleaseRequest("session_token_1", "rs_seed")),
            resourceSessionSync.releaseRequests,
        )
        assertEquals(listOf("session_token_1", "session_token_2"), manifestLoader.loadedSessionTokens)
        assertEquals(
            listOf(
                EntitlementLoadRequest("session_token_1", 321),
                EntitlementLoadRequest("session_token_2", 321),
            ),
            entitlementSync.loadRequests,
        )
    }

    @Test
    fun sync_releases_previous_resource_session_when_session_is_lost() = runTest {
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = activeResourceState(
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_seed",
                    queueStatus = "queued",
                    expiresAt = null,
                ),
                queueStatus = "queued",
            ),
        )
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = FakeManifestLoader()::load,
            entitlementSync = FakeEntitlementSync(),
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(BootstrapRuntimeState())

        assertEquals(
            listOf(ResourceSessionReleaseRequest("session_token_1", "rs_seed")),
            resourceSessionSync.releaseRequests,
        )
        assertEquals(1, resourceSessionSync.clearCount)
    }

    @Test
    fun sync_skips_release_for_terminal_resource_session_state() = runTest {
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = ResourceSessionRuntimeState(
                phase = ResourceSessionRuntimePhase.IDLE,
                queueStatus = "expired",
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_terminal",
                    queueStatus = "expired",
                    expiresAt = "1970-01-01T00:00:10.000Z",
                ),
                nextPollAfterSeconds = null,
                errorMessage = null,
                lastSyncedAtEpochMs = 1_000L,
            ),
            updateStateOnRequest = false,
        )
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = FakeManifestLoader()::load,
            entitlementSync = FakeEntitlementSync(),
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(readyState("session_token_2"))

        assertTrue(resourceSessionSync.releaseRequests.isEmpty())
        assertEquals(1, resourceSessionSync.clearCount)
    }

    @Test
    fun sync_clears_local_state_even_when_previous_resource_session_release_fails() = runTest {
        val loggedErrors = mutableListOf<String>()
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = activeResourceState(
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_seed",
                    queueStatus = "granted",
                    expiresAt = "2099-04-21T00:20:00.000Z",
                ),
            ),
            releaseError = IllegalStateException("release failed"),
        )
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = FakeManifestLoader()::load,
            entitlementSync = FakeEntitlementSync(),
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
            logError = { message, _ -> loggedErrors += message },
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))
        coordinator.syncForBootstrapState(readyState("session_token_2"))

        assertEquals(1, resourceSessionSync.clearCount)
        assertEquals(
            listOf(ResourceSessionReleaseRequest("session_token_1", "rs_seed")),
            resourceSessionSync.releaseRequests,
        )
        assertTrue(loggedErrors.contains("Failed to release previous resource-session state"))
    }

    @Test
    fun sync_requests_resource_session_when_none_is_stored() = runTest {
        val resourceSessionSync = FakeResourceSessionSync()
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = FakeManifestLoader()::load,
            entitlementSync = FakeEntitlementSync(),
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            resourceSessionLeaseProfile = "client_short",
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))

        assertEquals(1, resourceSessionSync.requestCount)
        assertEquals("client_short", resourceSessionSync.lastRequestedLeaseProfile)
        assertEquals(0, resourceSessionSync.pollCount)
    }

    @Test
    fun granted_resource_session_renews_when_expiry_is_near() = runTest {
        val resourceSessionSync = FakeResourceSessionSync(
            currentState = activeResourceState(
                resourceSession = storedResourceSession(
                    resourceSessionId = "rs_renew",
                    queueStatus = "granted",
                    expiresAt = "1970-01-01T00:00:20.000Z",
                ),
            ),
        )
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = FakeManifestLoader()::load,
            entitlementSync = FakeEntitlementSync(),
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            nowEpochMs = { 1_000L },
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))

        assertEquals(1, resourceSessionSync.renewCount)
        assertEquals(0, resourceSessionSync.pollCount)
    }

    @Test
    fun entitlement_refresh_uses_local_floor_when_manifest_poll_is_too_small() = runTest {
        val entitlementSync = FakeEntitlementSync()
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(
                config = TvHomeRepository.fallback().copy(
                    manifestPollAfterSeconds = 60,
                    resourceSessionPollAfterSeconds = 15,
                ),
            ),
            manifestLoader = FakeManifestLoader(expectedPollAfterSeconds = 60)::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = FakeResourceSessionSync(),
            appDeliverySync = FakeAppDeliverySync(expectedManifestPollAfterSeconds = 60),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.syncForBootstrapState(readyState("session_token_1"))

        assertEquals(
            listOf(EntitlementLoadRequest("session_token_1", 300)),
            entitlementSync.loadRequests,
        )
    }

    @Test
    fun sync_rethrows_manifest_cancellation_without_logging_runtime_failure() = runTest {
        val loggedErrors = mutableListOf<String>()
        val manifestLoader = FakeManifestLoader(
            loadError = CancellationException("sync cancelled"),
        )
        val entitlementSync = FakeEntitlementSync()
        val resourceSessionSync = FakeResourceSessionSync()
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = emptyFlow(),
            configLoader = FakeConfigLoader(),
            manifestLoader = manifestLoader::load,
            entitlementSync = entitlementSync,
            resourceSessionSync = resourceSessionSync,
            appDeliverySync = FakeAppDeliverySync(),
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
            logError = { message, _ -> loggedErrors += message },
        )

        try {
            coordinator.syncForBootstrapState(readyState("session_token_1"))
            org.junit.Assert.fail("Expected sync cancellation to propagate")
        } catch (expected: CancellationException) {
            assertEquals("sync cancelled", expected.message)
        }

        assertTrue(loggedErrors.isEmpty())
        assertTrue(entitlementSync.loadRequests.isEmpty())
        assertEquals(0, resourceSessionSync.requestCount)
        assertEquals(0, resourceSessionSync.pollCount)
        assertEquals(0, resourceSessionSync.renewCount)
    }

    @Test
    fun steady_sync_reports_failure_and_recovery_transitions() = runTest {
        var manifestLoadCount = 0
        val diagnosticsReporter = FakeRuntimeDiagnosticsReporter()
        val delayController = TwoCycleDelayController()
        val coordinator = ApplicationRuntimeCoordinator(
            scope = this,
            bootstrapState = flowOf(
                BootstrapRuntimeState(),
                readyState("session_token_1"),
            ),
            configLoader = FakeConfigLoader(),
            manifestLoader = { sessionToken, pollAfterSeconds ->
                manifestLoadCount += 1
                assertEquals("session_token_1", sessionToken)
                assertEquals(321, pollAfterSeconds)
                if (manifestLoadCount == 2) {
                    throw IllegalStateException("manifest unavailable")
                }
                RuntimeManifestSnapshot(
                    manifest = com.openclaw.tv.core.storage.StoredRuntimeManifest(
                        manifestVersion = "remote-v1",
                        countryCode = "CN",
                        regionCode = "SH",
                        apps = emptyList(),
                        adSlots = emptyList(),
                        cachedAtEpochMs = 99_000L,
                    ),
                    source = RuntimeManifestSnapshotSource.REMOTE,
                    refreshed = true,
                    nextRefreshAtEpochMs = null,
                )
            },
            entitlementSync = FakeEntitlementSync(),
            resourceSessionSync = FakeResourceSessionSync(
                currentState = activeResourceState(
                    resourceSession = storedResourceSession(
                        resourceSessionId = "rs_steady",
                        queueStatus = "granted",
                        expiresAt = "2099-04-21T00:20:00.000Z",
                    ),
                ),
            ),
            appDeliverySync = FakeAppDeliverySync(),
            delayFor = delayController::delay,
            diagnosticsReporter = diagnosticsReporter,
            loopDelayPolicy = RuntimeLoopDelayPolicy(randomDouble = { 0.5 }),
        )

        coordinator.start()
        advanceUntilIdle()

        assertEquals(listOf(false, true), diagnosticsReporter.reports.map { it.cycleSuccessful })
        assertEquals(listOf(1, 0), diagnosticsReporter.reports.map { it.consecutiveFailures })
        assertTrue(diagnosticsReporter.resetCount >= 1)
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

    private fun activeResourceState(
        resourceSession: StoredResourceSession,
        queueStatus: String = resourceSession.queueStatus,
        nextPollAfterSeconds: Int = 15,
    ): ResourceSessionRuntimeState {
        return ResourceSessionRuntimeState(
            phase = ResourceSessionRuntimePhase.ACTIVE,
            queueStatus = queueStatus,
            resourceSession = resourceSession,
            nextPollAfterSeconds = nextPollAfterSeconds,
            errorMessage = null,
            lastSyncedAtEpochMs = 1_000L,
        )
    }

    private fun storedResourceSession(
        resourceSessionId: String,
        queueStatus: String,
        expiresAt: String?,
    ): StoredResourceSession {
        return StoredResourceSession(
            resourceSessionId = resourceSessionId,
            queueStatus = queueStatus,
            priorityClass = "paid_active",
            queuePosition = if (queueStatus == "queued") 2 else null,
            estimatedWaitSeconds = if (queueStatus == "queued") 90 else null,
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
            expiresAt = expiresAt,
            updatedAt = "2026-04-20T11:45:00.000Z",
            polledAtEpochMs = 1_000L,
        )
    }

    private class FakeConfigLoader(
        private val config: ResolvedTvHomeConfig =
            TvHomeRepository.fallback().copy(
                manifestPollAfterSeconds = 321,
                resourceSessionPollAfterSeconds = 15,
                backgroundDownloadEnabled = true,
                idleDownloadOnly = true,
            ),
    ) : RuntimeConfigLoader {
        var loadCount = 0

        override suspend fun load(): ResolvedTvHomeConfig {
            loadCount += 1
            return config
        }
    }

    private class FakeManifestLoader(
        private val expectedPollAfterSeconds: Int = 321,
        private val loadError: Throwable? = null,
    ) {
        val loadedSessionTokens = mutableListOf<String>()

        suspend fun load(
            sessionToken: String,
            pollAfterSeconds: Int,
        ): RuntimeManifestSnapshot {
            loadedSessionTokens += sessionToken
            assertEquals(expectedPollAfterSeconds, pollAfterSeconds)
            loadError?.let { throw it }
            return RuntimeManifestSnapshot(
                manifest = com.openclaw.tv.core.storage.StoredRuntimeManifest(
                    manifestVersion = "remote-v1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = emptyList(),
                    adSlots = emptyList(),
                    cachedAtEpochMs = 99_000L,
                ),
                source = RuntimeManifestSnapshotSource.REMOTE,
                refreshed = true,
                nextRefreshAtEpochMs = null,
            )
        }
    }

    private class FakeEntitlementSync : RuntimeEntitlementSync {
        val loadRequests = mutableListOf<EntitlementLoadRequest>()
        var clearCount = 0

        override suspend fun load(sessionToken: String, pollAfterSeconds: Int) {
            loadRequests += EntitlementLoadRequest(sessionToken, pollAfterSeconds)
        }

        override suspend fun clear() {
            clearCount += 1
        }
    }

    private inner class FakeResourceSessionSync(
        currentState: ResourceSessionRuntimeState = ResourceSessionRuntimeState(),
        private val releaseError: Throwable? = null,
        private val updateStateOnRequest: Boolean = true,
    ) : ResourceSessionRuntimeSync {
        var resumeCount = 0
        var requestCount = 0
        var pollCount = 0
        var renewCount = 0
        var clearCount = 0
        val releaseRequests = mutableListOf<ResourceSessionReleaseRequest>()
        var lastRequestedLeaseProfile: String? = null
        private var runtimeState = currentState

        override suspend fun resume() {
            resumeCount += 1
        }

        override fun currentState(): ResourceSessionRuntimeState = runtimeState

        override suspend fun request(leaseProfile: String?) {
            requestCount += 1
            lastRequestedLeaseProfile = leaseProfile
            if (updateStateOnRequest) {
                runtimeState = activeResourceState(
                    resourceSession = storedResourceSession(
                        resourceSessionId = "rs_requested",
                        queueStatus = "queued",
                        expiresAt = null,
                    ),
                    queueStatus = "queued",
                )
            }
        }

        override suspend fun poll() {
            pollCount += 1
        }

        override suspend fun renew() {
            renewCount += 1
        }

        override suspend fun releaseBestEffort(sessionToken: String, resourceSessionId: String?) {
            releaseRequests += ResourceSessionReleaseRequest(sessionToken, resourceSessionId)
            releaseError?.let { throw it }
        }

        override suspend fun clear() {
            clearCount += 1
            runtimeState = ResourceSessionRuntimeState()
        }
    }

    private class FakeAppDeliverySync(
        private val expectedManifestPollAfterSeconds: Int = 321,
    ) : AppDeliveryRuntimeSync {
        var enqueueCount = 0

        override suspend fun enqueueEligibleDownloads(
            manifest: RuntimeManifestSnapshot,
            config: ResolvedTvHomeConfig,
            deviceIsActive: Boolean,
        ) {
            enqueueCount += 1
            assertTrue(manifest.manifest != null)
            assertEquals(expectedManifestPollAfterSeconds, config.manifestPollAfterSeconds)
        }
    }

    private class SingleCycleDelayController {
        var delayCount = 0

        suspend fun delay(delayMillis: Long) {
            delayCount += 1
            assertTrue(delayMillis > 0)
            throw CancellationException("Stop after one steady-sync delay")
        }
    }

    private class TwoCycleDelayController {
        var delayCount = 0

        suspend fun delay(delayMillis: Long) {
            delayCount += 1
            assertTrue(delayMillis > 0)
            if (delayCount >= 2) {
                throw CancellationException("Stop after two steady-sync delays")
            }
        }
    }

    private class FakeRuntimeDiagnosticsReporter : RuntimeDiagnosticsReporter {
        val reports = mutableListOf<RuntimeSyncCycleReport>()
        var resetCount = 0

        override fun onRuntimeSyncCycle(report: RuntimeSyncCycleReport) {
            reports += report
        }

        override fun onRuntimeSyncReset() {
            resetCount += 1
        }
    }

    private data class EntitlementLoadRequest(
        val sessionToken: String,
        val pollAfterSeconds: Int,
    )

    private data class ResourceSessionReleaseRequest(
        val sessionToken: String,
        val resourceSessionId: String?,
    )
}
