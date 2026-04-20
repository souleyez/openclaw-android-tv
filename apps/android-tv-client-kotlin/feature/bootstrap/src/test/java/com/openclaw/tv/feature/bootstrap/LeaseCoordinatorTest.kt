package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.PlatformApi
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
import com.openclaw.tv.core.network.dto.LeaseProfileDto
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaseCoordinatorTest {

    @Test
    fun bootstrap_stores_session_then_fetches_policy_and_lease() = runTest {
        val api = FakePlatformApi()
        val repository = BootstrapRepository(
            platformApi = api,
            sessionStore = InMemorySessionStore(),
            leaseStore = InMemoryLeaseStore(),
        )
        val coordinator = LeaseCoordinator(repository)

        val result = coordinator.bootstrap(
            BootstrapAuthRequestDto(
                principalType = "phone",
                principalKey = "18800001111",
                principalLabel = "ATV Test",
                projectKey = "openclaw",
                deviceFingerprint = "fingerprint-01",
                clientVersion = "0.1.0",
            ),
            leaseProfile = "client_short",
        )

        assertEquals(listOf("bootstrap", "policy", "issueLease", "latestRelease"), api.calls)
        assertEquals("session_token_1", result.session.sessionToken)
        assertEquals("stable", result.policy.channel)
        assertEquals("lease_1", result.lease.id)
        assertEquals("0.2.0", result.release?.version)
    }

    @Test
    fun renew_uses_stored_lease_and_release_clears_local_state() = runTest {
        val api = FakePlatformApi()
        val sessionStore = InMemorySessionStore()
        val leaseStore = InMemoryLeaseStore()
        val repository = BootstrapRepository(api, sessionStore, leaseStore)
        val coordinator = LeaseCoordinator(repository)

        coordinator.bootstrap(
            BootstrapAuthRequestDto(
                principalType = "phone",
                principalKey = "18800001111",
                principalLabel = "ATV Test",
                projectKey = "openclaw",
                deviceFingerprint = "fingerprint-01",
            ),
        )

        val renewed = coordinator.renewActiveLease()
        val released = coordinator.releaseActiveLease()

        assertEquals("lease_1", api.renewRequest?.leaseId)
        assertEquals("lease_token_1", api.renewRequest?.leaseToken)
        assertEquals("2026-04-16T12:05:00.000Z", renewed.lastRenewedAt)
        assertTrue(released)
        assertNull(repository.getStoredLease())
    }

    private class FakePlatformApi : PlatformApi {
        val calls = mutableListOf<String>()
        var renewRequest: RenewLeaseRequestDto? = null

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            calls += "bootstrap"
            return BootstrapAuthEnvelope(
                status = "ok",
                user = BootstrapUserDto(
                    id = "user_1",
                    principalType = "phone",
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
                    projectKey = "openclaw",
                    channel = channel ?: "stable",
                    version = "0.2.0",
                    status = "published",
                    artifactType = "apk",
                    artifactUrl = "https://cdn.example.com/app.apk",
                    artifactSha256 = "abc",
                    artifactSize = 12345,
                    runtimeVersion = "0.2.0",
                    releaseMetadata = mapOf("track" to "stable"),
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
                lease = lease(lastRenewedAt = "2026-04-16T12:00:00.000Z", token = "lease_token_1"),
                proxy = ProxyDto(baseUrl = "https://proxy.example.com"),
            )
        }

        override suspend fun getLeaseStatus(
            sessionToken: String,
            request: LeaseStatusRequestDto,
        ): LeaseStatusEnvelope {
            return LeaseStatusEnvelope(
                status = "ok",
                lease = lease(lastRenewedAt = "2026-04-16T12:00:00.000Z", token = null),
                availableProfiles = listOf(
                    LeaseProfileDto(
                        id = "client_short",
                        ttlMinutes = 5,
                        leaseMode = "direct_provider_temporary",
                        renewWindowSeconds = 45,
                        contentionPriority = 10,
                        contentionIdleReleaseMinutes = 0,
                        sticky = false,
                    ),
                ),
                proxy = ProxyDto(baseUrl = "https://proxy.example.com"),
            )
        }

        override suspend fun renewLease(sessionToken: String, request: RenewLeaseRequestDto): LeaseStatusEnvelope {
            calls += "renewLease"
            renewRequest = request
            return LeaseStatusEnvelope(
                status = "ok",
                lease = lease(lastRenewedAt = "2026-04-16T12:05:00.000Z", token = null),
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

        private fun lease(lastRenewedAt: String, token: String?): LeaseDto {
            return LeaseDto(
                id = "lease_1",
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
