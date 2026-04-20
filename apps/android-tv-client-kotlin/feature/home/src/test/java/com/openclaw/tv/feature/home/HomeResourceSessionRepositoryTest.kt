package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
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
import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredResourceAppAccountLease
import com.openclaw.tv.core.storage.StoredResourceModelLease
import com.openclaw.tv.core.storage.StoredResourceSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeResourceSessionRepositoryTest {

    @Test
    fun repository_maps_remote_resource_session_and_persists_cache() = runTest {
        val cacheStore = InMemoryResourceSessionStore()
        val repository = HomeResourceSessionRepository(
            platformApi = FakePlatformApi(
                resourceSession = TvResourceSessionDto(
                    resourceSessionId = "rs_123",
                    queueStatus = "granted",
                    priorityClass = "priority_plus",
                    queuePosition = null,
                    estimatedWaitSeconds = null,
                    appAccountLease = TvResourceAppAccountLeaseDto(
                        leaseId = "app_lease_1",
                        appId = "youtube",
                        accountLabel = "家庭共享",
                        expiresAt = "2026-04-21T00:00:00.000Z",
                    ),
                    modelLease = TvResourceModelLeaseDto(
                        leaseId = "model_lease_1",
                        providerScope = "openclaw.tv",
                        leaseMode = "shared",
                        leaseProfile = "tv-chat",
                        expiresAt = "2026-04-21T00:00:00.000Z",
                    ),
                    entitlementSummary = TvEntitlementSummaryDto(
                        accountId = "acct_123",
                        displayId = "member_789",
                        planCode = "tv_plus",
                        paymentState = "paid",
                        priorityClass = "priority_plus",
                        renewalState = "active",
                    ),
                    expiresAt = "2026-04-21T00:00:00.000Z",
                    updatedAt = "2026-04-20T12:00:00.000Z",
                ),
            ),
            cacheStore = cacheStore,
            nowEpochMs = { 4321L },
        )

        val resolved = repository.load("session_token_1")

        assertEquals(ResourceSessionSource.REMOTE, resolved?.source)
        assertEquals("granted", resolved?.queueStatus)
        assertTrue(resolved?.hasAppAccountLease == true)
        assertTrue(resolved?.hasModelLease == true)
        assertEquals(
            StoredResourceSession(
                resourceSessionId = "rs_123",
                queueStatus = "granted",
                priorityClass = "priority_plus",
                queuePosition = null,
                estimatedWaitSeconds = null,
                appAccountLease = StoredResourceAppAccountLease(
                    leaseId = "app_lease_1",
                    appId = "youtube",
                    accountLabel = "家庭共享",
                    expiresAt = "2026-04-21T00:00:00.000Z",
                ),
                modelLease = StoredResourceModelLease(
                    leaseId = "model_lease_1",
                    providerScope = "openclaw.tv",
                    leaseMode = "shared",
                    leaseProfile = "tv-chat",
                    expiresAt = "2026-04-21T00:00:00.000Z",
                ),
                entitlementSummary = StoredEntitlementSnapshot(
                    accountId = "acct_123",
                    displayId = "member_789",
                    planCode = "tv_plus",
                    paymentState = "paid",
                    priorityClass = "priority_plus",
                    renewalState = "active",
                ),
                expiresAt = "2026-04-21T00:00:00.000Z",
                updatedAt = "2026-04-20T12:00:00.000Z",
                polledAtEpochMs = 4321L,
            ),
            cacheStore.read(),
        )
    }

    @Test
    fun repository_uses_cached_resource_session_when_remote_fetch_fails() = runTest {
        val cacheStore = InMemoryResourceSessionStore(
            StoredResourceSession(
                resourceSessionId = "rs_cached",
                queueStatus = "queued",
                priorityClass = "priority_standard",
                queuePosition = 4,
                estimatedWaitSeconds = 120,
                appAccountLease = null,
                modelLease = null,
                entitlementSummary = StoredEntitlementSnapshot(
                    accountId = "acct_cached",
                    displayId = "member_cached",
                    planCode = "tv_free",
                    paymentState = "free",
                    priorityClass = "priority_standard",
                    renewalState = "active",
                ),
                expiresAt = null,
                updatedAt = "2026-04-20T12:05:00.000Z",
                polledAtEpochMs = 100L,
            ),
        )
        val repository = HomeResourceSessionRepository(
            platformApi = FakePlatformApi(throwOnStatus = true),
            cacheStore = cacheStore,
        )

        val resolved = repository.load("session_token_1")

        assertEquals(ResourceSessionSource.CACHE, resolved?.source)
        assertEquals("queued", resolved?.queueStatus)
        assertEquals(4, resolved?.queuePosition)
        assertEquals("free", resolved?.entitlementSummary?.paymentState)
    }

    @Test
    fun repository_returns_null_when_remote_and_cache_are_unavailable() = runTest {
        val repository = HomeResourceSessionRepository(
            platformApi = FakePlatformApi(throwOnStatus = true),
            cacheStore = InMemoryResourceSessionStore(),
        )

        val resolved = repository.load("session_token_1")

        assertEquals(null, resolved)
    }

    private class FakePlatformApi(
        private val resourceSession: TvResourceSessionDto = TvResourceSessionDto(),
        private val throwOnStatus: Boolean = false,
    ) : PlatformApi {

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
        }

        override suspend fun getTvHomeConfig(): TvHomeConfigDto {
            error("Not used in this test")
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
            if (throwOnStatus) {
                error("resource session unavailable")
            }
            return resourceSession
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
