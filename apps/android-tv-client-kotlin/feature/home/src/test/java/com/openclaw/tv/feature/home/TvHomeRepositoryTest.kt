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
import com.openclaw.tv.core.network.dto.TvHomeAppDto
import com.openclaw.tv.core.network.dto.TvHomeBrandingDto
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.network.dto.TvHomeCustomerDto
import com.openclaw.tv.core.network.dto.TvHomeDistributionDto
import com.openclaw.tv.core.network.dto.TvHomeThemeDto
import com.openclaw.tv.core.network.dto.TvHotelServiceDto
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryTvHomeConfigStore
import com.openclaw.tv.core.storage.StoredTvHomeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TvHomeRepositoryTest {

    @Test
    fun remote_config_maps_runtime_paths_and_toggles() = runTest {
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(
                TvHomeConfigDto(
                    projectKey = "openclaw-android-tv",
                    projectLabel = "百万龙虾 TV",
                    runtimeManifestPath = "/api/me/runtime-manifest",
                    entitlementPath = "/api/me/entitlement",
                    resourceSessionBasePath = "/api/client/resource-session",
                    manifestPollAfterSeconds = 900,
                    resourceSessionPollAfterSeconds = 15,
                    backgroundDownloadEnabled = true,
                    idleDownloadOnly = false,
                ),
            ),
        )

        val resolved = repository.load()

        assertEquals(ConfigSource.REMOTE, resolved.source)
        assertEquals("openclaw-android-tv", resolved.projectKey)
        assertEquals("百万龙虾 TV", resolved.projectLabel)
        assertEquals("/api/me/runtime-manifest", resolved.runtimeManifestPath)
        assertEquals(15, resolved.resourceSessionPollAfterSeconds)
        assertTrue(resolved.backgroundDownloadEnabled)
    }

    @Test
    fun remote_config_maps_customer_branding_theme_apps_and_hotel_services() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore()
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(
                TvHomeConfigDto(
                    customer = TvHomeCustomerDto(
                        id = "cust_1",
                        slug = "hanting-sh",
                        displayName = "汉庭上海客户",
                        hotelName = "汉庭上海虹桥酒店",
                    ),
                    distribution = TvHomeDistributionDto(
                        distributionKey = "hanting-sh-001",
                        packageName = "com.openclaw.tv.hanting",
                        releaseChannel = "stable-hotel",
                    ),
                    branding = TvHomeBrandingDto(
                        logoUrl = "https://cdn.example.com/logo.png",
                        intro = "欢迎入住，早餐 7 点开始。",
                        versionLabel = "酒店版 1.0",
                    ),
                    theme = TvHomeThemeDto(
                        defaultMode = "day",
                        switcherEnabled = true,
                        dayPalette = mapOf("background" to "#EEF3F6"),
                        nightPalette = mapOf("background" to "#091019"),
                    ),
                    homeApps = listOf(
                        TvHomeAppDto(appId = "mango", title = "芒果TV", packageName = "com.starcor.mango", sortOrder = 2),
                        TvHomeAppDto(appId = "aurora", title = "云视听极光", packageName = "com.ktcp.tvvideo", sortOrder = 1),
                    ),
                    hotelServices = listOf(
                        TvHotelServiceDto(
                            id = "breakfast",
                            title = "早餐服务",
                            summary = "7:00-10:00 二楼餐厅",
                            imageUrl = "https://cdn.example.com/breakfast.png",
                            actionType = "none",
                            actionValue = "",
                            sortOrder = 1,
                        ),
                    ),
                ),
            ),
            cacheStore = cacheStore,
            nowEpochMs = { 5678L },
        )

        val resolved = repository.load()

        assertEquals("汉庭上海虹桥酒店", resolved.customer?.hotelName)
        assertEquals("hanting-sh-001", resolved.distribution?.distributionKey)
        assertEquals("欢迎入住，早餐 7 点开始。", resolved.branding?.intro)
        assertEquals("day", resolved.theme?.defaultMode)
        assertEquals(true, resolved.theme?.switcherEnabled)
        assertEquals(listOf("aurora", "mango"), resolved.homeApps.map { it.appId })
        assertEquals("早餐服务", resolved.hotelServices.single().title)
        assertEquals("汉庭上海虹桥酒店", cacheStore.read()?.customer?.hotelName)
        assertEquals("早餐服务", cacheStore.read()?.hotelServices?.single()?.title)
    }

    @Test
    fun remote_config_success_persists_cache_snapshot() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore()
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(
                TvHomeConfigDto(
                    projectKey = "openclaw-android-tv",
                    projectLabel = "百万龙虾 TV",
                    runtimeManifestPath = "/api/me/runtime-manifest",
                    entitlementPath = "/api/me/entitlement",
                    resourceSessionBasePath = "/api/client/resource-session",
                    manifestPollAfterSeconds = 1200,
                    resourceSessionPollAfterSeconds = 20,
                    backgroundDownloadEnabled = true,
                    idleDownloadOnly = true,
                ),
            ),
            cacheStore = cacheStore,
            nowEpochMs = { 1234L },
        )

        repository.load()

        assertEquals(
            StoredTvHomeConfig(
                projectKey = "openclaw-android-tv",
                projectLabel = "百万龙虾 TV",
                runtimeManifestPath = "/api/me/runtime-manifest",
                entitlementPath = "/api/me/entitlement",
                resourceSessionBasePath = "/api/client/resource-session",
                manifestPollAfterSeconds = 1200,
                resourceSessionPollAfterSeconds = 20,
                backgroundDownloadEnabled = true,
                idleDownloadOnly = true,
                cachedAtEpochMs = 1234L,
            ),
            cacheStore.read(),
        )
    }

    @Test
    fun repository_uses_cached_config_when_remote_fetch_fails() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore(
            StoredTvHomeConfig(
                projectKey = "openclaw-android-tv",
                projectLabel = "缓存配置",
                runtimeManifestPath = "/api/me/runtime-manifest",
                entitlementPath = "/api/me/entitlement",
                resourceSessionBasePath = "/api/client/resource-session",
                manifestPollAfterSeconds = 900,
                resourceSessionPollAfterSeconds = 15,
                backgroundDownloadEnabled = false,
                idleDownloadOnly = false,
                cachedAtEpochMs = 999L,
            ),
        )
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(throwOnTvHome = true),
            cacheStore = cacheStore,
        )

        val resolved = repository.load()

        assertEquals(ConfigSource.CACHE, resolved.source)
        assertEquals("缓存配置", resolved.projectLabel)
        assertEquals("/api/client/resource-session", resolved.resourceSessionBasePath)
        assertEquals(false, resolved.backgroundDownloadEnabled)
    }

    @Test
    fun repository_uses_cached_config_when_remote_request_times_out() = runTest {
        val cacheStore = InMemoryTvHomeConfigStore(
            StoredTvHomeConfig(
                projectKey = "openclaw-android-tv",
                projectLabel = "缓存配置",
                runtimeManifestPath = "/api/me/runtime-manifest",
                entitlementPath = "/api/me/entitlement",
                resourceSessionBasePath = "/api/client/resource-session",
                manifestPollAfterSeconds = 900,
                resourceSessionPollAfterSeconds = 15,
                backgroundDownloadEnabled = true,
                idleDownloadOnly = true,
                cachedAtEpochMs = 999L,
            ),
        )
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(delayMillis = 100),
            cacheStore = cacheStore,
            requestTimeoutMillis = 10,
        )

        val resolved = repository.load()

        assertEquals(ConfigSource.CACHE, resolved.source)
        assertEquals("缓存配置", resolved.projectLabel)
        assertEquals(15, resolved.resourceSessionPollAfterSeconds)
    }

    @Test
    fun repository_rethrows_external_cancellation_instead_of_falling_back() = runTest {
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(throwCancellationOnTvHome = true),
            cacheStore = InMemoryTvHomeConfigStore(),
        )

        try {
            repository.load()
            fail("Expected cancellation to propagate")
        } catch (error: CancellationException) {
            assertEquals("tv home cancelled", error.message)
        }
    }

    @Test
    fun repository_falls_back_to_default_runtime_contract_when_remote_and_cache_are_unavailable() = runTest {
        val repository = TvHomeRepository(
            platformApi = FakePlatformApi(throwOnTvHome = true),
            cacheStore = InMemoryTvHomeConfigStore(),
        )

        val resolved = repository.load()

        assertEquals(ConfigSource.FALLBACK, resolved.source)
        assertEquals("openclaw-android-tv", resolved.projectKey)
        assertEquals("/api/me/runtime-manifest", resolved.runtimeManifestPath)
        assertEquals("/api/client/resource-session", resolved.resourceSessionBasePath)
        assertTrue(resolved.idleDownloadOnly)
    }

    private class FakePlatformApi(
        private val tvHomeConfig: TvHomeConfigDto = TvHomeConfigDto(),
        private val throwOnTvHome: Boolean = false,
        private val throwCancellationOnTvHome: Boolean = false,
        private val delayMillis: Long = 0L,
    ) : PlatformApi {

        override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
            error("Not used in this test")
        }

        override suspend fun getTvHomeConfig(): TvHomeConfigDto {
            if (throwOnTvHome) {
                error("network down")
            }
            if (throwCancellationOnTvHome) {
                throw CancellationException("tv home cancelled")
            }
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            return tvHomeConfig
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
