package com.openclaw.tv.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openclaw.tv.core.capability.CapabilitySnapshot
import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.core.storage.DataStoreAppDownloadStore
import com.openclaw.tv.core.storage.DataStoreEntitlementStore
import com.openclaw.tv.core.storage.DataStoreRuntimeManifestStore
import com.openclaw.tv.core.storage.DataStoreResourceSessionStore
import com.openclaw.tv.core.storage.DataStoreTvHomeConfigStore
import com.openclaw.tv.core.storage.DataStoreUpgradeStateStore
import com.openclaw.tv.core.storage.EntitlementStore
import com.openclaw.tv.core.storage.ResourceSessionStore
import com.openclaw.tv.core.storage.RuntimeManifestStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.core.storage.TvHomeConfigStore
import com.openclaw.tv.core.storage.UpgradeStateStore
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeState
import kotlinx.coroutines.flow.collect
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
    val installState: FeaturedAppInstallState,
    val monogram: String,
    val accentColorHex: String,
    val statusLabel: String,
    val actionLabel: String,
    val downloadId: Long? = null,
    val localFilePath: String? = null,
    val downloadDetailMessage: String? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val downloadErrorMessage: String? = null,
)

enum class FeaturedAppInstallState {
    INSTALLED,
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY_TO_INSTALL,
    FAILED,
}

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
    private val tvHomeConfigStore: TvHomeConfigStore? = null,
    private val runtimeManifestStore: RuntimeManifestStore? = null,
    private val entitlementStore: EntitlementStore? = null,
    private val resourceSessionStore: ResourceSessionStore? = null,
    private val appDownloadStore: AppDownloadStore? = null,
    private val manifestRepository: HomeRuntimeManifestRepository? = null,
    private val entitlementRepository: HomeEntitlementRepository? = null,
    private val resourceSessionRepository: HomeResourceSessionRepository? = null,
    private val upgradeStateStore: UpgradeStateStore? = null,
    private val runtimePresenter: HomeRuntimePresenter = HomeRuntimePresenter(),
) : ViewModel() {

    private var hasLoadedRemoteConfig = false
    private var hasReceivedNetworkSnapshot = false
    private var latestCapabilities: CapabilitySnapshot? = null
    private var latestBootstrapState: BootstrapRuntimeState? = null
    private var latestNetworkSnapshot = HomeNetworkSnapshot.fallback
    private var latestAppDownloads: Map<String, StoredAppDownloadState> = emptyMap()
    private var resolvedConfig = TvHomeRepository.fallback()
    private var resolvedRuntimeManifest = manifestRepository?.fallback() ?: runtimePresenter.fallbackRuntimeManifest()
    private var startupHeroAdsLocked = false
    private var startupHeroAds: List<HeroAdItem> = emptyList()
    private var resolvedEntitlementSummary: ResolvedEntitlementSummary? = null
    private var resolvedResourceSession: ResolvedResourceSession? = null
    private var latestUpgradeNotice: Pair<String, String>? = null
    private var activeManifestSessionToken: String? = null
    private var activeEntitlementSessionToken: String? = null
    private var activeResourceSessionToken: String? = null
    private val _uiState = MutableStateFlow(
        defaultState(
            snapshot = latestCapabilities,
            config = resolvedConfig,
            runtimeManifest = resolvedRuntimeManifest,
            entitlementSummary = resolvedEntitlementSummary,
            resourceSession = resolvedResourceSession,
            bootstrapState = latestBootstrapState,
            networkSnapshot = latestNetworkSnapshot,
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeHomeConfigStore()
        observeRuntimeStores()
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
        if (state.session?.sessionToken.isNullOrBlank()) {
            resetRuntimeSessionTracking()
            clearSessionScopedAccessState()
        }
        maybeLoadRuntimeManifest(state)
        maybeLoadEntitlementSummary(state)
        maybeLoadResourceSession(state)
        refreshState()
    }

    internal fun bindNetworkSnapshot(snapshot: HomeNetworkSnapshot) {
        hasReceivedNetworkSnapshot = true
        latestNetworkSnapshot = snapshot
        maybeLockStartupHeroAdsFromStoredManifest()
        refreshState()
    }

    fun loadRemoteConfig() {
        val activeRepository = repository ?: return
        if (hasLoadedRemoteConfig) {
            return
        }
        hasLoadedRemoteConfig = true
        viewModelScope.launch {
            resolvedConfig = activeRepository.load()
            refreshState()
        }
    }

    fun markFeaturedAppDownloadFailed(
        appId: String,
        message: String,
    ) {
        val normalizedAppId = appId.trim()
        val normalizedMessage = message.trim()
        val store = appDownloadStore ?: return
        if (normalizedAppId.isBlank() || normalizedMessage.isBlank()) {
            return
        }
        viewModelScope.launch {
            val current = store.read(normalizedAppId) ?: return@launch
            store.upsert(
                current.copy(
                    status = "failed",
                    errorMessage = normalizedMessage,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun refreshState() {
        _uiState.value = defaultState(
            snapshot = latestCapabilities,
            config = resolvedConfig,
            runtimeManifest = resolvedRuntimeManifest,
            entitlementSummary = resolvedEntitlementSummary,
            resourceSession = resolvedResourceSession,
            bootstrapState = latestBootstrapState,
            networkSnapshot = latestNetworkSnapshot,
        )
    }

    private fun observeRuntimeStores() {
        runtimeManifestStore?.let { store ->
            viewModelScope.launch {
                store.manifest.collect { manifest ->
                    resolvedRuntimeManifest = runtimePresenter.presentRuntimeManifest(manifest)
                    maybeLockStartupHeroAdsFromStoredManifest()
                    refreshState()
                }
            }
        }
        entitlementStore?.let { store ->
            viewModelScope.launch {
                store.entitlement.collect { summary ->
                    resolvedEntitlementSummary = runtimePresenter.presentEntitlement(summary)
                    refreshState()
                }
            }
        }
        resourceSessionStore?.let { store ->
            viewModelScope.launch {
                store.resourceSession.collect { resourceSession ->
                    resolvedResourceSession = runtimePresenter.presentResourceSession(resourceSession)
                    refreshState()
                }
            }
        }
        appDownloadStore?.let { store ->
            viewModelScope.launch {
                store.downloads.collect { downloads ->
                    latestAppDownloads = downloads
                    refreshState()
                }
            }
        }
    }

    private fun observeHomeConfigStore() {
        tvHomeConfigStore?.let { store ->
            viewModelScope.launch {
                store.config.collect { config ->
                    resolvedConfig = config?.toResolvedConfig(source = ConfigSource.CACHE) ?: TvHomeRepository.fallback()
                    refreshState()
                }
            }
        }
    }

    private fun defaultState(
        snapshot: CapabilitySnapshot? = null,
        config: ResolvedTvHomeConfig = TvHomeRepository.fallback(),
        runtimeManifest: ResolvedRuntimeManifest = resolvedRuntimeManifest,
        entitlementSummary: ResolvedEntitlementSummary? = resolvedEntitlementSummary,
        resourceSession: ResolvedResourceSession? = resolvedResourceSession,
        bootstrapState: BootstrapRuntimeState? = null,
        networkSnapshot: HomeNetworkSnapshot = HomeNetworkSnapshot.fallback,
    ): HomeUiState {
        val featuredApps = runtimeManifest.featuredApps.map { app ->
            val installed = snapshot?.isAppInstalled(app.packageName) == true
            val downloadState = latestAppDownloads[app.appId]
            val installState = resolveInstallState(
                installed = installed,
                downloadState = downloadState,
            )
            FeaturedAppItem(
                appId = app.appId,
                title = app.title,
                packageName = app.packageName,
                summary = app.summary,
                installed = installed,
                installState = installState,
                monogram = app.monogram,
                accentColorHex = app.accentColorHex,
                statusLabel = buildFeaturedStatusLabel(
                    installState = installState,
                    requiresEntitlement = app.requiresEntitlement,
                    downloadState = downloadState,
                ),
                actionLabel = buildFeaturedActionLabel(
                    installState = installState,
                    requiresEntitlement = app.requiresEntitlement,
                    installMode = app.installMode,
                    downloadState = downloadState,
                ),
                downloadId = downloadState?.downloadId,
                localFilePath = downloadState?.localFilePath,
                downloadDetailMessage = downloadState?.downloadDetailMessage,
                downloadedBytes = downloadState?.downloadedBytes,
                totalBytes = downloadState?.totalBytes,
                downloadErrorMessage = downloadState?.errorMessage,
            )
        }
        val hasInstalledFeatured = featuredApps.any { it.installed }
        val accessUi = buildAccessUi(entitlementSummary, resourceSession)
        val isOnline = networkSnapshot.isConnected
        val runtimeUi = resolveRuntimeUiForPresentation(
            runtimeUi = buildRuntimeUi(bootstrapState),
            runtimeManifest = runtimeManifest,
            bootstrapState = bootstrapState,
            accessUi = accessUi,
            isOnline = isOnline,
        )
        val configNotice = buildContentNotice(runtimeManifest, featuredApps)
        val installNotice = buildInstallNotice(featuredApps)
        val notice = mergeNotices(
            accessUi.notice?.toPrioritizedNotice(priority = noticePriority(accessUi.tone, base = 40)),
            runtimeUi.notice?.toPrioritizedNotice(
                priority = noticePriority(runtimeUi.tone, base = 30),
                mergeGroup = runtimeNoticeMergeGroup(bootstrapState),
            ),
            latestUpgradeNotice?.toPrioritizedNotice(
                priority = 35,
                mergeGroup = NoticeMergeGroup.UPGRADE,
            ),
            installNotice?.toPrioritizedNotice(priority = 25),
            configNotice?.toPrioritizedNotice(priority = 10),
        )
        val resolvedTone = combineTones(runtimeUi.tone, accessUi.tone)

        return HomeUiState(
            surfaceMode = if (isOnline) HomeSurfaceMode.ONLINE else HomeSurfaceMode.OFFLINE,
            brandTitle = "RS AITV",
            wifiLabel = buildWifiLabel(networkSnapshot),
            wifiConnected = isOnline,
            modeLabel = buildModeLabel(isOnline = isOnline, runtimeUi = runtimeUi, accessUi = accessUi),
            tokenLabel = accessUi.tokenLabel,
            heroDialogue = buildHeroDialogue(
                isOnline = isOnline,
                networkSnapshot = networkSnapshot,
            ),
            heroHint = buildHeroHint(
                isOnline = isOnline,
                config = config,
                runtimeManifest = runtimeManifest,
                runtimeUi = runtimeUi,
                accessUi = accessUi,
                networkSnapshot = networkSnapshot,
            ),
            heroAds = startupHeroAds,
            noticeVisible = isOnline && notice != null,
            noticeTitle = notice?.first.orEmpty(),
            noticeBody = notice?.second.orEmpty(),
            statusTone = if (isOnline) resolvedTone else HomeStatusTone.NEUTRAL,
            featuredSectionTitle = "内容入口",
            featuredVisible = isOnline && featuredApps.isNotEmpty(),
            featuredApps = featuredApps,
            wifiSectionTitle = "选择 Wi-Fi 网络",
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
            if (!startupHeroAdsLocked) {
                lockStartupHeroAds(resolvedRuntimeManifest.heroAds)
            }
            refreshState()
        }
    }

    private fun maybeLockStartupHeroAdsFromStoredManifest() {
        if (startupHeroAdsLocked || !hasReceivedNetworkSnapshot) {
            return
        }
        if (resolvedRuntimeManifest.source == RuntimeManifestSource.FALLBACK) {
            return
        }
        val shouldUseStoredManifest = manifestRepository == null || !latestNetworkSnapshot.isConnected
        if (!shouldUseStoredManifest) {
            return
        }
        lockStartupHeroAds(resolvedRuntimeManifest.heroAds)
    }

    private fun lockStartupHeroAds(heroAds: List<HeroAdItem>) {
        startupHeroAds = heroAds.distinctBy { it.creativeId }
        startupHeroAdsLocked = true
    }

    private fun maybeLoadEntitlementSummary(state: BootstrapRuntimeState) {
        val sessionToken = state.session?.sessionToken?.takeIf(String::isNotBlank) ?: return
        val activeRepository = entitlementRepository ?: return
        if (activeEntitlementSessionToken == sessionToken) {
            return
        }
        activeEntitlementSessionToken = sessionToken
        viewModelScope.launch {
            resolvedEntitlementSummary = activeRepository.load(sessionToken)
            refreshState()
        }
    }

    private fun maybeLoadResourceSession(state: BootstrapRuntimeState) {
        val sessionToken = state.session?.sessionToken?.takeIf(String::isNotBlank) ?: return
        val activeRepository = resourceSessionRepository ?: return
        if (activeResourceSessionToken == sessionToken) {
            return
        }
        activeResourceSessionToken = sessionToken
        viewModelScope.launch {
            resolvedResourceSession = activeRepository.load(sessionToken)
            refreshState()
        }
    }

    private fun resetRuntimeSessionTracking() {
        activeManifestSessionToken = null
        activeEntitlementSessionToken = null
        activeResourceSessionToken = null
    }

    private fun clearSessionScopedAccessState() {
        resolvedEntitlementSummary = null
        resolvedResourceSession = null
    }

    private fun buildModeLabel(
        isOnline: Boolean,
        runtimeUi: RuntimeUiSummary,
        accessUi: AccessUiSummary,
    ): String {
        if (!isOnline) {
            return "联网向导"
        }
        accessUi.modeLabel?.let { return it }
        return when (runtimeUi.tone) {
            HomeStatusTone.SUCCESS -> "在线待命"
            HomeStatusTone.WARNING -> "在线同步中"
            HomeStatusTone.CRITICAL -> "在线受限"
            HomeStatusTone.NEUTRAL -> "在线待命"
        }
    }

    private fun resolveRuntimeUiForPresentation(
        runtimeUi: RuntimeUiSummary,
        runtimeManifest: ResolvedRuntimeManifest,
        bootstrapState: BootstrapRuntimeState?,
        accessUi: AccessUiSummary,
        isOnline: Boolean,
    ): RuntimeUiSummary {
        val shouldUseFallbackShellVisual = isOnline &&
            runtimeManifest.source == RuntimeManifestSource.FALLBACK &&
            bootstrapState?.phase in setOf(
                BootstrapRuntimePhase.SYNCING,
                BootstrapRuntimePhase.DEGRADED,
                BootstrapRuntimePhase.FAILED,
            ) &&
            accessUi.tone == HomeStatusTone.NEUTRAL
        if (!shouldUseFallbackShellVisual) {
            return runtimeUi
        }
        return RuntimeUiSummary(
            label = "运行待同步",
            tone = HomeStatusTone.SUCCESS,
        )
    }

    private fun buildHeroDialogue(
        isOnline: Boolean,
        networkSnapshot: HomeNetworkSnapshot,
    ): String {
        return if (isOnline) {
            "你好，我可以帮你找节目、打开应用，也可以直接对我说。"
        } else {
            networkSnapshot.currentSsid?.let { "当前识别到 $it，请先完成 Wi-Fi 连接。" }
                ?: "未连接网络，请先完成 Wi-Fi 配置。"
        }
    }

    private fun buildHeroHint(
        isOnline: Boolean,
        config: ResolvedTvHomeConfig,
        runtimeManifest: ResolvedRuntimeManifest,
        runtimeUi: RuntimeUiSummary,
        accessUi: AccessUiSummary,
        networkSnapshot: HomeNetworkSnapshot,
    ): String {
        if (!isOnline) {
            return if (networkSnapshot.canReadWifiList) {
                "左侧选择网络，右侧继续连接；也可以直接进入系统网络设置。"
            } else {
                "当前拿不到可见 Wi-Fi 列表，请直接进入系统网络设置完成连接。"
            }
        }
        val accessHint = accessUi.hintText
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return when {
            accessHint != null -> "常用内容入口已经准备好。$accessHint"
            runtimeUi.tone == HomeStatusTone.CRITICAL -> "当前服务受限，建议先检查账号与网络状态。"
            runtimeManifest.featuredApps.isNotEmpty() -> "常用内容入口已经准备好，可继续浏览节目或直接发起语音交互。"
            else -> "首页已准备就绪，可继续使用语音交互和快捷入口。"
        }
    }

    private fun buildWifiGuide(snapshot: HomeNetworkSnapshot): String {
        return when {
            snapshot.currentSsid != null -> "当前识别到 ${snapshot.currentSsid}，可继续连接或切换到其他网络。"
            snapshot.canReadWifiList -> "请选择左侧 Wi-Fi，连接成功后首页会自动回到在线版式。"
            else -> "当前应用拿不到可见 Wi-Fi 列表，请直接从右侧进入系统网络设置。"
        }
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
        )
    }

    private fun buildWifiLabel(snapshot: HomeNetworkSnapshot): String {
        return when {
            snapshot.isConnected && snapshot.currentSsid != null -> "网络已连"
            snapshot.isConnected && snapshot.transport == "ethernet" -> "网络已连"
            snapshot.isConnected -> "网络已连"
            snapshot.currentSsid != null -> "未联网"
            else -> "未联网"
        }
    }

    private fun resolveInstallState(
        installed: Boolean,
        downloadState: StoredAppDownloadState?,
    ): FeaturedAppInstallState {
        if (installed) {
            return FeaturedAppInstallState.INSTALLED
        }
        return when (downloadState?.status?.trim()?.lowercase()) {
            "queued" -> FeaturedAppInstallState.QUEUED
            "downloading",
            "downloaded",
            -> FeaturedAppInstallState.DOWNLOADING

            "paused" -> FeaturedAppInstallState.PAUSED
            "verifying" -> FeaturedAppInstallState.VERIFYING
            "ready_to_install" -> FeaturedAppInstallState.READY_TO_INSTALL
            "failed" -> FeaturedAppInstallState.FAILED
            else -> FeaturedAppInstallState.NOT_INSTALLED
        }
    }

    private fun buildFeaturedStatusLabel(
        installState: FeaturedAppInstallState,
        requiresEntitlement: Boolean,
        downloadState: StoredAppDownloadState?,
    ): String {
        return when (installState) {
            FeaturedAppInstallState.INSTALLED ->
                if (requiresEntitlement) "已安装 / 需授权" else "已安装"

            FeaturedAppInstallState.QUEUED -> "排队下载"
            FeaturedAppInstallState.DOWNLOADING -> downloadState.progressStatusLabel(prefix = "下载中")
            FeaturedAppInstallState.PAUSED -> "下载已暂停"
            FeaturedAppInstallState.VERIFYING -> "校验中"
            FeaturedAppInstallState.READY_TO_INSTALL -> "待安装"
            FeaturedAppInstallState.FAILED -> {
                if (downloadState?.errorMessage?.isNotBlank() == true) {
                    "下载失败"
                } else {
                    "同步失败"
                }
            }

            FeaturedAppInstallState.NOT_INSTALLED ->
                if (requiresEntitlement) "需授权" else "未安装"
        }
    }

    private fun buildFeaturedActionLabel(
        installState: FeaturedAppInstallState,
        requiresEntitlement: Boolean,
        installMode: String,
        downloadState: StoredAppDownloadState?,
    ): String {
        return when (installState) {
            FeaturedAppInstallState.INSTALLED ->
                if (requiresEntitlement) "按确定查看授权状态" else "按确定键打开"

            FeaturedAppInstallState.QUEUED,
            -> downloadState.queueActionLabel(defaultLabel = "等待系统开始下载")

            FeaturedAppInstallState.DOWNLOADING -> downloadState.progressActionLabel(defaultLabel = "后台下载中")
            FeaturedAppInstallState.PAUSED -> {
                downloadState?.downloadDetailMessage
                    ?.takeIf(String::isNotBlank)
                    ?: "等待系统恢复下载"
            }

            FeaturedAppInstallState.VERIFYING -> "校验完成后可安装"
            FeaturedAppInstallState.READY_TO_INSTALL -> "按确定安装"
            FeaturedAppInstallState.FAILED -> "按确定重试下载"

            FeaturedAppInstallState.NOT_INSTALLED -> when (installMode.trim().lowercase()) {
                "auto" -> "等待后台下发"
                "prompt" -> "设备里还没装"
                else -> if (requiresEntitlement) "需授权后继续" else "设备里还没装"
            }
        }
    }

    private fun StoredAppDownloadState?.queueActionLabel(
        defaultLabel: String,
    ): String {
        return this?.downloadDetailMessage
            ?.takeIf(String::isNotBlank)
            ?: defaultLabel
    }

    private fun StoredAppDownloadState?.progressStatusLabel(
        prefix: String,
    ): String {
        val progressPercent = this.downloadPercent() ?: return prefix
        return "$prefix $progressPercent%"
    }

    private fun StoredAppDownloadState?.progressActionLabel(
        defaultLabel: String,
    ): String {
        val progressPercent = this.downloadPercent()
        return when {
            progressPercent != null -> "已下载 $progressPercent%"
            !this?.downloadDetailMessage.isNullOrBlank() -> this?.downloadDetailMessage.orEmpty()
            else -> defaultLabel
        }
    }

    private fun StoredAppDownloadState?.downloadPercent(): Int? {
        val downloadedBytes = this?.downloadedBytes?.takeIf { it >= 0L } ?: return null
        val totalBytes = this.totalBytes?.takeIf { it > 0L } ?: return null
        return ((downloadedBytes * 100) / totalBytes)
            .coerceIn(0L, 100L)
            .toInt()
    }

    private fun buildInstallNotice(
        featuredApps: List<FeaturedAppItem>,
    ): Pair<String, String>? {
        val readyApps = featuredApps.filter { it.installState == FeaturedAppInstallState.READY_TO_INSTALL }
        if (readyApps.isEmpty()) {
            return null
        }
        return if (readyApps.size == 1) {
            "${readyApps.first().title} 已下载完成" to "安装包已经校验通过，按确定可直接拉起系统安装提示。"
        } else {
            "有 ${readyApps.size} 个应用待安装" to "首页内容位里已经有安装包校验完成的应用，按确定可逐个拉起系统安装提示。"
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

    private fun buildAccessUi(
        entitlementSummary: ResolvedEntitlementSummary?,
        resourceSession: ResolvedResourceSession?,
    ): AccessUiSummary {
        val effectiveEntitlement = entitlementSummary ?: resourceSession?.entitlementSummary
        val entitlementUi = buildEntitlementUi(effectiveEntitlement)
        val resourceUi = buildResourceSessionUi(resourceSession)
        val dominantUi = if (toneSeverity(resourceUi.tone) >= toneSeverity(entitlementUi.tone)) {
            resourceUi
        } else {
            entitlementUi
        }
        return AccessUiSummary(
            tokenLabel = entitlementUi.tokenLabel,
            modeLabel = dominantUi.modeLabel,
            hintText = dominantUi.hintText,
            tone = combineTones(entitlementUi.tone, resourceUi.tone),
            notice = dominantUi.notice ?: entitlementUi.notice ?: resourceUi.notice,
        )
    }

    private fun buildEntitlementUi(
        summary: ResolvedEntitlementSummary?,
    ): AccessUiSummary {
        val paymentState = summary?.paymentState ?: return AccessUiSummary()
        return when (paymentState) {
            "free" -> AccessUiSummary(
                tokenLabel = "免费体验",
            )

            "paid" -> AccessUiSummary(
                tokenLabel = "会员已开通",
            )

            "pending" -> AccessUiSummary(
                tokenLabel = "支付确认中",
                tone = HomeStatusTone.WARNING,
                hintText = "支付状态还在确认中，资源排队和授权可能会延后。",
                notice = "支付状态待确认" to "支付摘要还在同步中，首页仍可继续浏览，但资源申请和授权可能需要稍后重试。",
            )

            "grace_period" -> AccessUiSummary(
                tokenLabel = "宽限期",
                tone = HomeStatusTone.WARNING,
                hintText = "账号摘要显示当前处于宽限期，建议尽快完成续费。",
                notice = "账号处于宽限期" to "当前仍可进入首页壳层，但后台可能随时收紧资源分配，建议尽快完成续费确认。",
            )

            "suspended" -> AccessUiSummary(
                tokenLabel = "服务受限",
                modeLabel = "在线受限",
                tone = HomeStatusTone.CRITICAL,
                hintText = "账号摘要显示当前服务受限，首页仍可浏览，但资源申请和授权暂不可用。",
                notice = "账号状态受限" to "支付摘要显示当前服务暂不可用。首页仍可继续浏览基础入口，但资源申请和授权需要等待账号恢复。",
            )

            else -> AccessUiSummary()
        }
    }

    private fun buildResourceSessionUi(
        session: ResolvedResourceSession?,
    ): AccessUiSummary {
        val queueStatus = session?.queueStatus ?: return AccessUiSummary()
        return when (queueStatus) {
            "queued" -> {
                val queueSummary = buildQueueSummary(session)
                AccessUiSummary(
                    modeLabel = "在线排队中",
                    tone = HomeStatusTone.WARNING,
                    hintText = queueSummary,
                    notice = "资源排队中" to "$queueSummary 首页仍可继续浏览其他入口。",
                )
            }

            "allocating" -> AccessUiSummary(
                modeLabel = "在线准备中",
                tone = HomeStatusTone.WARNING,
                hintText = "资源正在准备，首页可先继续浏览。",
                notice = "资源准备中" to "系统正在把本次运行需要的资源切到当前电视，首页仍可继续浏览其他入口。",
            )

            "granted" -> AccessUiSummary(
                hintText = "资源已就绪，可继续使用 AI 交互。",
                tone = HomeStatusTone.SUCCESS,
            )

            "degraded" -> AccessUiSummary(
                modeLabel = "在线受限",
                tone = HomeStatusTone.WARNING,
                hintText = "资源状态已降级，首页仍可继续浏览。",
                notice = "资源状态已降级" to "当前资源状态不完整，首页仍可继续浏览，但 AI 交互和授权能力可能临时受限。",
            )

            "expired" -> AccessUiSummary(
                modeLabel = "在线受限",
                tone = HomeStatusTone.WARNING,
                hintText = "资源使用时段已过期，系统会等待下一次分配。",
                notice = "资源状态已过期" to "当前分配给这台电视的资源已经过期，首页仍可继续浏览，后台会等待下一次可用分配。",
            )

            "rejected" -> AccessUiSummary(
                modeLabel = "在线受限",
                tone = HomeStatusTone.CRITICAL,
                hintText = "当前资源申请未通过，首页仍可继续浏览其他内容。",
                notice = "当前无法获取资源" to "本次资源申请没有通过，首页壳层仍可继续使用，但 AI 交互和授权能力暂不可用。",
            )

            else -> AccessUiSummary()
        }
    }

    private fun buildQueueSummary(session: ResolvedResourceSession): String {
        val queuePosition = session.queuePosition
        val estimatedWaitSeconds = session.estimatedWaitSeconds
        return when {
            queuePosition != null && estimatedWaitSeconds != null ->
                "资源排队中，前面还有 $queuePosition 台设备，预计 $estimatedWaitSeconds 秒。"
            queuePosition != null ->
                "资源排队中，前面还有 $queuePosition 台设备。"
            estimatedWaitSeconds != null ->
                "资源排队中，预计还要等待 $estimatedWaitSeconds 秒。"
            else -> "资源排队中，系统正在等待可用分配。"
        }
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
        vararg notices: PrioritizedNotice?,
    ): Pair<String, String>? {
        val resolvedNotices = notices
            .filterNotNull()
            .sortedByDescending { it.priority }
        if (resolvedNotices.isEmpty()) {
            return null
        }
        val primary = resolvedNotices.first()
        val mergedBodies = resolvedNotices
            .filter { notice ->
                notice == primary ||
                    (primary.mergeGroup != null && primary.mergeGroup == notice.mergeGroup)
            }
            .joinToString(separator = " ") { it.body }
        return primary.title to mergedBodies
    }

    private fun buildUpgradeSuccessNotice(version: String): Pair<String, String> {
        return "已升级到 $version" to "客户端已经完成版本切换，当前已按新版本重新启动。"
    }

    private fun runtimeNoticeMergeGroup(
        bootstrapState: BootstrapRuntimeState?,
    ): NoticeMergeGroup? {
        return if (bootstrapState?.upgradeStatus?.state == RuntimeUpgradeState.AVAILABLE) {
            NoticeMergeGroup.UPGRADE
        } else {
            null
        }
    }

    private fun combineTones(
        primary: HomeStatusTone,
        secondary: HomeStatusTone,
    ): HomeStatusTone {
        return if (toneSeverity(primary) >= toneSeverity(secondary)) primary else secondary
    }

    private fun toneSeverity(tone: HomeStatusTone): Int {
        return when (tone) {
            HomeStatusTone.NEUTRAL -> 0
            HomeStatusTone.SUCCESS -> 1
            HomeStatusTone.WARNING -> 2
            HomeStatusTone.CRITICAL -> 3
        }
    }

    private fun noticePriority(
        tone: HomeStatusTone,
        base: Int,
    ): Int {
        return base + toneSeverity(tone)
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
                        repository = null,
                        tvHomeConfigStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreTvHomeConfigStore) else null,
                        runtimeManifestStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreRuntimeManifestStore) else null,
                        entitlementStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreEntitlementStore) else null,
                        resourceSessionStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreResourceSessionStore) else null,
                        appDownloadStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreAppDownloadStore) else null,
                        upgradeStateStore = applicationContext?.let(::DataStoreUpgradeStateStore),
                    ) as T
                }
            }
        }
    }
}

private data class RuntimeUiSummary(
    val label: String,
    val tone: HomeStatusTone,
    val notice: Pair<String, String>? = null,
)

private data class AccessUiSummary(
    val tokenLabel: String = "服务中心",
    val modeLabel: String? = null,
    val hintText: String? = null,
    val tone: HomeStatusTone = HomeStatusTone.NEUTRAL,
    val notice: Pair<String, String>? = null,
)

private data class PrioritizedNotice(
    val priority: Int,
    val title: String,
    val body: String,
    val mergeGroup: NoticeMergeGroup? = null,
)

private enum class NoticeMergeGroup {
    UPGRADE,
}

private fun Pair<String, String>.toPrioritizedNotice(
    priority: Int,
    mergeGroup: NoticeMergeGroup? = null,
): PrioritizedNotice {
    return PrioritizedNotice(
        priority = priority,
        title = first,
        body = second,
        mergeGroup = mergeGroup,
    )
}
