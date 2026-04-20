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
import com.openclaw.tv.core.network.dto.TvRuntimeAdCreativeDto
import com.openclaw.tv.core.network.dto.TvRuntimeAdSlotDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestAppDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryRuntimeManifestStore
import com.openclaw.tv.core.storage.StoredRuntimeAdCreative
import com.openclaw.tv.core.storage.StoredRuntimeAdSlot
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRuntimeManifestRepositoryTest {

    @Test
    fun repository_returns_manifest_driven_featured_apps_and_active_hero_ads() = runTest {
        val repository = HomeRuntimeManifestRepository(
            platformApi = FakePlatformApi(
                runtimeManifest = TvRuntimeManifestDto(
                    manifestVersion = "2026-04-20.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        TvRuntimeManifestAppDto(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "abc",
                            versionCode = 1L,
                            versionName = "1.0.0",
                            minClientVersion = "2026.04.20",
                            installMode = "prompt",
                            visibility = "featured",
                            preloadPolicy = "idle_only",
                            requiresEntitlement = false,
                        ),
                        TvRuntimeManifestAppDto(
                            appId = "hulu",
                            title = "Hulu",
                            packageName = "com.hulu.livingroomplus",
                            downloadUrl = "https://cdn.example.com/hulu.apk",
                            sha256 = "def",
                            versionCode = 2L,
                            versionName = "2.0.0",
                            minClientVersion = "2026.04.20",
                            installMode = "prompt",
                            visibility = "featured",
                            preloadPolicy = "idle_only",
                            requiresEntitlement = true,
                        ),
                        TvRuntimeManifestAppDto(
                            appId = "hidden",
                            title = "Hidden",
                            packageName = "com.hidden.app",
                            downloadUrl = "https://cdn.example.com/hidden.apk",
                            sha256 = "ghi",
                            versionCode = 3L,
                            versionName = "3.0.0",
                            minClientVersion = "2026.04.20",
                            installMode = "prompt",
                            visibility = "hidden",
                            preloadPolicy = "idle_only",
                            requiresEntitlement = false,
                        ),
                    ),
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
        assertEquals(listOf("YouTube", "Hulu"), resolved.featuredApps.map { it.title })
        assertEquals(listOf("hero-1"), resolved.heroAds.map { it.creativeId })
        assertTrue(resolved.featuredApps.any { it.appId == "hulu" && it.requiresEntitlement })
    }

    @Test
    fun repository_tracks_invalid_featured_app_entries() = runTest {
        val repository = HomeRuntimeManifestRepository(
            platformApi = FakePlatformApi(
                runtimeManifest = TvRuntimeManifestDto(
                    manifestVersion = "2026-04-20.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        TvRuntimeManifestAppDto(
                            appId = "broken-entry",
                            title = "",
                            packageName = "com.example.broken",
                            downloadUrl = "https://cdn.example.com/broken.apk",
                            sha256 = "abc",
                            versionCode = 1L,
                            versionName = "1.0.0",
                            minClientVersion = "2026.04.20",
                            installMode = "prompt",
                            visibility = "featured",
                            preloadPolicy = "idle_only",
                            requiresEntitlement = false,
                        ),
                    ),
                ),
            ),
        )

        val resolved = repository.load("session_token_1")

        assertTrue(resolved.featuredApps.isEmpty())
        assertEquals(listOf("broken-entry"), resolved.ignoredFeaturedAppIds)
    }

    @Test
    fun repository_uses_cached_manifest_when_remote_fetch_fails() = runTest {
        val cacheStore = InMemoryRuntimeManifestStore()
        cacheStore.save(
            StoredRuntimeManifest(
                manifestVersion = "2026-04-20.2",
                countryCode = "CN",
                regionCode = "SH",
                apps = listOf(
                    StoredRuntimeApp(
                        appId = "youtube",
                        title = "YouTube",
                        packageName = "com.google.android.youtube.tv",
                        downloadUrl = "https://cdn.example.com/youtube.apk",
                        sha256 = "abc",
                        versionCode = 1L,
                        versionName = "1.0.0",
                        minClientVersion = "2026.04.20",
                        installMode = "prompt",
                        visibility = "featured",
                        preloadPolicy = "idle_only",
                        requiresEntitlement = false,
                    ),
                ),
                adSlots = listOf(
                    StoredRuntimeAdSlot(
                        slotId = "home.hero",
                        enabled = true,
                        creatives = listOf(
                            StoredRuntimeAdCreative(
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
        assertEquals(listOf("YouTube"), resolved.featuredApps.map { it.title })
        assertEquals("cached-hero-1", resolved.heroAds.first().creativeId)
    }

    private class FakePlatformApi(
        private val runtimeManifest: TvRuntimeManifestDto = TvRuntimeManifestDto(),
        private val throwOnManifest: Boolean = false,
    ) : PlatformApi {

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
        }

        override suspend fun getTvHomeConfig(): TvHomeConfigDto {
            error("Not used in this test")
        }

        override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
            if (throwOnManifest) {
                error("manifest unavailable")
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
