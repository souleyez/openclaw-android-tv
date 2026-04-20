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
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryEntitlementStore
import com.openclaw.tv.core.storage.StoredEntitlementSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEntitlementRepositoryTest {

    @Test
    fun repository_maps_remote_entitlement_summary_and_persists_cache() = runTest {
        val cacheStore = InMemoryEntitlementStore()
        val repository = HomeEntitlementRepository(
            platformApi = FakePlatformApi(
                entitlement = TvEntitlementSummaryDto(
                    accountId = "acct_123",
                    displayId = "member_789",
                    planCode = "tv_plus",
                    paymentState = "paid",
                    priorityClass = "priority_plus",
                    renewalState = "active",
                ),
            ),
            cacheStore = cacheStore,
            nowEpochMs = { 5678L },
        )

        val resolved = repository.load("session_token_1")

        assertEquals(EntitlementSource.REMOTE, resolved?.source)
        assertEquals("paid", resolved?.paymentState)
        assertEquals("priority_plus", resolved?.priorityClass)
        assertEquals(
            StoredEntitlementSummary(
                accountId = "acct_123",
                displayId = "member_789",
                planCode = "tv_plus",
                paymentState = "paid",
                priorityClass = "priority_plus",
                renewalState = "active",
                cachedAtEpochMs = 5678L,
            ),
            cacheStore.read(),
        )
    }

    @Test
    fun repository_uses_cached_entitlement_when_remote_fetch_fails() = runTest {
        val cacheStore = InMemoryEntitlementStore(
            StoredEntitlementSummary(
                accountId = "acct_cached",
                displayId = "member_cached",
                planCode = "tv_free",
                paymentState = "grace_period",
                priorityClass = "priority_standard",
                renewalState = "retrying",
                cachedAtEpochMs = 123L,
            ),
        )
        val repository = HomeEntitlementRepository(
            platformApi = FakePlatformApi(throwOnEntitlement = true),
            cacheStore = cacheStore,
        )

        val resolved = repository.load("session_token_1")

        assertEquals(EntitlementSource.CACHE, resolved?.source)
        assertEquals("grace_period", resolved?.paymentState)
        assertEquals("retrying", resolved?.renewalState)
    }

    @Test
    fun repository_returns_null_when_remote_and_cache_are_unavailable() = runTest {
        val repository = HomeEntitlementRepository(
            platformApi = FakePlatformApi(throwOnEntitlement = true),
            cacheStore = InMemoryEntitlementStore(),
            requestTimeoutMillis = 10,
        )

        val resolved = repository.load("session_token_1")

        assertEquals(null, resolved)
    }

    private class FakePlatformApi(
        private val entitlement: TvEntitlementSummaryDto = TvEntitlementSummaryDto(),
        private val throwOnEntitlement: Boolean = false,
        private val delayMillis: Long = 0L,
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
            if (throwOnEntitlement) {
                error("entitlement unavailable")
            }
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            return entitlement
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
