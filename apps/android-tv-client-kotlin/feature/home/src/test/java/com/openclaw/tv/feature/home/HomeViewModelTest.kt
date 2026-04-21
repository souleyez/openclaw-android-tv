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
import com.openclaw.tv.core.storage.InMemoryAppDownloadStore
import com.openclaw.tv.core.storage.InMemoryEntitlementStore
import com.openclaw.tv.core.storage.InMemoryResourceSessionStore
import com.openclaw.tv.core.storage.InMemoryRuntimeManifestStore
import com.openclaw.tv.core.storage.InMemoryTvHomeConfigStore
import com.openclaw.tv.core.storage.InMemoryUpgradeStateStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredEntitlementSummary
import com.openclaw.tv.core.storage.StoredResourceSession
import com.openclaw.tv.core.storage.StoredRuntimeAdCreative
import com.openclaw.tv.core.storage.StoredRuntimeAdSlot
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
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
    fun ready_to_install_download_updates_featured_app_action_and_notice() = runTest {
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        StoredRuntimeApp(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "sha-youtube",
                            versionCode = 1001L,
                            versionName = "1.0.1",
                            minClientVersion = "0.1.0",
                            installMode = "auto",
                            visibility = "featured",
                            preloadPolicy = "auto",
                            requiresEntitlement = false,
                        ),
                    ),
                    adSlots = emptyList(),
                    cachedAtEpochMs = 1_000L,
                ),
            ),
            appDownloadStore = InMemoryAppDownloadStore(
                mapOf(
                    "youtube" to StoredAppDownloadState(
                        appId = "youtube",
                        title = "YouTube",
                        packageName = "com.google.android.youtube.tv",
                        versionCode = 1001L,
                        versionName = "1.0.1",
                        downloadUrl = "https://cdn.example.com/youtube.apk",
                        sha256 = "sha-youtube",
                        status = "ready_to_install",
                        downloadId = 11L,
                        localFilePath = "/downloads/youtube.apk",
                        errorMessage = null,
                        updatedAtEpochMs = 2_000L,
                    ),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.featuredVisible)
        assertEquals("YouTube 已下载完成", state.noticeTitle)
        assertEquals(FeaturedAppInstallState.READY_TO_INSTALL, state.featuredApps.first().installState)
        assertEquals("按确定安装", state.featuredApps.first().actionLabel)
    }

    @Test
    fun mark_featured_app_download_failed_updates_store_driven_install_state() = runTest {
        val appDownloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to StoredAppDownloadState(
                    appId = "youtube",
                    title = "YouTube",
                    packageName = "com.google.android.youtube.tv",
                    versionCode = 1001L,
                    versionName = "1.0.1",
                    downloadUrl = "https://cdn.example.com/youtube.apk",
                    sha256 = "sha-youtube",
                    status = "ready_to_install",
                    downloadId = 11L,
                    localFilePath = "/downloads/youtube.apk",
                    errorMessage = null,
                    updatedAtEpochMs = 2_000L,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        StoredRuntimeApp(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "sha-youtube",
                            versionCode = 1001L,
                            versionName = "1.0.1",
                            minClientVersion = "0.1.0",
                            installMode = "auto",
                            visibility = "featured",
                            preloadPolicy = "auto",
                            requiresEntitlement = false,
                        ),
                    ),
                    adSlots = emptyList(),
                    cachedAtEpochMs = 1_000L,
                ),
            ),
            appDownloadStore = appDownloadStore,
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()
        viewModel.markFeaturedAppDownloadFailed(
            appId = "youtube",
            message = "安装包不存在，建议重新下载。",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FeaturedAppInstallState.FAILED, state.featuredApps.first().installState)
        assertEquals("下载失败", state.featuredApps.first().statusLabel)
        assertEquals("按确定重试下载", state.featuredApps.first().actionLabel)
        assertEquals("安装包不存在，建议重新下载。", state.featuredApps.first().downloadErrorMessage)
        assertEquals("failed", appDownloadStore.read("youtube")?.status)
    }

    @Test
    fun downloading_progress_updates_featured_app_labels() = runTest {
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        StoredRuntimeApp(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "sha-youtube",
                            versionCode = 1001L,
                            versionName = "1.0.1",
                            minClientVersion = "0.1.0",
                            installMode = "auto",
                            visibility = "featured",
                            preloadPolicy = "auto",
                            requiresEntitlement = false,
                        ),
                    ),
                    adSlots = emptyList(),
                    cachedAtEpochMs = 1_000L,
                ),
            ),
            appDownloadStore = InMemoryAppDownloadStore(
                mapOf(
                    "youtube" to StoredAppDownloadState(
                        appId = "youtube",
                        title = "YouTube",
                        packageName = "com.google.android.youtube.tv",
                        versionCode = 1001L,
                        versionName = "1.0.1",
                        downloadUrl = "https://cdn.example.com/youtube.apk",
                        sha256 = "sha-youtube",
                        status = "downloading",
                        downloadId = 11L,
                        localFilePath = "/downloads/youtube.apk",
                        downloadedBytes = 512L,
                        totalBytes = 1_024L,
                        downloadDetailMessage = null,
                        errorMessage = null,
                        updatedAtEpochMs = 2_000L,
                    ),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()

        val item = viewModel.uiState.value.featuredApps.first()
        assertEquals(FeaturedAppInstallState.DOWNLOADING, item.installState)
        assertEquals("下载中 50%", item.statusLabel)
        assertEquals("已下载 50%", item.actionLabel)
    }

    @Test
    fun paused_download_updates_featured_app_labels() = runTest {
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        StoredRuntimeApp(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "sha-youtube",
                            versionCode = 1001L,
                            versionName = "1.0.1",
                            minClientVersion = "0.1.0",
                            installMode = "auto",
                            visibility = "featured",
                            preloadPolicy = "auto",
                            requiresEntitlement = false,
                        ),
                    ),
                    adSlots = emptyList(),
                    cachedAtEpochMs = 1_000L,
                ),
            ),
            appDownloadStore = InMemoryAppDownloadStore(
                mapOf(
                    "youtube" to StoredAppDownloadState(
                        appId = "youtube",
                        title = "YouTube",
                        packageName = "com.google.android.youtube.tv",
                        versionCode = 1001L,
                        versionName = "1.0.1",
                        downloadUrl = "https://cdn.example.com/youtube.apk",
                        sha256 = "sha-youtube",
                        status = "paused",
                        downloadId = 11L,
                        localFilePath = "/downloads/youtube.apk",
                        downloadedBytes = 512L,
                        totalBytes = 1_024L,
                        downloadDetailMessage = "等待 Wi-Fi 后继续下载",
                        errorMessage = null,
                        updatedAtEpochMs = 2_000L,
                    ),
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()

        val item = viewModel.uiState.value.featuredApps.first()
        assertEquals(FeaturedAppInstallState.PAUSED, item.installState)
        assertEquals("下载已暂停", item.statusLabel)
        assertEquals("等待 Wi-Fi 后继续下载", item.actionLabel)
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
    fun store_driven_config_updates_shell_without_remote_fetch() = runTest {
        val viewModel = HomeViewModel(
            tvHomeConfigStore = InMemoryTvHomeConfigStore(
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
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.featuredVisible)
        assertTrue(state.heroHint.contains("缓存配置"))
    }

    @Test
    fun remote_config_load_runs_only_once_per_home_view_model() = runTest {
        val countingApi = CountingConfigPlatformApi()
        val viewModel = HomeViewModel(
            repository = TvHomeRepository(
                platformApi = countingApi,
            ),
        )

        viewModel.loadRemoteConfig()
        viewModel.loadRemoteConfig()
        advanceUntilIdle()

        assertEquals(1, countingApi.tvHomeConfigRequests)
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

    @Test
    fun store_driven_manifest_renders_featured_apps_and_ignores_unknown_slots() = runTest {
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = listOf(
                        StoredRuntimeApp(
                            appId = "youtube",
                            title = "YouTube",
                            packageName = "com.google.android.youtube.tv",
                            downloadUrl = "https://cdn.example.com/youtube.apk",
                            sha256 = "sha256-youtube",
                            versionCode = 1001L,
                            versionName = "1.0.1",
                            minClientVersion = "0.1.0",
                            installMode = "prompt",
                            visibility = "featured",
                            preloadPolicy = "idle_only",
                            requiresEntitlement = false,
                        ),
                        StoredRuntimeApp(
                            appId = "spotify",
                            title = "Spotify",
                            packageName = "com.spotify.tv.android",
                            downloadUrl = "https://cdn.example.com/spotify.apk",
                            sha256 = "sha256-spotify",
                            versionCode = 1002L,
                            versionName = "1.0.2",
                            minClientVersion = "0.1.0",
                            installMode = "prompt",
                            visibility = "featured",
                            preloadPolicy = "idle_only",
                            requiresEntitlement = true,
                        ),
                    ),
                    adSlots = listOf(
                        StoredRuntimeAdSlot(
                            slotId = "home.unknown",
                            enabled = true,
                            creatives = listOf(
                                StoredRuntimeAdCreative(
                                    creativeId = "ignored",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/ignored.png",
                                    altText = "Ignored",
                                    clickActionType = "none",
                                ),
                            ),
                        ),
                        StoredRuntimeAdSlot(
                            slotId = "home.hero",
                            enabled = true,
                            creatives = listOf(
                                StoredRuntimeAdCreative(
                                    creativeId = "hero-1",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/hero-1.png",
                                    altText = "首页广告 1",
                                    clickActionType = "deeplink",
                                    clickActionValue = "openclaw://promo/1",
                                ),
                                StoredRuntimeAdCreative(
                                    creativeId = "hero-2",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/hero-2.png",
                                    altText = "首页广告 2",
                                    clickActionType = "none",
                                ),
                            ),
                        ),
                    ),
                    cachedAtEpochMs = 100L,
                ),
            ),
            runtimePresenter = HomeRuntimePresenter(nowEpochMs = { 1_776_772_800_000L }),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.featuredVisible)
        assertEquals(listOf("YouTube", "Spotify"), state.featuredApps.map { it.title })
        assertEquals(listOf("hero-1", "hero-2"), state.heroAds.map { it.creativeId })
    }

    @Test
    fun store_driven_manifest_with_empty_or_inactive_home_slot_renders_no_hero_ads() = runTest {
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = emptyList(),
                    adSlots = listOf(
                        StoredRuntimeAdSlot(
                            slotId = "home.hero",
                            enabled = true,
                            creatives = listOf(
                                StoredRuntimeAdCreative(
                                    creativeId = "expired-hero",
                                    mediaType = "image",
                                    assetUrl = "https://cdn.example.com/expired.png",
                                    altText = "过期广告",
                                    clickActionType = "none",
                                    startsAt = "2026-04-01T00:00:00.000Z",
                                    endsAt = "2026-04-10T00:00:00.000Z",
                                ),
                            ),
                        ),
                    ),
                    cachedAtEpochMs = 100L,
                ),
            ),
            runtimePresenter = HomeRuntimePresenter(nowEpochMs = { 1_776_772_800_000L }),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.heroAds.isEmpty())
    }

    @Test
    fun store_driven_manifest_with_empty_ad_slots_renders_no_hero_ads() = runTest {
        val viewModel = HomeViewModel(
            runtimeManifestStore = InMemoryRuntimeManifestStore(
                StoredRuntimeManifest(
                    manifestVersion = "2026-04-21.1",
                    countryCode = "CN",
                    regionCode = "SH",
                    apps = emptyList(),
                    adSlots = emptyList(),
                    cachedAtEpochMs = 100L,
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.heroAds.isEmpty())
    }

    @Test
    fun store_driven_entitlement_and_resource_session_update_home_summary_without_raw_ids() = runTest {
        val viewModel = HomeViewModel(
            entitlementStore = InMemoryEntitlementStore(
                StoredEntitlementSummary(
                    accountId = "acct_001",
                    displayId = "TV-001",
                    planCode = "tv_plus",
                    paymentState = "paid",
                    priorityClass = "priority_plus",
                    renewalState = "active",
                    cachedAtEpochMs = 100L,
                ),
            ),
            resourceSessionStore = InMemoryResourceSessionStore(
                StoredResourceSession(
                    resourceSessionId = "rs_123",
                    queueStatus = "granted",
                    priorityClass = "priority_plus",
                    queuePosition = null,
                    estimatedWaitSeconds = null,
                    appAccountLease = null,
                    modelLease = null,
                    entitlementSummary = StoredEntitlementSnapshot(
                        accountId = "acct_001",
                        displayId = "TV-001",
                        planCode = "tv_plus",
                        paymentState = "paid",
                        priorityClass = "priority_plus",
                        renewalState = "active",
                    ),
                    expiresAt = "1970-01-01T00:00:30.000Z",
                    updatedAt = "2026-04-20T12:00:00.000Z",
                    polledAtEpochMs = 100L,
                ),
            ),
            runtimePresenter = HomeRuntimePresenter(
                nowEpochMs = { 20_000L },
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("会员已开通", state.tokenLabel)
        assertEquals("在线待命", state.modeLabel)
        assertTrue(state.heroHint.contains("资源已就绪"))
        assertFalse(state.heroHint.contains("rs_123"))
        assertFalse(state.heroHint.contains("priority_plus"))
    }

    @Test
    fun store_driven_expired_resource_session_surfaces_restricted_state() = runTest {
        val viewModel = HomeViewModel(
            entitlementStore = InMemoryEntitlementStore(
                StoredEntitlementSummary(
                    accountId = "acct_001",
                    displayId = "TV-001",
                    planCode = "tv_plus",
                    paymentState = "paid",
                    priorityClass = "priority_plus",
                    renewalState = "active",
                    cachedAtEpochMs = 100L,
                ),
            ),
            resourceSessionStore = InMemoryResourceSessionStore(
                StoredResourceSession(
                    resourceSessionId = "rs_expired",
                    queueStatus = "granted",
                    priorityClass = "priority_plus",
                    queuePosition = null,
                    estimatedWaitSeconds = null,
                    appAccountLease = null,
                    modelLease = null,
                    entitlementSummary = StoredEntitlementSnapshot(
                        accountId = "acct_001",
                        displayId = "TV-001",
                        planCode = "tv_plus",
                        paymentState = "paid",
                        priorityClass = "priority_plus",
                        renewalState = "active",
                    ),
                    expiresAt = "1970-01-01T00:00:10.000Z",
                    updatedAt = "2026-04-20T12:00:00.000Z",
                    polledAtEpochMs = 100L,
                ),
            ),
            runtimePresenter = HomeRuntimePresenter(
                nowEpochMs = { 20_000L },
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeStatusTone.WARNING, state.statusTone)
        assertEquals("在线受限", state.modeLabel)
        assertEquals("资源状态已过期", state.noticeTitle)
        assertTrue(state.noticeBody.contains("资源已经过期"))
        assertTrue(state.heroHint.contains("资源使用时段已过期"))
        assertFalse(state.heroHint.contains("资源已就绪"))
    }

    @Test
    fun repeated_bootstrap_updates_with_same_session_only_refresh_home_runtime_once() = runTest {
        val manifestRepository = CountingRuntimeManifestRepository(
            fallback = fallbackResolvedRuntimeManifest(),
        )
        val entitlementRepository = CountingEntitlementRepository(
            resolved = ResolvedEntitlementSummary(
                planCode = "tv_plus",
                paymentState = "paid",
                priorityClass = "priority_plus",
                renewalState = "active",
                source = EntitlementSource.REMOTE,
            ),
        )
        val resourceSessionRepository = CountingResourceSessionRepository(
            resolved = ResolvedResourceSession(
                resourceSessionId = "rs_123",
                queueStatus = "granted",
                priorityClass = "priority_plus",
                queuePosition = null,
                estimatedWaitSeconds = null,
                expiresAt = "2026-04-21T00:00:00.000Z",
                updatedAt = "2026-04-20T12:00:00.000Z",
                hasAppAccountLease = true,
                hasModelLease = true,
                entitlementSummary = null,
                source = ResourceSessionSource.REMOTE,
            ),
        )
        val viewModel = HomeViewModel(
            manifestRepository = manifestRepository,
            entitlementRepository = entitlementRepository,
            resourceSessionRepository = resourceSessionRepository,
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        assertEquals(1, manifestRepository.loadCount)
        assertEquals(1, entitlementRepository.loadCount)
        assertEquals(1, resourceSessionRepository.loadCount)
    }

    @Test
    fun paid_entitlement_and_granted_resource_session_update_home_summary_without_raw_ids() = runTest {
        val viewModel = HomeViewModel(
            entitlementRepository = FakeEntitlementRepository(
                ResolvedEntitlementSummary(
                    planCode = "tv_plus",
                    paymentState = "paid",
                    priorityClass = "priority_plus",
                    renewalState = "active",
                    source = EntitlementSource.REMOTE,
                ),
            ),
            resourceSessionRepository = FakeResourceSessionRepository(
                ResolvedResourceSession(
                    resourceSessionId = "rs_123",
                    queueStatus = "granted",
                    priorityClass = "priority_plus",
                    queuePosition = null,
                    estimatedWaitSeconds = null,
                    expiresAt = "2026-04-21T00:00:00.000Z",
                    updatedAt = "2026-04-20T12:00:00.000Z",
                    hasAppAccountLease = true,
                    hasModelLease = true,
                    entitlementSummary = null,
                    source = ResourceSessionSource.REMOTE,
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("会员已开通", state.tokenLabel)
        assertEquals("在线待命", state.modeLabel)
        assertTrue(state.heroHint.contains("资源已就绪"))
        assertFalse(state.heroHint.contains("rs_123"))
        assertFalse(state.heroHint.contains("priority_plus"))
    }

    @Test
    fun queued_resource_session_exposes_waiting_notice() = runTest {
        val viewModel = HomeViewModel(
            resourceSessionRepository = FakeResourceSessionRepository(
                ResolvedResourceSession(
                    resourceSessionId = "rs_queue_1",
                    queueStatus = "queued",
                    priorityClass = "priority_standard",
                    queuePosition = 3,
                    estimatedWaitSeconds = 95,
                    expiresAt = null,
                    updatedAt = "2026-04-20T12:05:00.000Z",
                    hasAppAccountLease = false,
                    hasModelLease = false,
                    entitlementSummary = ResolvedEntitlementSummary(
                        planCode = "tv_free",
                        paymentState = "free",
                        priorityClass = "priority_standard",
                        renewalState = "active",
                        source = EntitlementSource.REMOTE,
                    ),
                    source = ResourceSessionSource.REMOTE,
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeStatusTone.WARNING, state.statusTone)
        assertEquals("在线排队中", state.modeLabel)
        assertEquals("资源排队中", state.noticeTitle)
        assertTrue(state.noticeBody.contains("前面还有 3 台设备"))
        assertTrue(state.heroHint.contains("预计 95 秒"))
    }

    @Test
    fun suspended_entitlement_exposes_restricted_state_without_raw_account_fields() = runTest {
        val viewModel = HomeViewModel(
            entitlementRepository = FakeEntitlementRepository(
                ResolvedEntitlementSummary(
                    planCode = "tv_plus",
                    paymentState = "suspended",
                    priorityClass = "priority_plus",
                    renewalState = "past_due",
                    source = EntitlementSource.REMOTE,
                ),
            ),
        )

        viewModel.bindNetworkSnapshot(connectedNetworkSnapshot())
        viewModel.bindBootstrapState(readyState())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeStatusTone.CRITICAL, state.statusTone)
        assertEquals("服务受限", state.tokenLabel)
        assertEquals("在线受限", state.modeLabel)
        assertEquals("账号状态受限", state.noticeTitle)
        assertFalse(state.noticeBody.contains("tv_plus"))
        assertFalse(state.noticeBody.contains("priority_plus"))
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

private class CountingRuntimeManifestRepository(
    private val fallback: ResolvedRuntimeManifest,
) : HomeRuntimeManifestRepository(
    platformApi = FakePlatformApi(),
) {
    var loadCount: Int = 0
        private set

    override suspend fun load(sessionToken: String): ResolvedRuntimeManifest {
        loadCount += 1
        return fallback
    }

    override fun fallback(): ResolvedRuntimeManifest {
        return fallback
    }
}

private class FakeEntitlementRepository(
    private val resolved: ResolvedEntitlementSummary?,
) : HomeEntitlementRepository(
    platformApi = FakePlatformApi(),
) {
    override suspend fun load(sessionToken: String): ResolvedEntitlementSummary? {
        return resolved
    }
}

private class CountingEntitlementRepository(
    private val resolved: ResolvedEntitlementSummary?,
) : HomeEntitlementRepository(
    platformApi = FakePlatformApi(),
) {
    var loadCount: Int = 0
        private set

    override suspend fun load(sessionToken: String): ResolvedEntitlementSummary? {
        loadCount += 1
        return resolved
    }
}

private class FakeResourceSessionRepository(
    private val resolved: ResolvedResourceSession?,
) : HomeResourceSessionRepository(
    platformApi = FakePlatformApi(),
) {
    override suspend fun load(sessionToken: String): ResolvedResourceSession? {
        return resolved
    }
}

private class CountingResourceSessionRepository(
    private val resolved: ResolvedResourceSession?,
) : HomeResourceSessionRepository(
    platformApi = FakePlatformApi(),
) {
    var loadCount: Int = 0
        private set

    override suspend fun load(sessionToken: String): ResolvedResourceSession? {
        loadCount += 1
        return resolved
    }
}

private class CountingConfigPlatformApi : PlatformApi by FakePlatformApi() {
    var tvHomeConfigRequests: Int = 0
        private set

    override suspend fun getTvHomeConfig(): TvHomeConfigDto {
        tvHomeConfigRequests += 1
        return TvHomeConfigDto(
            projectKey = "openclaw-android-tv",
            projectLabel = "OpenClaw Android TV",
            runtimeManifestPath = "/api/me/runtime-manifest",
            entitlementPath = "/api/me/entitlement",
            resourceSessionBasePath = "/api/client/resource-session",
            manifestPollAfterSeconds = 900,
            resourceSessionPollAfterSeconds = 15,
            backgroundDownloadEnabled = true,
            idleDownloadOnly = true,
        )
    }
}

private fun fallbackResolvedRuntimeManifest(): ResolvedRuntimeManifest {
    return ResolvedRuntimeManifest(
        manifestVersion = "",
        countryCode = null,
        regionCode = null,
        source = RuntimeManifestSource.FALLBACK,
        featuredApps = emptyList(),
        ignoredFeaturedAppIds = emptyList(),
        heroAds = emptyList(),
    )
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
