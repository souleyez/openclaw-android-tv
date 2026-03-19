// ignore_for_file: unused_field, unused_element, prefer_final_fields, prefer_const_constructors

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_client.dart';
import '../../core/app_state.dart';
import '../account/current_user_profile.dart';
import '../account/entitlement_recovery_result.dart';
import '../avatar/avatar_profile.dart';
import '../avatar/virtual_host_avatar.dart';
import '../billing/billing_order_preview.dart';
import '../billing/create_billing_order_request.dart';
import '../billing/submit_tx_hash_request.dart';
import '../billing/update_confirmations_request.dart';
import '../control/control_action.dart';
import '../control/offline_local_router.dart';
import '../control/control_service.dart';
import '../conversation/assistant_log_record.dart';
import '../conversation/conversation_turn.dart';
import '../conversation/control_log_entry.dart';
import '../conversation/router_status.dart';
import '../device_sync/device_registration_request.dart';
import '../device_sync/registered_device.dart';
import '../network/network_snapshot.dart';
import '../network/network_status_service.dart';
import '../ota/ota_report_request.dart';
import '../voice/voice_command_service.dart';

class TvHomePage extends StatefulWidget {
  const TvHomePage({super.key});

  @override
  State<TvHomePage> createState() => _TvHomePageState();
}

class _TvHomePageState extends State<TvHomePage> {
  static const String _deviceUuid = 'device_demo_android_tv';
  static const int _currentVersionCode = 18;
  static const DeviceRegistrationRequest _deviceRegistration =
      DeviceRegistrationRequest(
        deviceUuid: _deviceUuid,
        deviceName: 'Living Room TV',
        androidVersion: '9',
        isAndroidTv: true,
      );

  final ApiClient _apiClient = const ApiClient();
  final VoiceCommandService _voiceService = const VoiceCommandService();
  final ControlService _controlService = const ControlService();
  final OfflineLocalRouter _offlineLocalRouter = const OfflineLocalRouter();
  final NetworkStatusService _networkStatusService = const NetworkStatusService();
  final FocusNode _pressToTalkFocusNode = FocusNode(debugLabel: 'press_to_talk');
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
  String _activeLogView = 'conversation';
  String _selectedAccessScope = 'household';
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
  CurrentUserProfile? _profile;
  AvatarProfile? _avatarProfile;
  List<AvatarProfile> _avatarProfiles = const [];
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

  static const List<_AppShortcut> _appShortcuts = [
    _AppShortcut(
      appId: 'youtube',
      label: 'YouTube',
      icon: Icons.ondemand_video_rounded,
      enabled: true,
      accentColor: Color(0xFFFF6B6B),
    ),
    _AppShortcut(
      appId: 'spotify',
      label: 'Spotify',
      icon: Icons.graphic_eq_rounded,
      enabled: true,
      accentColor: Color(0xFF6BE28D),
    ),
    _AppShortcut(
      appId: 'vlc',
      label: 'VLC',
      icon: Icons.play_circle_fill_rounded,
      enabled: true,
      accentColor: Color(0xFFFFB347),
    ),
    _AppShortcut(
      appId: 'settings',
      label: 'Settings',
      icon: Icons.settings_rounded,
      enabled: true,
      accentColor: Color(0xFF7CC6FE),
    ),
    _AppShortcut(
      appId: 'cast',
      label: 'Cast',
      icon: Icons.cast_connected_rounded,
      enabled: true,
      accentColor: Color(0xFF8BE9FD),
    ),
    _AppShortcut(
      appId: 'local_files',
      label: 'Local Files',
      icon: Icons.folder_open_rounded,
      enabled: true,
      accentColor: Color(0xFFFFD166),
    ),
    _AppShortcut(
      appId: 'netflix',
      label: 'Netflix',
      icon: Icons.live_tv_rounded,
      enabled: false,
      accentColor: Color(0xFFE05263),
    ),
    _AppShortcut(
      appId: 'kodi',
      label: 'Kodi',
      icon: Icons.view_in_ar_rounded,
      enabled: false,
      accentColor: Color(0xFF69A7FF),
    ),
  ];

  static const List<_OfflinePrimaryAction> _offlinePrimaryActions = [
    _OfflinePrimaryAction(
      appId: 'settings',
      label: 'Connect',
      hint: 'Default focus. Use up/down on Wi-Fi list.',
      icon: Icons.wifi_rounded,
      accentColor: Color(0xFFFF8C69),
    ),
    _OfflinePrimaryAction(
      appId: 'cast',
      label: 'Cast',
      hint: 'Move right, then say select.',
      icon: Icons.cast_connected_rounded,
      accentColor: Color(0xFF8BE9FD),
    ),
    _OfflinePrimaryAction(
      appId: 'local_files',
      label: 'Open USB',
      hint: 'Move right again, then say select.',
      icon: Icons.usb_rounded,
      accentColor: Color(0xFFFFD166),
    ),
  ];

  @override
  void initState() {
    super.initState();
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
    final profile = await _apiClient.getMe();
    final backendConnected = await _apiClient.getHealthStatus();
    final avatarProfile = await _apiClient.getActiveAvatarProfile();
    final avatarProfiles = await _apiClient.getAvatarProfiles();
    final speechStatus = await _voiceService.getSpeechStatus();
    final standbyStatus = await _voiceService.getStandbyStatus();
    final routerStatus = await _apiClient.getRouterStatus();
    final routerLogs = await _apiClient.getRouterLogs();
    final registeredDevice = await _apiClient.registerDevice(_deviceRegistration);
    final devices = await _apiClient.getDevices();
    final networkSnapshot = await _networkStatusService.getNetworkSnapshot();
    final billingOrders = await _apiClient.getBillingOrders();
    if (!mounted) {
      return;
    }

    setState(() {
      _profile = profile;
      _avatarProfile = avatarProfile;
      _avatarProfiles = avatarProfiles;
      _backendConnected = backendConnected;
      _routerStatus = routerStatus;
      _networkSnapshot = networkSnapshot;
      _offlineHomeActionIndex = _normalizedOfflineHomeActionIndex(
        _offlineHomeActionIndex,
      );
      _offlineWifiHighlightIndex = _normalizedWifiHighlightIndex(
        networkSnapshot.visibleNetworks,
        _offlineWifiHighlightIndex,
      );
      _voiceCapabilityStatus =
          '${_formatVoiceCapabilityStatus(speechStatus)}${standbyStatus.systemHotwordPrivilege ? ' | privileged' : ''}';
      _systemHotwordPrivilege = standbyStatus.systemHotwordPrivilege;
      _devices = devices.any((device) => device.deviceUuid == registeredDevice.deviceUuid)
          ? devices
          : <RegisteredDevice>[registeredDevice, ...devices];
      _billingOrders = billingOrders;
      _appState = _appState.copyWith(
        subscriptionLabel: _formatPlan(profile.plan),
        tokenBalance: profile.tokenBalance,
        selectedAvatar: avatarProfile.avatarLabel,
        backgroundStandbyEnabled: standbyStatus.enabled,
      );
      _pendingCommand = standbyStatus.statusText;
      if (routerLogs.isNotEmpty) {
        _conversation = _mapConversationLogs(routerLogs);
        _controlLog = _mapControlLogs(routerLogs);
      }
      });

      await _consumePendingWakeEvent();
      if (backendConnected && networkSnapshot.isConnected) {
        unawaited(_syncOtaManifest());
      }
      unawaited(_pollBillingOrders(forceRefresh: false));
    }

  Future<void> _syncOtaManifest() async {
    final manifest = await _apiClient.getOtaManifest(
      deviceUuid: _deviceUuid,
      currentVersionCode: _currentVersionCode,
    );

    if (!manifest.available ||
        manifest.releaseId == null ||
        manifest.versionCode == null) {
      return;
    }

    final latestStatus = manifest.latestStatus ?? '';
    if (latestStatus == 'queued' ||
        latestStatus == 'downloading' ||
        latestStatus == 'downloaded' ||
        latestStatus == 'staged' ||
        latestStatus == 'installed_pending_report' ||
        latestStatus == 'reported') {
      return;
    }

    await _apiClient.reportOtaState(
      OtaReportRequest(
        deviceUuid: _deviceUuid,
        releaseId: manifest.releaseId!,
        currentVersionCode: _currentVersionCode,
        targetVersionCode: manifest.versionCode!,
        status: 'queued',
        progressPercent: 0,
        note: 'idle_background_queue',
      ),
    );
  }

  Future<void> _handleVoicePress() async {
    setState(() {
      _isHandlingVoice = true;
      _subtitle = 'Listening...';
      _executionStatus = 'Listening';
      _pendingCommand = 'Wake channel open. Waiting for speech...';
    });

    final voiceResult = await _voiceService.pressToTalk(
      locale: _mapLocale(_appState.selectedLanguage),
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
        _voiceService.updateStandbyExecutionState(
          'Command received: ${voiceResult.recognizedText}',
        ),
      );
    }

      final offlineMode = !_networkSnapshot.isConnected;
      final resolution = offlineMode
          ? _offlineLocalRouter.resolve(
              voiceResult.recognizedText,
              snapshot: _networkSnapshot,
            )
          : await _apiClient.resolveIntentWithDirectLease(
              text: voiceResult.recognizedText,
              locale: _appState.selectedLanguage,
              deviceId: _deviceUuid,
            );
    final effectiveOfflineNavigation = offlineMode
        ? _consumeOfflineHomeNavigation(resolutionAction: resolution.action)
        : null;
    final offlinePrimaryExecution = effectiveOfflineNavigation?.launchAppId != null
        ? await _controlService.execute(
            ControlIntent(
              appId: effectiveOfflineNavigation!.launchAppId!,
              action: ControlAction.openApp,
            ),
          )
        : null;
    final authorization = resolution.shouldExecuteLocally
        ? effectiveOfflineNavigation != null
            ? null
            : offlineMode
            ? null
            : await _apiClient.authorizeControl(
                bindingScope: _selectedAccessScope,
                appId: resolution.appId,
                action: _actionName(resolution.action),
              )
        : null;
    final execution =
        offlinePrimaryExecution ??
        (resolution.shouldExecuteLocally &&
                effectiveOfflineNavigation == null &&
                (offlineMode || (authorization?.allowed ?? false))
            ? await _controlService.execute(
                ControlIntent(
                  appId: resolution.appId,
                  action: resolution.action,
                  queryText: resolution.queryText,
                ),
              )
            : null);

    if (!mounted) {
      return;
    }

    final wifiGuidance = offlineMode
        ? _buildOfflineWifiNavigationGuidance(resolution.action)
        : null;

    setState(() {
      final assistantText = effectiveOfflineNavigation?.assistantText ??
          (execution != null
              ? (execution.success ? execution.message : resolution.assistantText)
              : authorization != null && !authorization.allowed
                  ? _buildBlockedAssistantReply(authorization.bindingScope, resolution.appId)
                  : resolution.assistantText);
      if (!offlineMode && authorization != null && !authorization.allowed) {
        _apiClient.logControlBlock(
          locale: _appState.selectedLanguage,
          userText: voiceResult.recognizedText,
          assistantText: assistantText,
          appId: resolution.appId,
          action: _actionName(resolution.action),
          route: authorization.reason,
          deviceId: _deviceUuid,
        );
      }
      _subtitle = assistantText;
      _executionStatus = effectiveOfflineNavigation?.executionStatus ??
          (execution == null
              ? authorization != null && !authorization.allowed
                  ? 'Control blocked'
                  : 'Chat reply ready'
              : execution.success
                  ? _isNavigationAction(resolution.action)
                      ? 'Navigation mode active'
                      : _isVolumeAction(resolution.action)
                          ? 'Volume adjusted'
                          : 'Control executed'
                  : 'Control failed');
      if (wifiGuidance != null) {
        _executionStatus = wifiGuidance.executionStatus;
      }
      _pendingCommand = effectiveOfflineNavigation?.pendingCommand ??
          (execution != null
              ? execution.success
                  ? _isNavigationAction(resolution.action)
                      ? 'Navigating ${resolution.appId} with ${resolution.action.name}'
                      : _isVolumeAction(resolution.action)
                          ? 'Adjusting volume with ${resolution.action.name}'
                          : 'Executing ${resolution.appId} ${resolution.action.name}'
                  : 'Execution failed for ${resolution.appId} ${resolution.action.name}'
              : authorization != null && !authorization.allowed
                  ? 'Blocked ${resolution.appId} ${resolution.action.name}'
                  : resolution.shouldExecuteLocally
                      ? 'Local action queued for ${resolution.appId} ${resolution.action.name}'
                      : 'Conversation response prepared');
        if (wifiGuidance != null) {
          _pendingCommand = wifiGuidance.pendingCommand;
        }
        final transportLabel = offlineMode
            ? 'offline_local'
            : resolution.modelProvider.contains('client_direct_provider_lease')
                ? 'client_direct_provider_lease'
                : 'server_router_fallback';
        _channelStatus =
            'Voice: ${voiceResult.provider} | Transport: $transportLabel | Model: ${resolution.modelProvider} | Route: ${resolution.route} | '
            'Confidence: ${resolution.confidence.toStringAsFixed(2)} | Tokens: ${resolution.tokenUsage} | '
            'Free left: ${resolution.bootstrapTokenRemaining}'
            '${!offlineMode && authorization != null ? ' | Scope: ${authorization.bindingScope} ${authorization.allowed ? 'allowed' : 'blocked'}' : ''}'
            '${effectiveOfflineNavigation != null ? ' | Control: offline_home_surface' : execution != null ? ' | Control: ${execution.strategyUsed}' : ' | Control: chat_only'}'
            '${resolution.fallbackReason != null ? ' | Fallback: ${resolution.fallbackReason}' : ''}';
        _routerStatus = RouterStatus(
          provider: resolution.modelProvider,
          configured: _routerStatus.configured,
          baseUrl: _routerStatus.baseUrl,
          model: _routerStatus.model,
          transportMode: transportLabel,
        );
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'user',
          text: voiceResult.recognizedText,
          metadata: 'voice | ${voiceResult.provider}',
        ),
          ConversationTurn(
            speaker: 'assistant',
            text: assistantText,
            metadata: '${resolution.mode} | $transportLabel | ${resolution.modelProvider}',
          ),
        ..._conversation,
      ].take(8).toList();
      if (effectiveOfflineNavigation != null) {
        _controlLog = <ControlLogEntry>[
          ControlLogEntry(
            target: effectiveOfflineNavigation.target,
            action: effectiveOfflineNavigation.action,
            status: 'ok',
            strategy: 'offline_home_surface',
          ),
          ..._controlLog,
        ].take(8).toList();
      } else if (execution != null) {
        _controlLog = <ControlLogEntry>[
          ControlLogEntry(
            target: resolution.appId,
            action: resolution.queryText == null
                ? resolution.action.name
                : '${resolution.action.name}(${resolution.queryText})',
            status: execution.success ? 'ok' : 'failed',
            strategy: execution.strategyUsed,
          ),
          ..._controlLog,
        ].take(8).toList();
      } else if (authorization != null && !authorization.allowed) {
        _controlLog = <ControlLogEntry>[
          ControlLogEntry(
            target: resolution.appId,
            action: resolution.action.name,
            status: 'blocked',
            strategy: authorization.reason,
          ),
          ..._controlLog,
        ].take(8).toList();
      }
      _isHandlingVoice = false;
    });
    if (_appState.backgroundStandbyEnabled) {
      unawaited(_voiceService.updateStandbyExecutionState(_pendingCommand));
    }
  }

  Future<void> _handleLaunchShortcut(_AppShortcut shortcut) async {
    if (!shortcut.enabled) {
      setState(() {
        _executionStatus = 'Adapter coming soon';
        _subtitle = '${shortcut.label} adapter is not enabled yet.';
        _pendingCommand =
            'This app is visible on the TV desktop, but its deep control adapter is still pending.';
      });
      return;
    }

    final offlineMode = !_networkSnapshot.isConnected;
    final authorization = offlineMode
        ? null
        : await _apiClient.authorizeControl(
            bindingScope: _selectedAccessScope,
            appId: shortcut.appId,
            action: 'open_app',
          );
    if (!mounted) {
      return;
    }

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

    final execution = await _controlService.execute(
      ControlIntent(appId: shortcut.appId, action: ControlAction.openApp),
    );
    if (!mounted) {
      return;
    }

    setState(() {
      _executionStatus = execution.success ? 'Control executed' : 'Control failed';
      _subtitle = execution.message;
      _pendingCommand = execution.success
          ? offlineMode
              ? 'Opening ${shortcut.label} locally while offline.'
              : 'Opening ${shortcut.label} from the TV home surface.'
          : 'Open command failed for ${shortcut.label}.';
      _channelStatus =
          'Shortcut: ${shortcut.label} | ${offlineMode ? 'Mode: offline_local' : 'Mode: online'} | ${execution.strategyUsed}';
      _controlLog = <ControlLogEntry>[
        ControlLogEntry(
          target: shortcut.appId,
          action: 'openApp',
          status: execution.success ? 'ok' : 'failed',
          strategy: execution.strategyUsed,
        ),
        ..._controlLog,
      ].take(8).toList();
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'assistant',
          text: execution.message,
          metadata: 'desktop_shortcut | ${shortcut.appId}',
        ),
        ..._conversation,
      ].take(8).toList();
    });
  }

  Future<void> _handleSimulateWake() async {
    final status = await _voiceService.simulateHotwordTrigger('Open YouTube');
    if (!mounted) {
      return;
    }

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

    await _consumePendingWakeEvent();
  }

  Future<void> _consumePendingWakeEvent() async {
    final event = await _voiceService.consumeWakeEvent();
    if (!mounted || event.commandText == null || event.commandText!.isEmpty) {
      return;
    }

    setState(() {
      _executionStatus = 'Wake event received';
      _subtitle = 'Wake received: ${event.commandText}';
      _pendingCommand =
          'Wake source ${event.source}. Preparing to handle "${event.commandText}".';
      _channelStatus = 'Wake source: ${event.source}';
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'assistant',
          text: 'Wake event received for "${event.commandText}". Preparing command routing.',
          metadata: 'standby | ${event.source}',
        ),
        ..._conversation,
      ].take(8).toList();
    });
  }

  Future<void> _handleManageAvatar() async {
    final selected = await _showAvatarPickerDialog();
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
    final request = await _showCreateTopUpDialog();
    if (request == null) {
      return;
    }

    setState(() {
      _isCreatingTopUp = true;
      _executionStatus = 'Creating top-up order';
      _subtitle = 'Preparing stablecoin top-up order...';
    });

    final order = await _apiClient.createBillingOrder(request);
    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();

    if (!mounted) {
      return;
    }

    setState(() {
      _profile = profile;
      _billingOrders = updatedOrders;
      _appState = _appState.copyWith(tokenBalance: profile.tokenBalance);
      _subtitle =
          'Top-up order created: ${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} on ${order.chain}';
      _pendingCommand = 'Preparing settlement flow for ${order.stablecoinSymbol} on ${order.chain}.';
      _channelStatus =
          'Order ${order.id} | ${order.status} | ${order.confirmations} confirmations';
      _executionStatus = 'Top-up order ready';
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'assistant',
          text:
              'Created a ${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} top-up on ${order.chain}.',
          metadata: 'billing | ${order.status}',
        ),
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

    final txHash = await _showSubmitTxHashDialog(pendingOrder);
    if (txHash == null || txHash.trim().isEmpty) {
      return;
    }

    setState(() {
      _isSubmittingTxHash = true;
      _executionStatus = 'Submitting transaction hash';
      _subtitle = 'Submitting payment proof for ${pendingOrder.id}...';
    });

    final updated = await _apiClient.submitBillingOrderTxHash(
      SubmitTxHashRequest(orderId: pendingOrder.id, txHash: txHash.trim()),
    );
    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();

    if (!mounted) {
      return;
    }

    setState(() {
      _profile = profile;
      _billingOrders = updatedOrders;
      _subtitle =
          'Transaction hash submitted for ${updated.stablecoinSymbol} on ${updated.chain}.';
      _pendingCommand = 'Payment proof submitted. Waiting for blockchain confirmations.';
      _channelStatus =
          'Order ${updated.id} | ${updated.status} | Tx ${updated.txHash ?? 'pending'}';
      _executionStatus = 'Transaction hash submitted';
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'assistant',
          text:
              'I recorded the tx hash for ${updated.id}. I will keep checking confirmations on ${updated.chain}.',
          metadata: 'billing | ${updated.status}',
        ),
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

    final updated = await _apiClient.updateBillingOrderConfirmations(
      UpdateConfirmationsRequest(
        orderId: confirmingOrder.id,
        confirmations: nextConfirmations,
      ),
    );
    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();

    if (!mounted) {
      return;
    }

    setState(() {
      _profile = profile;
      _billingOrders = updatedOrders;
      _subtitle =
          'Order ${updated.id} is now ${updated.status} with ${updated.confirmations} confirmations.';
      _pendingCommand =
          'Settlement check updated to ${updated.confirmations} confirmations.';
      _channelStatus =
          'Order ${updated.id} | ${updated.status} | ${updated.confirmations} confirmations';
      _executionStatus = 'Confirmations refreshed';
      _conversation = <ConversationTurn>[
        ConversationTurn(
          speaker: 'assistant',
          text:
              'I refreshed the chain status. ${updated.stablecoinSymbol} on ${updated.chain} now has ${updated.confirmations} confirmations.',
          metadata: 'billing | ${updated.status}',
        ),
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

    final recoveryInput = await _showRecoverRightsDialog();
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
      final recovery = await _apiClient.recoverByPaymentProof(
        txHash: recoveryInput.txHash,
        chain: recoveryInput.chain,
        amountUsd: recoveryInput.amountUsd,
      );

      if (!mounted) {
        return;
      }

      if (recovery.deviceUserId == profile.id) {
        setState(() {
          _subtitle = 'This payment proof already belongs to the current device.';
          _executionStatus = 'Recovery not needed';
          _pendingCommand = 'No entitlement transfer was needed.';
          _channelStatus =
              'Recovery proof matched current device user ${profile.id}';
          _conversation = <ConversationTurn>[
            const ConversationTurn(
              speaker: 'assistant',
              text:
                  'I checked the payment proof. It already belongs to this device, so there is nothing to transfer.',
              metadata: 'recovery | same_device',
            ),
            ..._conversation,
          ].take(8).toList();
          _isRecoveringRights = false;
        });
        return;
      }

      final confirmed = await _showRecoveryTransferConfirmDialog(
        recovery: recovery,
        currentDeviceUserId: profile.id,
      );
      if (!mounted) {
        return;
      }
      if (!confirmed) {
        setState(() {
          _subtitle = 'Rights recovery was cancelled.';
          _executionStatus = 'Recovery cancelled';
          _pendingCommand = 'Waiting for another recovery confirmation.';
          _isRecoveringRights = false;
        });
        return;
      }

      final transfer = await _apiClient.transferEntitlements(
        fromDeviceUserId: recovery.deviceUserId,
        toDeviceUserId: profile.id,
        paymentProofTxHash: recovery.txHash,
      );

      final refreshedProfile = await _apiClient.getMe();
      final refreshedDevices = await _apiClient.getDevices();
      final refreshedOrders = await _apiClient.getBillingOrders();

      if (!mounted) {
        return;
      }

      setState(() {
        _profile = refreshedProfile;
        _devices = refreshedDevices;
        _billingOrders = refreshedOrders;
        _appState = _appState.copyWith(
          subscriptionLabel: _formatPlan(refreshedProfile.plan),
          tokenBalance: refreshedProfile.tokenBalance,
        );
        _subtitle =
            'Rights moved from ${transfer.fromDeviceUserId} to ${transfer.toDeviceUserId}.';
        _executionStatus = 'Rights recovered';
        _pendingCommand =
            'Previous device has lost its entitlement. This device is now active.';
        _channelStatus =
            'Recovery transfer completed | ${recovery.chain} | ${recovery.amountUsd.toStringAsFixed(2)} USD';
        _conversation = <ConversationTurn>[
          ConversationTurn(
            speaker: 'assistant',
            text:
                'I restored the entitlement from ${recovery.displayName} to this device. The previous device has now lost those rights.',
            metadata: 'recovery | transferred',
          ),
          ..._conversation,
        ].take(8).toList();
        _controlLog = <ControlLogEntry>[
          ControlLogEntry(
            target: 'entitlement',
            action: 'recover',
            status: transfer.transferred ? 'ok' : 'failed',
            strategy: 'payment_proof_transfer',
          ),
          ..._controlLog,
        ].take(8).toList();
        _isRecoveringRights = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _subtitle =
            'I could not verify that payment proof. Try the last successful payment hash again.';
        _executionStatus = 'Recovery failed';
        _pendingCommand =
            'Need a valid minimum-payment proof from the old payment account.';
        _channelStatus = 'Recovery proof validation failed';
        _conversation = <ConversationTurn>[
          const ConversationTurn(
            speaker: 'assistant',
            text:
                'I could not match that payment proof to a recoverable device user. Please try the latest successful payment from the old account.',
            metadata: 'recovery | failed',
          ),
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

    final confirmingOrder = _billingOrders.cast<BillingOrderPreview?>().firstWhere(
      (order) => order != null && order.txHash != null && order.status == 'confirming',
      orElse: () => null,
    );

    if (confirmingOrder == null && !forceRefresh) {
      return;
    }

    if (confirmingOrder != null) {
      await _apiClient.pollBillingOrders();
    }

    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();

    if (!mounted) {
      return;
    }

    setState(() {
      _billingOrders = updatedOrders;
      _profile = profile;
      _appState = _appState.copyWith(tokenBalance: profile.tokenBalance);
      final activeOrder = _primaryBillingOrder(updatedOrders);
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

  Future<_RecoveryInput?> _showRecoverRightsDialog() async {
    final txHashController = TextEditingController();
    final amountController = TextEditingController();
    String chain = 'Any';

    return showDialog<_RecoveryInput>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Recover Rights'),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Use the latest minimum verification payment from the old payment account to restore rights onto this TV.',
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: txHashController,
                  decoration: const InputDecoration(
                    labelText: 'Payment Tx Hash',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: chain,
                  decoration: const InputDecoration(
                    labelText: 'Chain',
                    border: OutlineInputBorder(),
                  ),
                  items: const [
                    DropdownMenuItem(value: 'Any', child: Text('Any')),
                    DropdownMenuItem(value: 'Polygon', child: Text('Polygon')),
                    DropdownMenuItem(value: 'Base', child: Text('Base')),
                    DropdownMenuItem(value: 'Solana', child: Text('Solana')),
                  ],
                  onChanged: (value) {
                    chain = value ?? 'Any';
                  },
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: amountController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: 'Amount USD (optional)',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final txHash = txHashController.text.trim();
                if (txHash.isEmpty) {
                  return;
                }
                Navigator.of(context).pop(
                  _RecoveryInput(
                    txHash: txHash,
                    chain: chain == 'Any' ? null : chain,
                    amountUsd: double.tryParse(amountController.text.trim()),
                  ),
                );
              },
              child: const Text('Verify'),
            ),
          ],
        );
      },
    );
  }

  Future<bool> _showRecoveryTransferConfirmDialog({
    required EntitlementRecoveryResult recovery,
    required String currentDeviceUserId,
  }) async {
    return (await showDialog<bool>(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: const Text('Confirm Rights Transfer'),
              content: SizedBox(
                width: 420,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Recovered source: ${recovery.displayName}'),
                    Text('Source device user: ${recovery.deviceUserId}'),
                    Text('Current device user: $currentDeviceUserId'),
                    Text('Plan: ${_formatPlan(recovery.planCode)}'),
                    Text(
                      'Payment proof: ${recovery.amountUsd.toStringAsFixed(2)} USD on ${recovery.chain}',
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'If you continue, the previous device will lose its entitlement and this TV will inherit it.',
                      style: TextStyle(color: Colors.white70),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(false),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  child: const Text('Transfer Here'),
                ),
              ],
            );
          },
        )) ??
        false;
  }

  Future<CreateBillingOrderRequest?> _showCreateTopUpDialog() async {
    String stablecoin = 'USDC';
    String chain = 'Polygon';
    double amountUsd = 10;

    return showDialog<CreateBillingOrderRequest>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Create Top-Up Order'),
          content: StatefulBuilder(
            builder: (context, setDialogState) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DropdownButton<String>(
                    value: stablecoin,
                    isExpanded: true,
                    items: const [
                      DropdownMenuItem(value: 'USDC', child: Text('USDC')),
                      DropdownMenuItem(value: 'USDT', child: Text('USDT')),
                    ],
                    onChanged: (value) {
                      if (value == null) {
                        return;
                      }
                      setDialogState(() {
                        stablecoin = value;
                        if (stablecoin == 'USDT' && chain == 'Base') {
                          chain = 'TRON';
                        }
                        if (stablecoin == 'USDC' && chain == 'TRON') {
                          chain = 'Polygon';
                        }
                      });
                    },
                  ),
                  const SizedBox(height: 12),
                  DropdownButton<String>(
                    value: chain,
                    isExpanded: true,
                    items: (stablecoin == 'USDC'
                            ? const ['Polygon', 'Base']
                            : const ['TRON', 'BSC'])
                        .map(
                          (item) =>
                              DropdownMenuItem(value: item, child: Text(item)),
                        )
                        .toList(),
                    onChanged: (value) {
                      if (value == null) {
                        return;
                      }
                      setDialogState(() {
                        chain = value;
                      });
                    },
                  ),
                  const SizedBox(height: 16),
                  const Text('Amount (USD)'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: [5.0, 10.0, 20.0, 50.0]
                        .map(
                          (value) => ChoiceChip(
                            label: Text('\$${value.toStringAsFixed(0)}'),
                            selected: amountUsd == value,
                            onSelected: (_) {
                              setDialogState(() {
                                amountUsd = value;
                              });
                            },
                          ),
                        )
                        .toList(),
                  ),
                ],
              );
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                Navigator.of(context).pop(
                  CreateBillingOrderRequest(
                    stablecoinSymbol: stablecoin,
                    chain: chain,
                    amountUsd: amountUsd,
                  ),
                );
              },
              child: const Text('Create'),
            ),
          ],
        );
      },
    );
  }

  Future<String?> _showSubmitTxHashDialog(BillingOrderPreview order) async {
    final controller = TextEditingController(text: order.txHash ?? '');

    return showDialog<String>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Report Tx Hash'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} on ${order.chain}'),
              const SizedBox(height: 8),
              Text(
                'Deposit address: ${order.walletAddress}',
                style: const TextStyle(fontSize: 12, color: Colors.white70),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: controller,
                decoration: const InputDecoration(
                  labelText: 'Tx Hash',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(controller.text),
              child: const Text('Submit'),
            ),
          ],
        );
      },
    );
  }

  Future<AvatarProfile?> _showAvatarPickerDialog() async {
    final items = _avatarProfiles.isEmpty
        ? <AvatarProfile>[_avatarProfile ?? await _apiClient.getActiveAvatarProfile()]
        : _avatarProfiles;
    if (!mounted) {
      return null;
    }

    return showDialog<AvatarProfile>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Select Avatar Profile'),
          content: SizedBox(
            width: 460,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: items
                    .map(
                      (profile) => Container(
                        margin: const EdgeInsets.only(bottom: 10),
                        decoration: BoxDecoration(
                          color: Colors.white10,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(
                            color: profile.active
                                ? Colors.white54
                                : Colors.transparent,
                          ),
                        ),
                        child: ListTile(
                          leading: _AvatarSwatch(profile: profile),
                          title: Text(profile.avatarLabel),
                          subtitle: Text('${profile.gender} | ${profile.ageGroup}'),
                          trailing: profile.active
                              ? const Text('Active')
                              : const Icon(Icons.chevron_right),
                          onTap: () => Navigator.of(context).pop(profile),
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Close'),
            ),
          ],
        );
      },
    );
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
    final order = _primaryBillingOrder();
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

  BillingOrderPreview? _primaryBillingOrder([List<BillingOrderPreview>? orders]) {
    final source = orders ?? _billingOrders;
    if (source.isEmpty) {
      return null;
    }

    for (final order in source) {
      if (order.status == 'confirming' || order.status == 'pending') {
        return order;
      }
    }

    return source.first;
  }

  String _actionName(ControlAction action) {
    switch (action) {
      case ControlAction.openApp:
        return 'open_app';
      case ControlAction.search:
        return 'search';
      case ControlAction.play:
        return 'play';
      case ControlAction.pause:
        return 'pause';
      case ControlAction.resume:
        return 'resume';
      case ControlAction.next:
        return 'next';
      case ControlAction.previous:
        return 'previous';
      case ControlAction.fastForward:
        return 'fast_forward';
      case ControlAction.rewind:
        return 'rewind';
      case ControlAction.back:
        return 'back';
      case ControlAction.up:
        return 'up';
      case ControlAction.down:
        return 'down';
      case ControlAction.left:
        return 'left';
      case ControlAction.right:
        return 'right';
      case ControlAction.select:
        return 'select';
      case ControlAction.home:
        return 'home';
      case ControlAction.menu:
        return 'menu';
      case ControlAction.volumeUp:
        return 'volume_up';
      case ControlAction.volumeDown:
        return 'volume_down';
      case ControlAction.mute:
        return 'mute';
    }
  }

  bool _isNavigationAction(ControlAction action) {
    switch (action) {
      case ControlAction.up:
      case ControlAction.down:
      case ControlAction.left:
      case ControlAction.right:
      case ControlAction.select:
      case ControlAction.home:
      case ControlAction.menu:
        return true;
      default:
        return false;
    }
  }

  bool _isVolumeAction(ControlAction action) {
    switch (action) {
      case ControlAction.volumeUp:
      case ControlAction.volumeDown:
      case ControlAction.mute:
        return true;
      default:
        return false;
    }
  }

  _HomeSurfaceMode _homeSurfaceMode() {
    if (!_networkSnapshot.isConnected) {
      return _HomeSurfaceMode.offline;
    }
    if (_isLowTokenState() || _isSubscriptionNearExpiry()) {
      return _HomeSurfaceMode.lowCapacity;
    }
    return _HomeSurfaceMode.online;
  }

  bool _isLowTokenState() {
    final profile = _profile;
    if (profile == null) {
      return false;
    }
    return profile.tokenBalance <= 12000 || profile.bootstrapTokenPool <= 1200;
  }

  bool _isSubscriptionNearExpiry() {
    final expiresAt = _profile?.entitlementExpiresAt;
    if (expiresAt == null) {
      return false;
    }
    final parsed = DateTime.tryParse(expiresAt);
    if (parsed == null) {
      return false;
    }
    final now = DateTime.now().toUtc();
    return parsed.isAfter(now) && parsed.difference(now).inDays <= 7;
  }

  int? _subscriptionDaysLeft() {
    final expiresAt = _profile?.entitlementExpiresAt;
    if (expiresAt == null) {
      return null;
    }
    final parsed = DateTime.tryParse(expiresAt);
    if (parsed == null) {
      return null;
    }
    return parsed.difference(DateTime.now().toUtc()).inDays;
  }

  Widget _buildAvatarSurface() {
    return FocusableActionDetector(
      child: InkWell(
        canRequestFocus: true,
        borderRadius: BorderRadius.circular(24),
        onTap: _isHandlingVoice ? null : _handleVoicePress,
        child: SizedBox(
          width: 190,
          child: VirtualHostAvatar(
            avatarLabel: _appState.selectedAvatar,
            subtitle: _subtitle,
            executionStatus: _executionStatus,
            isHandlingVoice: _isHandlingVoice,
            compact: true,
            isLowCapacity: _homeSurfaceMode() == _HomeSurfaceMode.lowCapacity,
            profile: _avatarProfile,
          ),
        ),
      ),
    );
  }

  Widget _buildConversationSurface() {
    final showOverlay = _shouldShowConversationOverlay();

    return Container(
      alignment: Alignment.centerLeft,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AnimatedOpacity(
            duration: const Duration(milliseconds: 220),
            opacity: showOverlay ? 1.0 : 0.0,
            child: Text(
              _subtitle,
              maxLines: 4,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 46,
                height: 1.16,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const SizedBox(height: 14),
          AnimatedOpacity(
            duration: const Duration(milliseconds: 220),
            opacity: showOverlay ? 0.88 : 0.0,
            child: Text(
              _pendingCommand,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 18, color: Colors.white70),
            ),
          ),
        ],
      ),
    );
  }

  bool _shouldShowConversationOverlay() {
    if (_isHandlingVoice) {
      return true;
    }

    final normalizedSubtitle = _subtitle.trim();
    final normalizedStatus = _executionStatus.trim().toLowerCase();
    if (normalizedSubtitle.isEmpty) {
      return false;
    }

    const hiddenDefaults = <String>{
      'press the microphone button to control your tv',
      'press to talk when you are ready',
      'standby ready for the next command.',
    };

    if (hiddenDefaults.contains(normalizedSubtitle.toLowerCase()) &&
        (normalizedStatus == 'idle' || normalizedStatus == 'ready')) {
      return false;
    }

    return normalizedStatus != 'idle' ||
        normalizedSubtitle.toLowerCase() !=
            'press the microphone button to control your tv';
  }

  Widget _buildAppShortcutsSurface() {
    if (_homeSurfaceMode() == _HomeSurfaceMode.offline) {
      return _buildOfflinePrimaryActionsSurface();
    }

    final visibleShortcuts = _appShortcuts
        .where((shortcut) =>
            shortcut.appId != 'cast' &&
            shortcut.appId != 'local_files' &&
            shortcut.appId != 'settings')
        .toList();

    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        height: 138,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: visibleShortcuts.asMap().entries.map((entry) {
            final index = entry.key;
            final shortcut = entry.value;
            return Padding(
              padding: EdgeInsets.only(left: index == 0 ? 0 : 18),
              child: FocusableActionDetector(
                onShowFocusHighlight: (focused) {
                  if (focused && mounted) {
                    setState(() {
                      _focusedAppShortcutIndex = index;
                    });
                  }
                },
                child: InkWell(
                  canRequestFocus: true,
                  borderRadius: BorderRadius.circular(32),
                  onTap: () => _handleLaunchShortcut(shortcut),
                  child: _buildIconOnlyShortcutTile(
                    shortcut,
                    isFocused: _focusedAppShortcutIndex == index,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildOfflinePrimaryActionsSurface() {
    final highlightedIndex = _normalizedOfflineHomeActionIndex(
      _offlineHomeActionIndex,
    );

    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        height: 138,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: List<Widget>.generate(_offlinePrimaryActions.length, (index) {
            final action = _offlinePrimaryActions[index];
            final isHighlighted = index == highlightedIndex;
            return Padding(
              padding: EdgeInsets.only(left: index == 0 ? 0 : 18),
              child: InkWell(
                canRequestFocus: true,
                borderRadius: BorderRadius.circular(32),
                onTap: () => _handleOfflinePrimaryActionTap(index),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 96,
                  height: 96,
                  decoration: BoxDecoration(
                    color: isHighlighted
                        ? action.accentColor.withValues(alpha: 0.18)
                        : Colors.white.withValues(alpha: 0.05),
                    borderRadius: BorderRadius.circular(30),
                    border: Border.all(
                      color: isHighlighted
                          ? action.accentColor
                          : Colors.white.withValues(alpha: 0.12),
                      width: isHighlighted ? 2 : 1,
                    ),
                  ),
                  child: Icon(
                    action.icon,
                    color: action.accentColor,
                    size: 42,
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }

  Widget _buildShortcutTile(_AppShortcut shortcut, {required bool isFocused}) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isFocused
            ? shortcut.accentColor.withValues(alpha: 0.16)
            : Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: isFocused
              ? shortcut.accentColor
              : shortcut.enabled
                  ? shortcut.accentColor.withValues(alpha: 0.35)
                  : Colors.white12,
          width: isFocused ? 2 : 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(
              color: shortcut.accentColor.withValues(alpha: 0.18),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Icon(
              shortcut.icon,
              color: shortcut.accentColor,
              size: 30,
            ),
          ),
          const Spacer(),
          Text(
            shortcut.label,
            style: TextStyle(
              fontSize: 18,
              fontWeight: isFocused ? FontWeight.w700 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            shortcut.enabled ? 'Ready' : 'Soon',
            style: TextStyle(
              fontSize: 12,
              color: shortcut.enabled ? shortcut.accentColor : Colors.white54,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildIconOnlyShortcutTile(
    _AppShortcut shortcut, {
    required bool isFocused,
  }) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      width: 96,
      height: 96,
      decoration: BoxDecoration(
        color: isFocused
            ? shortcut.accentColor.withValues(alpha: 0.18)
            : Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(30),
        border: Border.all(
          color: isFocused
              ? shortcut.accentColor
              : Colors.white.withValues(alpha: 0.12),
          width: isFocused ? 2 : 1,
        ),
      ),
      child: Icon(
        shortcut.icon,
        color: shortcut.enabled ? shortcut.accentColor : Colors.white38,
        size: 42,
      ),
    );
  }

  Widget _buildSurfaceStatusPanel() {
    return const SizedBox.shrink();
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

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Wrap(
        spacing: 12,
        runSpacing: 10,
        children: [
          _FooterChip(label: 'Network', value: networkLabel),
          _FooterChip(label: 'Mode', value: modeLabel),
          _FooterChip(label: 'Voice', value: _voiceCapabilityStatus),
          _FooterChip(label: 'Plan', value: _appState.subscriptionLabel),
          _FooterChip(label: 'Scope', value: _selectedAccessScope),
        ],
      ),
    );
  }

  Widget _buildTopToolBar() {
    final tools = <_AppShortcut>[
      if (_homeSurfaceMode() == _HomeSurfaceMode.offline)
        const _AppShortcut(
          appId: 'settings',
          label: 'Connect',
          icon: Icons.wifi_rounded,
          enabled: true,
          accentColor: Color(0xFFFF8C69),
        ),
      const _AppShortcut(
        appId: 'cast',
        label: 'Cast',
        icon: Icons.cast_connected_rounded,
        enabled: true,
        accentColor: Color(0xFF8BE9FD),
      ),
      const _AppShortcut(
        appId: 'local_files',
        label: 'USB',
        icon: Icons.usb_rounded,
        enabled: true,
        accentColor: Color(0xFFFFD166),
      ),
      const _AppShortcut(
        appId: 'settings',
        label: 'Settings',
        icon: Icons.settings_rounded,
        enabled: true,
        accentColor: Color(0xFF7CC6FE),
      ),
    ];

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: tools.map((tool) {
        return Padding(
          padding: const EdgeInsets.only(left: 10),
          child: InkWell(
            canRequestFocus: true,
            borderRadius: BorderRadius.circular(22),
            onTap: () => _handleLaunchShortcut(tool),
            child: Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(22),
                border: Border.all(
                  color: tool.accentColor.withValues(alpha: 0.35),
                ),
              ),
              child: Icon(tool.icon, color: tool.accentColor, size: 26),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildOfflineWifiAssistantCard() {
    final visibleNetworks = _networkSnapshot.visibleNetworks;
    final highlightedIndex = _normalizedWifiHighlightIndex(
      visibleNetworks,
      _offlineWifiHighlightIndex,
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

  int _normalizedOfflineHomeActionIndex(int currentIndex) {
    if (currentIndex < 0) {
      return 0;
    }
    if (currentIndex >= _offlinePrimaryActions.length) {
      return _offlinePrimaryActions.length - 1;
    }
    return currentIndex;
  }

  int _normalizedWifiHighlightIndex(List<String> networks, int currentIndex) {
    if (networks.isEmpty) {
      return 0;
    }
    if (currentIndex < 0) {
      return 0;
    }
    if (currentIndex >= networks.length) {
      return networks.length - 1;
    }
    return currentIndex;
  }

  Future<void> _handleOfflinePrimaryActionTap(int index) async {
    setState(() {
      _offlineHomeActionIndex = _normalizedOfflineHomeActionIndex(index);
    });

    final action = _offlinePrimaryActions[_offlineHomeActionIndex];
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

  _OfflineWifiGuidance? _buildOfflineWifiNavigationGuidance(ControlAction action) {
    if (_homeSurfaceMode() != _HomeSurfaceMode.offline ||
        _offlineHomeActionIndex != 0 ||
        _networkSnapshot.visibleNetworks.isEmpty) {
      return null;
    }

    final networks = _networkSnapshot.visibleNetworks;
    var index = _normalizedWifiHighlightIndex(networks, _offlineWifiHighlightIndex);

    switch (action) {
      case ControlAction.up:
        index = index > 0 ? index - 1 : 0;
        _offlineWifiHighlightIndex = index;
        return _OfflineWifiGuidance(
          executionStatus: 'Wi-Fi highlight moved',
          pendingCommand: 'Highlighted Wi-Fi: ${networks[index]}',
        );
      case ControlAction.down:
        index = index < networks.length - 1 ? index + 1 : networks.length - 1;
        _offlineWifiHighlightIndex = index;
        return _OfflineWifiGuidance(
          executionStatus: 'Wi-Fi highlight moved',
          pendingCommand: 'Highlighted Wi-Fi: ${networks[index]}',
        );
      case ControlAction.select:
        _offlineWifiHighlightIndex = index;
        return _OfflineWifiGuidance(
          executionStatus: 'Wi-Fi selected',
          pendingCommand:
              'Selected Wi-Fi: ${networks[index]}. Continue with the password on the system keyboard.',
        );
      default:
        return null;
    }
  }

  _OfflineHomeNavigation? _consumeOfflineHomeNavigation({
    required ControlAction resolutionAction,
  }) {
    if (_homeSurfaceMode() != _HomeSurfaceMode.offline) {
      return null;
    }

    switch (resolutionAction) {
      case ControlAction.left:
        _offlineHomeActionIndex = _normalizedOfflineHomeActionIndex(
          _offlineHomeActionIndex - 1,
        );
        final currentLeft = _offlinePrimaryActions[_offlineHomeActionIndex];
        return _OfflineHomeNavigation(
          target: currentLeft.appId,
          action: 'focus',
          assistantText: '${currentLeft.label} focused.',
          executionStatus: 'Offline action focused',
          pendingCommand: 'Focused ${currentLeft.label}.',
        );
      case ControlAction.right:
        _offlineHomeActionIndex = _normalizedOfflineHomeActionIndex(
          _offlineHomeActionIndex + 1,
        );
        final currentRight = _offlinePrimaryActions[_offlineHomeActionIndex];
        return _OfflineHomeNavigation(
          target: currentRight.appId,
          action: 'focus',
          assistantText: '${currentRight.label} focused.',
          executionStatus: 'Offline action focused',
          pendingCommand: 'Focused ${currentRight.label}.',
        );
      case ControlAction.select:
        final current = _offlinePrimaryActions[_offlineHomeActionIndex];
        if (current.appId == 'settings') {
          return _OfflineHomeNavigation(
            target: current.appId,
            action: 'focus',
            assistantText:
                'Connect is focused. Use up and down to choose a Wi-Fi network.',
            executionStatus: 'Wi-Fi list focused',
            pendingCommand: 'Focused Connect. Say up, down, or select.',
          );
        }
        return _OfflineHomeNavigation(
          target: current.appId,
          action: 'open_app',
          assistantText: 'Opening ${current.label} from the offline home surface.',
          executionStatus: 'Offline action selected',
          pendingCommand: 'Executing ${current.label}.',
          launchAppId: current.appId,
        );
      default:
        return null;
    }
  }

  String _mapLocale(String selectedLanguage) {
    switch (selectedLanguage.toLowerCase()) {
      case 'english':
        return 'en-US';
      case 'chinese':
        return 'zh-CN';
      case 'spanish':
        return 'es-ES';
      case 'russian':
        return 'ru-RU';
      case 'turkish':
        return 'tr-TR';
      default:
        return 'en-US';
    }
  }

  String _formatPlan(String rawPlan) {
    switch (rawPlan.toLowerCase()) {
      case 'family':
        return 'Family Plan';
      case 'basic':
        return 'Basic Plan';
      case 'premium':
        return 'Premium Plan';
      default:
        return rawPlan;
    }
  }

  String _formatVoiceCapabilityStatus(Map<String, dynamic> status) {
    final available = status['recognitionAvailable'] == true;
    final permission = status['recordAudioPermission'] == true;
    if (!available) {
      return 'Recognizer unavailable';
    }
    if (!permission) {
      return 'Mic permission needed';
    }
    return 'Ready';
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

class _AppShortcut {
  const _AppShortcut({
    required this.appId,
    required this.label,
    required this.icon,
    required this.enabled,
    required this.accentColor,
  });

  final String appId;
  final String label;
  final IconData icon;
  final bool enabled;
  final Color accentColor;
}

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

class _RecoveryInput {
  const _RecoveryInput({
    required this.txHash,
    required this.chain,
    required this.amountUsd,
  });

  final String txHash;
  final String? chain;
  final double? amountUsd;
}

class _FooterChip extends StatelessWidget {
  const _FooterChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(999),
      ),
      child: RichText(
        text: TextSpan(
          style: const TextStyle(fontSize: 13, color: Colors.white70),
          children: [
            TextSpan(
              text: '$label ',
              style: const TextStyle(color: Colors.white38),
            ),
            TextSpan(
              text: value,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OfflineWifiGuidance {
  const _OfflineWifiGuidance({
    required this.executionStatus,
    required this.pendingCommand,
  });

  final String executionStatus;
  final String pendingCommand;
}

class _OfflineHomeNavigation {
  const _OfflineHomeNavigation({
    required this.target,
    required this.action,
    required this.assistantText,
    required this.executionStatus,
    required this.pendingCommand,
    this.launchAppId,
  });

  final String target;
  final String action;
  final String assistantText;
  final String executionStatus;
  final String pendingCommand;
  final String? launchAppId;
}

class _OfflinePrimaryAction {
  const _OfflinePrimaryAction({
    required this.appId,
    required this.label,
    required this.hint,
    required this.icon,
    required this.accentColor,
  });

  final String appId;
  final String label;
  final String hint;
  final IconData icon;
  final Color accentColor;
}

class _AvatarSwatch extends StatelessWidget {
  const _AvatarSwatch({required this.profile});

  final AvatarProfile profile;

  @override
  Widget build(BuildContext context) {
    Color parseColor(String value, Color fallback) {
      final sanitized = value.replaceFirst('#', '');
      if (sanitized.length != 6 && sanitized.length != 8) {
        return fallback;
      }
      final buffer = StringBuffer();
      if (sanitized.length == 6) {
        buffer.write('ff');
      }
      buffer.write(sanitized);
      try {
        return Color(int.parse(buffer.toString(), radix: 16));
      } on FormatException {
        return fallback;
      }
    }

    final primary = parseColor(profile.primaryColorHex, const Color(0xFF6AE6D8));
    final secondary = parseColor(profile.secondaryColorHex, const Color(0xFF12656A));

    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: [primary, secondary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
    );
  }
}
