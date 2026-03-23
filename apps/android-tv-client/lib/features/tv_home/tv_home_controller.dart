import 'dart:ui';

import '../../core/api_client.dart';
import '../account/current_user_profile.dart';
import '../account/entitlement_recovery_result.dart';
import '../avatar/avatar_profile.dart';
import '../billing/billing_order_preview.dart';
import '../billing/create_billing_order_request.dart';
import '../billing/submit_tx_hash_request.dart';
import '../billing/update_confirmations_request.dart';
import '../control/control_action.dart';
import '../control/intent_resolution.dart';
import '../control/offline_local_router.dart';
import '../control/control_service.dart';
import '../conversation/assistant_log_record.dart';
import '../conversation/router_status.dart';
import '../device_sync/control_authorization_result.dart';
import '../device_sync/device_registration_request.dart';
import '../device_sync/registered_device.dart';
import '../network/network_snapshot.dart';
import '../network/network_status_service.dart';
import '../ota/ota_report_request.dart';
import 'models/tv_home_remote_config.dart';
import 'models/tv_home_shortcut.dart';
import '../voice/voice_command_service.dart';

class TvHomeDashboardData {
  const TvHomeDashboardData({
    required this.profile,
    required this.backendConnected,
    required this.avatarProfile,
    required this.avatarProfiles,
    required this.speechStatus,
    required this.standbyStatus,
    required this.routerStatus,
    required this.routerLogs,
    required this.registeredDevice,
    required this.devices,
    required this.networkSnapshot,
    required this.billingOrders,
    required this.homeConfig,
  });

  final CurrentUserProfile profile;
  final bool backendConnected;
  final AvatarProfile avatarProfile;
  final List<AvatarProfile> avatarProfiles;
  final Map<String, dynamic> speechStatus;
  final VoiceStandbyStatus standbyStatus;
  final RouterStatus routerStatus;
  final List<AssistantLogRecord> routerLogs;
  final RegisteredDevice registeredDevice;
  final List<RegisteredDevice> devices;
  final NetworkSnapshot networkSnapshot;
  final List<BillingOrderPreview> billingOrders;
  final TvHomeRemoteConfig homeConfig;
}

class TvHomeBillingPollData {
  const TvHomeBillingPollData({
    required this.updatedOrders,
    required this.profile,
    required this.activeOrder,
  });

  final List<BillingOrderPreview> updatedOrders;
  final CurrentUserProfile profile;
  final BillingOrderPreview? activeOrder;
}

class TvHomeShortcutFlowData {
  const TvHomeShortcutFlowData({
    required this.offlineMode,
    required this.authorization,
    required this.execution,
  });

  final bool offlineMode;
  final ControlAuthorizationResult? authorization;
  final ControlExecutionResult? execution;
}

class TvHomeWakeSimulationData {
  const TvHomeWakeSimulationData({
    required this.status,
    required this.event,
  });

  final VoiceStandbyStatus status;
  final VoiceWakeEvent event;
}

class TvHomeVoiceResolutionData {
  const TvHomeVoiceResolutionData({
    required this.offlineMode,
    required this.resolution,
  });

  final bool offlineMode;
  final IntentResolution resolution;
}

class TvHomeVoiceExecutionData {
  const TvHomeVoiceExecutionData({
    required this.authorization,
    required this.execution,
    required this.offlinePrimaryExecution,
  });

  final ControlAuthorizationResult? authorization;
  final ControlExecutionResult? execution;
  final ControlExecutionResult? offlinePrimaryExecution;
}

class TvHomeBlockedControlLogData {
  const TvHomeBlockedControlLogData({
    required this.locale,
    required this.userText,
    required this.assistantText,
    required this.appId,
    required this.action,
    required this.route,
    required this.deviceId,
  });

  final String locale;
  final String userText;
  final String assistantText;
  final String appId;
  final String action;
  final String route;
  final String deviceId;
}

class TvHomeBillingMutationData {
  const TvHomeBillingMutationData({
    required this.updatedOrder,
    required this.updatedOrders,
    required this.profile,
  });

  final BillingOrderPreview updatedOrder;
  final List<BillingOrderPreview> updatedOrders;
  final CurrentUserProfile profile;
}

class TvHomeEntitlementTransferData {
  const TvHomeEntitlementTransferData({
    required this.transfer,
    required this.profile,
    required this.devices,
    required this.billingOrders,
  });

  final EntitlementTransferResult transfer;
  final CurrentUserProfile profile;
  final List<RegisteredDevice> devices;
  final List<BillingOrderPreview> billingOrders;
}

class TvHomeController {
  const TvHomeController({
    required ApiClient apiClient,
    required VoiceCommandService voiceCommandService,
    required NetworkStatusService networkStatusService,
    required ControlService controlService,
    required OfflineLocalRouter offlineLocalRouter,
  })  : _apiClient = apiClient,
        _voiceCommandService = voiceCommandService,
        _networkStatusService = networkStatusService,
        _controlService = controlService,
        _offlineLocalRouter = offlineLocalRouter;

  final ApiClient _apiClient;
  final VoiceCommandService _voiceCommandService;
  final NetworkStatusService _networkStatusService;
  final ControlService _controlService;
  final OfflineLocalRouter _offlineLocalRouter;

  Future<TvHomeDashboardData> loadDashboard({
    required DeviceRegistrationRequest deviceRegistration,
  }) async {
    final profile = await _apiClient.getMe();
    final backendConnected = await _apiClient.getHealthStatus();
    final avatarProfile = await _apiClient.getActiveAvatarProfile();
    final avatarProfiles = await _apiClient.getAvatarProfiles();
    final speechStatus = await _voiceCommandService.getSpeechStatus();
    final standbyStatus = await _voiceCommandService.getStandbyStatus();
    final routerStatus = await _apiClient.getRouterStatus();
    final routerLogs = await _apiClient.getRouterLogs();
    final registeredDevice = await _apiClient.registerDevice(deviceRegistration);
    final devices = await _apiClient.getDevices();
    final networkSnapshot = await _networkStatusService.getNetworkSnapshot();
    final billingOrders = await _apiClient.getBillingOrders();
    final locale = PlatformDispatcher.instance.locale;
    final homeConfig = await _apiClient.getTvHomeConfig(
      countryCode: locale.countryCode?.toUpperCase() ?? 'GLOBAL',
    );

    return TvHomeDashboardData(
      profile: profile,
      backendConnected: backendConnected,
      avatarProfile: avatarProfile,
      avatarProfiles: avatarProfiles,
      speechStatus: speechStatus,
      standbyStatus: standbyStatus,
      routerStatus: routerStatus,
      routerLogs: routerLogs,
      registeredDevice: registeredDevice,
      devices: devices,
      networkSnapshot: networkSnapshot,
      billingOrders: billingOrders,
      homeConfig: homeConfig,
    );
  }

  Future<void> syncOtaManifest({
    required String deviceUuid,
    required int currentVersionCode,
  }) async {
    final manifest = await _apiClient.getOtaManifest(
      deviceUuid: deviceUuid,
      currentVersionCode: currentVersionCode,
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
        deviceUuid: deviceUuid,
        releaseId: manifest.releaseId!,
        currentVersionCode: currentVersionCode,
        targetVersionCode: manifest.versionCode!,
        status: 'queued',
        progressPercent: 0,
        note: 'idle_background_queue',
      ),
    );
  }

  Future<TvHomeBillingPollData?> pollBillingOrders({
    required List<BillingOrderPreview> currentOrders,
    bool forceRefresh = false,
  }) async {
    final confirmingOrder = currentOrders.cast<BillingOrderPreview?>().firstWhere(
      (order) => order != null && order.txHash != null && order.status == 'confirming',
      orElse: () => null,
    );

    if (confirmingOrder == null && !forceRefresh) {
      return null;
    }

    if (confirmingOrder != null) {
      await _apiClient.pollBillingOrders();
    }

    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();

    return TvHomeBillingPollData(
      updatedOrders: updatedOrders,
      profile: profile,
      activeOrder: primaryBillingOrder(updatedOrders),
    );
  }

  Future<TvHomeShortcutFlowData> handleShortcut({
    required TvHomeShortcut shortcut,
    required NetworkSnapshot networkSnapshot,
    required String selectedAccessScope,
  }) async {
    if (!shortcut.enabled) {
      return const TvHomeShortcutFlowData(
        offlineMode: false,
        authorization: null,
        execution: null,
      );
    }

    final offlineMode = !networkSnapshot.isConnected;
    final authorization = offlineMode
        ? null
        : await _apiClient.authorizeControl(
            bindingScope: selectedAccessScope,
            appId: shortcut.appId,
            action: 'open_app',
          );

    if (!offlineMode && authorization != null && !authorization.allowed) {
      return TvHomeShortcutFlowData(
        offlineMode: false,
        authorization: authorization,
        execution: null,
      );
    }

    final execution = await _controlService.execute(
      ControlIntent(appId: shortcut.appId, action: ControlAction.openApp),
    );

    return TvHomeShortcutFlowData(
      offlineMode: offlineMode,
      authorization: authorization,
      execution: execution,
    );
  }

  Future<TvHomeWakeSimulationData> simulateWake(String commandText) async {
    final status = await _voiceCommandService.simulateHotwordTrigger(commandText);
    final event = await _voiceCommandService.consumeWakeEvent();
    return TvHomeWakeSimulationData(status: status, event: event);
  }

  Future<VoiceWakeEvent> consumeWakeEvent() {
    return _voiceCommandService.consumeWakeEvent();
  }

  Future<TvHomeVoiceResolutionData> resolveVoiceCommand({
    required String recognizedText,
    required NetworkSnapshot networkSnapshot,
    required String selectedLanguage,
    required String deviceUuid,
  }) async {
    final offlineMode = !networkSnapshot.isConnected;
    final resolution = offlineMode
        ? _offlineLocalRouter.resolve(
            recognizedText,
            snapshot: networkSnapshot,
          )
        : await _apiClient.resolveIntentWithDirectLease(
            text: recognizedText,
            locale: selectedLanguage,
            deviceId: deviceUuid,
          );

    return TvHomeVoiceResolutionData(
      offlineMode: offlineMode,
      resolution: resolution,
    );
  }

  Future<TvHomeVoiceExecutionData> executeVoiceCommand({
    required bool offlineMode,
    required IntentResolution resolution,
    required String selectedAccessScope,
    required bool skipAuthorization,
    String? launchAppId,
  }) async {
    final offlinePrimaryExecution = launchAppId != null
        ? await _controlService.execute(
            ControlIntent(
              appId: launchAppId,
              action: ControlAction.openApp,
            ),
          )
        : null;

    final authorization = resolution.shouldExecuteLocally
        ? skipAuthorization || offlineMode
            ? null
            : await _apiClient.authorizeControl(
                bindingScope: selectedAccessScope,
                appId: resolution.appId,
                action: controlActionApiName(resolution.action),
              )
        : null;

    final execution =
        offlinePrimaryExecution ??
        (resolution.shouldExecuteLocally &&
                launchAppId == null &&
                (offlineMode || (authorization?.allowed ?? false))
            ? await _controlService.execute(
                ControlIntent(
                  appId: resolution.appId,
                  action: resolution.action,
                  queryText: resolution.queryText,
                ),
              )
            : null);

    return TvHomeVoiceExecutionData(
      authorization: authorization,
      execution: execution,
      offlinePrimaryExecution: offlinePrimaryExecution,
    );
  }

  Future<VoiceCommandResult> pressToTalk({
    required String selectedLanguage,
  }) {
    return _voiceCommandService.pressToTalk(
      locale: _mapLocale(selectedLanguage),
    );
  }

  Future<void> updateStandbyExecutionState(String statusText) async {
    await _voiceCommandService.updateStandbyExecutionState(statusText);
  }

  Future<void> logBlockedControl(TvHomeBlockedControlLogData data) {
    return _apiClient.logControlBlock(
      locale: data.locale,
      userText: data.userText,
      assistantText: data.assistantText,
      appId: data.appId,
      action: data.action,
      route: data.route,
      deviceId: data.deviceId,
    );
  }

  Future<TvHomeBillingMutationData> createTopUpOrder(
    CreateBillingOrderRequest request,
  ) async {
    final order = await _apiClient.createBillingOrder(request);
    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();
    return TvHomeBillingMutationData(
      updatedOrder: order,
      updatedOrders: updatedOrders,
      profile: profile,
    );
  }

  Future<TvHomeBillingMutationData> submitBillingOrderTxHash(
    SubmitTxHashRequest request,
  ) async {
    final updatedOrder = await _apiClient.submitBillingOrderTxHash(request);
    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();
    return TvHomeBillingMutationData(
      updatedOrder: updatedOrder,
      updatedOrders: updatedOrders,
      profile: profile,
    );
  }

  Future<TvHomeBillingMutationData> refreshBillingOrderConfirmations({
    required String orderId,
    required int confirmations,
  }) async {
    final updatedOrder = await _apiClient.updateBillingOrderConfirmations(
      UpdateConfirmationsRequest(
        orderId: orderId,
        confirmations: confirmations,
      ),
    );
    final updatedOrders = await _apiClient.getBillingOrders();
    final profile = await _apiClient.getMe();
    return TvHomeBillingMutationData(
      updatedOrder: updatedOrder,
      updatedOrders: updatedOrders,
      profile: profile,
    );
  }

  Future<EntitlementRecoveryResult> recoverByPaymentProof({
    required String txHash,
    String? chain,
    double? amountUsd,
  }) {
    return _apiClient.recoverByPaymentProof(
      txHash: txHash,
      chain: chain,
      amountUsd: amountUsd,
    );
  }

  Future<TvHomeEntitlementTransferData> transferEntitlementsAndRefresh({
    required String fromDeviceUserId,
    required String toDeviceUserId,
    required String paymentProofTxHash,
  }) async {
    final transfer = await _apiClient.transferEntitlements(
      fromDeviceUserId: fromDeviceUserId,
      toDeviceUserId: toDeviceUserId,
      paymentProofTxHash: paymentProofTxHash,
    );
    final profile = await _apiClient.getMe();
    final devices = await _apiClient.getDevices();
    final billingOrders = await _apiClient.getBillingOrders();
    return TvHomeEntitlementTransferData(
      transfer: transfer,
      profile: profile,
      devices: devices,
      billingOrders: billingOrders,
    );
  }
}

BillingOrderPreview? primaryBillingOrder(List<BillingOrderPreview> source) {
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

String controlActionApiName(ControlAction action) {
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

RouterStatus projectRouterStatus({
  required RouterStatus currentStatus,
  required String provider,
  required String transportMode,
}) {
  return RouterStatus(
    provider: provider,
    configured: currentStatus.configured,
    baseUrl: currentStatus.baseUrl,
    model: currentStatus.model,
    transportMode: transportMode,
  );
}
