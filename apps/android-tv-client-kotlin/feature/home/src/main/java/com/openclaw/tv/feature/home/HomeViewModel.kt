package com.openclaw.tv.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openclaw.tv.core.capability.CapabilitySnapshot
import com.openclaw.tv.core.network.OkHttpPlatformApi
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeaturedAppItem(
    val appId: String,
    val title: String,
    val packageName: String,
    val downloadUrl: String = "",
    val sha256: String = "",
    val versionCode: Long = 0L,
    val versionName: String = "",
    val installMode: String = "",
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
    val isInstallShortcut: Boolean = false,
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

data class ServicePackageItem(
    val sku: String,
    val title: String,
    val durationLabel: String,
    val amountLabel: String,
    val statusLabel: String,
    val qrCodeUrl: String,
    val loading: Boolean,
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
    val aiEntryLabel: String,
    val aiEntryMessage: String,
    val aiEntryAvailable: Boolean,
    val modelRenewalPaymentVisible: Boolean,
    val modelRenewalPaymentTitle: String,
    val modelRenewalPaymentAmountLabel: String,
    val modelRenewalPaymentStatusLabel: String,
    val modelRenewalPaymentQrCodeUrl: String,
    val servicePackages: List<ServicePackageItem>,
    val heroDialogue: String,
    val heroHint: String,
    val heroAds: List<HeroAdItem>,
    val assistantSpriteState: AssistantSpriteState,
    val noticeVisible: Boolean,
    val noticeTitle: String,
    val noticeBody: String,
    val statusTone: HomeStatusTone,
    val featuredSectionTitle: String,
    val featuredVisible: Boolean,
    val featuredApps: List<FeaturedAppItem>,
    val castStandbyVisible: Boolean,
    val castStandbyTitle: String,
    val castStandbyNetworkHint: String,
    val castStandbyProtocolSummary: String,
    val castStandbyActionLabel: String,
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
    private val modelRenewalPaymentRepository: HomeModelRenewalPaymentRepository? = null,
    private val upgradeStateStore: UpgradeStateStore? = null,
    private val runtimePresenter: HomeRuntimePresenter = HomeRuntimePresenter(),
) : ViewModel() {

    private var hasLoadedRemoteConfig = false
    private var hasReceivedNetworkSnapshot = false
    private var latestCapabilities: CapabilitySnapshot? = null
    private var latestBootstrapState: BootstrapRuntimeState? = null
    private var latestNetworkSnapshot = HomeNetworkSnapshot.fallback
    private var latestInstalledLaunchableApps: List<InstalledLaunchableAppItem> = emptyList()
    private var latestAppDownloads: Map<String, StoredAppDownloadState> = emptyMap()
    private var resolvedConfig = TvHomeRepository.fallback()
    private var resolvedRuntimeManifest = manifestRepository?.fallback() ?: runtimePresenter.fallbackRuntimeManifest()
    private var startupRuntimeManifestLocked = false
    private var startupRuntimeManifest: ResolvedRuntimeManifest? = null
    private var resolvedEntitlementSummary: ResolvedEntitlementSummary? = null
    private var resolvedResourceSession: ResolvedResourceSession? = null
    private var latestDlnaRendererActive = false
    private var latestDlnaRendererError: String? = null
    private var latestUpgradeNotice: Pair<String, String>? = null
    private var activeManifestSessionToken: String? = null
    private var activeEntitlementSessionToken: String? = null
    private var activeResourceSessionToken: String? = null
    private var activeModelRenewalPaymentSessionToken: String? = null
    private val activeModelRenewalPaymentOrders = mutableMapOf<String, ResolvedModelRenewalPaymentOrder>()
    private val modelRenewalPaymentLoadingSkus = mutableSetOf<String>()
    private val modelRenewalPaymentErrorMessages = mutableMapOf<String, String>()
    private val modelRenewalPaymentPollingJobs = mutableMapOf<String, Job>()
    private val _uiState = MutableStateFlow(
        defaultState(
            snapshot = latestCapabilities,
            config = resolvedConfig,
            runtimeManifest = visibleRuntimeManifest(),
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
        } else {
            clearModelRenewalPaymentForChangedSession(state.session?.sessionToken)
        }
        maybeLoadRuntimeManifest(state)
        maybeLoadEntitlementSummary(state)
        maybeLoadResourceSession(state)
        refreshState()
    }

    internal fun bindNetworkSnapshot(snapshot: HomeNetworkSnapshot) {
        hasReceivedNetworkSnapshot = true
        latestNetworkSnapshot = snapshot
        maybeLockStartupRuntimeManifestFromStoredManifest()
        refreshState()
    }

    internal fun bindInstalledLaunchableApps(apps: List<InstalledLaunchableAppItem>) {
        latestInstalledLaunchableApps = apps
            .filter { app -> app.packageName.isNotBlank() }
            .distinctBy { app -> app.packageName }
        refreshState()
    }

    internal fun bindCastReceiverState(
        active: Boolean,
        errorMessage: String?,
    ) {
        latestDlnaRendererActive = active
        latestDlnaRendererError = errorMessage?.trim()?.takeIf { it.isNotBlank() }
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

    fun requestServiceCenterPayments(): Boolean {
        val activeRepository = modelRenewalPaymentRepository ?: return false
        val sessionToken = latestBootstrapState?.session?.sessionToken?.takeIf(String::isNotBlank) ?: return false
        activeModelRenewalPaymentSessionToken = sessionToken
        val effectiveEntitlement = effectiveEntitlementSummary()
        for (paymentPackage in SERVICE_PACKAGE_DEFINITIONS) {
            if (isServicePackageActive(paymentPackage, effectiveEntitlement)) {
                clearModelRenewalPaymentState(paymentPackage.sku)
                continue
            }
            requestModelRenewalPayment(
                paymentPackage = paymentPackage,
                repository = activeRepository,
                sessionToken = sessionToken,
            )
        }
        return true
    }

    fun requestModelRenewalPayment(): Boolean {
        return requestServiceCenterPayments()
    }

    fun requestServicePackagePayment(sku: String): Boolean {
        val activeRepository = modelRenewalPaymentRepository ?: return false
        val sessionToken = latestBootstrapState?.session?.sessionToken?.takeIf(String::isNotBlank) ?: return false
        val paymentPackage = SERVICE_PACKAGE_DEFINITIONS.find { it.sku == sku } ?: return false
        activeModelRenewalPaymentSessionToken = sessionToken
        requestModelRenewalPayment(
            paymentPackage = paymentPackage,
            repository = activeRepository,
            sessionToken = sessionToken,
            forceNewOrder = true,
        )
        return true
    }

    private fun requestModelRenewalPayment(
        paymentPackage: ServicePackageDefinition,
        repository: HomeModelRenewalPaymentRepository,
        sessionToken: String,
        forceNewOrder: Boolean = false,
    ) {
        val currentOrder = activeModelRenewalPaymentOrders[paymentPackage.sku]
        if (currentOrder != null &&
            currentOrder.paymentState in MODEL_RENEWAL_PAYMENT_POLLING_STATES &&
            currentOrder.qrCodeUrl.isNotBlank() &&
            !forceNewOrder
        ) {
            startModelRenewalPaymentPolling(
                paymentPackage = paymentPackage,
                repository = repository,
                sessionToken = sessionToken,
                orderId = currentOrder.orderId,
            )
            refreshState()
            return
        }
        if (paymentPackage.sku in modelRenewalPaymentLoadingSkus) {
            return
        }
        modelRenewalPaymentLoadingSkus += paymentPackage.sku
        modelRenewalPaymentErrorMessages -= paymentPackage.sku
        refreshState()
        viewModelScope.launch {
            val order = repository.createOrder(
                sessionToken = sessionToken,
                sku = paymentPackage.sku,
            )
            modelRenewalPaymentLoadingSkus -= paymentPackage.sku
            if (order == null || order.orderId.isBlank() || order.qrCodeUrl.isBlank()) {
                modelRenewalPaymentErrorMessages[paymentPackage.sku] = "暂时无法生成微信支付二维码，请稍后重试。"
            } else {
                applyModelRenewalPaymentOrder(paymentPackage.sku, order)
                startModelRenewalPaymentPolling(
                    paymentPackage = paymentPackage,
                    repository = repository,
                    sessionToken = sessionToken,
                    orderId = order.orderId,
                )
            }
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
            runtimeManifest = visibleRuntimeManifest(),
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
                    maybeLockStartupRuntimeManifestFromStoredManifest()
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
        val installedLaunchablePackageNames = latestInstalledLaunchableApps
            .mapTo(mutableSetOf()) { app -> app.packageName }
        val manifestFeaturedApps = runtimeManifest.featuredApps.map { app ->
            val installed = snapshot?.isAppInstalled(app.packageName) == true ||
                app.packageName in installedLaunchablePackageNames
            val downloadState = latestAppDownloads[app.appId]
            val installState = resolveInstallState(
                installed = installed,
                downloadState = downloadState,
            )
            FeaturedAppItem(
                appId = app.appId,
                title = app.title,
                packageName = app.packageName,
                downloadUrl = app.downloadUrl,
                sha256 = app.sha256,
                versionCode = app.versionCode,
                versionName = app.versionName,
                installMode = app.installMode,
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
                    canRequestDownload = app.canRequestDownload(),
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
        val contentFeaturedApps = fillEmptyFeaturedSlots(manifestFeaturedApps)
        val featuredApps = appendInstallShortcut(contentFeaturedApps)
        val hasInstalledFeatured = contentFeaturedApps.any { it.installed }
        val accessUi = buildAccessUi(entitlementSummary, resourceSession)
        val isOnline = networkSnapshot.isConnected
        val runtimeUi = resolveRuntimeUiForPresentation(
            runtimeUi = buildRuntimeUi(bootstrapState),
            runtimeManifest = runtimeManifest,
            bootstrapState = bootstrapState,
            accessUi = accessUi,
            isOnline = isOnline,
        )
        val aiEntry = buildAiEntryState(
            isOnline = isOnline,
            entitlementSummary = entitlementSummary,
            resourceSession = resourceSession,
        )
        val servicePackages = buildServicePackageItems(
            entitlementSummary = entitlementSummary,
            resourceSession = resourceSession,
        )
        val configNotice = buildContentNotice(runtimeManifest, contentFeaturedApps)
        val installNotice = buildInstallNotice(contentFeaturedApps)
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

        val heroDialogue = buildHeroDialogue(
            isOnline = isOnline,
            networkSnapshot = networkSnapshot,
        )
        val heroAds = runtimeManifest.heroAds

        return HomeUiState(
            surfaceMode = if (isOnline) HomeSurfaceMode.ONLINE else HomeSurfaceMode.OFFLINE,
            brandTitle = "RS AITV",
            wifiLabel = buildWifiLabel(networkSnapshot),
            wifiConnected = isOnline,
            modeLabel = buildModeLabel(isOnline = isOnline, runtimeUi = runtimeUi, accessUi = accessUi),
            tokenLabel = accessUi.tokenLabel,
            aiEntryLabel = aiEntry.label,
            aiEntryMessage = aiEntry.message,
            aiEntryAvailable = aiEntry.available,
            modelRenewalPaymentVisible = false,
            modelRenewalPaymentTitle = "",
            modelRenewalPaymentAmountLabel = "",
            modelRenewalPaymentStatusLabel = "",
            modelRenewalPaymentQrCodeUrl = "",
            servicePackages = servicePackages,
            heroDialogue = heroDialogue,
            heroHint = buildHeroHint(
                isOnline = isOnline,
                config = config,
                runtimeManifest = runtimeManifest,
                runtimeUi = runtimeUi,
                accessUi = accessUi,
                networkSnapshot = networkSnapshot,
            ),
            heroAds = heroAds,
            assistantSpriteState = resolveAssistantSpriteState(
                isOnline = isOnline,
                statusTone = if (isOnline) resolvedTone else HomeStatusTone.NEUTRAL,
                heroAds = heroAds,
            ),
            noticeVisible = isOnline && notice != null,
            noticeTitle = notice?.first.orEmpty(),
            noticeBody = notice?.second.orEmpty(),
            statusTone = if (isOnline) resolvedTone else HomeStatusTone.NEUTRAL,
            featuredSectionTitle = "内容入口",
            featuredVisible = isOnline && featuredApps.isNotEmpty(),
            featuredApps = featuredApps,
            castStandbyVisible = false,
            castStandbyTitle = "投屏",
            castStandbyNetworkHint = buildCastStandbyNetworkHint(networkSnapshot),
            castStandbyProtocolSummary = buildCastStandbyProtocolSummary(
                isOnline = isOnline,
                dlnaActive = latestDlnaRendererActive,
                dlnaError = latestDlnaRendererError,
            ),
            castStandbyActionLabel = "统一连接",
            wifiSectionTitle = "选择 Wi-Fi 网络",
            wifiSectionVisible = !isOnline,
            wifiGuideText = buildWifiGuide(networkSnapshot),
            wifiNetworks = buildWifiNetworks(networkSnapshot),
            wifiEmptyText = buildWifiEmptyText(networkSnapshot),
            quickActionSectionTitle = "快捷入口",
            quickActions = buildQuickActions(
                networkSnapshot = networkSnapshot,
                hasInstalledFeatured = hasInstalledFeatured,
            ),
        )
    }

    private fun fillEmptyFeaturedSlots(
        featuredApps: List<FeaturedAppItem>,
    ): List<FeaturedAppItem> {
        if (latestInstalledLaunchableApps.isEmpty()) {
            return featuredApps
        }
        val usedPackageNames = featuredApps
            .filter { app -> app.installed }
            .mapTo(mutableSetOf()) { app -> app.packageName }
        val replacements = latestInstalledLaunchableApps
            .asSequence()
            .filterNot { app -> app.packageName in usedPackageNames }
            .iterator()
        return featuredApps.map { app ->
            if (app.installed || !replacements.hasNext()) {
                app
            } else {
                val replacement = replacements.next()
                usedPackageNames += replacement.packageName
                replacement.toFeaturedAppItem()
            }
        }
    }

    private fun appendInstallShortcut(
        featuredApps: List<FeaturedAppItem>,
    ): List<FeaturedAppItem> {
        return featuredApps + FeaturedAppItem(
            appId = INSTALL_SHORTCUT_APP_ID,
            title = "安装应用",
            packageName = "",
            summary = "从 U 盘或本机文件选择 APK",
            installed = false,
            installState = FeaturedAppInstallState.NOT_INSTALLED,
            monogram = "+",
            accentColorHex = "#5FB8FF",
            statusLabel = "添加",
            actionLabel = "选择 APK",
            isInstallShortcut = true,
        )
    }

    private fun InstalledLaunchableAppItem.toFeaturedAppItem(): FeaturedAppItem {
        val decoration = HomeAppCatalog.decorationFor(packageName = packageName)
        val resolvedAppId = "installed:$packageName"
        return FeaturedAppItem(
            appId = resolvedAppId,
            title = title,
            packageName = packageName,
            summary = summary,
            installed = true,
            installState = FeaturedAppInstallState.INSTALLED,
            monogram = decoration?.monogram ?: HomeAppCatalog.fallbackMonogram(title, packageName),
            accentColorHex = decoration?.accentColorHex ?: HomeAppCatalog.fallbackAccentColor(resolvedAppId, packageName),
            statusLabel = "已安装",
            actionLabel = "按确定键打开",
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
            lockStartupRuntimeManifest(resolvedRuntimeManifest)
            refreshState()
        }
    }

    private fun maybeLockStartupRuntimeManifestFromStoredManifest() {
        if (startupRuntimeManifestLocked || !hasReceivedNetworkSnapshot) {
            return
        }
        if (resolvedRuntimeManifest.source == RuntimeManifestSource.FALLBACK) {
            return
        }
        val shouldUseStoredManifest = manifestRepository == null || !latestNetworkSnapshot.isConnected
        if (!shouldUseStoredManifest) {
            return
        }
        lockStartupRuntimeManifest(resolvedRuntimeManifest)
    }

    private fun lockStartupRuntimeManifest(manifest: ResolvedRuntimeManifest) {
        if (manifest.source == RuntimeManifestSource.FALLBACK) {
            return
        }
        val existingStartupManifest = startupRuntimeManifest
        if (
            startupRuntimeManifestLocked &&
            !shouldReplaceStartupRuntimeManifest(existingStartupManifest, manifest)
        ) {
            return
        }
        startupRuntimeManifest = manifest.copy(
            featuredApps = manifest.featuredApps.distinctBy { it.appId },
            ignoredFeaturedAppIds = manifest.ignoredFeaturedAppIds.distinct(),
            heroAds = manifest.heroAds.distinctBy { it.creativeId },
        )
        startupRuntimeManifestLocked = true
    }

    private fun shouldReplaceStartupRuntimeManifest(
        existing: ResolvedRuntimeManifest?,
        candidate: ResolvedRuntimeManifest,
    ): Boolean {
        return existing?.source == RuntimeManifestSource.CACHE &&
            candidate.source == RuntimeManifestSource.REMOTE
    }

    private fun visibleRuntimeManifest(): ResolvedRuntimeManifest {
        return startupRuntimeManifest ?: resolvedRuntimeManifest
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

    private fun startModelRenewalPaymentPolling(
        paymentPackage: ServicePackageDefinition,
        repository: HomeModelRenewalPaymentRepository,
        sessionToken: String,
        orderId: String,
    ) {
        if (orderId.isBlank()) {
            return
        }
        val activeJob = modelRenewalPaymentPollingJobs[paymentPackage.sku]
        if (activeJob?.isActive == true &&
            activeModelRenewalPaymentOrders[paymentPackage.sku]?.orderId == orderId
        ) {
            return
        }
        activeJob?.cancel()
        modelRenewalPaymentPollingJobs[paymentPackage.sku] = viewModelScope.launch {
            repeat(MODEL_RENEWAL_PAYMENT_POLL_ATTEMPTS) {
                delay(MODEL_RENEWAL_PAYMENT_POLL_INTERVAL_MILLIS)
                val latestOrder = repository.loadOrder(
                    sessionToken = sessionToken,
                    orderId = orderId,
                )
                if (latestOrder != null) {
                    applyModelRenewalPaymentOrder(paymentPackage.sku, latestOrder)
                    refreshState()
                    if (latestOrder.paymentState !in MODEL_RENEWAL_PAYMENT_POLLING_STATES) {
                        return@launch
                    }
                }
            }
        }
    }

    private fun applyModelRenewalPaymentOrder(
        packageSku: String,
        order: ResolvedModelRenewalPaymentOrder,
    ) {
        activeModelRenewalPaymentOrders[packageSku] = order
        modelRenewalPaymentErrorMessages -= packageSku
        if (shouldApplyOrderEntitlementSummary(order.entitlementSummary)) {
            resolvedEntitlementSummary = order.entitlementSummary
        }
        if (order.paymentState == "paid") {
            activeResourceSessionToken = null
            latestBootstrapState?.let(::maybeLoadResourceSession)
        }
    }

    private fun shouldApplyOrderEntitlementSummary(
        orderEntitlementSummary: ResolvedEntitlementSummary,
    ): Boolean {
        val currentEntitlementSummary = effectiveEntitlementSummary() ?: return true
        val currentPaidActive = isPaidActiveEntitlement(currentEntitlementSummary)
        val orderPaidActive = isPaidActiveEntitlement(orderEntitlementSummary)
        return !currentPaidActive || orderPaidActive
    }

    private fun clearModelRenewalPaymentForChangedSession(sessionToken: String?) {
        val normalizedSessionToken = sessionToken?.takeIf(String::isNotBlank) ?: return
        val activeSessionToken = activeModelRenewalPaymentSessionToken ?: return
        if (activeSessionToken == normalizedSessionToken) {
            return
        }
        clearModelRenewalPaymentState()
    }

    private fun clearModelRenewalPaymentState() {
        modelRenewalPaymentPollingJobs.values.forEach(Job::cancel)
        modelRenewalPaymentPollingJobs.clear()
        activeModelRenewalPaymentSessionToken = null
        activeModelRenewalPaymentOrders.clear()
        modelRenewalPaymentLoadingSkus.clear()
        modelRenewalPaymentErrorMessages.clear()
    }

    private fun clearModelRenewalPaymentState(packageSku: String) {
        modelRenewalPaymentPollingJobs.remove(packageSku)?.cancel()
        activeModelRenewalPaymentOrders.remove(packageSku)
        modelRenewalPaymentLoadingSkus.remove(packageSku)
        modelRenewalPaymentErrorMessages.remove(packageSku)
    }

    private fun resetRuntimeSessionTracking() {
        activeManifestSessionToken = null
        activeEntitlementSessionToken = null
        activeResourceSessionToken = null
    }

    private fun clearSessionScopedAccessState() {
        resolvedEntitlementSummary = null
        resolvedResourceSession = null
        clearModelRenewalPaymentState()
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

    private fun resolveAssistantSpriteState(
        isOnline: Boolean,
        statusTone: HomeStatusTone,
        heroAds: List<HeroAdItem>,
    ): AssistantSpriteState {
        if (!isOnline) {
            return AssistantSpriteState.GUIDE
        }
        return when {
            statusTone == HomeStatusTone.CRITICAL -> AssistantSpriteState.WORRIED
            statusTone == HomeStatusTone.WARNING -> AssistantSpriteState.THINK
            heroAds.isNotEmpty() -> AssistantSpriteState.POINT_LEFT
            else -> AssistantSpriteState.SUMMER_IDLE
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

    private fun buildCastStandbyNetworkHint(snapshot: HomeNetworkSnapshot): String {
        val wifiName = snapshot.unifiedWifiDisplayName()
        return when {
            snapshot.isConnected -> "统一连接「$wifiName」：手机和 TV 保持同一 Wi-Fi 后，在手机投屏列表中查找这台 TV。"
            else -> "先让 TV 连接 Wi-Fi，联网后统一连接「当前 Wi-Fi」即可投屏。"
        }
    }

    private fun buildCastStandbyProtocolSummary(
        isOnline: Boolean,
        dlnaActive: Boolean,
        dlnaError: String?,
    ): String {
        return when {
            !isOnline -> "联网后启用自建投屏；手机和 TV 需要处在同一局域网。"
            dlnaActive -> "自建投屏接收已待机；若手机端无法发现，可打开乐播投屏兜底。"
            dlnaError != null -> "自建投屏暂不可用；可先打开乐播投屏兜底。"
            else -> "正在启动自建投屏接收；若手机端暂未发现，可打开乐播投屏兜底。"
        }
    }

    private fun buildQuickActions(
        networkSnapshot: HomeNetworkSnapshot,
        hasInstalledFeatured: Boolean,
    ): List<QuickActionItem> {
        val isOnline = networkSnapshot.isConnected
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
                summary = if (isOnline) {
                    "连「${networkSnapshot.unifiedWifiDisplayName()}」"
                } else {
                    "联网后显示当前 Wi-Fi"
                },
                actionLabel = "统一连接",
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

    private fun HomeNetworkSnapshot.unifiedWifiDisplayName(): String {
        return currentSsid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "当前 Wi-Fi"
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
        canRequestDownload: Boolean,
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
                "prompt" -> if (canRequestDownload) "按确定下载" else "设备里还没装"
                else -> if (requiresEntitlement) "需授权后继续" else "设备里还没装"
            }
        }
    }

    private fun RuntimeFeaturedApp.canRequestDownload(): Boolean {
        return downloadUrl.isNotBlank() && sha256.isNotBlank() && versionCode > 0L
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
            tokenLabel = "服务中心",
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
                tokenLabel = "续费开通",
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
                tokenLabel = "立即续费",
                tone = HomeStatusTone.WARNING,
                hintText = "账号摘要显示当前处于宽限期，建议尽快完成续费。",
                notice = "账号处于宽限期" to "当前仍可进入首页壳层，但后台可能随时收紧资源分配，建议尽快完成续费确认。",
            )

            "suspended" -> AccessUiSummary(
                tokenLabel = "续费恢复",
                modeLabel = "在线受限",
                tone = HomeStatusTone.CRITICAL,
                hintText = "账号摘要显示当前服务受限，首页仍可浏览，但资源申请和授权暂不可用。",
                notice = "账号状态受限" to "支付摘要显示当前服务暂不可用。首页仍可继续浏览基础入口，但资源申请和授权需要等待账号恢复。",
            )

            else -> AccessUiSummary()
        }
    }

    private fun buildServicePackageItems(
        entitlementSummary: ResolvedEntitlementSummary?,
        resourceSession: ResolvedResourceSession?,
    ): List<ServicePackageItem> {
        val effectiveEntitlement = effectiveEntitlementSummary(
            entitlementSummary = entitlementSummary,
            resourceSession = resourceSession,
        )
        return SERVICE_PACKAGE_DEFINITIONS.map { paymentPackage ->
            val order = activeModelRenewalPaymentOrders[paymentPackage.sku]
            val loading = paymentPackage.sku in modelRenewalPaymentLoadingSkus
            val errorMessage = modelRenewalPaymentErrorMessages[paymentPackage.sku]
            val active = isServicePackageActive(paymentPackage, effectiveEntitlement)
            ServicePackageItem(
                sku = paymentPackage.sku,
                title = order?.title?.takeIf(String::isNotBlank) ?: paymentPackage.title,
                durationLabel = order?.durationLabel?.takeIf(String::isNotBlank) ?: paymentPackage.durationLabel,
                amountLabel = order?.amountDisplay?.takeIf(String::isNotBlank) ?: paymentPackage.amountLabel,
                statusLabel = when {
                    loading -> "正在生成微信支付二维码..."
                    errorMessage != null -> errorMessage
                    active && order == null -> "已生效，点此可继续续费。"
                    order != null -> buildModelRenewalPaymentStatusLabel(order.paymentState)
                    else -> "点此生成微信支付二维码"
                },
                qrCodeUrl = if (loading || (active && order == null)) "" else order?.qrCodeUrl.orEmpty(),
                loading = loading,
            )
        }
    }

    private fun effectiveEntitlementSummary(): ResolvedEntitlementSummary? {
        return effectiveEntitlementSummary(
            entitlementSummary = resolvedEntitlementSummary,
            resourceSession = resolvedResourceSession,
        )
    }

    private fun effectiveEntitlementSummary(
        entitlementSummary: ResolvedEntitlementSummary?,
        resourceSession: ResolvedResourceSession?,
    ): ResolvedEntitlementSummary? {
        val candidates = listOfNotNull(
            entitlementSummary,
            resourceSession?.entitlementSummary,
        )
        return candidates.firstOrNull(::isPaidActiveEntitlement) ?: candidates.firstOrNull()
    }

    private fun isServicePackageActive(
        paymentPackage: ServicePackageDefinition,
        entitlementSummary: ResolvedEntitlementSummary?,
    ): Boolean {
        val activeEntitlement = entitlementSummary ?: return false
        return activeEntitlement.planCode.trim() == paymentPackage.planCode &&
            isPaidActiveEntitlement(activeEntitlement)
    }

    private fun isPaidActiveEntitlement(entitlementSummary: ResolvedEntitlementSummary): Boolean {
        return entitlementSummary.paymentState.normalizedPaymentState() == "paid"
    }

    private fun buildModelRenewalPaymentStatusLabel(paymentState: String): String {
        return when (paymentState.normalizedPaymentState()) {
            "pending" -> "微信扫码支付，支付后会自动刷新。"
            "paid" -> "支付已确认，正在恢复模型资源。"
            "expired" -> "二维码已过期，重新点击续费。"
            "cancelled" -> "支付已取消，可重新发起续费。"
            "failed" -> "支付未完成，可重新发起续费。"
            else -> "请使用微信扫码完成续费。"
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

    private fun buildAiEntryState(
        isOnline: Boolean,
        entitlementSummary: ResolvedEntitlementSummary?,
        resourceSession: ResolvedResourceSession?,
    ): AiEntryState {
        if (!isOnline) {
            return AiEntryState(
                label = "先联网",
                message = "请先完成 Wi-Fi 连接，联网后会继续同步 AI 资源状态。",
                available = false,
            )
        }
        val effectiveEntitlement = entitlementSummary ?: resourceSession?.entitlementSummary
        return when (effectiveEntitlement?.paymentState) {
            "suspended" -> AiEntryState(
                label = "服务受限",
                message = "当前账号摘要显示服务受限，AI 交互暂不可用。",
                available = false,
            )

            "pending" -> AiEntryState(
                label = "支付确认中",
                message = "支付摘要仍在确认中，AI 资源可能稍后才能分配。",
                available = false,
            )

            else -> buildResourceSessionAiEntryState(resourceSession)
        }
    }

    private fun buildResourceSessionAiEntryState(
        session: ResolvedResourceSession?,
    ): AiEntryState {
        val activeSession = session
        return when (activeSession?.queueStatus?.trim()?.lowercase().orEmpty()) {
            "granted" -> {
                if (activeSession?.hasModelLease == true) {
                    AiEntryState(
                        label = "AI 已就绪",
                        message = "AI 资源已就绪，可以开始语音交互。",
                        available = true,
                    )
                } else {
                    AiEntryState(
                        label = "资源准备中",
                        message = "本次资源会话已授予，但模型资源还未就绪，请稍后再试。",
                        available = false,
                    )
                }
            }

            "queued" -> AiEntryState(
                label = "资源排队中",
                message = activeSession?.let(::buildQueueSummary) ?: "资源排队中，系统正在等待可用分配。",
                available = false,
            )

            "allocating" -> AiEntryState(
                label = "资源准备中",
                message = "AI 资源正在分配，首页可先继续浏览其他入口。",
                available = false,
            )

            "degraded" -> AiEntryState(
                label = "资源降级",
                message = "当前资源状态已降级，AI 交互可能临时不可用。",
                available = false,
            )

            "expired" -> AiEntryState(
                label = "资源过期",
                message = "当前资源会话已过期，系统会等待下一次可用分配。",
                available = false,
            )

            "rejected" -> AiEntryState(
                label = "资源不可用",
                message = "本次资源申请未通过，AI 交互暂不可用。",
                available = false,
            )

            else -> AiEntryState(
                label = "资源同步中",
                message = "正在同步本机 AI 资源状态，请稍后再试。",
                available = false,
            )
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
                    append("首页仍可继续使用，后台会自动重试最新策略同步。")
                    runtimeErrorUserHint(state.errorMessage)?.let { append(" $it") }
                },
            )

            BootstrapRuntimePhase.FAILED -> RuntimeUiSummary(
                label = "运行初始化失败",
                tone = HomeStatusTone.CRITICAL,
                notice = "运行初始化失败" to buildString {
                    append("会话、租约和版本策略还没有完成同步。")
                    runtimeErrorUserHint(state.errorMessage)?.let { append(" $it") }
                },
            )
        }
    }

    private fun runtimeErrorUserHint(errorMessage: String?): String? {
        val normalized = errorMessage
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return when {
            normalized.contains("HTTP 503", ignoreCase = true) -> "服务暂时忙，请稍后再试。"
            normalized.contains("timeout", ignoreCase = true) ||
                normalized.contains("timed out", ignoreCase = true) -> "网络响应超时，请稍后再试。"
            normalized.contains("Unable to resolve host", ignoreCase = true) -> "网络解析暂时异常，请检查联网状态。"
            normalized.contains("failed to connect", ignoreCase = true) -> "网络连接暂时异常，请稍后再试。"
            else -> "同步暂时未完成，请稍后再试。"
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
        const val INSTALL_SHORTCUT_APP_ID = "install_shortcut"
        const val SERVICE_PACKAGE_VIP = "openclaw-tv-vip-30d"
        const val SERVICE_PACKAGE_AI = "openclaw-tv-ai-service-30d"
        private const val MODEL_RENEWAL_PAYMENT_POLL_INTERVAL_MILLIS = 3_000L
        private const val MODEL_RENEWAL_PAYMENT_POLL_ATTEMPTS = 300
        private val MODEL_RENEWAL_PAYMENT_POLLING_STATES = setOf("pending", "created")
        private val SERVICE_PACKAGE_DEFINITIONS = listOf(
            ServicePackageDefinition(
                sku = SERVICE_PACKAGE_VIP,
                planCode = "tv-vip-monthly",
                title = "大会员套餐",
                durationLabel = "30天",
                amountLabel = "CNY 0.01",
            ),
            ServicePackageDefinition(
                sku = SERVICE_PACKAGE_AI,
                planCode = "model-renewal-monthly",
                title = "AI服务套餐",
                durationLabel = "30天",
                amountLabel = "CNY 0.01",
            ),
        )

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
                    val platformApi = platformBaseUrl
                        ?.trim()
                        ?.takeIf { enableRemoteConfig && it.isNotBlank() }
                        ?.let(::OkHttpPlatformApi)
                    val viewModel = HomeViewModel(
                        repository = null,
                        tvHomeConfigStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreTvHomeConfigStore) else null,
                        runtimeManifestStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreRuntimeManifestStore) else null,
                        entitlementStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreEntitlementStore) else null,
                        resourceSessionStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreResourceSessionStore) else null,
                        appDownloadStore = if (enableRemoteConfig) applicationContext?.let(::DataStoreAppDownloadStore) else null,
                        modelRenewalPaymentRepository = platformApi?.let(::HomeModelRenewalPaymentRepository),
                        upgradeStateStore = applicationContext?.let(::DataStoreUpgradeStateStore),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return viewModel as T
                }
            }
        }
    }
}

private data class ServicePackageDefinition(
    val sku: String,
    val planCode: String,
    val title: String,
    val durationLabel: String,
    val amountLabel: String,
)

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

private data class AiEntryState(
    val label: String,
    val message: String,
    val available: Boolean,
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
