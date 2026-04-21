package com.openclaw.tv.feature.appdelivery

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
import com.openclaw.tv.core.network.dto.TvRuntimeAdCreativeDto
import com.openclaw.tv.core.network.dto.TvRuntimeAdSlotDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestAppDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryRuntimeManifestStore
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeManifestRepositoryTest {

    @Test
    fun load_uses_cache_until_manifest_poll_window_expires() = runTest {
        val cachedManifest = StoredRuntimeManifest(
            manifestVersion = "cached-v1",
            countryCode = "CN",
            regionCode = "SH",
            apps = emptyList(),
            adSlots = emptyList(),
            cachedAtEpochMs = 10_000L,
        )
        val manifestStore = InMemoryRuntimeManifestStore(cachedManifest)
        val platformApi = FakePlatformApi(
            runtimeManifest = TvRuntimeManifestDto(
                manifestVersion = "remote-v2",
            ),
        )
        val repository = RuntimeManifestRepository(
            platformApi = platformApi,
            manifestStore = manifestStore,
            nowEpochMs = { 20_000L },
        )

        val resolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )

        assertEquals(RuntimeManifestSnapshotSource.CACHE, resolved.source)
        assertFalse(resolved.refreshed)
        assertEquals("cached-v1", resolved.manifest?.manifestVersion)
        assertEquals(40_000L, resolved.nextRefreshAtEpochMs)
        assertEquals(0, platformApi.runtimeManifestRequestCount)
    }

    @Test
    fun load_accepts_empty_ad_slots_and_saves_unknown_slot_ids() = runTest {
        val manifestStore = InMemoryRuntimeManifestStore()
        val repository = RuntimeManifestRepository(
            platformApi = FakePlatformApi(
                runtimeManifest = TvRuntimeManifestDto(
                    manifestVersion = "remote-v1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        TvRuntimeManifestAppDto(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "sha256-youtube",
                            versionCode = 1001L,
                            versionName = "1.0.1",
                            visibility = "featured",
                            preloadPolicy = "idle_only",
                        ),
                    ),
                    adSlots = listOf(
                        TvRuntimeAdSlotDto(
                            slotId = "home.ai.gallery",
                            enabled = true,
                            creatives = listOf(
                                TvRuntimeAdCreativeDto(
                                    creativeId = "creative-1",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/banner-1.png",
                                    altText = "Banner",
                                    clickActionType = "deeplink",
                                    clickActionValue = "openclaw://campaign/1",
                                ),
                            ),
                        ),
                        TvRuntimeAdSlotDto(
                            slotId = "home.hero",
                            enabled = true,
                            creatives = emptyList(),
                        ),
                    ),
                ),
            ),
            manifestStore = manifestStore,
            nowEpochMs = { 60_000L },
        )

        val resolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )
        val cached = manifestStore.read()

        assertEquals(RuntimeManifestSnapshotSource.REMOTE, resolved.source)
        assertEquals(2, cached?.adSlots?.size)
        assertTrue(cached?.adSlots?.any { it.slotId == "home.ai.gallery" } == true)
        assertTrue(cached?.adSlots?.first { it.slotId == "home.hero" }?.creatives?.isEmpty() == true)
    }

    @Test
    fun load_throttles_failed_remote_refresh_until_next_window() = runTest {
        val cachedManifest = StoredRuntimeManifest(
            manifestVersion = "cached-v1",
            countryCode = "CN",
            regionCode = "SH",
            apps = emptyList(),
            adSlots = emptyList(),
            cachedAtEpochMs = 10_000L,
        )
        val platformApi = FakePlatformApi(throwOnRuntimeManifest = true)
        var now = 45_000L
        val repository = RuntimeManifestRepository(
            platformApi = platformApi,
            manifestStore = InMemoryRuntimeManifestStore(cachedManifest),
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

        assertEquals(RuntimeManifestSnapshotSource.CACHE, firstResolved.source)
        assertEquals(RuntimeManifestSnapshotSource.CACHE, secondResolved.source)
        assertEquals(1, platformApi.runtimeManifestRequestCount)
        assertEquals(75_000L, firstResolved.nextRefreshAtEpochMs)
        assertEquals(75_000L, secondResolved.nextRefreshAtEpochMs)
    }

    @Test
    fun load_refetches_manifest_when_session_token_changes_inside_cache_window() = runTest {
        val cachedManifest = StoredRuntimeManifest(
            manifestVersion = "cached-v1",
            countryCode = "CN",
            regionCode = "SH",
            apps = emptyList(),
            adSlots = emptyList(),
            cachedAtEpochMs = 10_000L,
        )
        val platformApi = FakePlatformApi(
            runtimeManifest = TvRuntimeManifestDto(
                manifestVersion = "remote-v2",
            ),
        )
        var now = 20_000L
        val repository = RuntimeManifestRepository(
            platformApi = platformApi,
            manifestStore = InMemoryRuntimeManifestStore(cachedManifest),
            nowEpochMs = { now },
        )

        val firstResolved = repository.load(
            sessionToken = "session_token_1",
            pollAfterSeconds = 30,
        )
        now = 25_000L
        val secondResolved = repository.load(
            sessionToken = "session_token_2",
            pollAfterSeconds = 30,
        )

        assertEquals(RuntimeManifestSnapshotSource.CACHE, firstResolved.source)
        assertEquals(RuntimeManifestSnapshotSource.REMOTE, secondResolved.source)
        assertEquals("remote-v2", secondResolved.manifest?.manifestVersion)
        assertEquals(1, platformApi.runtimeManifestRequestCount)
    }

    private class FakePlatformApi(
        private val runtimeManifest: TvRuntimeManifestDto = TvRuntimeManifestDto(),
        private val throwOnRuntimeManifest: Boolean = false,
    ) : PlatformApi {

        var runtimeManifestRequestCount = 0

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
        }

        override suspend fun getTvHomeConfig(): TvHomeConfigDto {
            return TvHomeConfigDto()
        }

        override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
            runtimeManifestRequestCount += 1
            if (throwOnRuntimeManifest) {
                error("runtime manifest unavailable")
            }
            return runtimeManifest
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
