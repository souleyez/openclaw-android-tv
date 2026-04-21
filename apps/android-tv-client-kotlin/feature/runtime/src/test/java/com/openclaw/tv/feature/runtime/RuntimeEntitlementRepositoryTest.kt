package com.openclaw.tv.feature.runtime

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEntitlementRepositoryTest {

    @Test
    fun load_persists_remote_entitlement_summary() = runTest {
        val entitlementStore = InMemoryEntitlementStore()
        val repository = RuntimeEntitlementRepository(
            platformApi = FakePlatformApi(
                entitlement = TvEntitlementSummaryDto(
                    accountId = "acct_123",
                    displayId = "TV-PLUS",
                    planCode = "tv_plus",
                    paymentState = "PAID",
                    priorityClass = "priority_plus",
                    renewalState = "active",
                ),
            ),
            entitlementStore = entitlementStore,
            nowEpochMs = { 42_000L },
        )

        val resolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )
        val cached = entitlementStore.read()

        assertEquals(RuntimeEntitlementSnapshotSource.REMOTE, resolved.source)
        assertTrue(resolved.refreshed)
        assertEquals("paid", cached?.paymentState)
        assertEquals(42_000L, cached?.cachedAtEpochMs)
        assertEquals(72_000L, resolved.nextRefreshAtEpochMs)
    }

    @Test
    fun load_uses_cached_entitlement_when_remote_fetch_fails() = runTest {
        val cachedSummary = StoredEntitlementSummary(
            accountId = "acct_cached",
            displayId = "TV-CACHED",
            planCode = "tv_basic",
            paymentState = "grace_period",
            priorityClass = "standard",
            renewalState = "pending",
            cachedAtEpochMs = 12_000L,
        )
        val repository = RuntimeEntitlementRepository(
            platformApi = FakePlatformApi(throwOnEntitlement = true),
            entitlementStore = InMemoryEntitlementStore(cachedSummary),
            nowEpochMs = { 45_000L },
        )

        val resolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )

        assertEquals(RuntimeEntitlementSnapshotSource.CACHE, resolved.source)
        assertFalse(resolved.refreshed)
        assertEquals("tv_basic", resolved.entitlement?.planCode)
        assertEquals(75_000L, resolved.nextRefreshAtEpochMs)
    }

    @Test
    fun load_returns_empty_when_no_cache_and_remote_fetch_fails() = runTest {
        val repository = RuntimeEntitlementRepository(
            platformApi = FakePlatformApi(throwOnEntitlement = true),
            entitlementStore = InMemoryEntitlementStore(),
            nowEpochMs = { 20_000L },
        )

        val resolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )

        assertEquals(RuntimeEntitlementSnapshotSource.EMPTY, resolved.source)
        assertNull(resolved.entitlement)
        assertEquals(50_000L, resolved.nextRefreshAtEpochMs)
    }

    @Test
    fun load_returns_cached_summary_without_remote_call_when_refresh_window_not_due() = runTest {
        val cachedSummary = StoredEntitlementSummary(
            accountId = "acct_cached",
            displayId = "TV-CACHED",
            planCode = "tv_basic",
            paymentState = "paid",
            priorityClass = "standard",
            renewalState = "active",
            cachedAtEpochMs = 40_000L,
        )
        val platformApi = FakePlatformApi()
        val repository = RuntimeEntitlementRepository(
            platformApi = platformApi,
            entitlementStore = InMemoryEntitlementStore(cachedSummary),
            nowEpochMs = { 45_000L },
        )

        val resolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )

        assertEquals(RuntimeEntitlementSnapshotSource.CACHE, resolved.source)
        assertFalse(resolved.refreshed)
        assertEquals(0, platformApi.entitlementRequestCount)
        assertEquals(70_000L, resolved.nextRefreshAtEpochMs)
    }

    @Test
    fun load_throttles_failed_remote_refresh_until_next_window() = runTest {
        val cachedSummary = StoredEntitlementSummary(
            accountId = "acct_cached",
            displayId = "TV-CACHED",
            planCode = "tv_basic",
            paymentState = "paid",
            priorityClass = "standard",
            renewalState = "active",
            cachedAtEpochMs = 10_000L,
        )
        val platformApi = FakePlatformApi(throwOnEntitlement = true)
        var now = 45_000L
        val repository = RuntimeEntitlementRepository(
            platformApi = platformApi,
            entitlementStore = InMemoryEntitlementStore(cachedSummary),
            nowEpochMs = { now },
        )

        val firstResolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )
        now = 50_000L
        val secondResolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )

        assertEquals(RuntimeEntitlementSnapshotSource.CACHE, firstResolved.source)
        assertEquals(RuntimeEntitlementSnapshotSource.CACHE, secondResolved.source)
        assertEquals(1, platformApi.entitlementRequestCount)
        assertEquals(75_000L, firstResolved.nextRefreshAtEpochMs)
        assertEquals(75_000L, secondResolved.nextRefreshAtEpochMs)
    }

    private class FakePlatformApi(
        private val entitlement: TvEntitlementSummaryDto = TvEntitlementSummaryDto(),
        private val throwOnEntitlement: Boolean = false,
    ) : PlatformApi {

        var entitlementRequestCount = 0

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
            entitlementRequestCount += 1
            if (throwOnEntitlement) {
                error("entitlement unavailable")
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
