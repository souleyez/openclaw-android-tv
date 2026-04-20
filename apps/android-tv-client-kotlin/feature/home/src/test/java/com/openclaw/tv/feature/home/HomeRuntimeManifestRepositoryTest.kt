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
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.network.dto.TvRuntimeAdCreativeDto
import com.openclaw.tv.core.network.dto.TvRuntimeAdSlotDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryRuntimeManifestStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRuntimeManifestRepositoryTest {

    @Test
    fun repository_returns_only_active_image_creatives_for_home_hero_slot() = runTest {
        val repository = HomeRuntimeManifestRepository(
            platformApi = FakePlatformApi(
                runtimeManifest = TvRuntimeManifestDto(
                    manifestVersion = "2026-04-20.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    adSlots = listOf(
                        TvRuntimeAdSlotDto(
                            slotId = "home.hero",
                            enabled = true,
                            creatives = listOf(
                                TvRuntimeAdCreativeDto(
                                    creativeId = "hero-1",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/ads/hero-1.png",
                                    altText = "首屏广告 1",
                                    clickActionType = "deeplink",
                                    clickActionValue = "openclaw://promo/hero-1",
                                    startsAt = "2026-04-20T00:00:00.000Z",
                                    endsAt = "2026-05-01T00:00:00.000Z",
                                ),
                                TvRuntimeAdCreativeDto(
                                    creativeId = "hero-video",
                                    mediaType = "video",
                                    assetUrl = "https://cdn.example.com/ads/hero.mp4",
                                    altText = "视频广告",
                                    clickActionType = "none",
                                ),
                                TvRuntimeAdCreativeDto(
                                    creativeId = "hero-future",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/ads/future.png",
                                    altText = "未开始广告",
                                    clickActionType = "none",
                                    startsAt = "2026-05-10T00:00:00.000Z",
                                ),
                            ),
                        ),
                        TvRuntimeAdSlotDto(
                            slotId = "home.unknown",
                            enabled = true,
                            creatives = listOf(
                                TvRuntimeAdCreativeDto(
                                    creativeId = "ignored",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/ads/ignored.png",
                                    altText = "忽略广告",
                                    clickActionType = "none",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            cacheStore = InMemoryRuntimeManifestStore(),
            nowEpochMs = { 1_776_686_400_000L },
        )

        val resolved = repository.load("session_token_1")

        assertEquals(RuntimeManifestSource.REMOTE, resolved.source)
        assertEquals("2026-04-20.1", resolved.manifestVersion)
        assertEquals(listOf("hero-1"), resolved.heroAds.map { it.creativeId })
    }

    @Test
    fun repository_uses_cached_manifest_when_remote_fetch_fails() = runTest {
        val cacheStore = InMemoryRuntimeManifestStore()
        cacheStore.save(
            com.openclaw.tv.core.storage.StoredRuntimeManifest(
                manifestVersion = "2026-04-20.2",
                countryCode = "CN",
                regionCode = "SH",
                adSlots = listOf(
                    com.openclaw.tv.core.storage.StoredRuntimeAdSlot(
                        slotId = "home.hero",
                        enabled = true,
                        creatives = listOf(
                            com.openclaw.tv.core.storage.StoredRuntimeAdCreative(
                                creativeId = "cached-hero-1",
                                mediaType = "image",
                                assetUrl = "https://cdn.example.com/ads/cached-hero-1.png",
                                altText = "缓存广告",
                                clickActionType = "none",
                            ),
                        ),
                    ),
                ),
                cachedAtEpochMs = 100L,
            ),
        )
        val repository = HomeRuntimeManifestRepository(
            platformApi = FakePlatformApi(throwOnManifest = true),
            cacheStore = cacheStore,
            nowEpochMs = { 1_776_686_400_000L },
        )

        val resolved = repository.load("session_token_1")

        assertEquals(RuntimeManifestSource.CACHE, resolved.source)
        assertTrue(resolved.heroAds.isNotEmpty())
        assertEquals("cached-hero-1", resolved.heroAds.first().creativeId)
    }

    private class FakePlatformApi(
        private val runtimeManifest: TvRuntimeManifestDto = TvRuntimeManifestDto(),
        private val throwOnManifest: Boolean = false,
    ) : PlatformApi {

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
        }

        override suspend fun getTvHomeConfig(countryCode: String, regionCode: String?): TvHomeConfigDto {
            error("Not used in this test")
        }

        override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
            if (throwOnManifest) {
                error("manifest unavailable")
            }
            return runtimeManifest
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

        override suspend fun getLeaseStatus(sessionToken: String, request: LeaseStatusRequestDto): LeaseStatusEnvelope {
            error("Not used in this test")
        }

        override suspend fun renewLease(sessionToken: String, request: RenewLeaseRequestDto): LeaseStatusEnvelope {
            error("Not used in this test")
        }

        override suspend fun releaseLease(sessionToken: String, request: ReleaseLeaseRequestDto): ReleaseLeaseEnvelope {
            error("Not used in this test")
        }
    }
}
