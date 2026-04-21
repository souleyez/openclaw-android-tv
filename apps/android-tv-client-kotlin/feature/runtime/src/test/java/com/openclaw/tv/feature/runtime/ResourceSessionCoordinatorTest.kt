package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.PlatformApiException
import com.openclaw.tv.core.network.dto.BootstrapAuthEnvelope
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LatestReleaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusEnvelope
import com.openclaw.tv.core.network.dto.LeaseStatusRequestDto
import com.openclaw.tv.core.network.dto.PolicyEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseEnvelope
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.network.dto.TvResourceAppAccountLeaseDto
import com.openclaw.tv.core.network.dto.TvResourceModelLeaseDto
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryResourceSessionStore
import com.openclaw.tv.core.storage.InMemorySessionStore
import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredResourceSession
import com.openclaw.tv.core.storage.StoredSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceSessionCoordinatorTest {

    @Test
    fun coordinator_starts_from_not_requested_and_advances_to_granted() = runTest {
        val api = FakePlatformApi(
            requestResponse = queuedSession(queueStatus = "queued", queuePosition = 2),
            statusResponses = ArrayDeque(
                listOf(
                    queuedSession(queueStatus = "allocating", queuePosition = 1),
                    grantedSession(),
                ),
            ),
        )
        val sessionStore = InMemorySessionStore(seedSession())
        val resourceStore = InMemoryResourceSessionStore()
        val repository = ResourceSessionRepository(
            platformApi = api,
            sessionStore = sessionStore,
            resourceSessionStore = resourceStore,
        )
        val coordinator = ResourceSessionCoordinator(
            repository = repository,
            pollPolicy = ResourceSessionPollPolicy(defaultPollAfterSeconds = 15),
            nowEpochMs = { 1234L },
        )

        assertEquals("not_requested", coordinator.state.value.queueStatus)

        coordinator.request(appId = "youtube", providerScope = "moonshot", leaseProfile = "server_10m")
        assertEquals("queued", coordinator.state.value.queueStatus)

        coordinator.poll()
        assertEquals("allocating", coordinator.state.value.queueStatus)

        coordinator.poll()
        assertEquals("granted", coordinator.state.value.queueStatus)
        assertTrue(coordinator.state.value.resourceSession?.appAccountLease != null)
        assertTrue(coordinator.state.value.resourceSession?.modelLease != null)
        assertEquals("granted", resourceStore.read()?.queueStatus)
    }

    @Test
    fun renew_reuses_resource_session_id() = runTest {
        val api = FakePlatformApi(
            renewResponse = grantedSession(resourceSessionId = "rs_seed"),
        )
        val sessionStore = InMemorySessionStore(seedSession())
        val resourceStore = InMemoryResourceSessionStore(
            StoredResourceSession(
                resourceSessionId = "rs_seed",
                queueStatus = "granted",
                priorityClass = "paid_active",
                queuePosition = null,
                estimatedWaitSeconds = null,
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
                expiresAt = "2026-04-20T12:00:00.000Z",
                updatedAt = "2026-04-20T11:45:00.000Z",
                polledAtEpochMs = 100L,
            ),
        )
        val repository = ResourceSessionRepository(
            platformApi = api,
            sessionStore = sessionStore,
            resourceSessionStore = resourceStore,
        )
        val coordinator = ResourceSessionCoordinator(
            repository = repository,
            pollPolicy = ResourceSessionPollPolicy(defaultPollAfterSeconds = 15),
            nowEpochMs = { 4321L },
        )

        coordinator.resume()
        coordinator.renew()

        assertEquals("rs_seed", api.lastRenewRequest?.resourceSessionId)
        assertEquals("rs_seed", coordinator.state.value.resourceSession?.resourceSessionId)
    }

    @Test
    fun release_clears_local_resource_session_store() = runTest {
        val api = FakePlatformApi(
            releaseResponse = releasedSession(resourceSessionId = "rs_seed"),
        )
        val sessionStore = InMemorySessionStore(seedSession())
        val resourceStore = InMemoryResourceSessionStore(
            StoredResourceSession(
                resourceSessionId = "rs_seed",
                queueStatus = "granted",
                priorityClass = "paid_active",
                queuePosition = null,
                estimatedWaitSeconds = null,
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
                expiresAt = "2026-04-20T12:00:00.000Z",
                updatedAt = "2026-04-20T11:45:00.000Z",
                polledAtEpochMs = 100L,
            ),
        )
        val repository = ResourceSessionRepository(
            platformApi = api,
            sessionStore = sessionStore,
            resourceSessionStore = resourceStore,
        )
        val coordinator = ResourceSessionCoordinator(
            repository = repository,
            pollPolicy = ResourceSessionPollPolicy(defaultPollAfterSeconds = 15),
            nowEpochMs = { 5678L },
        )

        coordinator.resume()
        coordinator.release()

        assertEquals("rs_seed", api.lastReleaseRequest?.resourceSessionId)
        assertNull(resourceStore.read())
        assertEquals("released", coordinator.state.value.queueStatus)
    }

    @Test
    fun transient_poll_failure_degrades_without_crashing() = runTest {
        val api = FakePlatformApi(
            statusResponses = ArrayDeque(
                listOf(
                    RuntimeException("boom"),
                ),
            ),
        )
        val sessionStore = InMemorySessionStore(seedSession())
        val resourceStore = InMemoryResourceSessionStore(
            StoredResourceSession(
                resourceSessionId = "rs_seed",
                queueStatus = "queued",
                priorityClass = "paid_active",
                queuePosition = 2,
                estimatedWaitSeconds = 90,
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
                expiresAt = null,
                updatedAt = "2026-04-20T11:45:00.000Z",
                polledAtEpochMs = 100L,
            ),
        )
        val repository = ResourceSessionRepository(
            platformApi = api,
            sessionStore = sessionStore,
            resourceSessionStore = resourceStore,
        )
        val coordinator = ResourceSessionCoordinator(
            repository = repository,
            pollPolicy = ResourceSessionPollPolicy(defaultPollAfterSeconds = 15),
            nowEpochMs = { 9876L },
        )

        coordinator.resume()
        coordinator.poll()

        assertEquals(ResourceSessionRuntimePhase.DEGRADED, coordinator.state.value.phase)
        assertEquals("queued", coordinator.state.value.queueStatus)
        assertTrue(coordinator.state.value.errorMessage?.contains("boom") == true)
    }

    @Test
    fun resume_marks_expired_granted_session_as_expired_and_prunes_local_leases() = runTest {
        val sessionStore = InMemorySessionStore(seedSession())
        val resourceStore = InMemoryResourceSessionStore(
            StoredResourceSession(
                resourceSessionId = "rs_expired",
                queueStatus = "granted",
                priorityClass = "paid_active",
                queuePosition = null,
                estimatedWaitSeconds = null,
                appAccountLease = com.openclaw.tv.core.storage.StoredResourceAppAccountLease(
                    leaseId = "aal_expired",
                    appId = "youtube",
                    accountLabel = "shared-premium-01",
                    expiresAt = "1970-01-01T00:00:10.000Z",
                ),
                modelLease = com.openclaw.tv.core.storage.StoredResourceModelLease(
                    leaseId = "ml_expired",
                    providerScope = "moonshot",
                    leaseMode = "proxy",
                    leaseProfile = "default",
                    expiresAt = "1970-01-01T00:00:10.000Z",
                ),
                entitlementSummary = StoredEntitlementSnapshot(
                    accountId = "acct_1",
                    displayId = "TV-001",
                    planCode = "pro-monthly",
                    paymentState = "paid",
                    priorityClass = "paid_active",
                    renewalState = "auto_renewing",
                ),
                expiresAt = "1970-01-01T00:00:10.000Z",
                updatedAt = "2026-04-20T11:45:00.000Z",
                polledAtEpochMs = 100L,
            ),
        )
        val repository = ResourceSessionRepository(
            platformApi = FakePlatformApi(),
            sessionStore = sessionStore,
            resourceSessionStore = resourceStore,
        )
        val coordinator = ResourceSessionCoordinator(
            repository = repository,
            pollPolicy = ResourceSessionPollPolicy(defaultPollAfterSeconds = 15),
            nowEpochMs = { 20_000L },
        )

        coordinator.resume()

        assertEquals(ResourceSessionRuntimePhase.IDLE, coordinator.state.value.phase)
        assertEquals("expired", coordinator.state.value.queueStatus)
        assertNull(coordinator.state.value.resourceSession?.appAccountLease)
        assertNull(coordinator.state.value.resourceSession?.modelLease)
        assertNull(coordinator.state.value.nextPollAfterSeconds)
        assertEquals("expired", resourceStore.read()?.queueStatus)
        assertNull(resourceStore.read()?.appAccountLease)
        assertNull(resourceStore.read()?.modelLease)
    }

    private fun seedSession(): StoredSession {
        return StoredSession(
            projectKey = "openclaw-android-tv",
            sessionToken = "session_token_1",
            expiresAt = "2026-04-20T12:30:00.000Z",
            userId = "user_1",
            deviceId = "device_1",
            principalLabel = "OpenClaw TV",
        )
    }

    private class FakePlatformApi(
        private val requestResponse: TvResourceSessionDto = queuedSession(queueStatus = "queued", queuePosition = 2),
        private val statusResponses: ArrayDeque<Any> = ArrayDeque(),
        private val renewResponse: TvResourceSessionDto = grantedSession(),
        private val releaseResponse: TvResourceSessionDto = releasedSession("rs_001"),
    ) : PlatformApi {
        var lastRenewRequest: TvResourceSessionReferenceDto? = null
        var lastReleaseRequest: TvResourceSessionReferenceDto? = null

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
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
            return requestResponse
        }

        override suspend fun getResourceSessionStatus(
            sessionToken: String,
            resourceSessionId: String?,
        ): TvResourceSessionDto {
            val next = statusResponses.removeFirstOrNull() ?: grantedSession(resourceSessionId ?: "rs_001")
            return when (next) {
                is TvResourceSessionDto -> next
                is PlatformApiException -> throw next
                is RuntimeException -> throw next
                else -> error("Unsupported queued response ${next::class.java.simpleName}")
            }
        }

        override suspend fun renewResourceSession(
            sessionToken: String,
            request: TvResourceSessionReferenceDto,
        ): TvResourceSessionDto {
            lastRenewRequest = request
            return renewResponse
        }

        override suspend fun releaseResourceSession(
            sessionToken: String,
            request: TvResourceSessionReferenceDto,
        ): TvResourceSessionDto {
            lastReleaseRequest = request
            return releaseResponse
        }

        override suspend fun getPolicy(sessionToken: String, projectKey: String?): PolicyEnvelope {
            error("Not used in this test")
        }

        override suspend fun getLatestRelease(
            sessionToken: String,
            channel: String?,
            projectKey: String?,
        ): LatestReleaseEnvelope {
            error("Not used in this test")
        }

        override suspend fun issueLease(sessionToken: String, request: IssueLeaseRequestDto): LeaseEnvelope {
            error("Not used in this test")
        }

        override suspend fun getLeaseStatus(
            sessionToken: String,
            request: LeaseStatusRequestDto,
        ): LeaseStatusEnvelope {
            error("Not used in this test")
        }

        override suspend fun renewLease(
            sessionToken: String,
            request: RenewLeaseRequestDto,
        ): LeaseStatusEnvelope {
            error("Not used in this test")
        }

        override suspend fun releaseLease(
            sessionToken: String,
            request: ReleaseLeaseRequestDto,
        ): ReleaseLeaseEnvelope {
            error("Not used in this test")
        }
    }
}

private fun queuedSession(
    resourceSessionId: String = "rs_001",
    queueStatus: String,
    queuePosition: Int,
): TvResourceSessionDto {
    return TvResourceSessionDto(
        resourceSessionId = resourceSessionId,
        queueStatus = queueStatus,
        priorityClass = "paid_active",
        queuePosition = queuePosition,
        estimatedWaitSeconds = 90,
        entitlementSummary = TvEntitlementSummaryDto(
            accountId = "acct_1",
            displayId = "TV-001",
            planCode = "pro-monthly",
            paymentState = "paid",
            priorityClass = "paid_active",
            renewalState = "auto_renewing",
        ),
        updatedAt = "2026-04-20T11:45:00.000Z",
    )
}

private fun grantedSession(
    resourceSessionId: String = "rs_001",
): TvResourceSessionDto {
    return TvResourceSessionDto(
        resourceSessionId = resourceSessionId,
        queueStatus = "granted",
        priorityClass = "paid_active",
        queuePosition = null,
        estimatedWaitSeconds = null,
        appAccountLease = TvResourceAppAccountLeaseDto(
            leaseId = "aal_001",
            appId = "youtube",
            accountLabel = "shared-premium-01",
            expiresAt = "2026-04-20T12:00:00.000Z",
        ),
        modelLease = TvResourceModelLeaseDto(
            leaseId = "ml_001",
            providerScope = "moonshot",
            leaseMode = "proxy",
            leaseProfile = "default",
            expiresAt = "2026-04-20T12:00:00.000Z",
        ),
        entitlementSummary = TvEntitlementSummaryDto(
            accountId = "acct_1",
            displayId = "TV-001",
            planCode = "pro-monthly",
            paymentState = "paid",
            priorityClass = "paid_active",
            renewalState = "auto_renewing",
        ),
        expiresAt = "2026-04-20T12:00:00.000Z",
        updatedAt = "2026-04-20T11:45:00.000Z",
    )
}

private fun releasedSession(
    resourceSessionId: String,
): TvResourceSessionDto {
    return TvResourceSessionDto(
        resourceSessionId = resourceSessionId,
        queueStatus = "released",
        priorityClass = "paid_active",
        entitlementSummary = TvEntitlementSummaryDto(
            accountId = "acct_1",
            displayId = "TV-001",
            planCode = "pro-monthly",
            paymentState = "paid",
            priorityClass = "paid_active",
            renewalState = "auto_renewing",
        ),
        updatedAt = "2026-04-20T11:50:00.000Z",
    )
}
