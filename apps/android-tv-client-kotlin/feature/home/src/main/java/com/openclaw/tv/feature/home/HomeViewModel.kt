package com.openclaw.tv.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openclaw.tv.core.capability.CapabilitySnapshot
import com.openclaw.tv.core.network.OkHttpPlatformApi
import com.openclaw.tv.core.storage.DataStoreRuntimeManifestStore
import com.openclaw.tv.core.storage.DataStoreTvHomeConfigStore
import com.openclaw.tv.core.storage.DataStoreUpgradeStateStore
import com.openclaw.tv.core.storage.UpgradeStateStore
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeaturedAppItem(
    val appId: String,
    val title: String,
    val packageName: String,
    val summary: String,
    val installed: Boolean,
    val monogram: String,
    val accentColorHex: String,
    val statusLabel: String,
    val actionLabel: String,
)

data class QuickActionItem(
    val id: String,
    val title: String,
    val summary: String,
    val actionLabel: String,
    val accentColorHex: String,
)

data class WifiNetworkItem(
    val ssid: String,
    val summary: String,
    val statusLabel: String,
)

enum class HomeSurfaceMode {
    ONLINE,
    OFFLINE,
}

enum class HomeStatusTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    CRITICAL,
}

data class HomeUiState(
    val surfaceMode: HomeSurfaceMode,
    val brandTitle: String,
    val wifiLabel: String,
    val wifiConnected: Boolean,
    val modeLabel: String,
    val tokenLabel: String,
    val heroDialogue: String,
    val heroHint: String,
    val heroAds: List<HeroAdItem>,
    val noticeVisible: Boolean,
    val noticeTitle: String,
    val noticeBody: String,
    val statusTone: HomeStatusTone,
    val featuredSectionTitle: String,
    val featuredVisible: Boolean,
    val featuredApps: List<FeaturedAppItem>,
    val wifiSectionTitle: String,
    val wifiSectionVisible: Boolean,
    val wifiGuideText: String,
    val wifiNetworks: List<WifiNetworkItem>,
    val wifiEmptyText: String,
    val quickActionSectionTitle: String,
    val quickActions: List<QuickActionItem>,
)

class HomeViewModel internal constructor(
    private val repository: TvHomeRepository? = null,
    private val manifestRepository: HomeRuntimeManifestRepository? = null,
    private val upgradeStateStore: UpgradeStateStore? = null,
) : ViewModel() {

    private var latestCapabilities: CapabilitySnapshot? = null
    private var latestBootstrapState: BootstrapRuntimeState? = null
    private var latestNetworkSnapshot = HomeNetworkSnapshot.fallback
    private var resolvedConfig = TvHomeRepository.fallback()
    private var resolvedRuntimeManifest = manifestRepository?.fallback() ?: fallbackRuntimeManifest()
    private var latestUpgradeNotice: Pair<String, String>? = null
    private var activeManifestSessionToken: String? = null
    private val _uiState = MutableStateFlow(
        defaultState(
            snapshot = latestCapabilities,
            config = resolvedConfig,
            runtimeManifest = resolvedRuntimeManifest,
            bootstrapState = latestBootstrapState,
            networkSnapshot = latestNetworkSnapshot,
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        upgradeStateStore?.let { store ->
            viewModelScope.launch {
                val successVersion = store.consumeSuccessVersion()
                if (successVersion.isNullOrBlank()) {
                    return@launch
                }
                latestUpgradeNotice = buildUpgradeSuccessNotice(successVersion)
                refreshState()
            }
        }
    }

    fun bindCapabilities(snapshot: CapabilitySnapshot) {
        latestCapabilities = snapshot
        refreshState()
    }

    fun bindBootstrapState(state: BootstrapRuntimeState) {
        latestBootstrapState = state
        maybeLoadRuntimeManifest(state)
        refreshState()
    }

    internal fun bindNetworkSnapshot(snapshot: HomeNetworkSnapshot) {
        latestNetworkSnapshot = snapshot
        refreshState()
    }

    fun loadRemoteConfig() {
        val activeRepository = repository ?: return
        viewModelScope.launch {
            resolvedConfig = activeRepository.load()
            refreshState()
        }
    }

    private fun refreshState() {
        _uiState.value = defaultState(
            snapshot = latestCapabilities,
            config = resolvedConfig,
            runtimeManifest = resolvedRuntimeManifest,
            bootstrapState = latestBootstrapState,
            networkSnapshot = latestNetworkSnapshot,
        )
    }

    private fun defaultState(
        snapshot: CapabilitySnapshot? = null,
        config: ResolvedTvHomeConfig = TvHomeRepository.fallback(),
        runtimeManifest: ResolvedRuntimeManifest = resolvedRuntimeManifest,
        bootstrapState: BootstrapRuntimeState? = null,
        networkSnapshot: HomeNetworkSnapshot = HomeNetworkSnapshot.fallback,
    ): HomeUiState {
        val featuredApps = runtimeManifest.featuredApps.map { app ->
            val installed = snapshot?.isAppInstalled(app.packageName) == true
            FeaturedAppItem(
                appId = app.appId,
                title = app.title,
                packageName = app.packageName,
                summary = app.summary,
                installed = installed,
                monogram = app.monogram,
                accentColorHex = app.accentColorHex,
                statusLabel = buildFeaturedStatusLabel(
                    installed = installed,
                    requiresEntitlement = app.requiresEntitlement,
                ),
                actionLabel = buildFeaturedActionLabel(
                    installed = installed,
                    requiresEntitlement = app.requiresEntitlement,
                    installMode = app.installMode,
                ),
            )
        }
        val hasInstalledFeatured = featuredApps.any { it.installed }
        val runtimeUi = buildRuntimeUi(bootstrapState)
        val configNotice = buildContentNotice(runtimeManifest, featuredApps)
        val notice = if (runtimeUi.tone == HomeStatusTone.CRITICAL) {
            mergeNotices(
                primaryNotice = runtimeUi.notice,
                secondaryNotice = latestUpgradeNotice,
                tertiaryNotice = configNotice,
            )
        } else {
            mergeNotices(
                primaryNotice = latestUpgradeNotice,
                secondaryNotice = runtimeUi.notice,
                tertiaryNotice = configNotice,
            )
        }
        val isOnline = networkSnapshot.isConnected

        return HomeUiState(
            surfaceMode = if (isOnline) HomeSurfaceMode.ONLINE else HomeSurfaceMode.OFFLINE,
            brandTitle = "RS AITV",
            wifiLabel = buildWifiLabel(networkSnapshot),
            wifiConnected = isOnline,
            modeLabel = buildModeLabel(isOnline = isOnline, runtimeUi = runtimeUi),
            tokenLabel = "服务中心",
            heroDialogue = buildHeroDialogue(
                isOnline = isOnline,
                networkSnapshot = networkSnapshot,
            ),
            heroHint = buildHeroHint(
                isOnline = isOnline,
                config = config,
                runtimeManifest = runtimeManifest,
                runtimeUi = runtimeUi,
                networkSnapshot = networkSnapshot,
            ),
            heroAds = if (isOnline) runtimeManifest.heroAds else emptyList(),
            noticeVisible = notice != null,
            noticeTitle = notice?.first.orEmpty(),
            noticeBody = notice?.second.orEmpty(),
            statusTone = runtimeUi.tone,
            featuredSectionTitle = "内容入口",
            featuredVisible = isOnline && featuredApps.isNotEmpty(),
            featuredApps = featuredApps,
            wifiSectionTitle = "Wi-Fi 连接",
            wifiSectionVisible = !isOnline,
            wifiGuideText = buildWifiGuide(networkSnapshot),
            wifiNetworks = buildWifiNetworks(networkSnapshot),
            wifiEmptyText = buildWifiEmptyText(networkSnapshot),
            quickActionSectionTitle = "快捷入口",
            quickActions = buildQuickActions(
                isOnline = isOnline,
                hasInstalledFeatured = hasInstalledFeatured,
            ),
        )
    }

    private fun maybeLoadRuntimeManifest(state: BootstrapRuntimeState) {
        val sessionToken = state.session?.sessionToken?.takeIf(String::isNotBlank) ?: return
        val activeRepository = manifestRepository ?: return
        if (activeManifestSessionToken == sessionToken) {
            return
        }
        activeManifestSessionToken = sessionToken
        viewModelScope.launch {
            resolvedRuntimeManifest = activeRepository.load(sessionToken)
            refreshState()
        }
    }

    private fun buildModeLabel(
        isOnline: Boolean,
        runtimeUi: RuntimeUiSummary,
    ): String {
        if (!isOnline) {
            return "离线引导"
        }
        return when (runtimeUi.tone) {
            HomeStatusTone.SUCCESS -> "在线待命"
            HomeStatusTone.WARNING -> "在线同步中"
            HomeStatusTone.CRITICAL -> "在线受限"
            HomeStatusTone.NEUTRAL -> "在线待命"
        }
    }

    private fun buildHeroDialogue(
        isOnline: Boolean,
        networkSnapshot: HomeNetworkSnapshot,
    ): String {
        return if (isOnline) {
            "想看节目、打开应用，或者直接对我说。"
        } else {
            networkSnapshot.currentSsid?.let { "当前 Wi-Fi 是 $it，但还没连上外网，先把网络接好。" }
                ?: "现在还没有联网，先在下面选一个 Wi-Fi。"
        }
    }

    private fun buildHeroHint(
        isOnline: Boolean,
        config: ResolvedTvHomeConfig,
        runtimeManifest: ResolvedRuntimeManifest,
        runtimeUi: RuntimeUiSummary,
        networkSnapshot: HomeNetworkSnapshot,
    ): String {
        if (!isOnline) {
            return if (networkSnapshot.canReadWifiList) {
                "按上下选择网络，按确定后会进入系统 Wi-Fi 设置继续完成连接。"
            } else {
                "当前拿不到可见 Wi-Fi 列表，按确定可直接进入系统网络设置。"
            }
        }
        val configLabel = when (config.source) {
            ConfigSource.REMOTE -> "云端配置"
            ConfigSource.CACHE -> "缓存配置"
            ConfigSource.FALLBACK -> "本地默认配置"
        }
        val manifestLabel = when (runtimeManifest.source) {
            RuntimeManifestSource.REMOTE -> "云端清单"
            RuntimeManifestSource.CACHE -> "缓存清单"
            RuntimeManifestSource.FALLBACK -> "本地样稿"
        }
        val areaLabel = runtimeManifest.regionCode
            ?.takeIf(String::isNotBlank)
            ?.let { "${runtimeManifest.countryCode} / $it" }
            ?: runtimeManifest.countryCode
        val manifestSummary = areaLabel?.let { "$manifestLabel 已加载，区域 $it。" } ?: "$manifestLabel 已加载。"
        return if (runtimeUi.tone == HomeStatusTone.NEUTRAL) {
            "$configLabel 已加载，$manifestSummary"
        } else {
            "$configLabel 已加载，$manifestSummary 当前状态：${runtimeUi.label}。"
        }
    }

    private fun buildWifiGuide(snapshot: HomeNetworkSnapshot): String {
        return snapshot.statusText
    }

    private fun buildWifiNetworks(snapshot: HomeNetworkSnapshot): List<WifiNetworkItem> {
        return snapshot.visibleNetworks.map { ssid ->
            WifiNetworkItem(
                ssid = ssid,
                summary = if (snapshot.currentSsid == ssid) "当前网络，按确定继续连接或排查" else "按确定进入系统设置连接这个网络",
                statusLabel = if (snapshot.currentSsid == ssid) "当前" else "可连接",
            )
        }
    }

    private fun buildWifiEmptyText(snapshot: HomeNetworkSnapshot): String {
        return when {
            snapshot.canReadWifiList -> "暂时没有扫描到可用 Wi-Fi，请检查路由器，或直接进入系统网络设置。"
            snapshot.currentSsid != null -> "当前只识别到 $snapshot.currentSsid，建议先进入系统网络设置检查连通性。"
            else -> "当前应用还拿不到可见 Wi-Fi 列表，请按确定直接进入系统网络设置。"
        }
    }

    private fun buildQuickActions(
        isOnline: Boolean,
        hasInstalledFeatured: Boolean,
    ): List<QuickActionItem> {
        return listOf(
            QuickActionItem(
                id = QUICK_ACTION_LOCAL,
                title = "本地(USB)",
                summary = "读取 U 盘、本地视频和音频文件",
                actionLabel = "打开",
                accentColorHex = "#4E89FF",
            ),
            QuickActionItem(
                id = QUICK_ACTION_CAST,
                title = "投屏",
                summary = "进入系统投屏或无线显示入口",
                actionLabel = "打开",
                accentColorHex = "#7C6BFF",
            ),
            QuickActionItem(
                id = QUICK_ACTION_SETTINGS,
                title = "设置",
                summary = "网络、蓝牙、账号与系统设置",
                actionLabel = "打开",
                accentColorHex = "#3BAA83",
            ),
            QuickActionItem(
                id = QUICK_ACTION_FREE_PLAY,
                title = "自由播放",
                summary = if (hasInstalledFeatured && isOnline) "直接进入已安装的内容应用" else "先联网或进入本机应用继续浏览",
                actionLabel = "进入",
                accentColorHex = "#FF9A57",
            ),
            QuickActionItem(
                id = QUICK_ACTION_LOCAL_APPS,
                title = "本机应用",
                summary = "查看这台电视上已经安装的应用",
                actionLabel = "查看",
                accentColorHex = "#F06EA5",
            ),
        )
    }

    private fun buildWifiLabel(snapshot: HomeNetworkSnapshot): String {
        return when {
            snapshot.isConnected && snapshot.transport == "ethernet" -> "有线已连"
            snapshot.isConnected && snapshot.currentSsid != null -> snapshot.currentSsid
            snapshot.isConnected -> "网络已连"
            snapshot.currentSsid != null -> snapshot.currentSsid
            else -> "未联网"
        }
    }

    private fun buildFeaturedStatusLabel(
        installed: Boolean,
        requiresEntitlement: Boolean,
    ): String {
        return when {
            installed && requiresEntitlement -> "已安装 / 需授权"
            installed -> "已安装"
            requiresEntitlement -> "需授权"
            else -> "未安装"
        }
    }

    private fun buildFeaturedActionLabel(
        installed: Boolean,
        requiresEntitlement: Boolean,
        installMode: String,
    ): String {
        if (installed) {
            return if (requiresEntitlement) "按确定查看授权状态" else "按确定键打开"
        }
        return when (installMode.trim().lowercase()) {
            "auto" -> "等待后台下发"
            "prompt" -> "设备里还没装"
            else -> if (requiresEntitlement) "需授权后继续" else "设备里还没装"
        }
    }

    private fun buildContentNotice(
        runtimeManifest: ResolvedRuntimeManifest,
        featuredApps: List<FeaturedAppItem>,
    ): Pair<String, String>? {
        if (runtimeManifest.ignoredFeaturedAppIds.isNotEmpty()) {
            val unresolvedCount = runtimeManifest.ignoredFeaturedAppIds.size
            return if (featuredApps.isEmpty()) {
                "该区域内容位待同步" to "runtime-manifest 已下发 $unresolvedCount 个首页应用描述，但关键字段还不完整，当前暂不展示节目入口。"
            } else {
                "部分内容位待同步" to "当前先展示结构完整的首页应用卡片，其余 $unresolvedCount 个入口还在等待服务端补齐字段。"
            }
        }
        if (runtimeManifest.source != RuntimeManifestSource.FALLBACK && featuredApps.isEmpty()) {
            return "当前没有内容入口" to "runtime-manifest 还没有为当前首页发布可展示的应用入口，首页暂时只保留基础壳层。"
        }
        return null
    }

    private fun buildRuntimeUi(state: BootstrapRuntimeState?): RuntimeUiSummary {
        if (state == null) {
            return RuntimeUiSummary(
                label = "运行未接入",
                tone = HomeStatusTone.NEUTRAL,
            )
        }

        val upgrade = state.upgradeStatus
        if (upgrade?.state == RuntimeUpgradeState.REQUIRED) {
            val requiredVersion = upgrade.minSupportedVersion ?: upgrade.targetVersion ?: upgrade.latestVersion ?: "目标版本"
            return RuntimeUiSummary(
                label = "需升级 $requiredVersion",
                tone = HomeStatusTone.CRITICAL,
                notice = "当前版本不满足策略要求" to buildString {
                    append("当前版本 ${upgrade.currentVersion} 低于最低支持 $requiredVersion。")
                    upgrade.targetVersion?.takeIf(String::isNotBlank)?.let { append(" 后台目标版本为 $it。") }
                    if (upgrade.forceUpgrade) {
                        append(" 当前策略要求强制升级。")
                    }
                },
            )
        }

        return when (state.phase) {
            BootstrapRuntimePhase.IDLE -> RuntimeUiSummary(
                label = "运行待同步",
                tone = HomeStatusTone.NEUTRAL,
            )

            BootstrapRuntimePhase.SYNCING -> RuntimeUiSummary(
                label = "运行同步中",
                tone = HomeStatusTone.WARNING,
            )

            BootstrapRuntimePhase.READY -> {
                if (upgrade?.state == RuntimeUpgradeState.AVAILABLE) {
                    val nextVersion = upgrade.latestVersion ?: upgrade.targetVersion ?: "新版本"
                    RuntimeUiSummary(
                        label = "可升级 $nextVersion",
                        tone = HomeStatusTone.WARNING,
                        notice = "发现新版本 $nextVersion" to buildString {
                            append("当前版本 ${upgrade.currentVersion} 仍可继续运行。")
                            upgrade.targetVersion?.takeIf(String::isNotBlank)?.let { append(" 后台目标版本为 $it。") }
                        },
                    )
                } else {
                    RuntimeUiSummary(
                        label = "运行已就绪",
                        tone = HomeStatusTone.SUCCESS,
                    )
                }
            }

            BootstrapRuntimePhase.DEGRADED -> RuntimeUiSummary(
                label = "运行已降级",
                tone = HomeStatusTone.WARNING,
                notice = "运行状态已降级" to buildString {
                    append("首页仍可继续使用，但最新策略刷新失败。")
                    state.errorMessage?.takeIf(String::isNotBlank)?.let { append(" 错误：$it。") }
                },
            )

            BootstrapRuntimePhase.FAILED -> RuntimeUiSummary(
                label = "运行初始化失败",
                tone = HomeStatusTone.CRITICAL,
                notice = "运行初始化失败" to buildString {
                    append("会话、租约和版本策略还没有完成同步。")
                    state.errorMessage?.takeIf(String::isNotBlank)?.let { append(" 错误：$it。") }
                },
            )
        }
    }

    private fun mergeNotices(
        primaryNotice: Pair<String, String>?,
        secondaryNotice: Pair<String, String>?,
        tertiaryNotice: Pair<String, String>?,
    ): Pair<String, String>? {
        val notices = listOfNotNull(primaryNotice, secondaryNotice, tertiaryNotice)
        if (notices.isEmpty()) {
            return null
        }
        return notices.first().first to notices.joinToString(separator = " ") { it.second }
    }

    private fun buildUpgradeSuccessNotice(version: String): Pair<String, String> {
        return "已升级到 $version" to "客户端已经完成版本切换，当前已按新版本重新启动。"
    }

    companion object {
        const val QUICK_ACTION_LOCAL = "local_usb"
        const val QUICK_ACTION_CAST = "cast"
        const val QUICK_ACTION_SETTINGS = "settings"
        const val QUICK_ACTION_FREE_PLAY = "free_play"
        const val QUICK_ACTION_LOCAL_APPS = "local_apps"

        fun factory(
            applicationContext: Context?,
            platformBaseUrl: String?,
            enableRemoteConfig: Boolean,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java).not()) {
                        throw IllegalArgumentException("Unsupported ViewModel class: $modelClass")
                    }
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        repository = createRepository(
                            applicationContext = applicationContext,
                            platformBaseUrl = platformBaseUrl,
                            enableRemoteConfig = enableRemoteConfig,
                        ),
                        manifestRepository = createManifestRepository(
                            applicationContext = applicationContext,
                            platformBaseUrl = platformBaseUrl,
                            enableRemoteConfig = enableRemoteConfig,
                        ),
                        upgradeStateStore = applicationContext?.let(::DataStoreUpgradeStateStore),
                    ) as T
                }
            }
        }

        private fun fallbackRuntimeManifest(): ResolvedRuntimeManifest {
            return ResolvedRuntimeManifest(
                manifestVersion = "",
                countryCode = null,
                regionCode = null,
                source = RuntimeManifestSource.FALLBACK,
                featuredApps = HomeAppCatalog.defaultFeaturedApps().map { app ->
                    RuntimeFeaturedApp(
                        appId = app.id,
                        title = app.title,
                        packageName = app.packageName,
                        summary = app.summary,
                        monogram = app.monogram,
                        accentColorHex = app.accentColorHex,
                        installMode = "prompt",
                        requiresEntitlement = false,
                    )
                },
                ignoredFeaturedAppIds = emptyList(),
                heroAds = emptyList(),
            )
        }

        private fun createRepository(
            applicationContext: Context?,
            platformBaseUrl: String?,
            enableRemoteConfig: Boolean,
        ): TvHomeRepository? {
            val resolvedBaseUrl = platformBaseUrl?.trim()?.takeIf(String::isNotBlank)
            if (!enableRemoteConfig || resolvedBaseUrl == null) {
                return null
            }
            return TvHomeRepository(
                platformApi = OkHttpPlatformApi(resolvedBaseUrl),
                cacheStore = applicationContext?.let(::DataStoreTvHomeConfigStore),
            )
        }

        private fun createManifestRepository(
            applicationContext: Context?,
            platformBaseUrl: String?,
            enableRemoteConfig: Boolean,
        ): HomeRuntimeManifestRepository? {
            val resolvedBaseUrl = platformBaseUrl?.trim()?.takeIf(String::isNotBlank)
            if (!enableRemoteConfig || resolvedBaseUrl == null) {
                return null
            }
            return HomeRuntimeManifestRepository(
                platformApi = OkHttpPlatformApi(resolvedBaseUrl),
                cacheStore = applicationContext?.let(::DataStoreRuntimeManifestStore),
            )
        }
    }
}

private data class RuntimeUiSummary(
    val label: String,
    val tone: HomeStatusTone,
    val notice: Pair<String, String>? = null,
)
