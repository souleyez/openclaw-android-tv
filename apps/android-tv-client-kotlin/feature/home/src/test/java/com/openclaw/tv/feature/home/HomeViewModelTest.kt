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
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.InMemoryTvHomeConfigStore
import com.openclaw.tv.core.storage.InMemoryUpgradeStateStore
import com.openclaw.tv.core.storage.StoredSession
import com.openclaw.tv.core.storage.StoredTvHomeConfig
import com.openclaw.tv.core.storage.StoredUpgradeState
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun manifest_driven_featured_apps_render_even_if_not_in_local_catalog() = runTest {
        val viewModel = HomeViewModel(
            manifestRepository = FakeRuntimeManifestRepository(
                ResolvedRuntimeManifest(
                    manifestVersion = "2026-04-20.1",
                    countryCode = "US",
                    regionCode = "CA",
                    source = RuntimeManifestSource.REMOTE,
                    featuredApps = listOf(
                        RuntimeFeaturedApp(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            summary = "全球通用视频入口",
                            monogram = "YT",
                            accentColorHex = "#FF4E45",
                            installMode = "prompt",
                            requiresEntitlement = false,
                        ),
                        RuntimeFeaturedApp(
                            appId = "hulu",
                            title = "Hulu",
                            packageName = "com.hulu.livingroomplus",
                            summary = "Hulu 由 home 通过 runtime-manifest 分发",
                            monogram = "HU",
                            accentColorHex = "#4E89FF",
                            installMode = "prompt",
                            requiresEntitlement = true,
                        ),
                    ),
                    ignoredFeaturedAppIds = emptyList(),
                    heroAds = emptyList(),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeSurfaceMode.ONLINE, state.surfaceMode)
        assertTrue(state.featuredVisible)
        assertEquals(listOf("YouTube", "Hulu"), state.featuredApps.map { it.title })
        assertEquals("需授权", state.featuredApps.last().statusLabel)
    }

    @Test
    fun malformed_manifest_featured_apps_show_sync_notice() = runTest {
        val viewModel = HomeViewModel(
            manifestRepository = FakeRuntimeManifestRepository(
                ResolvedRuntimeManifest(
                    manifestVersion = "2026-04-20.1",
                    countryCode = "GB",
                    regionCode = "LON",
                    source = RuntimeManifestSource.REMOTE,
                    featuredApps = emptyList(),
                    ignoredFeaturedAppIds = listOf("broken-entry"),
                    heroAds = emptyList(),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.featuredVisible)
        assertEquals("该区域内容位待同步", state.noticeTitle)
    }

    @Test
    fun offline_snapshot_exposes_wifi_section() = runTest {
        val viewModel = HomeViewModel()

        viewModel.bindNetworkSnapshot(
            HomeNetworkSnapshot(
                isConnected = false,
                transport = "offline",
                currentSsid = null,
                visibleNetworks = listOf("OpenClaw-Guest", "LivingRoom-5G"),
                canReadWifiList = true,
                statusText = "当前未联网",
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(HomeSurfaceMode.OFFLINE, state.surfaceMode)
        assertTrue(state.wifiSectionVisible)
        assertFalse(state.featuredVisible)
        assertEquals(listOf("OpenClaw-Guest", "LivingRoom-5G"), state.wifiNetworks.map { it.ssid })
    }

    @Test
    fun cached_config_keeps_shell_online_and_exposes_cache_hint() = runTest {
        val viewModel = HomeViewModel(
            repository = TvHomeRepository(
                platformApi = FakePlatformApi(throwOnTvHome = true),
                cacheStore = InMemoryTvHomeConfigStore(
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
                        cachedAtEpochMs = 100L,
                    ),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.loadRemoteConfig()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.featuredVisible)
        assertFalse(state.noticeVisible)
        assertTrue(state.heroHint.contains("缓存配置"))
    }

    @Test
    fun ready_runtime_with_upgrade_available_exposes_warning_state() = runTest {
        val viewModel = HomeViewModel()

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(
            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.READY,
                upgradeStatus = RuntimeUpgradeStatus(
                    state = RuntimeUpgradeState.AVAILABLE,
                    currentVersion = "0.1.0",
                    channel = "stable",
                    targetVersion = "0.2.0",
                    latestVersion = "0.2.0",
                    minSupportedVersion = "0.1.0",
                ),
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(HomeStatusTone.WARNING, state.statusTone)
        assertEquals("发现新版本 0.2.0", state.noticeTitle)
        assertEquals("在线同步中", state.modeLabel)
    }

    @Test
    fun required_upgrade_exposes_critical_runtime_state() = runTest {
        val viewModel = HomeViewModel()

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(
            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.READY,
                upgradeStatus = RuntimeUpgradeStatus(
                    state = RuntimeUpgradeState.REQUIRED,
                    currentVersion = "0.1.0",
                    channel = "stable",
                    targetVersion = "0.3.0",
                    latestVersion = "0.3.0",
                    minSupportedVersion = "0.2.0",
                    forceUpgrade = true,
                ),
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(HomeStatusTone.CRITICAL, state.statusTone)
        assertEquals("当前版本不满足策略要求", state.noticeTitle)
        assertEquals("在线受限", state.modeLabel)
    }

    @Test
    fun consumed_upgrade_success_notice_takes_priority_over_non_critical_runtime_notice() = runTest {
        val upgradeStateStore = InMemoryUpgradeStateStore(
            initial = StoredUpgradeState(
                lastSeenVersion = "0.1.0",
                pendingSuccessVersion = "0.2.0",
            ),
        )
        val viewModel = HomeViewModel(
            upgradeStateStore = upgradeStateStore,
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(
            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.READY,
                upgradeStatus = RuntimeUpgradeStatus(
                    state = RuntimeUpgradeState.AVAILABLE,
                    currentVersion = "0.2.0",
                    channel = "stable",
                    targetVersion = "0.3.0",
                    latestVersion = "0.3.0",
                    minSupportedVersion = "0.2.0",
                ),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("已升级到 0.2.0", state.noticeTitle)
        assertTrue(state.noticeBody.contains("客户端已经完成版本切换"))
        assertTrue(state.noticeBody.contains("当前版本 0.2.0 仍可继续运行"))
    }

    @Test
    fun online_runtime_exposes_multi_image_home_hero_ads_from_manifest() = runTest {
        val viewModel = HomeViewModel(
            manifestRepository = FakeRuntimeManifestRepository(
                ResolvedRuntimeManifest(
                    manifestVersion = "2026-04-20.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    source = RuntimeManifestSource.REMOTE,
                    featuredApps = emptyList(),
                    ignoredFeaturedAppIds = emptyList(),
                    heroAds = listOf(
                        HeroAdItem(
                            creativeId = "hero-1",
                            imageUrl = "https://cdn.example.com/ads/hero-1.png",
                            altText = "首页广告 1",
                            clickActionType = "deeplink",
                            clickActionValue = "openclaw://promo/1",
                        ),
                        HeroAdItem(
                            creativeId = "hero-2",
                            imageUrl = "https://cdn.example.com/ads/hero-2.png",
                            altText = "首页广告 2",
                            clickActionType = "none",
                            clickActionValue = null,
                        ),
                    ),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.heroAds.size)
        assertEquals(listOf("hero-1", "hero-2"), state.heroAds.map { it.creativeId })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakePlatformApi(
    private val tvHomeConfig: TvHomeConfigDto = TvHomeConfigDto(),
    private val throwOnTvHome: Boolean = false,
) : PlatformApi {

    override suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
        error("Not used in this test")
    }

    override suspend fun getTvHomeConfig(): TvHomeConfigDto {
        if (throwOnTvHome) {
            error("network down")
        }
        return tvHomeConfig
    }

    override suspend fun getRuntimeManifest(sessionToken: String): TvRuntimeManifestDto {
        return TvRuntimeManifestDto(
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
                            altText = "首页广告 1",
                            clickActionType = "none",
                        ),
                    ),
                ),
            ),
        )
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

private class FakeRuntimeManifestRepository(
    private val resolved: ResolvedRuntimeManifest,
) : HomeRuntimeManifestRepository(
    platformApi = FakePlatformApi(),
) {
    override suspend fun load(sessionToken: String): ResolvedRuntimeManifest {
        return resolved
    }
}

private fun connectedNetworkSnapshot(): HomeNetworkSnapshot {
    return HomeNetworkSnapshot(
        isConnected = true,
        transport = "wifi",
        currentSsid = "OpenClaw-WiFi",
        visibleNetworks = listOf("OpenClaw-WiFi"),
        canReadWifiList = true,
        statusText = "当前已连接 Wi-Fi：OpenClaw-WiFi",
    )
}

private fun readyState(): BootstrapRuntimeState {
    return BootstrapRuntimeState(
        phase = BootstrapRuntimePhase.READY,
        session = StoredSession(
            projectKey = "openclaw-android-tv",
            sessionToken = "session_token_1",
            expiresAt = "2026-04-20T12:00:00.000Z",
            userId = "user_1",
            deviceId = "device_1",
            principalLabel = "OpenClaw TV",
        ),
    )
}
