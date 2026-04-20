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
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryTvHomeConfigStore
import com.openclaw.tv.core.storage.StoredTvHomeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TvHomeRepositoryTest {

    @Test
    fun remote_config_preserves_server_order_and_skips_unknown_apps() = runTest {
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(
                TvHomeConfigDto(
                    id = "tv_home_us",
                    countryCode = "US",
                    regionCode = "GLOBAL",
                    backgroundImageUrl = "https://cdn.example.com/tv-home/us.jpg",
                    featuredAppIds = listOf("plex", "youtube", "unknown_app"),
                    status = "active",
                    version = 3,
                ),
            ),
        )

        val resolved = repository.load(Locale.US)

        assertEquals("US", resolved.countryCode)
        assertEquals("https://cdn.example.com/tv-home/us.jpg", resolved.backgroundImageUrl)
        assertEquals(listOf("Plex", "YouTube"), resolved.featuredApps.map { it.title })
        assertEquals(listOf("unknown_app"), resolved.unresolvedFeaturedAppIds)
    }

    @Test
    fun remote_config_with_only_unknown_apps_keeps_empty_catalog_state() = runTest {
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(
                TvHomeConfigDto(
                    id = "tv_home_gb",
                    countryCode = "GB",
                    regionCode = "LON",
                    featuredAppIds = listOf("bbc_iplayer", "itvx"),
                    status = "active",
                    version = 5,
                ),
            ),
        )

        val resolved = repository.load(Locale.UK)

        assertEquals("GB", resolved.countryCode)
        assertEquals(emptyList<String>(), resolved.featuredApps.map { it.title })
        assertEquals(listOf("bbc_iplayer", "itvx"), resolved.unresolvedFeaturedAppIds)
    }

    @Test
    fun remote_config_success_persists_cache_snapshot() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore()
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(
                TvHomeConfigDto(
                    countryCode = "US",
                    regionCode = "GLOBAL",
                    backgroundImageUrl = "https://cdn.example.com/tv-home/us.jpg",
                    featuredAppIds = listOf("youtube", "netflix"),
                    status = "active",
                    version = 7,
                ),
            ),
            cacheStore = cacheStore,
            nowEpochMs = { 1234L },
        )

        repository.load(Locale.US)

        assertEquals(
            StoredTvHomeConfig(
                countryCode = "US",
                regionCode = "GLOBAL",
                backgroundImageUrl = "https://cdn.example.com/tv-home/us.jpg",
                featuredAppIds = listOf("youtube", "netflix"),
                cachedAtEpochMs = 1234L,
            ),
            cacheStore.read(),
        )
    }

    @Test
    fun repository_uses_cached_config_when_remote_fetch_fails() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore(
            StoredTvHomeConfig(
                countryCode = "US",
                regionCode = null,
                backgroundImageUrl = "https://cdn.example.com/tv-home/cached.jpg",
                featuredAppIds = listOf("plex", "youtube"),
                cachedAtEpochMs = 999L,
            ),
        )
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(throwOnTvHome = true),
            cacheStore = cacheStore,
        )

        val resolved = repository.load(Locale.US)

        assertEquals(ConfigSource.CACHE, resolved.source)
        assertEquals("https://cdn.example.com/tv-home/cached.jpg", resolved.backgroundImageUrl)
        assertEquals(listOf("Plex", "YouTube"), resolved.featuredApps.map { it.title })
    }

    @Test
    fun repository_uses_cached_config_when_remote_request_times_out() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore(
            StoredTvHomeConfig(
                countryCode = "US",
                regionCode = null,
                backgroundImageUrl = null,
                featuredAppIds = listOf("youtube"),
                cachedAtEpochMs = 999L,
            ),
        )
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(delayMillis = 100),
            cacheStore = cacheStore,
            requestTimeoutMillis = 10,
        )

        val resolved = repository.load(Locale.US)

        assertEquals(ConfigSource.CACHE, resolved.source)
        assertEquals(listOf("YouTube"), resolved.featuredApps.map { it.title })
    }

    @Test
    fun repository_falls_back_to_default_catalog_when_remote_and_cache_are_unavailable() = runTest {
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(throwOnTvHome = true),
            cacheStore = InMemoryTvHomeConfigStore(),
        )

        val resolved = repository.load(Locale.US)

        assertEquals(ConfigSource.FALLBACK, resolved.source)
        assertNull(resolved.backgroundImageUrl)
        assertEquals(
            listOf("腾讯视频", "爱奇艺", "优酷", "哔哩哔哩", "芒果TV"),
            resolved.featuredApps.map { it.title },
        )
        assertEquals(emptyList<String>(), resolved.unresolvedFeaturedAppIds)
    }

    private class FakePlatformApi(
        private val tvHomeConfig: TvHomeConfigDto = TvHomeConfigDto(),
        private val throwOnTvHome: Boolean = false,
        private val delayMillis: Long = 0L,
    ) : PlatformApi {

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
        }

        override suspend fun getTvHomeConfig(countryCode: String, regionCode: String?): TvHomeConfigDto {
            if (throwOnTvHome) {
                error("network down")
            }
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            return tvHomeConfig
        }

        override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
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
