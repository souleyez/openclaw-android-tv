// ignore_for_file: unused_field, unused_element

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_client.dart';
import '../../core/app_state.dart';
import '../account/current_user_profile.dart';
import '../avatar/avatar_profile.dart';
import '../billing/billing_order_preview.dart';
import '../billing/submit_tx_hash_request.dart';
import '../control/control_action.dart';
import '../control/offline_local_router.dart';
import '../control/control_service.dart';
import '../conversation/assistant_log_record.dart';
import '../conversation/conversation_turn.dart';
import '../conversation/control_log_entry.dart';
import '../conversation/router_status.dart';
import '../device_sync/registered_device.dart';
import '../network/network_snapshot.dart';
import '../network/network_status_service.dart';
import 'tv_home_config.dart';
import 'models/tv_home_offline_navigation.dart';
import 'tv_home_dialogs.dart';
import 'models/tv_home_shortcut.dart';
import 'tv_home_controller.dart';
import 'tv_home_view_helpers.dart';
import '../voice/voice_command_service.dart';
import 'widgets/tv_home_avatar_surface.dart';
import 'widgets/tv_home_conversation_surface.dart';
import 'widgets/tv_home_footer_status_bar.dart';
import 'widgets/tv_home_shortcuts_surface.dart';
import 'widgets/tv_home_top_toolbar.dart';

class TvHomePage extends StatefulWidget {
  const TvHomePage({super.key});

  @override
  State<TvHomePage> createState() => _TvHomePageState();
}

class _TvHomePageState extends State<TvHomePage> {
  final ApiClient _apiClient = const ApiClient();
  final VoiceCommandService _voiceService = const VoiceCommandService();
  final ControlService _controlService = const ControlService();
  final OfflineLocalRouter _offlineLocalRouter = const OfflineLocalRouter();
  final NetworkStatusService _networkStatusService = const NetworkStatusService();
  final FocusNode _pressToTalkFocusNode = FocusNode(debugLabel: 'press_to_talk');
  late final TvHomeController _tvHomeController;
  Timer? _billingPoller;

  AppState _appState = const AppState(
    selectedLanguage: 'English',
    selectedAvatar: 'Warm Female',
    subscriptionLabel: 'Family Plan',
    tokenBalance: 128000,
    backgroundStandbyEnabled: true,
  );

  String _subtitle = 'Press the microphone button to control your TV';
  String _channelStatus = 'Waiting for native bridge';
  String _executionStatus = 'Idle';
  String _pendingCommand = 'Standby ready for the next command.';
  final String _activeLogView = 'conversation';
  final String _selectedAccessScope = 'household';
  bool _isHandlingVoice = false;
  bool _isCreatingTopUp = false;
  bool _isSubmittingTxHash = false;
  bool _isRefreshingConfirmations = false;
  bool _isRecoveringRights = false;
  bool _backendConnected = false;
  String _voiceCapabilityStatus = 'Checking';
  int _offlineHomeActionIndex = 0;
  int _offlineWifiHighlightIndex = 0;
  int _focusedAppShortcutIndex = 0;
  bool _systemHotwordPrivilege = false;
  String? _backgroundImageUrl;
  CurrentUserProfile? _profile;
  AvatarProfile? _avatarProfile;
  List<AvatarProfile> _avatarProfiles = const [];
  List<TvHomeShortcut> _featuredShortcuts = const [];
  RouterStatus _routerStatus = const RouterStatus(
    provider: 'mock_llm_router',
    configured: false,
    baseUrl: 'local_fallback',
    model: 'local_mock',
  );
  List<RegisteredDevice> _devices = const [];
  NetworkSnapshot _networkSnapshot = NetworkSnapshot.fallback;
  List<BillingOrderPreview> _billingOrders = const [];
  List<ConversationTurn> _conversation = const [
    ConversationTurn(
      speaker: 'assistant',
      text: 'Welcome to OpenClaw TV. Press to talk when you are ready.',
      metadata: 'boot',
    ),
  ];
  List<ControlLogEntry> _controlLog = const [
    ControlLogEntry(
      target: 'system',
      action: 'boot',
      status: 'ready',
      strategy: 'dashboard_init',
    ),
  ];

  @override
  void initState() {
    super.initState();
    _tvHomeController = TvHomeController(
      apiClient: _apiClient,
      voiceCommandService: _voiceService,
      networkStatusService: _networkStatusService,
      controlService: _controlService,
      offlineLocalRouter: _offlineLocalRouter,
    );
    _loadDashboard();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _pressToTalkFocusNode.requestFocus();
      }
    });
    _billingPoller = Timer.periodic(
      const Duration(seconds: 20),
      (_) => _pollBillingOrders(),
    );
  }

  @override
  void dispose() {
    _billingPoller?.cancel();
    _pressToTalkFocusNode.dispose();
    super.dispose();
  }

  Future<void> _loadDashboard() async {
    final dashboard = await _tvHomeController.loadDashboard(
      deviceRegistration: tvHomeDeviceRegistration,
    );
    if (!mounted) {
      return;
    }

    setState(() {
      _profile = dashboard.profile;
      _avatarProfile = dashboard.avatarProfile;
      _avatarProfiles = dashboard.avatarProfiles;
      _backendConnected = dashboard.backendConnected;
      _routerStatus = dashboard.routerStatus;
      _networkSnapshot = dashboard.networkSnapshot;
      _backgroundImageUrl = dashboard.homeConfig.backgroundImageUrl;
      _featuredShortcuts = resolveTvHomeFeaturedShortcuts(
        dashboard.homeConfig.featuredAppIds,
      );
      _offlineHomeActionIndex = normalizedOfflineHomeActionIndex(
        currentIndex: _offlineHomeActionIndex,
        actionCount: tvHomeOfflinePrimaryActions.length,
      );
      _offlineWifiHighlightIndex = normalizedWifiHighlightIndex(
        networks: dashboard.networkSnapshot.visibleNetworks,
        currentIndex: _offlineWifiHighlightIndex,
      );
      _voiceCapabilityStatus =
          '${formatVoiceCapabilityStatus(dashboard.speechStatus)}${dashboard.standbyStatus.systemHotwordPrivilege ? ' | privileged' : ''}';
      _systemHotwordPrivilege = dashboard.standbyStatus.systemHotwordPrivilege;
      _devices = dashboard.devices.any(
            (device) => device.deviceUuid == dashboard.registeredDevice.deviceUuid,
          )
          ? dashboard.devices
          : <RegisteredDevice>[
              dashboard.registeredDevice,
              ...dashboard.devices,
            ];
      _billingOrders = dashboard.billingOrders;
      _appState = _appState.copyWith(
        subscriptionLabel: formatPlan(dashboard.profile.plan),
        tokenBalance: dashboard.profile.tokenBalance,
        selectedAvatar: dashboard.avatarProfile.avatarLabel,
        backgroundStandbyEnabled: dashboard.standbyStatus.enabled,
      );
      _pendingCommand = dashboard.standbyStatus.statusText;
      if (dashboard.routerLogs.isNotEmpty) {
        _conversation = _mapConversationLogs(dashboard.routerLogs);
        _controlLog = _mapControlLogs(dashboard.routerLogs);
      }
    });

    await _consumePendingWakeEvent();
    if (dashboard.backendConnected && dashboard.networkSnapshot.isConnected) {
      unawaited(_syncOtaManifest());
    }
    unawaited(_pollBillingOrders(forceRefresh: false));
  }

  Future<void> _syncOtaManifest() async {
    await _tvHomeController.syncOtaManifest(
      deviceUuid: tvHomeDeviceUuid,
      currentVersionCode: tvHomeCurrentVersionCode,
    );
  }

  Future<void> _handleVoicePress() async {
    setState(() {
      _isHandlingVoice = true;
      _subtitle = 'Listening...';
      _executionStatus = 'Listening';
      _pendingCommand = 'Wake channel open. Waiting for speech...';
    });

    final voiceResult = await _tvHomeController.pressToTalk(
      selectedLanguage: _appState.selectedLanguage,
    );
    if (!mounted) {
      return;
    }

    setState(() {
      _subtitle = 'Received: ${voiceResult.recognizedText}';
      _executionStatus = 'Command received';
      _pendingCommand = 'Recognized "${voiceResult.recognizedText}". Understanding intent...';
      _channelStatus =
          'Voice: ${voiceResult.provider} | Feedback: ${voiceResult.feedbackText}';
    });
    if (_appState.backgroundStandbyEnabled) {
      unawaited(
        _tvHomeController.updateStandbyExecutionState(
          'Command received: ${voiceResult.recognizedText}',
        ),
      );
    }

    final voiceResolution = await _tvHomeController.resolveVoiceCommand(
      recognizedText: voiceResult.recognizedText,
      networkSnapshot: _networkSnapshot,
      selectedLanguage: _appState.selectedLanguage,
      deviceUuid: tvHomeDeviceUuid,
    );
    final offlineMode = voiceResolution.offlineMode;
    final resolution = voiceResolution.resolution;
    final effectiveOfflineNavigation = offlineMode
        ? consumeOfflineHomeNavigation(
            isOfflineHomeMode: _homeSurfaceMode() == _HomeSurfaceMode.offline,
            resolutionAction: resolution.action,
            currentOfflineHomeActionIndex: _offlineHomeActionIndex,
            offlinePrimaryActions: tvHomeOfflinePrimaryActions,
            normalizeOfflineHomeActionIndex: normalizedOfflineHomeActionIndex,
          )
        : null;
    if (effectiveOfflineNavigation != null) {
      _offlineHomeActionIndex = effectiveOfflineNavigation.focusedActionIndex;
    }
    final voiceExecution = await _tvHomeController.executeVoiceCommand(
      offlineMode: offlineMode,
      resolution: resolution,
      selectedAccessScope: _selectedAccessScope,
      skipAuthorization: effectiveOfflineNavigation != null,
      launchAppId: effectiveOfflineNavigation?.launchAppId,
    );
    final authorization = voiceExecution.authorization;
    final execution = voiceExecution.execution;

    if (!mounted) {
      return;
    }

    final wifiGuidance = offlineMode
        ? buildOfflineWifiNavigationGuidance(
            isOfflineHomeMode: _homeSurfaceMode() == _HomeSurfaceMode.offline,
            offlineHomeActionIndex: _offlineHomeActionIndex,
            visibleNetworks: _networkSnapshot.visibleNetworks,
            currentWifiHighlightIndex: _offlineWifiHighlightIndex,
            action: resolution.action,
            normalizeWifiHighlightIndex: normalizedWifiHighlightIndex,
          )
        : null;
    if (wifiGuidance != null) {
      _offlineWifiHighlightIndex = wifiGuidance.highlightedIndex;
    }
    final voicePresentation = projectVoicePresentation(
      recognizedText: voiceResult.recognizedText,
      voiceProvider: voiceResult.provider,
      offlineMode: offlineMode,
      resolution: resolution,
      authorization: authorization,
      execution: execution,
      blockedAssistantReply: _buildBlockedAssistantReply(
        authorization?.bindingScope ?? '',
        resolution.appId,
      ),
      offlineTarget: effectiveOfflineNavigation?.target,
      offlineAction: effectiveOfflineNavigation?.action,
      offlineAssistantText: effectiveOfflineNavigation?.assistantText,
      offlineExecutionStatus: effectiveOfflineNavigation?.executionStatus,
      offlinePendingCommand: effectiveOfflineNavigation?.pendingCommand,
      wifiExecutionStatus: wifiGuidance?.executionStatus,
      wifiPendingCommand: wifiGuidance?.pendingCommand,
    );

    setState(() {
      final assistantText = voicePresentation.assistantText;
      if (!offlineMode && authorization != null && !authorization.allowed) {
        unawaited(
          _tvHomeController.logBlockedControl(
            TvHomeBlockedControlLogData(
              locale: _appState.selectedLanguage,
              userText: voiceResult.recognizedText,
              assistantText: assistantText,
              appId: resolution.appId,
              action: controlActionApiName(resolution.action),
              route: authorization.reason,
              deviceId: tvHomeDeviceUuid,
            ),
          ),
        );
      }
      _subtitle = assistantText;
      _executionStatus = voicePresentation.executionStatus;
      _pendingCommand = voicePresentation.pendingCommand;
      _channelStatus = voicePresentation.channelStatus;
      _routerStatus = projectRouterStatus(
        currentStatus: _routerStatus,
        provider: resolution.modelProvider,
        transportMode: voicePresentation.transportLabel,
      );
      _conversation = <ConversationTurn>[
        ...voicePresentation.conversationTurns,
        ..._conversation,
      ].take(8).toList();
      if (voicePresentation.controlLogEntry != null) {
        _controlLog = <ControlLogEntry>[
          voicePresentation.controlLogEntry!,
          ..._controlLog,
        ].take(8).toList();
      }
      _isHandlingVoice = false;
    });
    if (_appState.backgroundStandbyEnabled) {
      unawaited(_tvHomeController.updateStandbyExecutionState(_pendingCommand));
    }
  }

  Future<void> _handleLaunchShortcut(TvHomeShortcut shortcut) async {
    if (!shortcut.enabled) {
      setState(() {
        _executionStatus = 'Adapter coming soon';
        _subtitle = '${shortcut.label} adapter is not enabled yet.';
        _pendingCommand =
            'This app is visible on the TV desktop, but its deep control adapter is still pending.';
      });
      return;
    }

    final shortcutFlow = await _tvHomeController.handleShortcut(
      shortcut: shortcut,
      networkSnapshot: _networkSnapshot,
      selectedAccessScope: _selectedAccessScope,
    );
    if (!mounted) {
      return;
    }

    final offlineMode = shortcutFlow.offlineMode;
    final authorization = shortcutFlow.authorization;
    if (!offlineMode && authorization != null && !authorization.allowed) {
      setState(() {
        _executionStatus = 'Control blocked';
        _subtitle = _buildBlockedAssistantReply(
          authorization.bindingScope,
          shortcut.appId,
        );
        _pendingCommand =
            'Blocked opening ${shortcut.label} for scope ${authorization.bindingScope}.';
      });
      return;
    }

    final execution = shortcutFlow.execution;
    if (execution == null) {
      return;
    }
    final shortcutPresentation = projectShortcutPresentation(
      shortcut: shortcut,
      offlineMode: offlineMode,
      execution: execution,
    );

    setState(() {
      _executionStatus = shortcutPresentation.executionStatus;
      _subtitle = shortcutPresentation.subtitle;
      _pendingCommand = shortcutPresentation.pendingCommand;
      _channelStatus = shortcutPresentation.channelStatus;
      _controlLog = <ControlLogEntry>[
        if (shortcutPresentation.controlLogEntry != null)
          shortcutPresentation.controlLogEntry!,
        ..._controlLog,
      ].take(8).toList();
      _conversation = <ConversationTurn>[
        ...shortcutPresentation.conversationTurns,
        ..._conversation,
      ].take(8).toList();
    });
  }

  Future<void> _handleSimulateWake() async {
    final wakeData = await _tvHomeController.simulateWake('Open YouTube');
    if (!mounted) {
      return;
    }

    final status = wakeData.status;
    setState(() {
      _appState = _appState.copyWith(
        backgroundStandbyEnabled: status.enabled,
      );
      _systemHotwordPrivilege = status.systemHotwordPrivilege;
      _executionStatus = 'Hotword detected';
      _subtitle = 'Wake word detected. Bringing OpenClaw to the front...';
      _pendingCommand = status.statusText;
      _channelStatus =
          'Standby: ${status.running ? 'running' : 'stopped'} | Wake source: ${status.pendingWakeSource ?? 'stub'}';
    });

    _applyWakeEvent(wakeData.event);
  }

  Future<void> _consumePendingWakeEvent() async {
    final event = await _tvHomeController.consumeWakeEvent();
    if (!mounted) {
      return;
    }

    _applyWakeEvent(event);
  }

  void _applyWakeEvent(VoiceWakeEvent event) {
    final wakePresentation = projectWakePresentation(event);
    if (wakePresentation == null) {
      return;
    }

    setState(() {
      _executionStatus = wakePresentation.executionStatus;
      _subtitle = wakePresentation.subtitle;
      _pendingCommand = wakePresentation.pendingCommand;
      _channelStatus = wakePresentation.channelStatus;
      _conversation = <ConversationTurn>[
        ...wakePresentation.conversationTurns,
        ..._conversation,
      ].take(8).toList();
    });
  }

  Future<void> _handleManageAvatar() async {
    final items = _avatarProfiles.isEmpty
        ? <AvatarProfile>[
            _avatarProfile ?? await _apiClient.getActiveAvatarProfile(),
          ]
        : _avatarProfiles;
    if (!mounted) {
      return;
    }

    final selected = await showAvatarPickerDialog(
      context: context,
      items: items,
    );
    if (selected == null) {
      return;
    }

    setState(() {
      _executionStatus = 'Switching avatar';
      _subtitle = 'Loading avatar profile ${selected.avatarLabel}...';
    });

    final activeProfile = await _apiClient.activateAvatarProfile(selected.id);
    final avatarProfiles = await _apiClient.getAvatarProfiles();

    if (!mounted) {
      return;
    }

    setState(() {
      _avatarProfile = activeProfile;
      _avatarProfiles = avatarProfiles;
      _appState = _appState.copyWith(selectedAvatar: activeProfile.avatarLabel);
      _executionStatus = 'Avatar updated';
      _subtitle = 'Avatar switched to ${activeProfile.avatarLabel}.';
      _pendingCommand = 'Avatar presentation updated from backend profile.';
      _channelStatus =
          'Avatar profile: ${activeProfile.id} | ${activeProfile.gender} | ${activeProfile.ageGroup}';
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'assistant',
          text: 'Avatar changed to ${activeProfile.avatarLabel}. This profile is now managed by the backend.',
          metadata: 'avatar | activate',
        ),
        ..._conversation,
      ].take(8).toList();
    });
  }

  Future<void> _handleCreateTopUp() async {
    final request = await showCreateTopUpDialog(context);
    if (request == null) {
      return;
    }

    setState(() {
      _isCreatingTopUp = true;
      _executionStatus = 'Creating top-up order';
      _subtitle = 'Preparing stablecoin top-up order...';
    });

    final billingMutation = await _tvHomeController.createTopUpOrder(request);

    if (!mounted) {
      return;
    }

    final billingPresentation = projectBillingTopUpPresentation(
      billingMutation.updatedOrder,
    );

    setState(() {
      _profile = billingMutation.profile;
      _billingOrders = billingMutation.updatedOrders;
      _appState = _appState.copyWith(
        tokenBalance: billingMutation.profile.tokenBalance,
      );
      _subtitle = billingPresentation.subtitle;
      _pendingCommand = billingPresentation.pendingCommand;
      _channelStatus = billingPresentation.channelStatus;
      _executionStatus = billingPresentation.executionStatus;
      _conversation = <ConversationTurn>[
        ...billingPresentation.conversationTurns,
        ..._conversation,
      ].take(8).toList();
      _isCreatingTopUp = false;
    });

    unawaited(_pollBillingOrders(forceRefresh: true));
  }

  Future<void> _handleSubmitTxHash() async {
    final pendingOrder = _billingOrders.isEmpty ? null : _billingOrders.first;
    if (pendingOrder == null) {
      return;
    }

    final txHash = await showSubmitTxHashDialog(
      context: context,
      order: pendingOrder,
    );
    if (txHash == null || txHash.trim().isEmpty) {
      return;
    }

    setState(() {
      _isSubmittingTxHash = true;
      _executionStatus = 'Submitting transaction hash';
      _subtitle = 'Submitting payment proof for ${pendingOrder.id}...';
    });

    final billingMutation = await _tvHomeController.submitBillingOrderTxHash(
      SubmitTxHashRequest(orderId: pendingOrder.id, txHash: txHash.trim()),
    );

    if (!mounted) {
      return;
    }

    final billingPresentation = projectBillingTxHashPresentation(
      billingMutation.updatedOrder,
    );

    setState(() {
      _profile = billingMutation.profile;
      _billingOrders = billingMutation.updatedOrders;
      _subtitle = billingPresentation.subtitle;
      _pendingCommand = billingPresentation.pendingCommand;
      _channelStatus = billingPresentation.channelStatus;
      _executionStatus = billingPresentation.executionStatus;
      _conversation = <ConversationTurn>[
        ...billingPresentation.conversationTurns,
        ..._conversation,
      ].take(8).toList();
      _isSubmittingTxHash = false;
    });

    unawaited(_pollBillingOrders(forceRefresh: true));
  }

  Future<void> _handleRefreshConfirmations() async {
    final confirmingOrder = _billingOrders.cast<BillingOrderPreview?>().firstWhere(
      (order) => order != null && order.txHash != null && order.status == 'confirming',
      orElse: () => _billingOrders.isNotEmpty ? _billingOrders.first : null,
    );
    if (confirmingOrder == null) {
      return;
    }

    final nextConfirmations = _nextConfirmationCount(confirmingOrder);

    setState(() {
      _isRefreshingConfirmations = true;
      _executionStatus = 'Refreshing confirmations';
      _subtitle = 'Checking blockchain confirmations for ${confirmingOrder.id}...';
    });

    final billingMutation = await _tvHomeController.refreshBillingOrderConfirmations(
      orderId: confirmingOrder.id,
      confirmations: nextConfirmations,
    );

    if (!mounted) {
      return;
    }

    final billingPresentation = projectBillingConfirmationPresentation(
      billingMutation.updatedOrder,
    );

    setState(() {
      _profile = billingMutation.profile;
      _billingOrders = billingMutation.updatedOrders;
      _subtitle = billingPresentation.subtitle;
      _pendingCommand = billingPresentation.pendingCommand;
      _channelStatus = billingPresentation.channelStatus;
      _executionStatus = billingPresentation.executionStatus;
      _conversation = <ConversationTurn>[
        ...billingPresentation.conversationTurns,
        ..._conversation,
      ].take(8).toList();
      _isRefreshingConfirmations = false;
    });
  }

  Future<void> _handleRecoverRights() async {
    final profile = _profile;
    if (profile == null) {
      return;
    }

    final recoveryInput = await showRecoverRightsDialog(context);
    if (recoveryInput == null) {
      return;
    }

    setState(() {
      _isRecoveringRights = true;
      _executionStatus = 'Recovering rights';
      _subtitle = 'Verifying the latest payment proof...';
      _pendingCommand = 'Checking whether the payment belongs to another active device user.';
    });

    try {
      final recovery = await _tvHomeController.recoverByPaymentProof(
        txHash: recoveryInput.txHash,
        chain: recoveryInput.chain,
        amountUsd: recoveryInput.amountUsd,
      );

      if (!mounted) {
        return;
      }

      if (recovery.deviceUserId == profile.id) {
        final recoveryPresentation = projectRecoverySameDevicePresentation(
          currentDeviceUserId: profile.id,
        );
        setState(() {
          _subtitle = recoveryPresentation.subtitle;
          _executionStatus = recoveryPresentation.executionStatus;
          _pendingCommand = recoveryPresentation.pendingCommand;
          _channelStatus = recoveryPresentation.channelStatus;
          _conversation = <ConversationTurn>[
            ...recoveryPresentation.conversationTurns,
            ..._conversation,
          ].take(8).toList();
          _isRecoveringRights = false;
        });
        return;
      }

      final confirmed = await showRecoveryTransferConfirmDialog(
        context: context,
        recovery: recovery,
        currentDeviceUserId: profile.id,
        formattedPlan: formatPlan(recovery.planCode),
      );
      if (!mounted) {
        return;
      }
      if (!confirmed) {
        final recoveryPresentation = projectRecoveryCancelledPresentation();
        setState(() {
          _subtitle = recoveryPresentation.subtitle;
          _executionStatus = recoveryPresentation.executionStatus;
          _pendingCommand = recoveryPresentation.pendingCommand;
          _channelStatus = recoveryPresentation.channelStatus;
          _isRecoveringRights = false;
        });
        return;
      }

      final transferData = await _tvHomeController.transferEntitlementsAndRefresh(
        fromDeviceUserId: recovery.deviceUserId,
        toDeviceUserId: profile.id,
        paymentProofTxHash: recovery.txHash,
      );

      if (!mounted) {
        return;
      }

      final recoveryPresentation = projectRecoverySuccessPresentation(
        recovery: recovery,
        transfer: transferData.transfer,
      );

      setState(() {
        _profile = transferData.profile;
        _devices = transferData.devices;
        _billingOrders = transferData.billingOrders;
        _appState = _appState.copyWith(
          subscriptionLabel: formatPlan(transferData.profile.plan),
          tokenBalance: transferData.profile.tokenBalance,
        );
        _subtitle = recoveryPresentation.subtitle;
        _executionStatus = recoveryPresentation.executionStatus;
        _pendingCommand = recoveryPresentation.pendingCommand;
        _channelStatus = recoveryPresentation.channelStatus;
        _conversation = <ConversationTurn>[
          ...recoveryPresentation.conversationTurns,
          ..._conversation,
        ].take(8).toList();
        _controlLog = <ControlLogEntry>[
          if (recoveryPresentation.controlLogEntry != null)
            recoveryPresentation.controlLogEntry!,
          ..._controlLog,
        ].take(8).toList();
        _isRecoveringRights = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      final recoveryPresentation = projectRecoveryFailurePresentation();
      setState(() {
        _subtitle = recoveryPresentation.subtitle;
        _executionStatus = recoveryPresentation.executionStatus;
        _pendingCommand = recoveryPresentation.pendingCommand;
        _channelStatus = recoveryPresentation.channelStatus;
        _conversation = <ConversationTurn>[
          ...recoveryPresentation.conversationTurns,
          ..._conversation,
        ].take(8).toList();
        _isRecoveringRights = false;
      });
    }
  }

  Future<void> _pollBillingOrders({bool forceRefresh = false}) async {
    if (!mounted) {
      return;
    }

    final billingPollData = await _tvHomeController.pollBillingOrders(
      currentOrders: _billingOrders,
      forceRefresh: forceRefresh,
    );
    if (billingPollData == null || !mounted) {
      return;
    }

    setState(() {
      _billingOrders = billingPollData.updatedOrders;
      _profile = billingPollData.profile;
      _appState = _appState.copyWith(
        tokenBalance: billingPollData.profile.tokenBalance,
      );
      final activeOrder = billingPollData.activeOrder;
      if (activeOrder != null && activeOrder.status == 'confirmed') {
        _subtitle =
            'Payment confirmed: ${activeOrder.stablecoinSymbol} ${activeOrder.amountUsd.toStringAsFixed(2)} settled successfully.';
        _executionStatus = 'Payment confirmed';
        _pendingCommand =
            'Settlement completed for ${activeOrder.stablecoinSymbol} on ${activeOrder.chain}.';
        _channelStatus =
            'Order ${activeOrder.id} | ${activeOrder.status} | ${activeOrder.confirmations} confirmations';
      }
    });
  }

  String _buildBlockedAssistantReply(String bindingScope, String appId) {
    if (bindingScope == 'api_key') {
      return 'This shared access scope can control playback, but it cannot open $appId. Switch to household scope if you want me to launch apps.';
    }

    return 'This request was blocked by the current access scope. Please adjust the share permissions and try again.';
  }

  int _nextConfirmationCount(BillingOrderPreview order) {
    final required = order.chain == 'TRON' ? 12 : 8;
    if (order.confirmations >= required) {
      return required;
    }

    final next = order.confirmations + (order.chain == 'TRON' ? 3 : 2);
    return next > required ? required : next;
  }

  Future<void> _handleCopyDepositAddress() async {
    final order = primaryBillingOrder(_billingOrders);
    if (order == null) {
      return;
    }

    await Clipboard.setData(ClipboardData(text: order.walletAddress));
    if (!mounted) {
      return;
    }

    setState(() {
      _subtitle = 'Deposit address copied for ${order.stablecoinSymbol} on ${order.chain}.';
      _executionStatus = 'Deposit address copied';
      _channelStatus = 'Copied: ${order.walletAddress}';
    });
  }

  _HomeSurfaceMode _homeSurfaceMode() {
    if (!_networkSnapshot.isConnected) {
      return _HomeSurfaceMode.offline;
    }
    if (isLowTokenState(_profile) || isSubscriptionNearExpiry(_profile)) {
      return _HomeSurfaceMode.lowCapacity;
    }
    return _HomeSurfaceMode.online;
  }

  Widget _buildAvatarSurface() {
    return TvHomeAvatarSurface(
      selectedAvatar: _appState.selectedAvatar,
      subtitle: _subtitle,
      executionStatus: _executionStatus,
      isHandlingVoice: _isHandlingVoice,
      isLowCapacity: _homeSurfaceMode() == _HomeSurfaceMode.lowCapacity,
      profile: _avatarProfile,
      onTap: _isHandlingVoice ? null : _handleVoicePress,
    );
  }

  Widget _buildConversationSurface() {
    return TvHomeConversationSurface(
      subtitle: _subtitle,
      pendingCommand: _pendingCommand,
      showOverlay: shouldShowConversationOverlay(
        isHandlingVoice: _isHandlingVoice,
        subtitle: _subtitle,
        executionStatus: _executionStatus,
      ),
    );
  }

  Widget _buildAppShortcutsSurface() {
    final visibleShortcuts = _featuredShortcuts.isEmpty
        ? resolveTvHomeFeaturedShortcuts(const [])
        : _featuredShortcuts;

    return TvHomeShortcutsSurface(
      isOffline: _homeSurfaceMode() == _HomeSurfaceMode.offline,
      shortcuts: visibleShortcuts,
      offlineActions: tvHomeOfflinePrimaryActions,
      focusedShortcutIndex: _focusedAppShortcutIndex,
      highlightedOfflineActionIndex: normalizedOfflineHomeActionIndex(
        currentIndex: _offlineHomeActionIndex,
        actionCount: tvHomeOfflinePrimaryActions.length,
      ),
      onShortcutTap: _handleLaunchShortcut,
      onShortcutFocus: (index) {
        if (!mounted) {
          return;
        }
        setState(() {
          _focusedAppShortcutIndex = index;
        });
      },
      onOfflineActionTap: _handleOfflinePrimaryActionTap,
    );
  }

  Widget _buildFooterStatusBar() {
    final networkLabel = _networkSnapshot.isConnected
        ? _networkSnapshot.currentSsid ?? _networkSnapshot.statusText
        : 'Offline';
    final modeLabel = switch (_homeSurfaceMode()) {
      _HomeSurfaceMode.online => 'Ready',
      _HomeSurfaceMode.lowCapacity => 'Ready',
      _HomeSurfaceMode.offline => 'Local only',
    };

    return TvHomeFooterStatusBar(
      networkLabel: networkLabel,
      modeLabel: modeLabel,
      voiceLabel: _voiceCapabilityStatus,
      planLabel: _appState.subscriptionLabel,
      scopeLabel: _selectedAccessScope,
    );
  }

  Widget _buildTopToolBar() {
    final tools = <TvHomeShortcut>[
      if (_homeSurfaceMode() == _HomeSurfaceMode.offline)
        const TvHomeShortcut(
          appId: 'settings',
          label: 'Connect',
          icon: Icons.wifi_rounded,
          enabled: true,
          accentColor: Color(0xFFFF8C69),
        ),
      const TvHomeShortcut(
        appId: 'cast',
        label: 'Cast',
        icon: Icons.cast_connected_rounded,
        enabled: true,
        accentColor: Color(0xFF8BE9FD),
      ),
      const TvHomeShortcut(
        appId: 'local_files',
        label: 'USB',
        icon: Icons.usb_rounded,
        enabled: true,
        accentColor: Color(0xFFFFD166),
      ),
      const TvHomeShortcut(
        appId: 'settings',
        label: 'Settings',
        icon: Icons.settings_rounded,
        enabled: true,
        accentColor: Color(0xFF7CC6FE),
      ),
    ];

    return TvHomeTopToolbar(
      tools: tools,
      onTap: _handleLaunchShortcut,
    );
  }

  Widget _buildOfflineWifiAssistantCard() {
    final visibleNetworks = _networkSnapshot.visibleNetworks;
    final highlightedIndex = normalizedWifiHighlightIndex(
      networks: visibleNetworks,
      currentIndex: _offlineWifiHighlightIndex,
    );

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF18202B),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFFFF8C69).withValues(alpha: 0.45)),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: const BoxDecoration(
                    color: Color(0xFFFF8C69),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 10),
                const Text(
                  'Wi-Fi Assistant',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              'Network: ${_networkSnapshot.statusText}',
              style: const TextStyle(fontSize: 15, color: Colors.white70),
            ),
            if (_networkSnapshot.currentSsid != null) ...[
              const SizedBox(height: 8),
              Text(
                'Current network: ${_networkSnapshot.currentSsid}',
                style: const TextStyle(fontSize: 15, color: Colors.white70),
              ),
            ],
            const SizedBox(height: 14),
            const Text(
              'Visible Wi-Fi',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 10),
            if (visibleNetworks.isEmpty)
              const Text(
                'Visible Wi-Fi list is unavailable right now.',
                style: TextStyle(fontSize: 15, color: Colors.white70),
              )
            else
              ...List<Widget>.generate(visibleNetworks.length, (index) {
                final network = visibleNetworks[index];
                final isHighlighted = index == highlightedIndex;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    decoration: BoxDecoration(
                      color: isHighlighted
                          ? const Color(0x33FF8C69)
                          : Colors.white.withValues(alpha: 0.04),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isHighlighted
                            ? const Color(0xFFFF8C69)
                            : Colors.white12,
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          isHighlighted
                              ? Icons.radio_button_checked_rounded
                              : Icons.radio_button_unchecked_rounded,
                          size: 18,
                          color: isHighlighted
                              ? const Color(0xFFFFB49A)
                              : Colors.white54,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            network,
                            style: TextStyle(
                              fontSize: 15,
                              color: isHighlighted ? Colors.white : Colors.white70,
                              fontWeight: isHighlighted
                                  ? FontWeight.w700
                                  : FontWeight.w500,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }),
            const SizedBox(height: 10),
            const Text(
              'Default highlight stays on the Wi-Fi list. Say up, down, or select.',
              style: TextStyle(fontSize: 14, color: Colors.white70),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleOfflinePrimaryActionTap(int index) async {
    setState(() {
      _offlineHomeActionIndex = normalizedOfflineHomeActionIndex(
        currentIndex: index,
        actionCount: tvHomeOfflinePrimaryActions.length,
      );
    });

    final action = tvHomeOfflinePrimaryActions[_offlineHomeActionIndex];
    if (action.appId == 'settings') {
      setState(() {
        _executionStatus = 'Wi-Fi list focused';
        _subtitle = 'Connect is focused. Use up and down to choose a Wi-Fi network.';
        _pendingCommand = 'Focused Connect. Say up, down, or select.';
        _channelStatus = 'Offline home: connect_focus';
      });
      return;
    }

    final execution = await _controlService.execute(
      ControlIntent(appId: action.appId, action: ControlAction.openApp),
    );
    if (!mounted) {
      return;
    }

    setState(() {
      _executionStatus = execution.success ? 'Control executed' : 'Control failed';
      _subtitle = execution.message;
      _pendingCommand = execution.success
          ? 'Opening ${action.label} from the offline home surface.'
          : 'Open command failed for ${action.label}.';
      _channelStatus = 'Offline home: ${action.appId} | ${execution.strategyUsed}';
      _controlLog = <ControlLogEntry>[
        ControlLogEntry(
          target: action.appId,
          action: 'openApp',
          status: execution.success ? 'ok' : 'failed',
          strategy: execution.strategyUsed,
        ),
        ..._controlLog,
      ].take(8).toList();
    });
  }

  List<ConversationTurn> _mapConversationLogs(List<AssistantLogRecord> logs) {
    final turns = <ConversationTurn>[];
    for (final log in logs) {
      if (log.userText.isNotEmpty) {
        turns.add(
          ConversationTurn(
            speaker: 'user',
            text: log.userText,
            metadata: '${log.kind} | ${log.locale}',
          ),
        );
      }
      turns.add(
        ConversationTurn(
          speaker: 'assistant',
          text: log.assistantText,
          metadata: '${log.mode} | ${log.modelProvider}',
        ),
      );
    }
    return turns.take(8).toList();
  }

  List<ControlLogEntry> _mapControlLogs(List<AssistantLogRecord> logs) {
    return logs
        .where((log) => log.mode == 'control' && log.appId != null && log.action != null)
        .map(
          (log) => ControlLogEntry(
            target: log.appId!,
            action: log.queryText == null ? log.action! : '${log.action}(${log.queryText})',
            status: 'saved',
            strategy: log.route ?? 'persisted_log',
          ),
        )
        .take(8)
        .toList();
  }

  Color _turnColor(String speaker) {
    return speaker == 'assistant' ? const Color(0xFF17384D) : const Color(0xFF24363B);
  }

  Widget _buildLogView() {
    if (_activeLogView == 'controls') {
      return ListView.separated(
        itemCount: _controlLog.length,
        separatorBuilder: (context, index) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          final item = _controlLog[index];
          return Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFF203046),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${item.target} | ${item.action}',
                  style: const TextStyle(fontSize: 20),
                ),
                const SizedBox(height: 6),
                Text(
                  'Status: ${item.status}',
                  style: const TextStyle(fontSize: 14, color: Colors.white70),
                ),
                Text(
                  'Strategy: ${item.strategy}',
                  style: const TextStyle(fontSize: 14, color: Colors.white54),
                ),
              ],
            ),
          );
        },
      );
    }

    return ListView.separated(
      itemCount: _conversation.length,
      separatorBuilder: (context, index) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final turn = _conversation[index];
        return Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: _turnColor(turn.speaker),
            borderRadius: BorderRadius.circular(18),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                turn.speaker == 'assistant' ? 'Assistant' : 'You',
                style: const TextStyle(
                  fontSize: 14,
                  color: Colors.white70,
                ),
              ),
              const SizedBox(height: 6),
              Text(turn.text, style: const TextStyle(fontSize: 20)),
              const SizedBox(height: 6),
              Text(
                turn.metadata,
                style: const TextStyle(
                  fontSize: 13,
                  color: Colors.white54,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF08111B),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(28, 24, 28, 24),
          child: Stack(
            children: [
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: const RadialGradient(
                      center: Alignment(-0.25, -0.15),
                      radius: 1.05,
                      colors: [
                        Color(0xFF16324A),
                        Color(0xFF0B1724),
                        Color(0xFF08111B),
                      ],
                    ),
                    image: _backgroundImageUrl == null || _backgroundImageUrl!.isEmpty
                        ? null
                        : DecorationImage(
                            image: NetworkImage(_backgroundImageUrl!),
                            fit: BoxFit.cover,
                            opacity: 0.24,
                          ),
                    borderRadius: BorderRadius.circular(28),
                  ),
                ),
              ),
              Positioned(
                top: 10,
                left: 10,
                child: _buildAvatarSurface(),
              ),
              Positioned(
                top: 4,
                right: 0,
                child: _buildTopToolBar(),
              ),
              Positioned.fill(
                left: 220,
                right: 220,
                top: 80,
                bottom: 130,
                child: _buildConversationSurface(),
              ),
              Positioned(
                left: 220,
                right: 110,
                bottom: 18,
                child: _buildAppShortcutsSurface(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

enum _HomeSurfaceMode { online, lowCapacity, offline }

class _InfoSurfaceCard extends StatelessWidget {
  const _InfoSurfaceCard({
    required this.title,
    required this.accentColor,
    required this.lines,
  });

  final String title;
  final Color accentColor;
  final List<String> lines;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF18202B),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: accentColor.withValues(alpha: 0.45)),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: accentColor,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  title,
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 14),
            ...lines.map(
              (line) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  line,
                  style: const TextStyle(fontSize: 15, color: Colors.white70),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
