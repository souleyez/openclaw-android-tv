package com.openclaw.tv.feature.home

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.openclaw.tv.core.capability.CapabilityDetector
import com.openclaw.tv.core.storage.DataStoreAppDownloadStore
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.AppDownloadRequest
import com.openclaw.tv.feature.appdelivery.AppInstallPromptResult
import com.openclaw.tv.feature.appdelivery.AppPackageInstaller
import com.openclaw.tv.feature.appdelivery.DownloadManagerTrackedAppDownloadStatusResolver
import com.openclaw.tv.feature.appdelivery.DownloadManagerAppDownloadEnqueuer
import com.openclaw.tv.feature.appdelivery.FileSha256ChecksumVerifier
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import com.openclaw.tv.feature.cast.CastPlaybackInterrupter
import com.openclaw.tv.feature.cast.DlnaMediaRequest
import com.openclaw.tv.feature.cast.DlnaPlaybackActivity
import com.openclaw.tv.feature.cast.DlnaRendererController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val debugForceOffline by lazy(LazyThreadSafetyMode.NONE) {
        arguments?.getBoolean(ARG_DEBUG_FORCE_OFFLINE) == true
    }

    private val viewModel by viewModels<HomeViewModel> {
        HomeViewModel.factory(
            applicationContext = requireContext().applicationContext,
            platformBaseUrl = arguments?.getString(ARG_PLATFORM_BASE_URL),
            enableRemoteConfig = arguments?.getBoolean(ARG_ENABLE_REMOTE_CONFIG) ?: true,
        )
    }
    private val apkPickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            val data = result.data ?: return@registerForActivityResult
            val apkUri = data.data ?: return@registerForActivityResult
            persistPickedApkPermission(data, apkUri)
            pendingPickedInstallUri = apkUri
            triggerPickedApkInstall(apkUri)
        }
    private val storagePermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (currentLocalAppsVisible) {
                refreshLocalAppsOverlay()
            }
        }
    private val featuredAdapter = AppRailAdapter()
    private val quickActionAdapter = QuickActionAdapter()
    private val wifiAdapter = WifiListAdapter()
    private val localAppsAdapter = InstalledAppsAdapter()
    private var capabilityDetector: CapabilityDetector? = null
    private var appLauncher: AppLauncher? = null
    private var appPackageInstaller: AppPackageInstaller? = null
    private var appDownloadCoordinator: AppDownloadCoordinator? = null
    private var appDownloadStatusResolver: DownloadManagerTrackedAppDownloadStatusResolver? = null
    private var networkSnapshotProvider: HomeNetworkSnapshotProvider? = null
    private var installedAppCatalogProvider: InstalledAppCatalogProvider? = null
    private var dlnaRendererController: DlnaRendererController? = null
    private val featuredRailFocusBridge = RailChildFocusBridge()
    private val wifiRailFocusBridge = RailChildFocusBridge()
    private val quickActionRailFocusBridge = RailChildFocusBridge()
    private val localAppsRailFocusBridge = RailChildFocusBridge()
    private var rootView: View? = null
    private var tokenButtonView: Button? = null
    private var featuredRailView: RecyclerView? = null
    private var wifiListView: RecyclerView? = null
    private var wifiActionCardView: View? = null
    private var wifiSelectedNameView: TextView? = null
    private var wifiSelectedSummaryView: TextView? = null
    private var wifiConnectButtonView: Button? = null
    private var wifiSystemSettingsButtonView: Button? = null
    private var wifiRefreshButtonView: Button? = null
    private var castStandbyButtonView: Button? = null
    private var quickActionRailView: RecyclerView? = null
    private var localAppsOverlayView: View? = null
    private var localAppsCloseButtonView: Button? = null
    private var localAppsEmptyStateView: TextView? = null
    private var localAppsListView: RecyclerView? = null
    private var currentSurfaceMode = HomeSurfaceMode.OFFLINE
    private var currentFeaturedVisible = false
    private var currentCastStandbyVisible = false
    private var currentWifiVisible = false
    private var currentWifiActionVisible = false
    private var currentLocalAppsVisible = false
    private var currentFeaturedCount = 0
    private var currentWifiCount = 0
    private var currentQuickActionCount = 0
    private var currentLocalAppCount = 0
    private var currentSelectedWifiItem: WifiNetworkItem? = null
    private var lastFocusedSection = FocusSection.PRIMARY_CONTENT
    private var lastFeaturedFocusPosition = 0
    private var lastWifiFocusPosition = 0
    private var lastQuickActionFocusPosition = 0
    private var lastLocalAppFocusPosition = 0
    private var hasSavedFocusState = false
    private var hasAppliedInitialFocus = false
    private var heroAdRotationJob: Job? = null
    private var appDownloadRefreshJob: Job? = null
    private var dlnaStartJob: Job? = null
    private var leboDiscoveryStartJob: Job? = null
    private var leboReturnGuardJob: Job? = null
    private var hasAttemptedLeboDiscoveryStart = false
    private var shouldKeepLeboDiscoveryService = false
    private var lastLeboReturnAtMs = 0L
    private var lastLeboFallbackStartedAtMs = 0L
    private var homeStoppedAtMs = 0L
    private var suspendNextLeboReturnOnStop = false
    private var leboReturnSuspendedWhileBackground = false
    private var homeTaskId = -1
    private var hasTrimmedBackgroundPlaybackAppsForCurrentCast = false
    private var capabilityRefreshJob: Job? = null
    private var networkRefreshJob: Job? = null
    private var installedAppsRefreshJob: Job? = null
    private var localAppsRefreshJob: Job? = null
    private var homeBackgroundTrimJob: Job? = null
    private var activeHeroAds: List<HeroAdItem> = emptyList()
    private var currentHeroAdIndex = 0
    private var assistantBaseSpriteState = AssistantSpriteState.IDLE
    private var assistantTalkResetJob: Job? = null
    private var lastHeroDialogueText: String? = null
    private var heroAdFocused = false
    private var pendingInstallRequest: PendingInstallRequest? = null
    private var pendingPickedInstallUri: Uri? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreFocusMemory(savedInstanceState)
        pendingInstallRequest = savedInstanceState?.readPendingInstallRequestState()
        pendingPickedInstallUri = savedInstanceState
            ?.getString(STATE_PENDING_PICKED_INSTALL_URI)
            ?.takeIf(String::isNotBlank)
            ?.let(Uri::parse)
        rootView = view
        val brandTitle = view.findViewById<TextView>(R.id.brand_title)
        val wifiStatusIcon = view.findViewById<ImageView>(R.id.wifi_status_icon)
        val wifiStatusText = view.findViewById<TextView>(R.id.wifi_status_text)
        val heroCard = view.findViewById<View>(R.id.hero_card)
        val heroAdCard = view.findViewById<View>(R.id.hero_ad_card)
        val assistantShadow = view.findViewById<View>(R.id.assistant_shadow)
        val assistantGlow = view.findViewById<View>(R.id.assistant_glow)
        val assistantCharacter = view.findViewById<AssistantSpriteView>(R.id.assistant_character)
        val assistantArtFrame = view.findViewById<View>(R.id.assistant_art_frame)
        val assistantChip = view.findViewById<TextView>(R.id.assistant_chip)
        val modeChip = view.findViewById<TextView>(R.id.mode_chip)
        val heroDialogue = view.findViewById<TextView>(R.id.hero_dialogue)
        val heroHint = view.findViewById<TextView>(R.id.hero_hint)
        val heroAdImage = view.findViewById<ImageView>(R.id.hero_ad_image)
        val heroAdCaption = view.findViewById<TextView>(R.id.hero_ad_caption)
        val heroAdIndex = view.findViewById<TextView>(R.id.hero_ad_index)
        val noticeCard = view.findViewById<LinearLayout>(R.id.notice_card)
        val noticeTitle = view.findViewById<TextView>(R.id.notice_title)
        val noticeBody = view.findViewById<TextView>(R.id.notice_body)
        val tokenButton = view.findViewById<Button>(R.id.token_button)
        val mainContentSection = view.findViewById<LinearLayout>(R.id.main_content_section)
        val featuredSectionTitle = view.findViewById<TextView>(R.id.featured_section_title)
        val featuredRail = view.findViewById<RecyclerView>(R.id.featured_rail)
        val castStandbyCard = view.findViewById<View>(R.id.cast_standby_card)
        val castStandbyTitle = view.findViewById<TextView>(R.id.cast_standby_title)
        val castDeviceName = view.findViewById<TextView>(R.id.cast_device_name)
        val castNetworkHint = view.findViewById<TextView>(R.id.cast_network_hint)
        val castProtocolSummary = view.findViewById<TextView>(R.id.cast_protocol_summary)
        val castStandbyButton = view.findViewById<Button>(R.id.cast_standby_button)
        val wifiSection = view.findViewById<LinearLayout>(R.id.wifi_section)
        val wifiSectionTitle = view.findViewById<TextView>(R.id.wifi_section_title)
        val wifiGuideText = view.findViewById<TextView>(R.id.wifi_guide_text)
        val wifiContentRow = view.findViewById<LinearLayout>(R.id.wifi_content_row)
        val wifiActionCard = view.findViewById<View>(R.id.wifi_action_card)
        val wifiSelectedName = view.findViewById<TextView>(R.id.wifi_selected_name)
        val wifiSelectedSummary = view.findViewById<TextView>(R.id.wifi_selected_summary)
        val wifiConnectButton = view.findViewById<Button>(R.id.wifi_connect_button)
        val wifiSystemSettingsButton = view.findViewById<Button>(R.id.wifi_system_settings_button)
        val wifiRefreshButton = view.findViewById<Button>(R.id.wifi_refresh_button)
        val wifiEmptyState = view.findViewById<TextView>(R.id.wifi_empty_state)
        val wifiList = view.findViewById<RecyclerView>(R.id.wifi_list)
        val quickActionSection = view.findViewById<LinearLayout>(R.id.quick_action_section)
        val quickActionSectionTitle = view.findViewById<TextView>(R.id.quick_action_section_title)
        val quickActionRail = view.findViewById<RecyclerView>(R.id.quick_action_rail)
        val localAppsOverlay = view.findViewById<View>(R.id.local_apps_overlay)
        val localAppsCloseButton = view.findViewById<Button>(R.id.local_apps_close_button)
        val localAppsEmptyState = view.findViewById<TextView>(R.id.local_apps_empty_state)
        val localAppsList = view.findViewById<RecyclerView>(R.id.local_apps_list)

        tokenButtonView = tokenButton
        featuredRailView = featuredRail
        wifiListView = wifiList
        wifiActionCardView = wifiActionCard
        wifiSelectedNameView = wifiSelectedName
        wifiSelectedSummaryView = wifiSelectedSummary
        wifiConnectButtonView = wifiConnectButton
        wifiSystemSettingsButtonView = wifiSystemSettingsButton
        wifiRefreshButtonView = wifiRefreshButton
        castStandbyButtonView = castStandbyButton
        quickActionRailView = quickActionRail
        localAppsOverlayView = localAppsOverlay
        localAppsCloseButtonView = localAppsCloseButton
        localAppsEmptyStateView = localAppsEmptyState
        localAppsListView = localAppsList

        featuredRail.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        featuredRail.adapter = featuredAdapter
        wifiList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        wifiList.adapter = wifiAdapter
        quickActionRail.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        quickActionRail.adapter = quickActionAdapter
        localAppsList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        localAppsList.adapter = localAppsAdapter
        listOf(wifiConnectButton, wifiSystemSettingsButton, wifiRefreshButton, castStandbyButton).forEach { button ->
            button.backgroundTintList = null
        }
        capabilityDetector = CapabilityDetector(requireContext())
        appLauncher = AppLauncher(requireContext())
        appPackageInstaller = AppPackageInstaller(requireContext())
        appDownloadCoordinator = AppDownloadCoordinator(
            downloadStore = DataStoreAppDownloadStore(requireContext()),
            enqueuer = DownloadManagerAppDownloadEnqueuer(requireContext()),
            checksumVerifier = FileSha256ChecksumVerifier(),
        )
        appDownloadStatusResolver = DownloadManagerTrackedAppDownloadStatusResolver(requireContext())
        networkSnapshotProvider = HomeNetworkSnapshotProvider(requireContext())
        installedAppCatalogProvider = InstalledAppCatalogProvider(requireContext())
        dlnaRendererController = DlnaRendererController(
            context = requireContext().applicationContext,
            deviceName = resolveCastDeviceName(),
            onMediaRequest = ::openDlnaMediaRequest,
        )

        configureHeroAdCard(heroAdCard, assistantCharacter)
        featuredAdapter.setOnItemClickListener(::launchFeaturedApp)
        featuredAdapter.setOnItemFocusListener { position, _ ->
            rememberFeaturedFocus(position)
        }
        wifiAdapter.setOnItemClickListener(::openWifiConnectStep)
        wifiAdapter.setOnItemFocusListener { position, item ->
            rememberWifiFocus(position)
            previewWifiSelection(item)
        }
        quickActionAdapter.setOnItemClickListener(::handleQuickAction)
        quickActionAdapter.setOnItemFocusListener { position, _ ->
            rememberQuickActionFocus(position)
        }
        quickActionAdapter.setOnItemNavigateUpListener { _, _ ->
            if (currentSurfaceMode == HomeSurfaceMode.OFFLINE && currentWifiActionVisible) {
                requestFocusForSection(FocusSection.WIFI_ACTIONS)
            } else {
                requestFocusForPrimaryContent()
            }
        }
        localAppsAdapter.setOnItemClickListener(::handleAppManagementItem)
        localAppsAdapter.setOnItemDeleteListener(::requestUninstallManagedApp)
        localAppsAdapter.setOnItemFocusListener { position, _ ->
            rememberLocalAppsFocus(position)
        }

        featuredRail.addOnChildAttachStateChangeListener(featuredRailFocusBridge)
        wifiList.addOnChildAttachStateChangeListener(wifiRailFocusBridge)
        quickActionRail.addOnChildAttachStateChangeListener(quickActionRailFocusBridge)
        localAppsList.addOnChildAttachStateChangeListener(localAppsRailFocusBridge)
        configureRailFocusProxy(featuredRail, FocusSection.PRIMARY_CONTENT)
        configureRailFocusProxy(wifiList, FocusSection.PRIMARY_CONTENT)
        configureRailFocusProxy(quickActionRail, FocusSection.QUICK_ACTIONS)
        configureRailFocusProxy(localAppsList, FocusSection.LOCAL_APPS_LIST)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (closeLocalAppsOverlay()) {
                        return
                    }
                    if (moveFocusUpOneSection()) {
                        return
                    }
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )

        scheduleCapabilityRefresh()
        scheduleInstalledLaunchableAppsRefresh()
        viewModel.bindNetworkSnapshot(resolveNetworkSnapshot())
        val runtimeOwner = requireContext().applicationContext as? BootstrapRuntimeOwner
        if (runtimeOwner != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runtimeOwner.bootstrapRuntime.state.collect(viewModel::bindBootstrapState)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dlnaRendererController?.state?.collect { state ->
                    viewModel.bindCastReceiverState(
                        active = state.isRunning,
                        errorMessage = state.errorMessage,
                    )
                }
            }
        }

        tokenButton.setOnClickListener {
            Toast.makeText(
                requireContext(),
                viewModel.uiState.value.aiEntryMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
        tokenButton.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                rememberFocus(FocusSection.HERO_ACTION)
            }
        }
        tokenButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return@setOnKeyListener requestFocusForPrimaryContent()
            }
            false
        }

        configureWifiActionButtons(
            wifiConnectButton = wifiConnectButton,
            wifiSystemSettingsButton = wifiSystemSettingsButton,
            wifiRefreshButton = wifiRefreshButton,
            wifiList = wifiList,
            quickActionRail = quickActionRail,
        )

        wifiConnectButton.setOnClickListener {
            currentSelectedWifiItem?.let(::connectSelectedWifi) ?: openWifiSettings()
        }
        wifiSystemSettingsButton.setOnClickListener {
            currentSelectedWifiItem?.let(::openWifiSettings) ?: openWifiSettings()
        }
        wifiRefreshButton.setOnClickListener {
            refreshWifiNetworks(manual = true)
        }
        castStandbyButton.setOnClickListener {
            openUnifiedCastEntry()
        }
        castStandbyButton.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                rememberFocus(FocusSection.CAST_STANDBY)
            }
            castStandbyCard.isSelected = hasFocus
        }

        localAppsCloseButton.setOnClickListener {
            closeLocalAppsOverlay()
        }
        localAppsCloseButton.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                rememberFocus(FocusSection.LOCAL_APPS_CLOSE)
            }
        }
        localAppsCloseButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return@setOnKeyListener requestFocusForSection(FocusSection.LOCAL_APPS_LIST)
            }
            false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    currentSurfaceMode = state.surfaceMode
                    syncDlnaRenderer(online = state.wifiConnected)
                    brandTitle.text = state.brandTitle
                    wifiStatusText.text = state.wifiLabel
                    bindWifiClusterVisuals(
                        wifiStatusIcon = wifiStatusIcon,
                        wifiStatusText = wifiStatusText,
                        connected = state.wifiConnected,
                    )
                    bindHeroVisualState(
                        heroCard = heroCard,
                        heroAdCard = heroAdCard,
                        assistantArtFrame = assistantArtFrame,
                        assistantShadow = assistantShadow,
                        assistantGlow = assistantGlow,
                        assistantCharacter = assistantCharacter,
                        heroDialogue = heroDialogue,
                        heroHint = heroHint,
                        assistantChip = assistantChip,
                        serviceButton = tokenButton,
                        surfaceMode = state.surfaceMode,
                    )
                    bindContentLayoutMode(
                        heroCard = heroCard,
                        mainContentSection = mainContentSection,
                        wifiSection = wifiSection,
                        wifiSectionTitle = wifiSectionTitle,
                        wifiGuideText = wifiGuideText,
                        wifiContentRow = wifiContentRow,
                        castStandbyCard = castStandbyCard,
                        quickActionSection = quickActionSection,
                        wifiRefreshButton = wifiRefreshButton,
                        castStandbyButton = castStandbyButton,
                        quickActionRail = quickActionRail,
                        surfaceMode = state.surfaceMode,
                    )
                    modeChip.text = state.modeLabel
                    bindModeChipVisuals(modeChip, state.statusTone)
                    heroDialogue.text = state.heroDialogue
                    heroHint.text = state.heroHint
                    bindHeroAds(
                        heroAdCard = heroAdCard,
                        heroAdImage = heroAdImage,
                        heroAdCaption = heroAdCaption,
                        heroAdIndex = heroAdIndex,
                        heroAds = state.heroAds,
                    )
                    bindAssistantSprite(
                        assistantCharacter = assistantCharacter,
                        baseState = state.assistantSpriteState,
                        dialogue = state.heroDialogue,
                    )
                    tokenButton.text = state.tokenLabel
                    tokenButton.contentDescription = "${state.aiEntryLabel}，${state.aiEntryMessage}"
                    noticeCard.visibility = if (state.noticeVisible) View.VISIBLE else View.GONE
                    noticeTitle.text = state.noticeTitle
                    noticeBody.text = state.noticeBody
                    bindNoticeCardVisuals(noticeCard, state.statusTone)

                    featuredSectionTitle.text = state.featuredSectionTitle
                    featuredSectionTitle.visibility = View.GONE
                    featuredRail.visibility = if (state.featuredVisible) View.VISIBLE else View.GONE
                    featuredAdapter.submitList(state.featuredApps)
                    castStandbyCard.visibility = when {
                        state.castStandbyVisible -> View.VISIBLE
                        state.surfaceMode == HomeSurfaceMode.ONLINE -> View.INVISIBLE
                        else -> View.GONE
                    }
                    castStandbyTitle.text = state.castStandbyTitle
                    castDeviceName.text = resolveCastDeviceName()
                    castNetworkHint.text = state.castStandbyNetworkHint
                    castProtocolSummary.text = state.castStandbyProtocolSummary
                    castStandbyButton.text = state.castStandbyActionLabel
                    castStandbyButton.contentDescription = "${state.castStandbyTitle}，${state.castStandbyNetworkHint}，${state.castStandbyActionLabel}"

                    wifiSection.visibility = if (state.wifiSectionVisible) View.VISIBLE else View.GONE
                    wifiSectionTitle.text = state.wifiSectionTitle
                    wifiGuideText.text = state.wifiGuideText
                    wifiAdapter.submitList(state.wifiNetworks)
                    wifiEmptyState.visibility = if (state.wifiSectionVisible && state.wifiNetworks.isEmpty()) View.VISIBLE else View.GONE
                    wifiEmptyState.text = state.wifiEmptyText
                    resolveWifiSelection(state.wifiNetworks)
                    bindWifiActionCard()

                    quickActionSectionTitle.text = state.quickActionSectionTitle
                    quickActionSectionTitle.visibility = View.GONE
                    quickActionAdapter.submitList(state.quickActions)

                    currentFeaturedVisible = state.featuredVisible
                    currentCastStandbyVisible = state.castStandbyVisible
                    currentWifiVisible = state.wifiSectionVisible
                    currentFeaturedCount = state.featuredApps.size
                    currentWifiCount = state.wifiNetworks.size
                    currentQuickActionCount = state.quickActions.size
                    clampRememberedFocusPositions()
                    updateRailFocusTargets(
                        tokenButton = tokenButton,
                        featuredRail = featuredRail,
                        wifiList = wifiList,
                        quickActionRail = quickActionRail,
                        wifiConnectButton = wifiConnectButton,
                        castStandbyButton = castStandbyButton,
                        localAppsCloseButton = localAppsCloseButton,
                        localAppsList = localAppsList,
                    )
                    restoreFocusIfNeeded()
                }
            }
        }
        observeTrackedAppDownloadProgress()
    }

    override fun onStop() {
        homeStoppedAtMs = System.currentTimeMillis()
        if (suspendNextLeboReturnOnStop) {
            leboReturnSuspendedWhileBackground = true
            suspendNextLeboReturnOnStop = false
        }
        dlnaStartJob?.cancel()
        dlnaStartJob = null
        dlnaRendererController?.stop()
        viewModel.bindCastReceiverState(active = false, errorMessage = null)
        homeBackgroundTrimJob?.cancel()
        homeBackgroundTrimJob = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        homeTaskId = activity?.taskId ?: homeTaskId
        homeStoppedAtMs = 0L
        suspendNextLeboReturnOnStop = false
        leboReturnSuspendedWhileBackground = false
        scheduleCapabilityRefresh()
        scheduleNetworkRefresh()
        scheduleInstalledLaunchableAppsRefresh()
        scheduleHomeBackgroundTrim()
        resumePendingInstallIfPossible()
        resumePendingPickedApkInstallIfPossible()
        if (currentLocalAppsVisible) {
            refreshLocalAppsOverlay()
        }
        restoreFocusIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        syncFocusMemoryWithCurrentFocus()
        outState.putString(STATE_LAST_FOCUSED_SECTION, lastFocusedSection.name)
        outState.putInt(STATE_LAST_FEATURED_FOCUS_POSITION, lastFeaturedFocusPosition)
        outState.putInt(STATE_LAST_WIFI_FOCUS_POSITION, lastWifiFocusPosition)
        outState.putInt(STATE_LAST_QUICK_ACTION_FOCUS_POSITION, lastQuickActionFocusPosition)
        outState.putInt(STATE_LAST_LOCAL_APP_FOCUS_POSITION, lastLocalAppFocusPosition)
        outState.putPendingInstallRequestState(pendingInstallRequest)
        outState.putString(STATE_PENDING_PICKED_INSTALL_URI, pendingPickedInstallUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        capabilityDetector = null
        appLauncher = null
        appPackageInstaller = null
        appDownloadCoordinator = null
        appDownloadStatusResolver = null
        networkSnapshotProvider = null
        installedAppCatalogProvider = null
        dlnaRendererController?.stop()
        dlnaRendererController = null
        rootView = null
        tokenButtonView = null
        featuredRailView = null
        wifiListView = null
        wifiActionCardView = null
        wifiSelectedNameView = null
        wifiSelectedSummaryView = null
        wifiConnectButtonView = null
        wifiSystemSettingsButtonView = null
        wifiRefreshButtonView = null
        castStandbyButtonView = null
        quickActionRailView = null
        localAppsOverlayView = null
        localAppsCloseButtonView = null
        localAppsEmptyStateView = null
        localAppsListView = null
        currentSelectedWifiItem = null
        hasSavedFocusState = false
        hasAppliedInitialFocus = false
        heroAdRotationJob?.cancel()
        heroAdRotationJob = null
        appDownloadRefreshJob?.cancel()
        appDownloadRefreshJob = null
        dlnaStartJob?.cancel()
        dlnaStartJob = null
        leboDiscoveryStartJob?.cancel()
        leboDiscoveryStartJob = null
        homeBackgroundTrimJob?.cancel()
        homeBackgroundTrimJob = null
        leboReturnGuardJob?.cancel()
        leboReturnGuardJob = null
        assistantTalkResetJob?.cancel()
        assistantTalkResetJob = null
        lastHeroDialogueText = null
        assistantBaseSpriteState = AssistantSpriteState.IDLE
        heroAdFocused = false
        hasAttemptedLeboDiscoveryStart = false
        shouldKeepLeboDiscoveryService = false
        lastLeboReturnAtMs = 0L
        lastLeboFallbackStartedAtMs = 0L
        homeStoppedAtMs = 0L
        suspendNextLeboReturnOnStop = false
        leboReturnSuspendedWhileBackground = false
        homeTaskId = -1
        hasTrimmedBackgroundPlaybackAppsForCurrentCast = false
        capabilityRefreshJob?.cancel()
        capabilityRefreshJob = null
        networkRefreshJob?.cancel()
        networkRefreshJob = null
        installedAppsRefreshJob?.cancel()
        installedAppsRefreshJob = null
        localAppsRefreshJob?.cancel()
        localAppsRefreshJob = null
        activeHeroAds = emptyList()
        currentHeroAdIndex = 0
        super.onDestroyView()
    }

    private fun syncDlnaRenderer(online: Boolean) {
        val controller = dlnaRendererController ?: return
        if (online) {
            syncLightweightLeboDiscovery(online = true)
            if (controller.state.value.isRunning || dlnaStartJob?.isActive == true) {
                Log.i(
                    CAST_TAG,
                    "Home DLNA sync skipped running=${controller.state.value.isRunning} startJobActive=${dlnaStartJob?.isActive == true}",
                )
                return
            }
            Log.i(CAST_TAG, "Home DLNA sync scheduling start delayMs=$DLNA_START_DELAY_MS")
            dlnaStartJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(DLNA_START_DELAY_MS)
                if (!isAdded || currentSurfaceMode != HomeSurfaceMode.ONLINE) {
                    Log.i(CAST_TAG, "Home DLNA delayed start skipped isAdded=$isAdded surface=$currentSurfaceMode")
                    return@launch
                }
                Log.i(CAST_TAG, "Home DLNA delayed start executing")
                controller.start()
            }
            return
        } else {
            Log.i(CAST_TAG, "Home DLNA sync stopping because surface is offline")
            dlnaStartJob?.cancel()
            dlnaStartJob = null
            controller.stop()
            syncLightweightLeboDiscovery(online = false)
            resetLeboFallbackState()
        }
    }

    private fun syncLightweightLeboDiscovery(online: Boolean) {
        if (!online) {
            Log.i(CAST_TAG, "Lebo lightweight discovery stopping because surface is offline")
            shouldKeepLeboDiscoveryService = false
            hasAttemptedLeboDiscoveryStart = false
            leboDiscoveryStartJob?.cancel()
            leboDiscoveryStartJob = null
            killLeboPackages()
            return
        }
        shouldKeepLeboDiscoveryService = true
        if (hasAttemptedLeboDiscoveryStart || leboDiscoveryStartJob?.isActive == true) {
            Log.i(
                CAST_TAG,
                "Lebo lightweight discovery skipped attempted=$hasAttemptedLeboDiscoveryStart jobActive=${leboDiscoveryStartJob?.isActive == true}",
            )
            return
        }
        Log.i(CAST_TAG, "Lebo lightweight discovery scheduling service-only start delayMs=$LEBO_DISCOVERY_SERVICE_START_DELAY_MS")
        leboDiscoveryStartJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(LEBO_DISCOVERY_SERVICE_START_DELAY_MS)
            if (!isAdded || currentSurfaceMode != HomeSurfaceMode.ONLINE) {
                Log.i(CAST_TAG, "Lebo lightweight discovery delayed start skipped isAdded=$isAdded surface=$currentSurfaceMode")
                return@launch
            }
            hasAttemptedLeboDiscoveryStart = true
            val started = startLeboAirPlayServiceOnly()
            Log.i(CAST_TAG, "Lebo lightweight discovery service-only result=$started")
            if (started) {
                startLeboReturnGuard()
            }
        }
    }

    private fun resetLeboFallbackState() {
        leboReturnGuardJob?.cancel()
        leboReturnGuardJob = null
        lastLeboReturnAtMs = 0L
        lastLeboFallbackStartedAtMs = 0L
        homeStoppedAtMs = 0L
        suspendNextLeboReturnOnStop = false
        leboReturnSuspendedWhileBackground = false
        hasTrimmedBackgroundPlaybackAppsForCurrentCast = false
    }

    private fun startLeboReturnGuard() {
        if (leboReturnGuardJob?.isActive == true) {
            Log.i(CAST_TAG, "Lebo return guard already active")
            return
        }
        Log.i(CAST_TAG, "Lebo return guard starting")
        leboReturnGuardJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(LEBO_RETURN_GUARD_INITIAL_DELAY_MS)
            while (isActive) {
                val topActivity = currentTopActivity()
                val hasActiveLeboCast = hasActivePrivateLeboTcpSession()
                if (hasActiveLeboCast) {
                    trimBackgroundPlaybackAppsForCastOnce()
                    delay(LEBO_RETURN_GUARD_POLL_INTERVAL_MS)
                    continue
                }
                hasTrimmedBackgroundPlaybackAppsForCurrentCast = false
                trimIdleLeboFallbackIfExpired()
                if (shouldReturnFromLebo(topActivity, hasActiveLeboCast = false)) {
                    Log.i(
                        CAST_TAG,
                        "Lebo return guard reclaiming home topActivity=${topActivity?.flattenToShortString()}",
                    )
                    returnFromLeboIfAllowed()
                    delay(LEBO_RETURN_GUARD_RECLAIM_COOLDOWN_MS)
                } else {
                    delay(LEBO_RETURN_GUARD_POLL_INTERVAL_MS)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentTopActivity(): ComponentName? {
        val context = context ?: return null
        val activityManager = context.activityManager() ?: return null
        return runCatching {
            activityManager.getRunningTasks(1).firstOrNull()?.topActivity
        }.getOrNull()
    }

    private fun shouldReturnFromLebo(
        topActivity: ComponentName?,
        hasActiveLeboCast: Boolean,
    ): Boolean {
        if (leboReturnSuspendedWhileBackground) {
            return false
        }
        if (topActivity != null && shouldReturnFromLeboActivity(topActivity)) {
            return !hasActiveLeboCast
        }
        return shouldReturnAfterHomeWasBackgrounded(hasActiveLeboCast)
    }

    private fun shouldReturnFromLeboActivity(componentName: ComponentName): Boolean {
        return componentName.packageName == LEBO_CAST_PRIMARY_PACKAGE &&
            componentName.className in LEBO_RETURN_HOME_ACTIVITY_CLASSES
    }

    private fun shouldReturnAfterHomeWasBackgrounded(hasActiveLeboCast: Boolean): Boolean {
        if (leboReturnSuspendedWhileBackground) {
            return false
        }
        val stoppedAt = homeStoppedAtMs.takeIf { it > 0L } ?: return false
        val elapsedMs = System.currentTimeMillis() - stoppedAt
        return elapsedMs >= LEBO_RETURN_AFTER_HOME_STOP_DELAY_MS &&
            !hasActiveLeboCast
    }

    private fun hasActivePrivateLeboTcpSession(): Boolean {
        val context = context ?: return false
        val leboUid = runCatching {
            context.packageManager.getApplicationInfo(LEBO_CAST_PRIMARY_PACKAGE, 0).uid
        }.getOrNull() ?: return false
        return runCatching {
            File(PROC_NET_TCP).useLines { lines ->
                lines.drop(1).any { line -> line.isEstablishedPrivateTcpForUid(leboUid) }
            }
        }.getOrDefault(false)
    }

    private fun trimBackgroundPlaybackAppsForCastOnce() {
        if (hasTrimmedBackgroundPlaybackAppsForCurrentCast) {
            return
        }
        hasTrimmedBackgroundPlaybackAppsForCurrentCast = true
        Log.i(CAST_TAG, "Cast active; trimming background playback apps once")
        trimBackgroundPlaybackAppsForCast()
    }

    private fun trimBackgroundPlaybackAppsForCast() {
        val appContext = context?.applicationContext ?: return
        killBackgroundPackages(
            appContext = appContext,
            packageNames = CAST_BACKGROUND_PLAYBACK_PACKAGES,
            preserveLeboPackages = true,
        )
        rootView?.postDelayed(
            {
                killBackgroundPackages(
                    appContext = appContext,
                    packageNames = CAST_BACKGROUND_PLAYBACK_PACKAGES,
                    preserveLeboPackages = true,
                )
            },
            CAST_BACKGROUND_TRIM_DELAY_MS,
        )
    }

    private fun scheduleHomeBackgroundTrim() {
        val appContext = context?.applicationContext ?: return
        val provider = installedAppCatalogProvider
        homeBackgroundTrimJob?.cancel()
        Log.i(TRIM_TAG, "Home background trim scheduled")
        homeBackgroundTrimJob = viewLifecycleOwner.lifecycleScope.launch {
            trimBackgroundActivitiesForHome(appContext, provider)
            delay(HOME_BACKGROUND_TRIM_DELAY_MS)
            trimBackgroundActivitiesForHome(appContext, provider)
        }
    }

    private suspend fun trimBackgroundActivitiesForHome(
        appContext: Context,
        provider: InstalledAppCatalogProvider?,
    ) {
        val preserveLeboPackages = shouldPreserveLeboPackagesForHomeTrim()
        val packageNames = withContext(Dispatchers.IO) {
            collectHomeBackgroundTrimPackageNames(
                appContext = appContext,
                provider = provider,
                includeLeboPackages = !preserveLeboPackages,
            )
        }
        Log.i(
            TRIM_TAG,
            "Home background trim collected count=${packageNames.size} preserveLeboPackages=$preserveLeboPackages",
        )
        killBackgroundPackages(
            appContext = appContext,
            packageNames = packageNames,
            preserveLeboPackages = preserveLeboPackages,
        )
    }

    private fun collectHomeBackgroundTrimPackageNames(
        appContext: Context,
        provider: InstalledAppCatalogProvider?,
        includeLeboPackages: Boolean,
    ): Set<String> {
        val launchablePackages = provider
            ?.loadLaunchableApps()
            .orEmpty()
            .filterNot { app -> app.isSystemApp }
            .map { app -> app.packageName }
        val runningPackages = appContext.activityManager()
            ?.runningAppProcesses
            .orEmpty()
            .flatMap { process -> process.pkgList.orEmpty().asIterable() }
            .filter { packageName ->
                packageName.shouldTrimForHome(
                    appContext = appContext,
                    preserveLeboPackages = !includeLeboPackages,
                )
            }
            .filterNot { packageName -> packageName.isSystemPackage(appContext) }
        return buildSet {
            addAll(CAST_BACKGROUND_PLAYBACK_PACKAGES)
            addAll(HomeAppCatalog.allPackageNames())
            addAll(APP_STORE_PACKAGE_CANDIDATES)
            if (includeLeboPackages) {
                addAll(LEBO_CAST_PACKAGES)
            }
            addAll(launchablePackages)
            addAll(runningPackages)
        }
    }

    private fun killBackgroundPackages(
        appContext: Context,
        packageNames: Iterable<String>,
        preserveLeboPackages: Boolean = true,
    ) {
        val activityManager = appContext.activityManager() ?: return
        val eligiblePackageNames = packageNames
            .distinct()
            .filter { packageName -> packageName.shouldTrimForHome(appContext, preserveLeboPackages) }
        if (eligiblePackageNames.isEmpty()) {
            Log.i(TRIM_TAG, "Background trim found no eligible packages preserveLeboPackages=$preserveLeboPackages")
            return
        }
        Log.i(
            TRIM_TAG,
            "Background trim attempting count=${eligiblePackageNames.size} preserveLeboPackages=$preserveLeboPackages packages=${eligiblePackageNames.toLogList()}",
        )
        eligiblePackageNames.forEach { packageName ->
            runCatching {
                activityManager.killBackgroundProcesses(packageName)
            }
        }
    }

    private fun List<String>.toLogList(): String {
        val truncated = take(TRIM_LOG_PACKAGE_LIMIT)
        val suffix = if (size > TRIM_LOG_PACKAGE_LIMIT) ",..." else ""
        return truncated.joinToString(separator = ",") + suffix
    }

    private fun String.shouldTrimForHome(
        appContext: Context,
        preserveLeboPackages: Boolean = true,
    ): Boolean {
        return isNotBlank() &&
            this != appContext.packageName &&
            (!preserveLeboPackages || this !in LEBO_CAST_PACKAGES) &&
            this !in HOME_BACKGROUND_TRIM_PACKAGE_ALLOWLIST
    }

    private fun shouldPreserveLeboPackagesForHomeTrim(): Boolean {
        val fallbackStartedAt = lastLeboFallbackStartedAtMs
        val fallbackWaitingForConnection = fallbackStartedAt > 0L &&
            System.currentTimeMillis() - fallbackStartedAt < LEBO_IDLE_FALLBACK_TRIM_DELAY_MS
        return shouldKeepLeboDiscoveryService ||
            fallbackWaitingForConnection ||
            hasActivePrivateLeboTcpSession()
    }

    private fun trimIdleLeboFallbackIfExpired() {
        val fallbackStartedAt = lastLeboFallbackStartedAtMs.takeIf { it > 0L } ?: return
        if (System.currentTimeMillis() - fallbackStartedAt < LEBO_IDLE_FALLBACK_TRIM_DELAY_MS) {
            return
        }
        Log.i(CAST_TAG, "Lebo idle fallback expired; trimming Lebo packages")
        killLeboPackages()
        lastLeboFallbackStartedAtMs = 0L
        if (shouldKeepLeboDiscoveryService) {
            hasAttemptedLeboDiscoveryStart = false
            syncLightweightLeboDiscovery(online = true)
        }
    }

    private fun markLeboFallbackStarted() {
        lastLeboFallbackStartedAtMs = System.currentTimeMillis()
        shouldKeepLeboDiscoveryService = true
        homeBackgroundTrimJob?.cancel()
        homeBackgroundTrimJob = null
        Log.i(CAST_TAG, "Lebo fallback marked started")
        startLeboReturnGuard()
    }

    private fun killLeboPackages() {
        val appContext = context?.applicationContext ?: return
        Log.i(CAST_TAG, "Killing Lebo background packages")
        killBackgroundPackages(
            appContext = appContext,
            packageNames = LEBO_CAST_PACKAGES,
            preserveLeboPackages = false,
        )
    }

    private fun String.isSystemPackage(appContext: Context): Boolean {
        return runCatching {
            val flags = appContext.packageManager.getApplicationInfo(this, 0).flags
            (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        }.getOrDefault(true)
    }

    private fun armUserDirectedBackgroundReturnSuppression() {
        suspendNextLeboReturnOnStop = true
        rootView?.postDelayed(
            {
                if (homeStoppedAtMs == 0L) {
                    suspendNextLeboReturnOnStop = false
                }
            },
            LEBO_RETURN_USER_LAUNCH_ARM_WINDOW_MS,
        )
    }

    private fun disarmUserDirectedBackgroundReturnSuppressionIfVisible() {
        if (homeStoppedAtMs == 0L) {
            suspendNextLeboReturnOnStop = false
        }
    }

    private fun launchPackage(
        packageName: String,
        suppressLeboReturn: Boolean = true,
    ): AppLaunchResult? {
        val launcher = appLauncher ?: return null
        if (suppressLeboReturn) {
            armUserDirectedBackgroundReturnSuppression()
        }
        val result = launcher.launch(packageName)
        if (suppressLeboReturn && result != AppLaunchResult.Launched) {
            disarmUserDirectedBackgroundReturnSuppressionIfVisible()
        }
        return result
    }

    private fun returnFromLeboIfAllowed() {
        val now = System.currentTimeMillis()
        if (now - lastLeboReturnAtMs < LEBO_RETURN_GUARD_RECLAIM_COOLDOWN_MS) {
            Log.i(CAST_TAG, "Lebo home reclaim skipped by cooldown")
            return
        }
        lastLeboReturnAtMs = now
        val context = context ?: return
        val taskId = activity?.taskId?.takeIf { it >= 0 } ?: homeTaskId.takeIf { it >= 0 }
        if (taskId != null) {
            val activityManager = context.activityManager()
            val moved = activityManager != null &&
                runCatching {
                    activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
                    true
                }.getOrDefault(false)
            if (moved) {
                Log.i(CAST_TAG, "Lebo home reclaim moved taskId=$taskId to front")
                return
            }
            Log.i(CAST_TAG, "Lebo home reclaim moveTaskToFront failed taskId=$taskId")
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        val launched = runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
        Log.i(CAST_TAG, "Lebo home reclaim launchIntent result=$launched")
    }

    private fun Context.activityManager(): ActivityManager? {
        return getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    private fun String.isEstablishedPrivateTcpForUid(uid: Int): Boolean {
        val columns = trim().split(Regex("\\s+"))
        if (columns.size <= PROC_NET_TCP_UID_COLUMN_INDEX) {
            return false
        }
        val remoteAddress = columns[PROC_NET_TCP_REMOTE_COLUMN_INDEX].substringBefore(':')
        val state = columns[PROC_NET_TCP_STATE_COLUMN_INDEX]
        val socketUid = columns[PROC_NET_TCP_UID_COLUMN_INDEX].toIntOrNull()
        return socketUid == uid &&
            state == PROC_NET_TCP_ESTABLISHED_STATE &&
            remoteAddress.isPrivateIpv4AddressHex()
    }

    private fun String.isPrivateIpv4AddressHex(): Boolean {
        if (length < 8 || take(8) == PROC_NET_TCP_EMPTY_IPV4_HEX) {
            return false
        }
        val octets = runCatching {
            take(8)
                .chunked(2)
                .map { it.toInt(16) }
                .asReversed()
        }.getOrNull() ?: return false
        val first = octets.getOrNull(0) ?: return false
        val second = octets.getOrNull(1) ?: return false
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private fun scheduleCapabilityRefresh() {
        capabilityRefreshJob?.cancel()
        capabilityRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(CAPABILITY_REFRESH_DELAY_MS)
            if (!isAdded) {
                return@launch
            }
            refreshCapabilities()
        }
    }

    private fun scheduleNetworkRefresh() {
        networkRefreshJob?.cancel()
        networkRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(NETWORK_REFRESH_DELAY_MS)
            if (!isAdded) {
                return@launch
            }
            refreshWifiNetworks(manual = false)
        }
    }

    private fun scheduleInstalledLaunchableAppsRefresh() {
        val provider = installedAppCatalogProvider ?: return
        installedAppsRefreshJob?.cancel()
        installedAppsRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(INSTALLED_APPS_REFRESH_DELAY_MS)
            val apps = withContext(Dispatchers.IO) {
                provider.loadLaunchableApps()
            }
            if (!isAdded) {
                return@launch
            }
            viewModel.bindInstalledLaunchableApps(apps)
        }
    }

    private fun refreshCapabilities() {
        val snapshot = capabilityDetector?.snapshot(
            targetPackages = HomeAppCatalog.allPackageNames(),
        ) ?: return
        viewModel.bindCapabilities(snapshot)
    }

    private fun bindWifiClusterVisuals(
        wifiStatusIcon: ImageView,
        wifiStatusText: TextView,
        connected: Boolean,
    ) {
        val tint = if (connected) Color.parseColor("#8EF0B8") else Color.parseColor("#F7FBFF")
        wifiStatusIcon.setColorFilter(tint)
        wifiStatusText.setTextColor(tint)
    }

    private fun configureHeroAdCard(
        heroAdCard: View,
        assistantCharacter: AssistantSpriteView,
    ) {
        heroAdCard.setOnClickListener {
            activeHeroAds.getOrNull(currentHeroAdIndex)?.let(::handleHeroAdClick)
        }
        heroAdCard.setOnFocusChangeListener { view, hasFocus ->
            heroAdFocused = hasFocus
            applyHeroAdFocusState(view, hasFocus)
            applyAssistantSpriteFocusState(assistantCharacter)
        }
        updateHeroAdInteractivity(heroAdCard, activeHeroAds.getOrNull(currentHeroAdIndex))
    }

    private fun bindHeroAds(
        heroAdCard: View,
        heroAdImage: ImageView,
        heroAdCaption: TextView,
        heroAdIndex: TextView,
        heroAds: List<HeroAdItem>,
    ) {
        val normalizedAds = heroAds.distinctBy { it.creativeId }
        if (normalizedAds.isEmpty()) {
            heroAdCard.visibility = View.GONE
            heroAdRotationJob?.cancel()
            heroAdRotationJob = null
            activeHeroAds = emptyList()
            currentHeroAdIndex = 0
            heroAdImage.setImageDrawable(null)
            heroAdImage.contentDescription = null
            heroAdCaption.text = ""
            heroAdIndex.text = ""
            updateHeroAdInteractivity(heroAdCard, null)
            return
        }

        val idsChanged = normalizedAds.map { it.creativeId } != activeHeroAds.map { it.creativeId }
        if (idsChanged) {
            activeHeroAds = normalizedAds
            currentHeroAdIndex = 0
            heroAdRotationJob?.cancel()
            heroAdRotationJob = null
        } else if (currentHeroAdIndex > normalizedAds.lastIndex) {
            currentHeroAdIndex = 0
        }

        heroAdCard.visibility = View.VISIBLE
        renderHeroAd(heroAdCard, heroAdImage, heroAdCaption, heroAdIndex)

        if (activeHeroAds.size <= 1) {
            heroAdRotationJob?.cancel()
            heroAdRotationJob = null
            return
        }
        if (heroAdRotationJob?.isActive == true) {
            return
        }
        heroAdRotationJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(HERO_AD_ROTATION_INTERVAL_MS)
                if (!isAdded || activeHeroAds.size <= 1) {
                    continue
                }
                currentHeroAdIndex = (currentHeroAdIndex + 1) % activeHeroAds.size
                renderHeroAd(heroAdCard, heroAdImage, heroAdCaption, heroAdIndex)
            }
        }
    }

    private fun renderHeroAd(
        heroAdCard: View,
        heroAdImage: ImageView,
        heroAdCaption: TextView,
        heroAdIndex: TextView,
    ) {
        val heroAd = activeHeroAds.getOrNull(currentHeroAdIndex) ?: return
        heroAdImage.load(heroAd.imageUrl) {
            crossfade(false)
            allowHardware(false)
        }
        heroAdImage.contentDescription = heroAd.altText
        heroAdCaption.text = heroAd.altText
        heroAdIndex.text = if (activeHeroAds.size > 1) {
            "${currentHeroAdIndex + 1} / ${activeHeroAds.size}"
        } else {
            ""
        }
        updateHeroAdInteractivity(heroAdCard, heroAd)
    }

    private fun updateHeroAdInteractivity(heroAdCard: View, heroAd: HeroAdItem?) {
        val hasAnyClickableHeroAds = activeHeroAds.any(::hasClickableHeroAdAction)
        val currentAdClickable = heroAd?.let(::hasClickableHeroAdAction) == true
        heroAdCard.isFocusable = hasAnyClickableHeroAds
        heroAdCard.isFocusableInTouchMode = hasAnyClickableHeroAds
        heroAdCard.isClickable = currentAdClickable
        heroAdCard.contentDescription = when {
            heroAd == null -> null
            currentAdClickable -> "${heroAd.altText}，按确定查看"
            else -> heroAd.altText
        }
        applyHeroAdFocusState(heroAdCard, heroAdCard.hasFocus())
    }

    private fun applyHeroAdFocusState(
        heroAdCard: View,
        hasFocus: Boolean,
    ) {
        val currentAdClickable = activeHeroAds.getOrNull(currentHeroAdIndex)
            ?.let(::hasClickableHeroAdAction) == true
        heroAdCard.animate()
            .scaleX(if (hasFocus && currentAdClickable) 1.025f else 1f)
            .scaleY(if (hasFocus && currentAdClickable) 1.025f else 1f)
            .translationY(if (hasFocus && currentAdClickable) (-4f).dpToPx().toFloat() else 0f)
            .setDuration(140L)
            .start()
        heroAdCard.alpha = if (currentAdClickable || !hasFocus) 1f else 0.82f
        heroAdCard.translationZ = if (hasFocus && currentAdClickable) 20f else 0f
    }

    private fun bindAssistantSprite(
        assistantCharacter: AssistantSpriteView,
        baseState: AssistantSpriteState,
        dialogue: String,
    ) {
        val previousDialogue = lastHeroDialogueText
        lastHeroDialogueText = dialogue
        assistantBaseSpriteState = baseState
        val dialogueChanged = previousDialogue != null && previousDialogue != dialogue
        if (heroAdFocused && activeHeroAds.isNotEmpty()) {
            assistantTalkResetJob?.cancel()
            assistantTalkResetJob = null
            assistantCharacter.setSpriteState(AssistantSpriteState.POINT_LEFT)
            return
        }
        if (dialogueChanged) {
            assistantCharacter.setSpriteState(AssistantSpriteState.TALK)
            assistantTalkResetJob?.cancel()
            assistantTalkResetJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(ASSISTANT_TALK_ANIMATION_MS)
                if (!isAdded) {
                    return@launch
                }
                assistantCharacter.setSpriteState(resolveAssistantSpriteForCurrentFocus())
            }
            return
        }
        assistantCharacter.setSpriteState(resolveAssistantSpriteForCurrentFocus())
    }

    private fun applyAssistantSpriteFocusState(assistantCharacter: AssistantSpriteView) {
        assistantTalkResetJob?.cancel()
        assistantTalkResetJob = null
        assistantCharacter.setSpriteState(resolveAssistantSpriteForCurrentFocus())
    }

    private fun resolveAssistantSpriteForCurrentFocus(): AssistantSpriteState {
        return if (heroAdFocused && activeHeroAds.isNotEmpty()) {
            AssistantSpriteState.POINT_LEFT
        } else {
            assistantBaseSpriteState
        }
    }

    private fun hasClickableHeroAdAction(heroAd: HeroAdItem): Boolean {
        val actionValue = heroAd.clickActionValue?.trim()
        return when (heroAd.clickActionType.trim().lowercase()) {
            HERO_AD_ACTION_DEEPLINK,
            HERO_AD_ACTION_URL,
            -> !actionValue.isNullOrBlank()

            else -> false
        }
    }

    private fun handleHeroAdClick(heroAd: HeroAdItem) {
        val actionValue = heroAd.clickActionValue?.trim().orEmpty()
        if (actionValue.isBlank()) {
            return
        }
        val targetUri = actionValue.toHeroAdUri() ?: return
        val intent = when (heroAd.clickActionType.trim().lowercase()) {
            HERO_AD_ACTION_DEEPLINK -> Intent(Intent.ACTION_VIEW, targetUri)
            HERO_AD_ACTION_URL -> {
                if (targetUri.scheme?.lowercase() !in setOf("http", "https")) {
                    return
                }
                Intent(Intent.ACTION_VIEW, targetUri)
            }

            else -> return
        }
        openIntent(listOf(intent))
    }

    private fun String.toHeroAdUri(): Uri? {
        return runCatching { Uri.parse(this) }
            .getOrNull()
            ?.takeIf { !it.scheme.isNullOrBlank() }
    }

    private fun bindHeroVisualState(
        heroCard: View,
        heroAdCard: View,
        assistantArtFrame: View,
        assistantShadow: View,
        assistantGlow: View,
        assistantCharacter: AssistantSpriteView,
        heroDialogue: TextView,
        heroHint: TextView,
        assistantChip: TextView,
        serviceButton: Button,
        surfaceMode: HomeSurfaceMode,
    ) {
        val offline = surfaceMode == HomeSurfaceMode.OFFLINE
        heroCard.setBackgroundResource(
            if (offline) R.drawable.bg_home_hero_card_offline else R.drawable.bg_home_hero_card,
        )
        heroAdCard.updateBoxLayoutParams(
            width = if (offline) 276f.dpToPx() else 312f.dpToPx(),
            height = if (offline) 104f.dpToPx() else 132f.dpToPx(),
            marginStart = if (offline) 12f.dpToPx() else 14f.dpToPx(),
        )
        assistantArtFrame.updateBoxLayoutParams(
            width = if (offline) 188f.dpToPx() else 230f.dpToPx(),
            height = LinearLayout.LayoutParams.MATCH_PARENT,
            marginStart = if (offline) 8f.dpToPx() else 10f.dpToPx(),
        )
        assistantShadow.updateFrameLayoutParams(
            width = if (offline) 96f.dpToPx() else 118f.dpToPx(),
            height = if (offline) 14f.dpToPx() else 18f.dpToPx(),
            marginEnd = if (offline) 12f.dpToPx() else 18f.dpToPx(),
            marginBottom = if (offline) 6f.dpToPx() else 10f.dpToPx(),
        )
        assistantGlow.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_glow_offline else R.drawable.bg_assistant_glow,
        )
        assistantGlow.updateFrameLayoutParams(
            width = if (offline) 132f.dpToPx() else 166f.dpToPx(),
            height = if (offline) 132f.dpToPx() else 166f.dpToPx(),
            marginTop = if (offline) 14f.dpToPx() else 18f.dpToPx(),
            marginEnd = if (offline) 12f.dpToPx() else 20f.dpToPx(),
        )
        assistantCharacter.updateFrameLayoutParams(
            width = if (offline) 164f.dpToPx() else 214f.dpToPx(),
            height = if (offline) 176f.dpToPx() else 228f.dpToPx(),
            marginEnd = if (offline) (-2f).dpToPx() else 2f.dpToPx(),
            marginBottom = if (offline) 0f.dpToPx() else 2f.dpToPx(),
        )
        assistantCharacter.alpha = if (offline) 0.9f else 1f
        assistantCharacter.setOfflineTreatment(offline)
        assistantChip.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_chip_offline else R.drawable.bg_assistant_chip,
        )
        assistantChip.text = if (offline) "联网向导" else "语音伙伴"
        assistantChip.setTextColor(
            Color.parseColor(if (offline) "#E8EEF5" else "#F7FBFF"),
        )
        serviceButton.setBackgroundResource(
            if (offline) R.drawable.bg_service_button_offline else R.drawable.bg_token_button,
        )
        serviceButton.backgroundTintList = null
        serviceButton.setTextColor(
            Color.parseColor(if (offline) "#EDF5FB" else "#FFF7EF"),
        )
        heroDialogue.maxLines = if (offline) 2 else 3
        heroDialogue.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (offline) 16.5f else 18.5f)
        heroHint.visibility = if (offline) View.GONE else View.VISIBLE
    }

    private fun bindModeChipVisuals(
        modeChip: TextView,
        tone: HomeStatusTone,
    ) {
        val (fillColor, strokeColor) = when (tone) {
            HomeStatusTone.SUCCESS -> "#24463A" to "#6EDB99"
            HomeStatusTone.WARNING -> "#3A3021" to "#F0B868"
            HomeStatusTone.CRITICAL -> "#452529" to "#F08A8A"
            HomeStatusTone.NEUTRAL -> "#2A4159" to "#6FA6FF"
        }
        modeChip.background = buildRoundedBackground(
            fillColor = Color.parseColor(fillColor),
            strokeColor = Color.parseColor(strokeColor),
            cornerRadiusDp = 999f,
        )
        modeChip.setTextColor(Color.parseColor("#F7FBFF"))
    }

    private fun bindNoticeCardVisuals(
        noticeCard: LinearLayout,
        tone: HomeStatusTone,
    ) {
        val strokeColor = when (tone) {
            HomeStatusTone.SUCCESS -> "#6EDB99"
            HomeStatusTone.WARNING -> "#F0B868"
            HomeStatusTone.CRITICAL -> "#F08A8A"
            HomeStatusTone.NEUTRAL -> "#5D7085"
        }
        noticeCard.background = buildRoundedBackground(
            fillColor = Color.parseColor("#55202A35"),
            strokeColor = Color.parseColor(strokeColor),
            cornerRadiusDp = 22f,
        )
    }

    private fun launchFeaturedApp(item: FeaturedAppItem) {
        if (item.isInstallShortcut) {
            openLocalAppsOverlay()
            return
        }
        when (item.installState) {
            FeaturedAppInstallState.INSTALLED -> {
                val result = launchPackage(item.packageName) ?: return
                val message = when (result) {
                    AppLaunchResult.Launched -> null
                    AppLaunchResult.NotInstalled -> getString(R.string.feature_home_app_not_installed, item.title)
                    AppLaunchResult.NoLaunchActivity -> getString(R.string.feature_home_app_unavailable, item.title)
                }
                if (message != null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }

            FeaturedAppInstallState.READY_TO_INSTALL -> {
                triggerInstallPrompt(
                    PendingInstallRequest(
                        appId = item.appId,
                        title = item.title,
                        downloadId = item.downloadId,
                        localFilePath = item.localFilePath,
                    ),
                )
            }

            FeaturedAppInstallState.QUEUED,
            FeaturedAppInstallState.DOWNLOADING,
            -> {
                val message = item.downloadDetailMessage
                    ?.takeIf(String::isNotBlank)
                    ?: item.actionLabel
                Toast.makeText(requireContext(), "${item.title} $message。", Toast.LENGTH_SHORT).show()
            }

            FeaturedAppInstallState.PAUSED -> {
                val message = item.downloadDetailMessage
                    ?.takeIf(String::isNotBlank)
                    ?: "${item.title} 的下载已暂停"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }

            FeaturedAppInstallState.VERIFYING -> {
                Toast.makeText(requireContext(), "${item.title} 的安装包正在校验，请稍后再试。", Toast.LENGTH_SHORT).show()
            }

            FeaturedAppInstallState.FAILED -> {
                retryFeaturedAppDownload(item)
            }

            FeaturedAppInstallState.NOT_INSTALLED -> {
                if (item.canRequestDownload()) {
                    enqueueFeaturedAppDownload(item)
                    return
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.feature_home_app_not_installed, item.title),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun openAppInstallPicker() {
        if (launchKnownAppStore()) {
            return
        }
        val pickerIntent = buildApkPickerIntent()
        val resolvedIntent = listOf(
            pickerIntent,
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, APK_PICKER_MIME_TYPES)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        ).firstOrNull { intent ->
            intent.resolveActivity(requireContext().packageManager) != null
        }
        if (resolvedIntent == null) {
            Toast.makeText(requireContext(), "当前设备没有可用的 APK 文件选择入口。", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            armUserDirectedBackgroundReturnSuppression()
            apkPickerLauncher.launch(resolvedIntent)
        } catch (_: ActivityNotFoundException) {
            disarmUserDirectedBackgroundReturnSuppressionIfVisible()
            Toast.makeText(requireContext(), "当前设备没有可用的 APK 文件选择入口。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchKnownAppStore(): Boolean {
        for (packageName in APP_STORE_PACKAGE_CANDIDATES) {
            if (launchPackage(packageName) == AppLaunchResult.Launched) {
                Toast.makeText(requireContext(), "已打开应用商店，可继续安装新应用。", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return false
    }

    private fun buildApkPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, APK_PICKER_MIME_TYPES)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    private fun persistPickedApkPermission(data: Intent, apkUri: Uri) {
        val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if ((data.flags and readFlag) == 0) {
            return
        }
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(apkUri, readFlag)
        }
    }

    private fun triggerPickedApkInstall(apkUri: Uri) {
        val installer = appPackageInstaller ?: return
        armUserDirectedBackgroundReturnSuppression()
        val installResult = installer.promptInstall(apkUri)
        if (installResult is AppInstallPromptResult.Failed) {
            disarmUserDirectedBackgroundReturnSuppressionIfVisible()
        }
        val message = when (installResult) {
            AppInstallPromptResult.Launched -> {
                pendingPickedInstallUri = null
                "系统安装提示已打开。"
            }

            AppInstallPromptResult.PermissionRequired -> {
                pendingPickedInstallUri = apkUri
                "请先允许当前应用安装未知来源应用，然后再继续安装。"
            }

            is AppInstallPromptResult.Failed -> {
                pendingPickedInstallUri = null
                installResult.message
            }
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun enqueueFeaturedAppDownload(item: FeaturedAppItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val coordinator = appDownloadCoordinator
            if (coordinator == null) {
                Toast.makeText(
                    requireContext(),
                    "${item.title} 暂时无法加入下载队列。",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val queuedState = coordinator.enqueueManualDownload(
                AppDownloadRequest(
                    appId = item.appId,
                    title = item.title,
                    packageName = item.packageName,
                    versionCode = item.versionCode,
                    versionName = item.versionName,
                    downloadUrl = item.downloadUrl,
                    sha256 = item.sha256,
                ),
            )
            val message = when (queuedState?.status?.trim()?.lowercase()) {
                "queued" -> "${item.title} 已加入后台下载队列。"
                "failed" -> {
                    queuedState.errorMessage
                        ?.takeIf(String::isNotBlank)
                        ?.let { "${item.title} 加入下载队列失败：$it" }
                        ?: "${item.title} 加入下载队列失败。"
                }

                else -> "${item.title} 暂时无法加入下载队列。"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerInstallPrompt(request: PendingInstallRequest) {
        val installer = appPackageInstaller ?: return
        armUserDirectedBackgroundReturnSuppression()
        val installResult = installer.promptInstall(
            downloadId = request.downloadId,
            localFilePath = request.localFilePath,
        )
        if (installResult is AppInstallPromptResult.Failed) {
            disarmUserDirectedBackgroundReturnSuppressionIfVisible()
        }
        val message = when (installResult) {
            AppInstallPromptResult.Launched -> {
                pendingInstallRequest = null
                "${request.title} 的系统安装提示已打开。"
            }

            AppInstallPromptResult.PermissionRequired -> {
                pendingInstallRequest = request
                "请先允许当前应用安装未知来源应用，然后再继续安装 ${request.title}。"
            }

            is AppInstallPromptResult.Failed -> {
                pendingInstallRequest = null
                if (!request.isLocalApkInstall()) {
                    viewModel.markFeaturedAppDownloadFailed(
                        appId = request.appId,
                        message = installResult.message,
                    )
                }
                installResult.message
            }
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun retryFeaturedAppDownload(item: FeaturedAppItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val retriedState = appDownloadCoordinator?.retry(item.appId)
            val message = when (retriedState?.status?.trim()?.lowercase()) {
                "queued" -> "${item.title} 已重新加入后台下载队列。"
                "failed" -> {
                    retriedState.errorMessage
                        ?.takeIf(String::isNotBlank)
                        ?.let { "${item.title} 重新入队失败：$it" }
                        ?: "${item.title} 当前下载失败，稍后再试。"
                }

                null -> "${item.title} 当前没有可重试的下载记录。"
                else -> "${item.title} 当前状态为 ${retriedState.status}。"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumePendingInstallIfPossible() {
        val request = pendingInstallRequest ?: return
        val installer = appPackageInstaller ?: return
        if (!installer.canRequestPackageInstalls()) {
            return
        }
        triggerInstallPrompt(request)
    }

    private fun resumePendingPickedApkInstallIfPossible() {
        val apkUri = pendingPickedInstallUri ?: return
        val installer = appPackageInstaller ?: return
        if (!installer.canRequestPackageInstalls()) {
            return
        }
        triggerPickedApkInstall(apkUri)
    }

    private fun observeTrackedAppDownloadProgress() {
        appDownloadRefreshJob?.cancel()
        appDownloadRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshTrackedAppDownloads()
                    delay(APP_DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun refreshTrackedAppDownloads() {
        val coordinator = appDownloadCoordinator ?: return
        val statusResolver = appDownloadStatusResolver ?: return
        coordinator.refreshTrackedDownloads(statusResolver)
    }

    private fun handleAppManagementItem(item: AppManagementItem) {
        when (item.kind) {
            AppManagementItemKind.ADD_SHORTCUT -> openAppInstallPicker()
            AppManagementItemKind.INSTALLED -> launchManagedInstalledApp(item)
            AppManagementItemKind.APK_INSTALL,
            AppManagementItemKind.APK_UPGRADE,
            -> triggerLocalApkInstall(item)
        }
    }

    private fun launchManagedInstalledApp(item: AppManagementItem) {
        val result = launchPackage(item.packageName) ?: return
        val message = when (result) {
            AppLaunchResult.Launched -> {
                closeLocalAppsOverlay(restoreQuickActionFocus = false)
                null
            }

            AppLaunchResult.NotInstalled -> "${item.title} 已不存在或当前未安装。"
            AppLaunchResult.NoLaunchActivity -> "${item.title} 当前没有可直接启动的入口。"
        }
        if (message != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerLocalApkInstall(item: AppManagementItem) {
        val apkPath = item.apkPath
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (apkPath == null) {
            Toast.makeText(requireContext(), "${item.title} 的安装包路径不可用。", Toast.LENGTH_SHORT).show()
            return
        }
        triggerInstallPrompt(
            PendingInstallRequest(
                appId = "$LOCAL_APK_INSTALL_PREFIX$apkPath",
                title = item.title,
                downloadId = null,
                localFilePath = apkPath,
            ),
        )
    }

    private fun requestUninstallManagedApp(item: AppManagementItem) {
        if (item.kind != AppManagementItemKind.INSTALLED || item.packageName.isBlank()) {
            return
        }
        if (item.isSystemApp) {
            Toast.makeText(requireContext(), "${item.title} 是系统应用，不能从这里卸载。", Toast.LENGTH_SHORT).show()
            return
        }
        val uninstallIntent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${item.packageName}"),
        )
        if (openIntent(listOf(uninstallIntent))) {
            Toast.makeText(requireContext(), "已打开 ${item.title} 的系统卸载确认。", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "当前设备无法打开系统卸载确认。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleQuickAction(item: QuickActionItem) {
        when (item.id) {
            HomeViewModel.QUICK_ACTION_LOCAL -> openLocalFiles()
            HomeViewModel.QUICK_ACTION_CAST -> openUnifiedCastEntry()
            HomeViewModel.QUICK_ACTION_SETTINGS -> openSystemSettings()
            HomeViewModel.QUICK_ACTION_FREE_PLAY -> openFreePlay()
            HomeViewModel.QUICK_ACTION_LOCAL_APPS -> openLocalAppsOverlay()
        }
    }

    private fun previewWifiSelection(item: WifiNetworkItem) {
        currentSelectedWifiItem = item
        bindWifiActionCard()
    }

    private fun openWifiConnectStep(item: WifiNetworkItem) {
        previewWifiSelection(item)
        wifiActionCardView?.post {
            if (!isAdded) {
                return@post
            }
            requestFocusForSection(FocusSection.WIFI_ACTIONS)
        }
    }

    private fun connectSelectedWifi(item: WifiNetworkItem) {
        if (openIntent(buildWifiSettingsCandidates())) {
            Toast.makeText(
                requireContext(),
                "已进入系统网络设置，继续完成 ${item.ssid} 的连接。",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(requireContext(), "当前设备无法打开网络设置。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWifiSettings(item: WifiNetworkItem) {
        if (openIntent(buildWifiSettingsCandidates())) {
            Toast.makeText(
                requireContext(),
                "已打开系统网络设置，可继续排查或切换 ${item.ssid}。",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(requireContext(), "当前设备无法打开网络设置。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWifiSettings() {
        if (openIntent(buildWifiSettingsCandidates())) {
            Toast.makeText(
                requireContext(),
                "已打开系统网络设置，可继续连接或排查当前网络。",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(requireContext(), "当前设备无法打开网络设置。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshWifiNetworks(manual: Boolean) {
        viewModel.bindNetworkSnapshot(resolveNetworkSnapshot())
        if (manual) {
            val message = if (debugForceOffline) {
                "已刷新 Wi-Fi 列表（离线预览态）。"
            } else {
                "已刷新 Wi-Fi 列表。"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveNetworkSnapshot(): HomeNetworkSnapshot {
        if (!debugForceOffline) {
            return networkSnapshotProvider?.snapshot() ?: HomeNetworkSnapshot.fallback
        }
        return HomeNetworkSnapshot(
            isConnected = false,
            transport = "offline",
            currentSsid = null,
            visibleNetworks = listOf(
                "宽带WiFi",
                "HUAWEI_1234",
                "TP-LINK_5678",
                "Xiaomi_Guest",
                "ChinaNet-5G",
                "CMCC-Home",
                "OpenClaw_Test",
                "Office_WiFi",
            ),
            canReadWifiList = true,
            statusText = "当前未联网",
        )
    }

    private fun openSystemSettings() {
        if (!openIntent(listOf(Intent(Settings.ACTION_SETTINGS)))) {
            Toast.makeText(requireContext(), "当前设备无法打开系统设置。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUnifiedCastEntry() {
        val snapshot = resolveNetworkSnapshot()
        Log.i(
            CAST_TAG,
            "Unified cast entry clicked connected=${snapshot.isConnected} ssid=${snapshot.unifiedWifiDisplayName()}",
        )
        if (!snapshot.isConnected) {
            Log.i(CAST_TAG, "Unified cast entry blocked because TV is offline")
            Toast.makeText(
                requireContext(),
                "先让 TV 联网，之后手机和 TV 统一连接「当前 Wi-Fi」即可投屏。",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        interruptPlaybackForCast()
        trimBackgroundPlaybackAppsForCast()

        val controller = dlnaRendererController
        if (controller == null) {
            Log.w(CAST_TAG, "Unified cast entry falling back because DLNA controller is null")
            openCastFallback()
            return
        }

        val currentState = controller.state.value
        if (!currentState.isRunning && currentState.errorMessage.isNullOrBlank()) {
            Log.i(CAST_TAG, "Unified cast entry starting DLNA renderer immediately")
            dlnaStartJob?.cancel()
            dlnaStartJob = null
            controller.start()
            val startedState = controller.state.value
            viewModel.bindCastReceiverState(
                active = startedState.isRunning,
                errorMessage = startedState.errorMessage,
            )
        }

        val updatedState = controller.state.value
        if (updatedState.isRunning) {
            Log.i(
                CAST_TAG,
                "Unified cast entry ready via DLNA descriptionUrl=${updatedState.descriptionUrl}",
            )
            Toast.makeText(
                requireContext(),
                unifiedCastInstruction(snapshot),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        Log.w(CAST_TAG, "Unified cast entry falling back because DLNA unavailable error=${updatedState.errorMessage}")
        openCastFallback()
    }

    private fun openCastFallback() {
        Log.i(CAST_TAG, "Opening cast fallback")
        if (openLeboCastFallback()) {
            Log.i(CAST_TAG, "Cast fallback started Lebo")
            Toast.makeText(
                requireContext(),
                "自建投屏暂不可用，已启动乐播投屏兜底。",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (openCastSettings(showFailureToast = false)) {
            Log.i(CAST_TAG, "Cast fallback opened system cast settings")
            Toast.makeText(
                requireContext(),
                "自建投屏和乐播暂不可用，已打开系统投屏设置。",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Log.w(CAST_TAG, "Cast fallback found no Lebo or system cast entry")
            Toast.makeText(
                requireContext(),
                "自建投屏暂不可用，也没有找到乐播或系统投屏入口。",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun openLeboCastFallback(): Boolean {
        if (startLeboCastReceiverSilently()) {
            Log.i(CAST_TAG, "Lebo fallback satisfied by silent receiver start")
            return true
        }
        val leboIntents = listOf(
            Intent(LEBO_CAST_APP_ACTION)
                .setPackage(LEBO_CAST_PRIMARY_PACKAGE)
                .addCategory(Intent.CATEGORY_DEFAULT),
            Intent(LEBO_CAST_SERVER_ACTION)
                .setPackage(LEBO_CAST_PRIMARY_PACKAGE)
                .addCategory(Intent.CATEGORY_DEFAULT),
        )
        if (openIntent(leboIntents, suppressLeboReturn = false)) {
            Log.i(CAST_TAG, "Lebo fallback opened explicit Lebo action")
            markLeboFallbackStarted()
            return true
        }
        val launched = LEBO_CAST_PACKAGES.any { packageName ->
            launchPackage(packageName, suppressLeboReturn = false) == AppLaunchResult.Launched
        }
        if (launched) {
            Log.i(CAST_TAG, "Lebo fallback launched installed Lebo package")
            markLeboFallbackStarted()
        } else {
            Log.w(CAST_TAG, "Lebo fallback failed to launch any Lebo package")
        }
        return launched
    }

    private fun startLeboCastReceiverSilently(): Boolean {
        val context = context ?: run {
            Log.w(CAST_TAG, "Lebo silent receiver start skipped because context is null")
            return false
        }
        val packageManager = context.packageManager
        val leboInstalled = runCatching {
            packageManager.getPackageInfo(LEBO_CAST_PRIMARY_PACKAGE, 0)
        }.isSuccess
        if (!leboInstalled) {
            Log.w(CAST_TAG, "Lebo silent receiver start skipped because primary package is not installed")
            return false
        }
        val serviceStarted = startLeboAirPlayServiceOnly()
        val receiverNotified = runCatching {
            val receiverIntent = Intent(LEBO_CAST_RECEIVER_ACTION)
                .setClassName(LEBO_CAST_PRIMARY_PACKAGE, LEBO_CAST_RECEIVER_CLASS)
                .addCategory(Intent.CATEGORY_DEFAULT)
            context.sendBroadcast(receiverIntent)
            true
        }.getOrDefault(false)
        val started = serviceStarted || receiverNotified
        Log.i(
            CAST_TAG,
            "Lebo silent receiver result serviceStarted=$serviceStarted receiverNotified=$receiverNotified started=$started",
        )
        if (started) {
            markLeboFallbackStarted()
        }
        return started
    }

    private fun startLeboAirPlayServiceOnly(): Boolean {
        val context = context ?: run {
            Log.w(CAST_TAG, "Lebo service-only start skipped because context is null")
            return false
        }
        val started = runCatching {
            val serviceIntent = Intent(LEBO_CAST_SERVICE_ACTION)
                .setClassName(LEBO_CAST_PRIMARY_PACKAGE, LEBO_CAST_SERVICE_CLASS)
                .addCategory(Intent.CATEGORY_DEFAULT)
            context.startService(serviceIntent) != null
        }.onFailure { throwable ->
            Log.w(CAST_TAG, "Lebo service-only start failed", throwable)
        }.getOrDefault(false)
        Log.i(CAST_TAG, "Lebo service-only start result=$started")
        return started
    }

    private fun openCastSettings(showFailureToast: Boolean = true): Boolean {
        val opened = openIntent(
            listOf(
                Intent("android.settings.CAST_SETTINGS"),
                Intent(Settings.ACTION_CAST_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            ),
        )
        if (!opened) {
            if (showFailureToast) {
                Toast.makeText(requireContext(), "当前设备没有可用的投屏设置入口。", Toast.LENGTH_SHORT).show()
            }
        }
        return opened
    }

    private fun unifiedCastInstruction(snapshot: HomeNetworkSnapshot): String {
        return "手机和 TV 统一连接「${snapshot.unifiedWifiDisplayName()}」后，媒体投屏选择「${resolveCastDeviceName()}」；苹果/小米镜像可找「$LEBO_CAST_DISPLAY_NAME」。"
    }

    private fun HomeNetworkSnapshot.unifiedWifiDisplayName(): String {
        return currentSsid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "当前 Wi-Fi"
    }

    private fun openDlnaMediaRequest(request: DlnaMediaRequest) {
        activity?.runOnUiThread {
            if (!isAdded) {
                Log.w(CAST_TAG, "DLNA media request ignored because fragment is not added")
                return@runOnUiThread
            }
            Log.i(CAST_TAG, "Opening DLNA media request uri=${request.uri.take(CAST_URI_LOG_LIMIT)}")
            interruptPlaybackForCast()
            trimBackgroundPlaybackAppsForCast()
            armUserDirectedBackgroundReturnSuppression()
            startActivity(DlnaPlaybackActivity.intent(requireContext(), request.uri))
        }
    }

    private fun interruptPlaybackForCast() {
        CastPlaybackInterrupter.interruptCurrentPlayback(requireContext())
    }

    private fun resolveCastDeviceName(): String {
        val configuredName = runCatching {
            Settings.Global.getString(requireContext().contentResolver, "device_name")
        }.getOrNull()
        return configuredName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: "RS AITV"
    }

    private fun openLocalFiles() {
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
        }
        if (!openIntent(listOf(pickerIntent))) {
            Toast.makeText(requireContext(), "当前设备没有可用的本地文件入口。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFreePlay() {
        val preferredApp = viewModel.uiState.value.featuredApps.firstOrNull { it.installed }
        if (preferredApp != null) {
            launchFeaturedApp(preferredApp)
            return
        }
        Toast.makeText(requireContext(), "当前没有已安装的内容应用，先去“本机应用”或联网后再试。", Toast.LENGTH_SHORT).show()
    }

    private fun openLocalAppsOverlay() {
        currentLocalAppsVisible = true
        localAppsOverlayView?.visibility = View.VISIBLE
        refreshLocalAppsOverlay()
        requestStoragePermissionForApkScanIfNeeded()
        localAppsOverlayView?.post {
            if (!isAdded) {
                return@post
            }
            requestFocusForSection(
                if (currentLocalAppCount > 0) {
                    FocusSection.LOCAL_APPS_LIST
                } else {
                    FocusSection.LOCAL_APPS_CLOSE
                },
            )
        }
    }

    private fun refreshLocalAppsOverlay() {
        val provider = installedAppCatalogProvider
        if (provider == null) {
            localAppsAdapter.submitList(emptyList())
            currentLocalAppCount = 0
            localAppsEmptyStateView?.text = "当前无法读取本机应用列表。"
            localAppsEmptyStateView?.visibility = View.VISIBLE
            localAppsListView?.visibility = View.GONE
            return
        }
        localAppsRefreshJob?.cancel()
        val scanLocalApks = hasExternalStorageReadPermission()
        localAppsEmptyStateView?.text = if (scanLocalApks) {
            "正在扫描本机应用和 USB APK..."
        } else {
            "正在扫描本机应用。授权存储权限后可读取 U 盘或本机 APK。"
        }
        localAppsEmptyStateView?.visibility = View.VISIBLE
        localAppsRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                provider.loadAppManagementCatalog(scanLocalApks = scanLocalApks)
            }
            if (!isAdded) {
                return@launch
            }
            viewModel.bindInstalledLaunchableApps(catalog.installedApps)
            localAppsAdapter.submitList(catalog.managementItems)
            currentLocalAppCount = catalog.managementItems.size
            lastLocalAppFocusPosition = lastLocalAppFocusPosition
                .coerceIn(0, (currentLocalAppCount - 1).coerceAtLeast(0))
            val isEmpty = catalog.managementItems.isEmpty()
            localAppsEmptyStateView?.text = "当前没有扫描到可管理的应用。"
            localAppsEmptyStateView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
            localAppsListView?.visibility = if (isEmpty) View.GONE else View.VISIBLE
            if (currentLocalAppsVisible && !isEmpty) {
                localAppsListView?.post {
                    requestFocusForSection(FocusSection.LOCAL_APPS_LIST)
                }
            }
        }
    }

    private fun hasExternalStorageReadPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermissionForApkScanIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            hasExternalStorageReadPermission()
        ) {
            return
        }
        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun closeLocalAppsOverlay(restoreQuickActionFocus: Boolean = true): Boolean {
        if (!currentLocalAppsVisible) {
            return false
        }
        currentLocalAppsVisible = false
        localAppsRefreshJob?.cancel()
        localAppsRefreshJob = null
        localAppsOverlayView?.visibility = View.GONE
        if (restoreQuickActionFocus) {
            quickActionRailView?.post {
                if (!isAdded) {
                    return@post
                }
                requestFocusForSection(FocusSection.QUICK_ACTIONS)
            }
        }
        return true
    }

    private fun buildWifiSettingsCandidates(): List<Intent> {
        return listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openIntent(
        candidates: List<Intent>,
        suppressLeboReturn: Boolean = true,
    ): Boolean {
        val packageManager = requireContext().packageManager
        val targetIntent = candidates
            .map { intent -> intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { intent -> intent.resolveActivity(packageManager) != null }
            ?: return false
        if (suppressLeboReturn) {
            armUserDirectedBackgroundReturnSuppression()
        }
        return runCatching {
            startActivity(targetIntent)
            true
        }.getOrElse {
            if (suppressLeboReturn) {
                disarmUserDirectedBackgroundReturnSuppressionIfVisible()
            }
            false
        }
    }

    private fun configureWifiActionButtons(
        wifiConnectButton: Button,
        wifiSystemSettingsButton: Button,
        wifiRefreshButton: Button,
        wifiList: RecyclerView,
        quickActionRail: RecyclerView,
    ) {
        wifiConnectButton.nextFocusLeftId = wifiList.id
        wifiConnectButton.nextFocusUpId = wifiList.id
        wifiConnectButton.nextFocusDownId = wifiSystemSettingsButton.id
        wifiSystemSettingsButton.nextFocusLeftId = wifiList.id
        wifiSystemSettingsButton.nextFocusUpId = wifiConnectButton.id
        wifiSystemSettingsButton.nextFocusDownId = wifiRefreshButton.id
        wifiRefreshButton.nextFocusLeftId = wifiList.id
        wifiRefreshButton.nextFocusUpId = wifiSystemSettingsButton.id
        wifiRefreshButton.nextFocusDownId = quickActionRail.id

        val actionFocusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                rememberFocus(FocusSection.WIFI_ACTIONS)
            }
        }
        wifiConnectButton.onFocusChangeListener = actionFocusListener
        wifiSystemSettingsButton.onFocusChangeListener = actionFocusListener
        wifiRefreshButton.onFocusChangeListener = actionFocusListener
    }

    private fun resolveWifiSelection(wifiNetworks: List<WifiNetworkItem>) {
        currentSelectedWifiItem = currentSelectedWifiItem
            ?.let { selected -> wifiNetworks.firstOrNull { it.ssid == selected.ssid } }
            ?: wifiNetworks.getOrNull(lastWifiFocusPosition)
            ?: wifiNetworks.firstOrNull()
    }

    private fun bindWifiActionCard() {
        val offline = currentSurfaceMode == HomeSurfaceMode.OFFLINE
        val selectedWifi = if (offline) currentSelectedWifiItem else null
        currentWifiActionVisible = offline
        wifiActionCardView?.visibility = if (offline) View.VISIBLE else View.GONE
        wifiSelectedNameView?.text = selectedWifi?.ssid ?: "先选择一个 Wi-Fi"
        wifiSelectedSummaryView?.text = if (!offline) {
            ""
        } else {
            selectedWifi?.let {
                buildString {
                    append(it.summary)
                    append("。先连上这个网络，再继续使用节目入口与语音能力。")
                }
            } ?: "从左侧选择网络后可直接继续连接；如果列表不完整，也可以进入系统网络设置处理。"
        }
        wifiConnectButtonView?.text = if (selectedWifi != null) "连接这个网络" else "打开网络设置"
    }

    private fun bindContentLayoutMode(
        heroCard: View,
        mainContentSection: LinearLayout,
        wifiSection: LinearLayout,
        wifiSectionTitle: TextView,
        wifiGuideText: TextView,
        wifiContentRow: LinearLayout,
        castStandbyCard: View,
        quickActionSection: LinearLayout,
        wifiRefreshButton: Button,
        castStandbyButton: Button,
        quickActionRail: RecyclerView,
        surfaceMode: HomeSurfaceMode,
    ) {
        val offline = surfaceMode == HomeSurfaceMode.OFFLINE
        mainContentSection.updateVerticalLayoutParams(
            height = if (offline) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
            weight = if (offline) 1f else 0f,
        )
        wifiSection.updateVerticalLayoutParams(
            height = if (offline) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
            weight = if (offline) 1f else 0f,
        )
        wifiSectionTitle.visibility = if (offline) View.GONE else View.VISIBLE
        wifiGuideText.visibility = if (offline) View.GONE else View.VISIBLE
        wifiContentRow.updateVerticalLayoutParams(
            height = if (offline) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
            weight = if (offline) 1f else 0f,
            topMargin = if (offline) 0 else 8f.dpToPx(),
        )
        castStandbyCard.visibility = if (offline) View.GONE else castStandbyCard.visibility
        castStandbyButton.nextFocusUpId = featuredRailView?.id ?: View.NO_ID
        castStandbyButton.nextFocusDownId = quickActionRail.id
        castStandbyButton.nextFocusLeftId = View.NO_ID
        castStandbyButton.nextFocusRightId = View.NO_ID
        heroCard.updateVerticalLayoutParams(
            height = if (offline) 184f.dpToPx() else 248f.dpToPx(),
            weight = 0f,
            topMargin = if (offline) 8f.dpToPx() else 10f.dpToPx(),
        )
        quickActionSection.visibility = if (offline) View.GONE else View.VISIBLE
        wifiRefreshButton.nextFocusDownId = if (offline) View.NO_ID else quickActionRail.id
    }

    private fun restoreFocusMemory(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            return
        }
        hasSavedFocusState = true
        lastFocusedSection = savedInstanceState.getString(STATE_LAST_FOCUSED_SECTION)
            ?.let { sectionName -> runCatching { FocusSection.valueOf(sectionName) }.getOrNull() }
            ?: FocusSection.PRIMARY_CONTENT
        lastFeaturedFocusPosition = savedInstanceState.getInt(STATE_LAST_FEATURED_FOCUS_POSITION, 0)
        lastWifiFocusPosition = savedInstanceState.getInt(STATE_LAST_WIFI_FOCUS_POSITION, 0)
        lastQuickActionFocusPosition = savedInstanceState.getInt(STATE_LAST_QUICK_ACTION_FOCUS_POSITION, 0)
        lastLocalAppFocusPosition = savedInstanceState.getInt(STATE_LAST_LOCAL_APP_FOCUS_POSITION, 0)
    }

    private fun restoreFocusIfNeeded() {
        val root = rootView ?: return
        root.post {
            if (!isAdded) {
                return@post
            }
            if (!hasSavedFocusState && !hasAppliedInitialFocus) {
                root.postDelayed(
                    {
                        if (!isAdded || hasSavedFocusState || hasAppliedInitialFocus) {
                            return@postDelayed
                        }
                        val initialFocusApplied =
                            requestFocusForPrimaryContent() ||
                                (currentSurfaceMode == HomeSurfaceMode.OFFLINE && requestFocusForSection(FocusSection.WIFI_ACTIONS)) ||
                                requestFocusForSection(FocusSection.QUICK_ACTIONS)
                        hasAppliedInitialFocus = initialFocusApplied
                    },
                    180L,
                )
                return@post
            }
            val activeFocus = root.findFocus()
            if (activeFocus != null && activeFocus.isShown) {
                return@post
            }
            focusRestoreCandidates().firstOrNull(::requestFocusForSection)
        }
    }

    private fun focusRestoreCandidates(): List<FocusSection> {
        return if (currentLocalAppsVisible) {
            buildList {
                add(lastFocusedSection)
                add(FocusSection.LOCAL_APPS_LIST)
                add(FocusSection.LOCAL_APPS_CLOSE)
            }.distinct()
        } else {
            buildList {
                add(lastFocusedSection)
                add(FocusSection.PRIMARY_CONTENT)
                add(FocusSection.CAST_STANDBY)
                add(FocusSection.WIFI_ACTIONS)
                add(FocusSection.HERO_ACTION)
                add(FocusSection.QUICK_ACTIONS)
            }.distinct()
        }
    }

    private fun configureRailFocusProxy(
        rail: RecyclerView,
        section: FocusSection,
    ) {
        rail.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                requestFocusForSection(section)
            }
        }
    }

    private fun updateRailFocusTargets(
        tokenButton: Button,
        featuredRail: RecyclerView,
        wifiList: RecyclerView,
        quickActionRail: RecyclerView,
        wifiConnectButton: Button,
        castStandbyButton: Button,
        localAppsCloseButton: Button,
        localAppsList: RecyclerView,
    ) {
        featuredRailFocusBridge.nextFocusUpId = tokenButton.id
        featuredRailFocusBridge.nextFocusDownId = if (currentCastStandbyVisible) castStandbyButton.id else quickActionRail.id
        featuredRailFocusBridge.nextFocusLeftId = View.NO_ID
        featuredRailFocusBridge.nextFocusRightId = View.NO_ID

        wifiRailFocusBridge.nextFocusUpId = tokenButton.id
        wifiRailFocusBridge.nextFocusDownId = View.NO_ID
        wifiRailFocusBridge.nextFocusLeftId = View.NO_ID
        wifiRailFocusBridge.nextFocusRightId = if (currentWifiActionVisible) wifiConnectButton.id else View.NO_ID

        quickActionRailFocusBridge.nextFocusUpId = when {
            currentSurfaceMode == HomeSurfaceMode.OFFLINE && currentWifiActionVisible -> wifiConnectButton.id
            currentSurfaceMode == HomeSurfaceMode.ONLINE && currentCastStandbyVisible -> castStandbyButton.id
            currentSurfaceMode == HomeSurfaceMode.ONLINE -> featuredRail.id
            else -> wifiList.id
        }
        quickActionRailFocusBridge.nextFocusDownId = View.NO_ID
        quickActionRailFocusBridge.nextFocusLeftId = View.NO_ID
        quickActionRailFocusBridge.nextFocusRightId = View.NO_ID

        localAppsRailFocusBridge.nextFocusUpId = localAppsCloseButton.id
        localAppsRailFocusBridge.nextFocusDownId = View.NO_ID
        localAppsRailFocusBridge.nextFocusLeftId = View.NO_ID
        localAppsRailFocusBridge.nextFocusRightId = View.NO_ID

        featuredRail.syncChildFocusTargets(featuredRailFocusBridge)
        wifiList.syncChildFocusTargets(wifiRailFocusBridge)
        quickActionRail.syncChildFocusTargets(quickActionRailFocusBridge)
        localAppsList.syncChildFocusTargets(localAppsRailFocusBridge)
    }

    private fun rememberFocus(
        section: FocusSection,
        position: Int? = null,
    ) {
        lastFocusedSection = section
        val resolvedPosition = position?.coerceAtLeast(0) ?: return
        when (section) {
            FocusSection.HERO_ACTION -> Unit
            FocusSection.PRIMARY_CONTENT -> {
                if (currentSurfaceMode == HomeSurfaceMode.ONLINE) {
                    lastFeaturedFocusPosition = resolvedPosition
                } else {
                    lastWifiFocusPosition = resolvedPosition
                }
            }

            FocusSection.CAST_STANDBY -> Unit
            FocusSection.WIFI_ACTIONS -> Unit
            FocusSection.QUICK_ACTIONS -> lastQuickActionFocusPosition = resolvedPosition
            FocusSection.LOCAL_APPS_CLOSE -> Unit
            FocusSection.LOCAL_APPS_LIST -> lastLocalAppFocusPosition = resolvedPosition
        }
    }

    private fun rememberFeaturedFocus(position: Int) {
        lastFocusedSection = FocusSection.PRIMARY_CONTENT
        lastFeaturedFocusPosition = position.coerceAtLeast(0)
    }

    private fun rememberWifiFocus(position: Int) {
        lastFocusedSection = FocusSection.PRIMARY_CONTENT
        lastWifiFocusPosition = position.coerceAtLeast(0)
    }

    private fun rememberQuickActionFocus(position: Int) {
        lastFocusedSection = FocusSection.QUICK_ACTIONS
        lastQuickActionFocusPosition = position.coerceAtLeast(0)
    }

    private fun rememberLocalAppsFocus(position: Int) {
        lastFocusedSection = FocusSection.LOCAL_APPS_LIST
        lastLocalAppFocusPosition = position.coerceAtLeast(0)
    }

    private fun clampRememberedFocusPositions() {
        lastFeaturedFocusPosition = lastFeaturedFocusPosition.coerceIn(0, (currentFeaturedCount - 1).coerceAtLeast(0))
        lastWifiFocusPosition = lastWifiFocusPosition.coerceIn(0, (currentWifiCount - 1).coerceAtLeast(0))
        lastQuickActionFocusPosition = lastQuickActionFocusPosition.coerceIn(0, (currentQuickActionCount - 1).coerceAtLeast(0))
        lastLocalAppFocusPosition = lastLocalAppFocusPosition.coerceIn(0, (currentLocalAppCount - 1).coerceAtLeast(0))
    }

    private fun requestFocusForSection(section: FocusSection): Boolean {
        return when (section) {
            FocusSection.HERO_ACTION -> {
                if (currentLocalAppsVisible) {
                    false
                } else {
                    tokenButtonView?.requestFocus() == true
                }
            }

            FocusSection.PRIMARY_CONTENT -> {
                if (currentLocalAppsVisible) {
                    false
                } else {
                    requestFocusForPrimaryContent()
                }
            }

            FocusSection.CAST_STANDBY -> {
                if (currentLocalAppsVisible || currentSurfaceMode != HomeSurfaceMode.ONLINE || !currentCastStandbyVisible) {
                    false
                } else {
                    castStandbyButtonView?.requestFocus() == true
                }
            }

            FocusSection.WIFI_ACTIONS -> {
                if (currentLocalAppsVisible || currentSurfaceMode != HomeSurfaceMode.OFFLINE || !currentWifiActionVisible) {
                    false
                } else {
                    wifiConnectButtonView?.requestFocus() == true
                }
            }

            FocusSection.QUICK_ACTIONS -> {
                if (currentLocalAppsVisible) {
                    false
                } else {
                    requestFocusInRail(
                        rail = quickActionRailView,
                        position = lastQuickActionFocusPosition,
                        itemCount = currentQuickActionCount,
                    )
                }
            }

            FocusSection.LOCAL_APPS_CLOSE -> {
                if (!currentLocalAppsVisible) {
                    false
                } else {
                    localAppsCloseButtonView?.requestFocus() == true
                }
            }

            FocusSection.LOCAL_APPS_LIST -> {
                if (!currentLocalAppsVisible) {
                    false
                } else {
                    requestFocusInRail(
                        rail = localAppsListView,
                        position = lastLocalAppFocusPosition,
                        itemCount = currentLocalAppCount,
                    )
                }
            }
        }
    }

    private fun requestFocusForPrimaryContent(): Boolean {
        return when (currentSurfaceMode) {
            HomeSurfaceMode.ONLINE -> {
                if (!currentFeaturedVisible) {
                    false
                } else {
                    requestFocusInRail(
                        rail = featuredRailView,
                        position = lastFeaturedFocusPosition,
                        itemCount = currentFeaturedCount,
                    )
                }
            }

            HomeSurfaceMode.OFFLINE -> {
                if (!currentWifiVisible || currentWifiCount == 0) {
                    false
                } else {
                    requestFocusInRail(
                        rail = wifiListView,
                        position = lastWifiFocusPosition,
                        itemCount = currentWifiCount,
                    )
                }
            }
        }
    }

    private fun requestFocusInRail(
        rail: RecyclerView?,
        position: Int,
        itemCount: Int,
    ): Boolean {
        if (rail == null || rail.visibility != View.VISIBLE || itemCount == 0) {
            return false
        }
        val targetPosition = position.coerceIn(0, itemCount - 1)
        val targetView = rail.findViewHolderForAdapterPosition(targetPosition)?.itemView
            ?: rail.layoutManager?.findViewByPosition(targetPosition)
        if (targetView != null) {
            return targetView.requestFocus()
        }
        rail.scrollToPosition(targetPosition)
        rail.post {
            if (!isAdded) {
                return@post
            }
            val reboundTarget = rail.findViewHolderForAdapterPosition(targetPosition)?.itemView
                ?: rail.layoutManager?.findViewByPosition(targetPosition)
                ?: rail.findViewHolderForAdapterPosition(0)?.itemView
                ?: rail.layoutManager?.findViewByPosition(0)
            reboundTarget?.requestFocus()
        }
        return true
    }

    private fun moveFocusUpOneSection(): Boolean {
        val sourceSection = currentFocusedSection() ?: lastFocusedSection
        if (sourceSection == FocusSection.QUICK_ACTIONS) {
            when {
                currentSurfaceMode == HomeSurfaceMode.OFFLINE && currentWifiActionVisible -> return requestFocusForSection(FocusSection.WIFI_ACTIONS)
                currentSurfaceMode == HomeSurfaceMode.ONLINE && currentCastStandbyVisible -> return requestFocusForSection(FocusSection.CAST_STANDBY)
                requestFocusForPrimaryContent() -> return true
            }
        }
        val targetSection = when (sourceSection) {
            FocusSection.QUICK_ACTIONS -> FocusSection.HERO_ACTION
            FocusSection.WIFI_ACTIONS -> FocusSection.PRIMARY_CONTENT
            FocusSection.CAST_STANDBY -> FocusSection.PRIMARY_CONTENT
            FocusSection.PRIMARY_CONTENT -> FocusSection.HERO_ACTION
            FocusSection.HERO_ACTION -> null
            FocusSection.LOCAL_APPS_CLOSE -> null
            FocusSection.LOCAL_APPS_LIST -> null
        }
        return targetSection?.let(::requestFocusForSection) == true
    }

    private fun currentFocusedSection(): FocusSection? {
        val root = rootView ?: return null
        val focusedView = root.findFocus() ?: return null
        return when {
            localAppsCloseButtonView === focusedView -> FocusSection.LOCAL_APPS_CLOSE
            focusedView.isWithin(localAppsListView) -> FocusSection.LOCAL_APPS_LIST
            focusedView === wifiConnectButtonView ||
                focusedView === wifiSystemSettingsButtonView ||
                focusedView === wifiRefreshButtonView -> FocusSection.WIFI_ACTIONS

            tokenButtonView === focusedView -> FocusSection.HERO_ACTION
            castStandbyButtonView === focusedView -> FocusSection.CAST_STANDBY
            focusedView.isWithin(featuredRailView) || focusedView.isWithin(wifiListView) -> FocusSection.PRIMARY_CONTENT
            focusedView.isWithin(quickActionRailView) -> FocusSection.QUICK_ACTIONS
            else -> null
        }
    }

    private fun syncFocusMemoryWithCurrentFocus() {
        val root = rootView ?: return
        val focusedView = root.findFocus() ?: return
        when (currentFocusedSection()) {
            FocusSection.HERO_ACTION -> rememberFocus(FocusSection.HERO_ACTION)
            FocusSection.PRIMARY_CONTENT -> {
                if (currentSurfaceMode == HomeSurfaceMode.ONLINE) {
                    rememberFeaturedFocus(
                        focusedView.findAdapterPosition(featuredRailView) ?: lastFeaturedFocusPosition,
                    )
                } else {
                    rememberWifiFocus(
                        focusedView.findAdapterPosition(wifiListView) ?: lastWifiFocusPosition,
                    )
                }
            }

            FocusSection.CAST_STANDBY -> rememberFocus(FocusSection.CAST_STANDBY)
            FocusSection.WIFI_ACTIONS -> rememberFocus(FocusSection.WIFI_ACTIONS)
            FocusSection.QUICK_ACTIONS -> rememberQuickActionFocus(
                focusedView.findAdapterPosition(quickActionRailView) ?: lastQuickActionFocusPosition,
            )

            FocusSection.LOCAL_APPS_CLOSE -> rememberFocus(FocusSection.LOCAL_APPS_CLOSE)
            FocusSection.LOCAL_APPS_LIST -> rememberLocalAppsFocus(
                focusedView.findAdapterPosition(localAppsListView) ?: lastLocalAppFocusPosition,
            )

            null -> Unit
        }
    }

    private fun buildRoundedBackground(
        fillColor: Int,
        strokeColor: Int,
        cornerRadiusDp: Float,
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = cornerRadiusDp * requireContext().resources.displayMetrics.density
            setColor(fillColor)
            setStroke(
                requireContext().resources.displayMetrics.density.toInt().coerceAtLeast(1),
                strokeColor,
            )
        }
    }

    private fun View.updateVerticalLayoutParams(
        height: Int,
        weight: Float,
        topMargin: Int? = null,
    ) {
        val params = layoutParams as? LinearLayout.LayoutParams ?: return
        params.height = height
        params.weight = weight
        topMargin?.let { params.topMargin = it }
        layoutParams = params
    }

    private fun View.updateBoxLayoutParams(
        width: Int,
        height: Int,
        marginStart: Int? = null,
    ) {
        val params = layoutParams as? LinearLayout.LayoutParams ?: return
        params.width = width
        params.height = height
        marginStart?.let { params.marginStart = it }
        layoutParams = params
    }

    private fun View.updateFrameLayoutParams(
        width: Int,
        height: Int,
        marginTop: Int? = null,
        marginEnd: Int? = null,
        marginBottom: Int? = null,
    ) {
        val params = layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = width
        params.height = height
        marginTop?.let { params.topMargin = it }
        marginEnd?.let { params.marginEnd = it }
        marginBottom?.let { params.bottomMargin = it }
        layoutParams = params
    }

    private fun Float.dpToPx(): Int {
        return (this * requireContext().resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ARG_PLATFORM_BASE_URL = "platform_base_url"
        private const val ARG_ENABLE_REMOTE_CONFIG = "enable_remote_config"
        private const val ARG_DEBUG_FORCE_OFFLINE = "debug_force_offline"
        private const val STATE_LAST_FOCUSED_SECTION = "last_focused_section"
        private const val STATE_LAST_FEATURED_FOCUS_POSITION = "last_featured_focus_position"
        private const val STATE_LAST_WIFI_FOCUS_POSITION = "last_wifi_focus_position"
        private const val STATE_LAST_QUICK_ACTION_FOCUS_POSITION = "last_quick_action_focus_position"
        private const val STATE_LAST_LOCAL_APP_FOCUS_POSITION = "last_local_app_focus_position"
        private const val HERO_AD_ROTATION_INTERVAL_MS = 4_500L
        private const val ASSISTANT_TALK_ANIMATION_MS = 1_600L
        private const val HERO_AD_ACTION_DEEPLINK = "deeplink"
        private const val HERO_AD_ACTION_URL = "url"
        private const val APP_DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS = 2_000L
        private const val DLNA_START_DELAY_MS = 3_000L
        private const val LEBO_DISCOVERY_SERVICE_START_DELAY_MS = 2_000L
        private const val LEBO_RETURN_GUARD_INITIAL_DELAY_MS = 4_000L
        private const val LEBO_RETURN_GUARD_POLL_INTERVAL_MS = 1_500L
        private const val LEBO_RETURN_GUARD_RECLAIM_COOLDOWN_MS = 6_000L
        private const val LEBO_RETURN_AFTER_HOME_STOP_DELAY_MS = 5_000L
        private const val LEBO_RETURN_USER_LAUNCH_ARM_WINDOW_MS = 3_000L
        private const val LEBO_IDLE_FALLBACK_TRIM_DELAY_MS = 60_000L
        private const val CAST_BACKGROUND_TRIM_DELAY_MS = 1_500L
        private const val CAST_URI_LOG_LIMIT = 180
        private const val CAST_TAG = "OpenClawCast"
        private const val TRIM_LOG_PACKAGE_LIMIT = 18
        private const val TRIM_TAG = "OpenClawTrim"
        private const val HOME_BACKGROUND_TRIM_DELAY_MS = 1_200L
        private const val PROC_NET_TCP = "/proc/net/tcp"
        private const val PROC_NET_TCP_REMOTE_COLUMN_INDEX = 2
        private const val PROC_NET_TCP_STATE_COLUMN_INDEX = 3
        private const val PROC_NET_TCP_UID_COLUMN_INDEX = 7
        private const val PROC_NET_TCP_ESTABLISHED_STATE = "01"
        private const val PROC_NET_TCP_EMPTY_IPV4_HEX = "00000000"
        private const val CAPABILITY_REFRESH_DELAY_MS = 3_000L
        private const val NETWORK_REFRESH_DELAY_MS = 1_000L
        private const val INSTALLED_APPS_REFRESH_DELAY_MS = 1_000L
        private const val LEBO_CAST_PRIMARY_PACKAGE = "com.hpplay.happyplay.aw"
        private const val LEBO_CAST_APP_ACTION = "android.intent.action.START_LEBO_APP"
        private const val LEBO_CAST_SERVER_ACTION = "android.intent.action.START_LEBO_SERVER"
        private const val LEBO_CAST_SERVICE_ACTION = "android.intent.action.START_LEBO_SERVICE"
        private const val LEBO_CAST_RECEIVER_ACTION = "android.intent.action.START_LEBO_RECEIVER"
        private const val LEBO_CAST_SERVICE_CLASS = "com.hpplay.happyplay.aw.AirPlayService"
        private const val LEBO_CAST_RECEIVER_CLASS = "com.hpplay.happyplay.bootBroadcast"
        private const val LEBO_CAST_DISPLAY_NAME = "投屏电视H2"
        private val LEBO_RETURN_HOME_ACTIVITY_CLASSES = setOf(
            "com.hpplay.happyplay.aw.WelcomeActivity",
            "com.hpplay.happyplay.aw.app.StartActivity",
            "com.hpplay.happyplay.main.app.MainActivity",
            "com.hpplay.happyplay.lib.app.TipActivity",
            "com.hpplay.sdk.sink.business.TipActivity",
        )
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private val LEBO_CAST_PACKAGES = listOf(
            "com.hpplay.happyplay.aw",
            "com.hpplay.happycast",
            "com.hpplay.happyplay",
        )
        private val HOME_BACKGROUND_TRIM_PACKAGE_ALLOWLIST = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.inputmethod.latin",
        )
        private val CAST_BACKGROUND_PLAYBACK_PACKAGES = setOf(
            "com.xiaodianshi.tv.yst",
            "com.ktcp.tvvideo",
            "com.ktcp.video",
            "com.gitvjisu.video",
            "com.qiyi.video.tv.ele",
            "com.youku.iot",
            "com.cibn.tv",
            "tv.danmaku.bili",
            "com.starcor.mango",
            "com.mgtv.tv",
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.plexapp.android",
        )
        private val APK_PICKER_MIME_TYPES = arrayOf(
            APK_MIME_TYPE,
            "application/octet-stream",
            "application/x-android-package-archive",
        )
        private val APP_STORE_PACKAGE_CANDIDATES = listOf(
            "com.mjapk.store",
        )

        fun newInstance(
            platformBaseUrl: String? = null,
            enableRemoteConfig: Boolean = true,
            debugForceOffline: Boolean = false,
        ): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLATFORM_BASE_URL, platformBaseUrl)
                    putBoolean(ARG_ENABLE_REMOTE_CONFIG, enableRemoteConfig)
                    putBoolean(ARG_DEBUG_FORCE_OFFLINE, debugForceOffline)
                }
            }
        }
    }
}

private enum class FocusSection {
    HERO_ACTION,
    PRIMARY_CONTENT,
    CAST_STANDBY,
    WIFI_ACTIONS,
    QUICK_ACTIONS,
    LOCAL_APPS_CLOSE,
    LOCAL_APPS_LIST,
}

internal data class PendingInstallRequest(
    val appId: String,
    val title: String,
    val downloadId: Long?,
    val localFilePath: String?,
)

private fun PendingInstallRequest.isLocalApkInstall(): Boolean {
    return appId.startsWith(LOCAL_APK_INSTALL_PREFIX)
}

internal fun Bundle.putPendingInstallRequestState(
    request: PendingInstallRequest?,
) {
    if (request == null) {
        remove(STATE_PENDING_INSTALL_APP_ID)
        remove(STATE_PENDING_INSTALL_TITLE)
        remove(STATE_PENDING_INSTALL_DOWNLOAD_ID)
        remove(STATE_PENDING_INSTALL_LOCAL_FILE_PATH)
        return
    }
    putString(STATE_PENDING_INSTALL_APP_ID, request.appId)
    putString(STATE_PENDING_INSTALL_TITLE, request.title)
    request.downloadId?.let { putLong(STATE_PENDING_INSTALL_DOWNLOAD_ID, it) }
    putString(STATE_PENDING_INSTALL_LOCAL_FILE_PATH, request.localFilePath)
}

internal fun Bundle.readPendingInstallRequestState(): PendingInstallRequest? {
    val appId = getString(STATE_PENDING_INSTALL_APP_ID)?.trim().orEmpty()
    val title = getString(STATE_PENDING_INSTALL_TITLE)?.trim().orEmpty()
    if (appId.isBlank() || title.isBlank()) {
        return null
    }
    return PendingInstallRequest(
        appId = appId,
        title = title,
        downloadId = if (containsKey(STATE_PENDING_INSTALL_DOWNLOAD_ID)) {
            getLong(STATE_PENDING_INSTALL_DOWNLOAD_ID)
        } else {
            null
        },
        localFilePath = getString(STATE_PENDING_INSTALL_LOCAL_FILE_PATH)?.trim()?.takeIf(String::isNotBlank),
    )
}

private fun View.isWithin(container: View?): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === container) {
            return true
        }
        current = current.parent as? View
    }
    return false
}

private fun View.findAdapterPosition(rail: RecyclerView?): Int? {
    if (rail == null) {
        return null
    }
    var current: View? = this
    while (current != null) {
        if (current.parent === rail) {
            val adapterPosition = rail.getChildViewHolder(current).bindingAdapterPosition
            return adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
        }
        current = current.parent as? View
    }
    return null
}

private fun FeaturedAppItem.canRequestDownload(): Boolean {
    return downloadUrl.isNotBlank() && sha256.isNotBlank() && versionCode > 0L
}

private const val STATE_PENDING_INSTALL_APP_ID = "pending_install_app_id"
private const val STATE_PENDING_INSTALL_TITLE = "pending_install_title"
private const val STATE_PENDING_INSTALL_DOWNLOAD_ID = "pending_install_download_id"
private const val STATE_PENDING_INSTALL_LOCAL_FILE_PATH = "pending_install_local_file_path"
private const val STATE_PENDING_PICKED_INSTALL_URI = "pending_picked_install_uri"
private const val LOCAL_APK_INSTALL_PREFIX = "local_apk:"

private fun RecyclerView.syncChildFocusTargets(bridge: RailChildFocusBridge) {
    repeat(childCount) { index ->
        bridge.onChildViewAttachedToWindow(getChildAt(index))
    }
}

private class RailChildFocusBridge(
    var nextFocusUpId: Int = View.NO_ID,
    var nextFocusDownId: Int = View.NO_ID,
    var nextFocusLeftId: Int = View.NO_ID,
    var nextFocusRightId: Int = View.NO_ID,
) : RecyclerView.OnChildAttachStateChangeListener {
    override fun onChildViewAttachedToWindow(view: View) {
        if (nextFocusUpId != View.NO_ID) {
            view.nextFocusUpId = nextFocusUpId
        }
        if (nextFocusDownId != View.NO_ID) {
            view.nextFocusDownId = nextFocusDownId
        }
        if (nextFocusLeftId != View.NO_ID) {
            view.nextFocusLeftId = nextFocusLeftId
        }
        if (nextFocusRightId != View.NO_ID) {
            view.nextFocusRightId = nextFocusRightId
        }
    }

    override fun onChildViewDetachedFromWindow(view: View) = Unit
}
