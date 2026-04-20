package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.PlatformApiException
import com.openclaw.tv.core.network.dto.BootstrapAuthEnvelope
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.BootstrapDeviceDto
import com.openclaw.tv.core.network.dto.BootstrapSessionDto
import com.openclaw.tv.core.network.dto.BootstrapUserDto
import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LatestReleaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseDto
import com.openclaw.tv.core.network.dto.LeaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusRequestDto
import com.openclaw.tv.core.network.dto.ModelAccessDto
import com.openclaw.tv.core.network.dto.PolicyEnvelope
import com.openclaw.tv.core.network.dto.ProxyDto
import com.openclaw.tv.core.network.dto.ReleaseDto
import com.openclaw.tv.core.network.dto.ReleaseLeaseEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.network.dto.UpgradeDto
import com.openclaw.tv.core.storage.InMemoryLeaseStore
import com.openclaw.tv.core.storage.InMemorySessionStore
import com.openclaw.tv.core.storage.StoredLease
import com.openclaw.tv.core.storage.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapRuntimeTest {

    @Test
    fun syncNow_bootstraps_when_no_local_session_exists() = runTest {
        val api = FakePlatformApi()
        val runtime = createRuntime(api)

        runtime.syncNow()

        assertEquals(
            listOf("bootstrap", "policy", "issueLease", "latestRelease"),
            api.calls,
        )
        assertEquals(BootstrapRuntimePhase.READY, runtime.state.value.phase)
        assertEquals("session_token_1", runtime.state.value.session?.sessionToken)
        assertEquals("lease_1", runtime.state.value.lease?.id)
        assertEquals(RuntimeUpgradeState.AVAILABLE, runtime.state.value.upgradeStatus?.state)
    }

    @Test
    fun syncNow_renews_existing_state_without_bootstrap() = runTest {
        val api = FakePlatformApi()
        val runtime = createRuntime(
            api = api,
            seedSession = true,
            seedLease = true,
        )

        runtime.syncNow()

        assertEquals(listOf("policy", "renewLease", "latestRelease"), api.calls)
        assertEquals(BootstrapRuntimePhase.READY, runtime.state.value.phase)
        assertEquals("2026-04-16T12:05:00.000Z", runtime.state.value.lease?.lastRenewedAt)
        assertEquals(RuntimeUpgradeState.AVAILABLE, runtime.state.value.upgradeStatus?.state)
    }

    @Test
    fun syncNow_rebootstraps_when_existing_session_is_unauthorized() = runTest {
        val api = FakePlatformApi(policyFailureStatusCode = 401)
        val runtime = createRuntime(
            api = api,
            seedSession = true,
            seedLease = true,
        )

        runtime.syncNow()

        assertEquals(
            listOf("policy", "bootstrap", "policy", "issueLease", "latestRelease"),
            api.calls,
        )
        assertEquals(BootstrapRuntimePhase.READY, runtime.state.value.phase)
        assertEquals("session_token_1", runtime.state.value.session?.sessionToken)
    }

    @Test
    fun syncNow_degrades_when_refresh_fails_without_auth_error() = runTest {
        val api = FakePlatformApi(policyFailureStatusCode = 500)
        val runtime = createRuntime(
            api = api,
            seedSession = true,
            seedLease = true,
        )

        runtime.syncNow()

        assertEquals(BootstrapRuntimePhase.DEGRADED, runtime.state.value.phase)
        assertTrue(runtime.state.value.errorMessage?.contains("HTTP 500") == true)
        assertEquals("stored_session_token", runtime.state.value.session?.sessionToken)
        assertEquals("stored_lease_id", runtime.state.value.lease?.id)
    }

    @Test
    fun syncNow_returns_idle_when_runtime_gateway_routes_are_missing_before_bootstrap() = runTest {
        val api = FakePlatformApi(bootstrapFailureStatusCode = 404)
        val runtime = createRuntime(api)

        runtime.syncNow()

        assertEquals(listOf("bootstrap"), api.calls)
        assertEquals(BootstrapRuntimePhase.IDLE, runtime.state.value.phase)
        assertEquals(null, runtime.state.value.session)
        assertEquals(null, runtime.state.value.lease)
        assertEquals(null, runtime.state.value.errorMessage)
    }

    @Test
    fun syncNow_clears_stored_state_when_runtime_gateway_routes_are_missing_after_session_restore() = runTest {
        val api = FakePlatformApi(policyFailureStatusCode = 404)
        val runtime = createRuntime(
            api = api,
            seedSession = true,
            seedLease = true,
        )

        runtime.syncNow()

        assertEquals(listOf("policy"), api.calls)
        assertEquals(BootstrapRuntimePhase.IDLE, runtime.state.value.phase)
        assertEquals(null, runtime.state.value.session)
        assertEquals(null, runtime.state.value.lease)
        assertEquals(null, runtime.state.value.errorMessage)
    }

    private suspend fun createRuntime(
        api: FakePlatformApi,
        seedSession: Boolean = false,
        seedLease: Boolean = false,
        currentVersion: String = "0.1.0",
    ): BootstrapRuntime {
        val sessionStore = InMemorySessionStore().also { store ->
            if (seedSession) {
                store.save(
                    StoredSession(
                        projectKey = "openclaw",
                        sessionToken = "stored_session_token",
                        expiresAt = "2026-04-16T13:00:00.000Z",
                        userId = "user_1",
                        deviceId = "device_1",
                        principalLabel = "Stored Device",
                    ),
                )
            }
        }
        val leaseStore = InMemoryLeaseStore().also { store ->
            if (seedLease) {
                store.save(
                    StoredLease(
                        id = "stored_lease_id",
                        token = "stored_lease_token",
                        providerScope = "moonshot",
                        expiresAt = "2026-04-16T12:10:00.000Z",
                        leaseMode = "direct_provider_temporary",
                        leaseProfile = "client_short",
                        lastUsedAt = "2026-04-16T12:00:00.000Z",
                        lastRenewedAt = "2026-04-16T12:00:00.000Z",
                        sticky = false,
                        proxyBaseUrl = "https://proxy.example.com",
                    ),
                )
            }
        }
        val repository = BootstrapRepository(api, sessionStore, leaseStore)

        return BootstrapRuntime(
            scope = CoroutineScope(SupervisorJob()),
            repository = repository,
            leaseCoordinator = LeaseCoordinator(repository),
            requestFactory = {
                BootstrapAuthRequestDto(
                    principalType = "device",
                    principalKey = "tv-install-01",
                    principalLabel = "OpenClaw TV",
                    projectKey = "openclaw",
                    deviceFingerprint = "install-01",
                    clientVersion = currentVersion,
                )
            },
            currentClientVersion = currentVersion,
            leaseProfile = "client_short",
            nowEpochMs = { 123456789L },
        )
    }

    private class FakePlatformApi(
        private val bootstrapFailureStatusCode: Int? = null,
        private val policyFailureStatusCode: Int? = null,
    ) : PlatformApi {
        val calls = mutableListOf<String>()
        private var remainingBootstrapFailures = if (bootstrapFailureStatusCode != null) 1 else 0
        private var remainingPolicyFailures = if (policyFailureStatusCode != null) 1 else 0

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            calls += "bootstrap"
            if (remainingBootstrapFailures > 0 && bootstrapFailureStatusCode != null) {
                remainingBootstrapFailures -= 1
                throw PlatformApiException(bootstrapFailureStatusCode, "{}")
            }
            return BootstrapAuthEnvelope(
                status = "ok",
                user = BootstrapUserDto(
                    id = "user_1",
                    principalType = request.principalType.orEmpty(),
                    principalKey = request.principalKey.orEmpty(),
                    principalLabel = request.principalLabel.orEmpty(),
                    phone = request.principalKey.orEmpty(),
                    source = "self_registered",
                    status = "active",
                ),
                device = BootstrapDeviceDto(id = "device_1"),
                session = BootstrapSessionDto(
                    token = "session_token_1",
                    expiresAt = "2026-04-16T13:00:00.000Z",
                ),
                upgrade = UpgradeDto(
                    state = "ok",
                    channel = "stable",
                    currentVersion = request.clientVersion.orEmpty(),
                    minSupportedVersion = "0.1.0",
                    latestVersion = "0.2.0",
                    targetVersion = "0.2.0",
                ),
                modelAccess = ModelAccessDto(
                    mode = "lease",
                    providers = listOf("moonshot"),
                    defaultModel = "moonshot-v1",
                    allowedModels = listOf("moonshot-v1"),
                ),
            )
        }

        override suspend fun getTvHomeConfig(): TvHomeConfigDto {
            return TvHomeConfigDto()
        }

        override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
            error("Not used in this test")
        }

        override suspend fun getEntitlement(sessionToken: String): TvEntitlementSummaryDto {
            error("Not used in this test")
        }

        override suspend fun requestResourceSession(
            sessionToken: String,
            request: TvResourceSessionRequestDto,
        ): TvResourceSessionDto {
            error("Not used in this test")
        }

        override suspend fun getResourceSessionStatus(
            sessionToken: String,
            resourceSessionId: String?,
        ): TvResourceSessionDto {
            error("Not used in this test")
        }

        override suspend fun renewResourceSession(
            sessionToken: String,
            request: TvResourceSessionReferenceDto,
        ): TvResourceSessionDto {
            error("Not used in this test")
        }

        override suspend fun releaseResourceSession(
            sessionToken: String,
            request: TvResourceSessionReferenceDto,
        ): TvResourceSessionDto {
            error("Not used in this test")
        }

        override suspend fun getPolicy(sessionToken: String, projectKey: String?): PolicyEnvelope {
            calls += "policy"
            if (remainingPolicyFailures > 0 && policyFailureStatusCode != null) {
                remainingPolicyFailures -= 1
                val statusCode = policyFailureStatusCode
                throw PlatformApiException(statusCode, "{}")
            }
            return PolicyEnvelope(
                status = "ok",
                policy = ClientPolicyDto(
                    channel = "stable",
                    minSupportedVersion = "0.1.0",
                    targetVersion = "0.2.0",
                    forceUpgrade = false,
                    allowSelfRegister = true,
                    modelAccessMode = "lease",
                    providerScopes = listOf("moonshot"),
                    defaultModel = "moonshot-v1",
                    allowedModels = listOf("moonshot-v1"),
                ),
            )
        }

        override suspend fun getLatestRelease(
            sessionToken: String,
            channel: String?,
            projectKey: String?,
        ): LatestReleaseEnvelope {
            calls += "latestRelease"
            return LatestReleaseEnvelope(
                status = "ok",
                release = ReleaseDto(
                    id = "rel_1",
                    projectKey = projectKey ?: "openclaw",
                    channel = channel ?: "stable",
                    version = "0.2.0",
                    status = "published",
                    artifactType = "apk",
                    artifactUrl = "https://cdn.example.com/app.apk",
                    artifactSha256 = "abc",
                    artifactSize = 12345,
                    runtimeVersion = "0.2.0",
                    releaseMetadata = emptyMap(),
                    openclawVersion = "0.2.0",
                    installerVersion = "1",
                    minSupportedVersion = "0.1.0",
                    releaseNotes = "notes",
                    publishedAt = "2026-04-16T10:00:00.000Z",
                    createdAt = "2026-04-16T09:00:00.000Z",
                    updatedAt = "2026-04-16T10:00:00.000Z",
                ),
            )
        }

        override suspend fun issueLease(sessionToken: String, request: IssueLeaseRequestDto): LeaseEnvelope {
            calls += "issueLease"
            return LeaseEnvelope(
                status = "ok",
                lease = buildLease(
                    id = "lease_1",
                    token = "lease_token_1",
                    lastRenewedAt = "2026-04-16T12:00:00.000Z",
                ),
                proxy = ProxyDto(baseUrl = "https://proxy.example.com"),
            )
        }

        override suspend fun getLeaseStatus(sessionToken: String, request: LeaseStatusRequestDto): LeaseStatusEnvelope {
            return LeaseStatusEnvelope(
                status = "ok",
                lease = null,
            )
        }

        override suspend fun renewLease(sessionToken: String, request: RenewLeaseRequestDto): LeaseStatusEnvelope {
            calls += "renewLease"
            return LeaseStatusEnvelope(
                status = "ok",
                lease = buildLease(
                    id = request.leaseId ?: "lease_1",
                    token = null,
                    lastRenewedAt = "2026-04-16T12:05:00.000Z",
                ),
            )
        }

        override suspend fun releaseLease(sessionToken: String, request: ReleaseLeaseRequestDto): ReleaseLeaseEnvelope {
            calls += "releaseLease"
            return ReleaseLeaseEnvelope(
                status = "ok",
                released = true,
                leaseId = request.leaseId.orEmpty(),
            )
        }

        private fun buildLease(
            id: String,
            token: String?,
            lastRenewedAt: String,
        ): LeaseDto {
            return LeaseDto(
                id = id,
                token = token,
                expiresAt = "2026-04-16T12:10:00.000Z",
                providerScope = "moonshot",
                leaseMode = "direct_provider_temporary",
                leaseProfile = "client_short",
                lastUsedAt = "2026-04-16T12:00:00.000Z",
                lastRenewedAt = lastRenewedAt,
                sticky = false,
            )
        }
    }
}
