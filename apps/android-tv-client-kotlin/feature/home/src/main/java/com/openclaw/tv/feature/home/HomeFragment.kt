package com.openclaw.tv.feature.home

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.openclaw.tv.core.capability.CapabilityDetector
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel by viewModels<HomeViewModel> {
        HomeViewModel.factory(
            applicationContext = requireContext().applicationContext,
            platformBaseUrl = arguments?.getString(ARG_PLATFORM_BASE_URL),
            enableRemoteConfig = arguments?.getBoolean(ARG_ENABLE_REMOTE_CONFIG) ?: true,
        )
    }
    private val featuredAdapter = AppRailAdapter()
    private val quickActionAdapter = QuickActionAdapter()
    private val wifiAdapter = WifiListAdapter()
    private val localAppsAdapter = InstalledAppsAdapter()
    private var appLauncher: AppLauncher? = null
    private var networkSnapshotProvider: HomeNetworkSnapshotProvider? = null
    private var installedAppCatalogProvider: InstalledAppCatalogProvider? = null
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
    private var quickActionRailView: RecyclerView? = null
    private var localAppsOverlayView: View? = null
    private var localAppsCloseButtonView: Button? = null
    private var localAppsEmptyStateView: TextView? = null
    private var localAppsListView: RecyclerView? = null
    private var currentSurfaceMode = HomeSurfaceMode.OFFLINE
    private var currentFeaturedVisible = false
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
    private var activeHeroAds: List<HeroAdItem> = emptyList()
    private var currentHeroAdIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreFocusMemory(savedInstanceState)
        rootView = view
        val backgroundImage = view.findViewById<ImageView>(R.id.background_image)
        val brandTitle = view.findViewById<TextView>(R.id.brand_title)
        val wifiStatusIcon = view.findViewById<ImageView>(R.id.wifi_status_icon)
        val wifiStatusText = view.findViewById<TextView>(R.id.wifi_status_text)
        val heroCard = view.findViewById<View>(R.id.hero_card)
        val assistantGlow = view.findViewById<View>(R.id.assistant_glow)
        val assistantHair = view.findViewById<View>(R.id.assistant_hair)
        val assistantRibbonLeft = view.findViewById<View>(R.id.assistant_ribbon_left)
        val assistantRibbonRight = view.findViewById<View>(R.id.assistant_ribbon_right)
        val assistantSideLockLeft = view.findViewById<View>(R.id.assistant_side_lock_left)
        val assistantSideLockRight = view.findViewById<View>(R.id.assistant_side_lock_right)
        val assistantHairBang = view.findViewById<View>(R.id.assistant_hair_bang)
        val assistantDress = view.findViewById<View>(R.id.assistant_dress)
        val assistantChip = view.findViewById<TextView>(R.id.assistant_chip)
        val assistantSkirt = view.findViewById<View>(R.id.assistant_skirt)
        val modeChip = view.findViewById<TextView>(R.id.mode_chip)
        val heroDialogue = view.findViewById<TextView>(R.id.hero_dialogue)
        val heroHint = view.findViewById<TextView>(R.id.hero_hint)
        val heroAdCard = view.findViewById<LinearLayout>(R.id.hero_ad_card)
        val heroAdImage = view.findViewById<ImageView>(R.id.hero_ad_image)
        val heroAdCaption = view.findViewById<TextView>(R.id.hero_ad_caption)
        val heroAdIndex = view.findViewById<TextView>(R.id.hero_ad_index)
        val noticeCard = view.findViewById<LinearLayout>(R.id.notice_card)
        val noticeTitle = view.findViewById<TextView>(R.id.notice_title)
        val noticeBody = view.findViewById<TextView>(R.id.notice_body)
        val tokenButton = view.findViewById<Button>(R.id.token_button)
        val featuredSectionTitle = view.findViewById<TextView>(R.id.featured_section_title)
        val featuredRail = view.findViewById<RecyclerView>(R.id.featured_rail)
        val wifiSection = view.findViewById<LinearLayout>(R.id.wifi_section)
        val wifiSectionTitle = view.findViewById<TextView>(R.id.wifi_section_title)
        val wifiGuideText = view.findViewById<TextView>(R.id.wifi_guide_text)
        val wifiActionCard = view.findViewById<View>(R.id.wifi_action_card)
        val wifiSelectedName = view.findViewById<TextView>(R.id.wifi_selected_name)
        val wifiSelectedSummary = view.findViewById<TextView>(R.id.wifi_selected_summary)
        val wifiConnectButton = view.findViewById<Button>(R.id.wifi_connect_button)
        val wifiSystemSettingsButton = view.findViewById<Button>(R.id.wifi_system_settings_button)
        val wifiRefreshButton = view.findViewById<Button>(R.id.wifi_refresh_button)
        val wifiEmptyState = view.findViewById<TextView>(R.id.wifi_empty_state)
        val wifiList = view.findViewById<RecyclerView>(R.id.wifi_list)
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
        appLauncher = AppLauncher(requireContext())
        networkSnapshotProvider = HomeNetworkSnapshotProvider(requireContext())
        installedAppCatalogProvider = InstalledAppCatalogProvider(requireContext())

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
        localAppsAdapter.setOnItemClickListener(::launchInstalledLocalApp)
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

        val detector = CapabilityDetector(requireContext())
        viewModel.bindCapabilities(
            detector.snapshot(
                targetPackages = HomeAppCatalog.allPackageNames(),
            ),
        )
        networkSnapshotProvider?.snapshot()?.let(viewModel::bindNetworkSnapshot)
        val runtimeOwner = requireContext().applicationContext as? BootstrapRuntimeOwner
        if (runtimeOwner != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runtimeOwner.bootstrapRuntime.state.collect(viewModel::bindBootstrapState)
                }
            }
        }
        viewModel.loadRemoteConfig()

        tokenButton.setOnClickListener {
            Toast.makeText(requireContext(), "服务入口下一轮接后台能力。", Toast.LENGTH_SHORT).show()
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
            currentSelectedWifiItem?.let(::connectSelectedWifi)
        }
        wifiSystemSettingsButton.setOnClickListener {
            currentSelectedWifiItem?.let(::openWifiSettings)
        }
        wifiRefreshButton.setOnClickListener {
            refreshWifiNetworks(manual = true)
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
                    bindBackground(backgroundImage, state.backgroundImageUrl)
                    brandTitle.text = state.brandTitle
                    wifiStatusText.text = state.wifiLabel
                    bindWifiClusterVisuals(
                        wifiStatusIcon = wifiStatusIcon,
                        wifiStatusText = wifiStatusText,
                        connected = state.wifiConnected,
                    )
                    bindHeroVisualState(
                        heroCard = heroCard,
                        assistantGlow = assistantGlow,
                        assistantHair = assistantHair,
                        assistantRibbonLeft = assistantRibbonLeft,
                        assistantRibbonRight = assistantRibbonRight,
                        assistantSideLockLeft = assistantSideLockLeft,
                        assistantSideLockRight = assistantSideLockRight,
                        assistantHairBang = assistantHairBang,
                        assistantDress = assistantDress,
                        assistantChip = assistantChip,
                        assistantSkirt = assistantSkirt,
                        serviceButton = tokenButton,
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
                    tokenButton.text = state.tokenLabel
                    noticeCard.visibility = if (state.noticeVisible) View.VISIBLE else View.GONE
                    noticeTitle.text = state.noticeTitle
                    noticeBody.text = state.noticeBody
                    bindNoticeCardVisuals(noticeCard, state.statusTone)

                    featuredSectionTitle.text = state.featuredSectionTitle
                    featuredSectionTitle.visibility = if (state.featuredVisible) View.VISIBLE else View.GONE
                    featuredRail.visibility = if (state.featuredVisible) View.VISIBLE else View.GONE
                    featuredAdapter.submitList(state.featuredApps)

                    wifiSection.visibility = if (state.wifiSectionVisible) View.VISIBLE else View.GONE
                    wifiSectionTitle.text = state.wifiSectionTitle
                    wifiGuideText.text = state.wifiGuideText
                    wifiAdapter.submitList(state.wifiNetworks)
                    wifiEmptyState.visibility = if (state.wifiSectionVisible && state.wifiNetworks.isEmpty()) View.VISIBLE else View.GONE
                    wifiEmptyState.text = state.wifiEmptyText
                    resolveWifiSelection(state.wifiNetworks)
                    bindWifiActionCard()

                    quickActionSectionTitle.text = state.quickActionSectionTitle
                    quickActionAdapter.submitList(state.quickActions)

                    currentFeaturedVisible = state.featuredVisible
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
                        localAppsCloseButton = localAppsCloseButton,
                        localAppsList = localAppsList,
                    )
                    restoreFocusIfNeeded()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWifiNetworks(manual = false)
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
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        appLauncher = null
        networkSnapshotProvider = null
        installedAppCatalogProvider = null
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
        activeHeroAds = emptyList()
        currentHeroAdIndex = 0
        super.onDestroyView()
    }

    private fun bindBackground(backgroundImage: ImageView, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) {
            backgroundImage.setImageDrawable(null)
            return
        }
        backgroundImage.load(imageUrl) {
            crossfade(false)
            allowHardware(false)
        }
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

    private fun bindHeroAds(
        heroAdCard: LinearLayout,
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
        renderHeroAd(heroAdImage, heroAdCaption, heroAdIndex)

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
                renderHeroAd(heroAdImage, heroAdCaption, heroAdIndex)
            }
        }
    }

    private fun renderHeroAd(
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
    }

    private fun bindHeroVisualState(
        heroCard: View,
        assistantGlow: View,
        assistantHair: View,
        assistantRibbonLeft: View,
        assistantRibbonRight: View,
        assistantSideLockLeft: View,
        assistantSideLockRight: View,
        assistantHairBang: View,
        assistantDress: View,
        assistantChip: TextView,
        assistantSkirt: View,
        serviceButton: Button,
        surfaceMode: HomeSurfaceMode,
    ) {
        val offline = surfaceMode == HomeSurfaceMode.OFFLINE
        heroCard.setBackgroundResource(
            if (offline) R.drawable.bg_home_hero_card_offline else R.drawable.bg_home_hero_card,
        )
        assistantGlow.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_glow_offline else R.drawable.bg_assistant_glow,
        )
        assistantHair.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_hair_offline else R.drawable.bg_assistant_hair,
        )
        assistantRibbonLeft.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_ribbon_offline else R.drawable.bg_assistant_ribbon,
        )
        assistantRibbonRight.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_ribbon_offline else R.drawable.bg_assistant_ribbon,
        )
        assistantSideLockLeft.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_side_lock_offline else R.drawable.bg_assistant_side_lock,
        )
        assistantSideLockRight.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_side_lock_offline else R.drawable.bg_assistant_side_lock,
        )
        assistantHairBang.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_hair_bang_offline else R.drawable.bg_assistant_hair_bang,
        )
        assistantDress.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_dress_offline else R.drawable.bg_assistant_dress,
        )
        assistantSkirt.setBackgroundResource(
            if (offline) R.drawable.bg_assistant_skirt_offline else R.drawable.bg_assistant_skirt,
        )
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
        serviceButton.setTextColor(
            Color.parseColor(if (offline) "#EDF5FB" else "#1B2029"),
        )
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
        val result = appLauncher?.launch(item.packageName) ?: return
        val message = when (result) {
            AppLaunchResult.Launched -> null
            AppLaunchResult.NotInstalled -> getString(R.string.feature_home_app_not_installed, item.title)
            AppLaunchResult.NoLaunchActivity -> getString(R.string.feature_home_app_unavailable, item.title)
        }
        if (message != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchInstalledLocalApp(item: InstalledLaunchableAppItem) {
        val result = appLauncher?.launch(item.packageName) ?: return
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

    private fun handleQuickAction(item: QuickActionItem) {
        when (item.id) {
            HomeViewModel.QUICK_ACTION_LOCAL -> openLocalFiles()
            HomeViewModel.QUICK_ACTION_CAST -> openCastSettings()
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

    private fun refreshWifiNetworks(manual: Boolean) {
        networkSnapshotProvider?.snapshot()?.let(viewModel::bindNetworkSnapshot)
        if (manual) {
            Toast.makeText(requireContext(), "已刷新 Wi-Fi 列表。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSystemSettings() {
        if (!openIntent(listOf(Intent(Settings.ACTION_SETTINGS)))) {
            Toast.makeText(requireContext(), "当前设备无法打开系统设置。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCastSettings() {
        val opened = openIntent(
            listOf(
                Intent("android.settings.CAST_SETTINGS"),
                Intent(Settings.ACTION_CAST_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            ),
        )
        if (!opened) {
            Toast.makeText(requireContext(), "当前设备没有可用的投屏设置入口。", Toast.LENGTH_SHORT).show()
        }
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
        val localApps = installedAppCatalogProvider?.loadLaunchableApps().orEmpty()
        localAppsAdapter.submitList(localApps)
        currentLocalAppCount = localApps.size
        lastLocalAppFocusPosition = lastLocalAppFocusPosition.coerceIn(0, (currentLocalAppCount - 1).coerceAtLeast(0))
        localAppsEmptyStateView?.visibility = if (localApps.isEmpty()) View.VISIBLE else View.GONE
        localAppsListView?.visibility = if (localApps.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun closeLocalAppsOverlay(restoreQuickActionFocus: Boolean = true): Boolean {
        if (!currentLocalAppsVisible) {
            return false
        }
        currentLocalAppsVisible = false
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

    private fun openIntent(candidates: List<Intent>): Boolean {
        val packageManager = requireContext().packageManager
        val targetIntent = candidates
            .map { intent -> intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { intent -> intent.resolveActivity(packageManager) != null }
            ?: return false
        startActivity(targetIntent)
        return true
    }

    private fun configureWifiActionButtons(
        wifiConnectButton: Button,
        wifiSystemSettingsButton: Button,
        wifiRefreshButton: Button,
        wifiList: RecyclerView,
        quickActionRail: RecyclerView,
    ) {
        wifiConnectButton.nextFocusRightId = wifiSystemSettingsButton.id
        wifiConnectButton.nextFocusUpId = wifiList.id
        wifiConnectButton.nextFocusDownId = quickActionRail.id
        wifiSystemSettingsButton.nextFocusLeftId = wifiConnectButton.id
        wifiSystemSettingsButton.nextFocusRightId = wifiRefreshButton.id
        wifiSystemSettingsButton.nextFocusUpId = wifiList.id
        wifiSystemSettingsButton.nextFocusDownId = quickActionRail.id
        wifiRefreshButton.nextFocusLeftId = wifiSystemSettingsButton.id
        wifiRefreshButton.nextFocusUpId = wifiList.id
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
        val selectedWifi = if (currentSurfaceMode == HomeSurfaceMode.OFFLINE) currentSelectedWifiItem else null
        currentWifiActionVisible = selectedWifi != null
        wifiActionCardView?.visibility = if (selectedWifi != null) View.VISIBLE else View.GONE
        wifiSelectedNameView?.text = selectedWifi?.ssid.orEmpty()
        wifiSelectedSummaryView?.text = selectedWifi?.let {
            buildString {
                append(it.summary)
                append("。先连接这个网络，再继续使用节目入口与语音能力。")
            }
        }.orEmpty()
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
        localAppsCloseButton: Button,
        localAppsList: RecyclerView,
    ) {
        featuredRailFocusBridge.nextFocusUpId = tokenButton.id
        featuredRailFocusBridge.nextFocusDownId = quickActionRail.id
        featuredRailFocusBridge.nextFocusLeftId = View.NO_ID
        featuredRailFocusBridge.nextFocusRightId = View.NO_ID

        wifiRailFocusBridge.nextFocusUpId = tokenButton.id
        wifiRailFocusBridge.nextFocusDownId = View.NO_ID
        wifiRailFocusBridge.nextFocusLeftId = View.NO_ID
        wifiRailFocusBridge.nextFocusRightId = if (currentWifiActionVisible) wifiConnectButton.id else View.NO_ID

        quickActionRailFocusBridge.nextFocusUpId = when {
            currentSurfaceMode == HomeSurfaceMode.OFFLINE && currentWifiActionVisible -> wifiConnectButton.id
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
                requestFocusForPrimaryContent() -> return true
            }
        }
        val targetSection = when (sourceSection) {
            FocusSection.QUICK_ACTIONS -> FocusSection.HERO_ACTION
            FocusSection.WIFI_ACTIONS -> FocusSection.PRIMARY_CONTENT
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

    companion object {
        private const val ARG_PLATFORM_BASE_URL = "platform_base_url"
        private const val ARG_ENABLE_REMOTE_CONFIG = "enable_remote_config"
        private const val STATE_LAST_FOCUSED_SECTION = "last_focused_section"
        private const val STATE_LAST_FEATURED_FOCUS_POSITION = "last_featured_focus_position"
        private const val STATE_LAST_WIFI_FOCUS_POSITION = "last_wifi_focus_position"
        private const val STATE_LAST_QUICK_ACTION_FOCUS_POSITION = "last_quick_action_focus_position"
        private const val STATE_LAST_LOCAL_APP_FOCUS_POSITION = "last_local_app_focus_position"
        private const val HERO_AD_ROTATION_INTERVAL_MS = 4_500L

        fun newInstance(
            platformBaseUrl: String? = null,
            enableRemoteConfig: Boolean = true,
        ): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLATFORM_BASE_URL, platformBaseUrl)
                    putBoolean(ARG_ENABLE_REMOTE_CONFIG, enableRemoteConfig)
                }
            }
        }
    }
}

private enum class FocusSection {
    HERO_ACTION,
    PRIMARY_CONTENT,
    WIFI_ACTIONS,
    QUICK_ACTIONS,
    LOCAL_APPS_CLOSE,
    LOCAL_APPS_LIST,
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
