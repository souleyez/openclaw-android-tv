import '../account/current_user_profile.dart';
import '../account/entitlement_recovery_result.dart';
import '../billing/billing_order_preview.dart';
import '../control/control_action.dart';
import '../control/control_service.dart';
import '../control/intent_resolution.dart';
import '../conversation/conversation_turn.dart';
import '../conversation/control_log_entry.dart';
import '../device_sync/control_authorization_result.dart';
import '../voice/voice_command_service.dart';
import 'models/tv_home_shortcut.dart';

class TvHomeVoicePresentation {
  const TvHomeVoicePresentation({
    required this.assistantText,
    required this.executionStatus,
    required this.pendingCommand,
    required this.transportLabel,
    required this.channelStatus,
    required this.conversationTurns,
    required this.controlLogEntry,
  });

  final String assistantText;
  final String executionStatus;
  final String pendingCommand;
  final String transportLabel;
  final String channelStatus;
  final List<ConversationTurn> conversationTurns;
  final ControlLogEntry? controlLogEntry;
}

class TvHomeShortcutPresentation {
  const TvHomeShortcutPresentation({
    required this.executionStatus,
    required this.subtitle,
    required this.pendingCommand,
    required this.channelStatus,
    required this.conversationTurns,
    required this.controlLogEntry,
  });

  final String executionStatus;
  final String subtitle;
  final String pendingCommand;
  final String channelStatus;
  final List<ConversationTurn> conversationTurns;
  final ControlLogEntry? controlLogEntry;
}

class TvHomeWakePresentation {
  const TvHomeWakePresentation({
    required this.executionStatus,
    required this.subtitle,
    required this.pendingCommand,
    required this.channelStatus,
    required this.conversationTurns,
  });

  final String executionStatus;
  final String subtitle;
  final String pendingCommand;
  final String channelStatus;
  final List<ConversationTurn> conversationTurns;
}

class TvHomeBillingPresentation {
  const TvHomeBillingPresentation({
    required this.executionStatus,
    required this.subtitle,
    required this.pendingCommand,
    required this.channelStatus,
    required this.conversationTurns,
  });

  final String executionStatus;
  final String subtitle;
  final String pendingCommand;
  final String channelStatus;
  final List<ConversationTurn> conversationTurns;
}

class TvHomeRecoveryPresentation {
  const TvHomeRecoveryPresentation({
    required this.executionStatus,
    required this.subtitle,
    required this.pendingCommand,
    required this.channelStatus,
    required this.conversationTurns,
    required this.controlLogEntry,
  });

  final String executionStatus;
  final String subtitle;
  final String pendingCommand;
  final String channelStatus;
  final List<ConversationTurn> conversationTurns;
  final ControlLogEntry? controlLogEntry;
}

bool shouldShowConversationOverlay({
  required bool isHandlingVoice,
  required String subtitle,
  required String executionStatus,
}) {
  if (isHandlingVoice) {
    return true;
  }

  final normalizedSubtitle = subtitle.trim();
  final normalizedStatus = executionStatus.trim().toLowerCase();
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

bool isLowTokenState(CurrentUserProfile? profile) {
  if (profile == null) {
    return false;
  }
  return profile.tokenBalance <= 12000 || profile.bootstrapTokenPool <= 1200;
}

bool isSubscriptionNearExpiry(CurrentUserProfile? profile) {
  final expiresAt = profile?.entitlementExpiresAt;
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

String mapLocale(String selectedLanguage) {
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

String formatPlan(String rawPlan) {
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

String formatVoiceCapabilityStatus(Map<String, dynamic> status) {
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

int normalizedOfflineHomeActionIndex({
  required int currentIndex,
  required int actionCount,
}) {
  if (currentIndex < 0) {
    return 0;
  }
  if (currentIndex >= actionCount) {
    return actionCount - 1;
  }
  return currentIndex;
}

int normalizedWifiHighlightIndex({
  required List<String> networks,
  required int currentIndex,
}) {
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

TvHomeVoicePresentation projectVoicePresentation({
  required String recognizedText,
  required String voiceProvider,
  required bool offlineMode,
  required IntentResolution resolution,
  required ControlAuthorizationResult? authorization,
  required ControlExecutionResult? execution,
  required String blockedAssistantReply,
  String? offlineTarget,
  String? offlineAction,
  String? offlineAssistantText,
  String? offlineExecutionStatus,
  String? offlinePendingCommand,
  String? wifiExecutionStatus,
  String? wifiPendingCommand,
}) {
  final assistantText =
      offlineAssistantText ??
      (execution != null
          ? (execution.success ? execution.message : resolution.assistantText)
          : authorization != null && !authorization.allowed
          ? blockedAssistantReply
          : resolution.assistantText);

  var executionStatus =
      offlineExecutionStatus ??
      (execution == null
          ? authorization != null && !authorization.allowed
                ? 'Control blocked'
                : 'Chat reply ready'
          : execution.success
          ? isNavigationAction(resolution.action)
                ? 'Navigation mode active'
                : isVolumeAction(resolution.action)
                ? 'Volume adjusted'
                : 'Control executed'
          : 'Control failed');
  if (wifiExecutionStatus != null) {
    executionStatus = wifiExecutionStatus;
  }

  var pendingCommand =
      offlinePendingCommand ??
      (execution != null
          ? execution.success
                ? isNavigationAction(resolution.action)
                    ? 'Navigating ${resolution.appId} with ${resolution.action.name}'
                    : isVolumeAction(resolution.action)
                    ? 'Adjusting volume with ${resolution.action.name}'
                    : 'Executing ${resolution.appId} ${resolution.action.name}'
                : 'Execution failed for ${resolution.appId} ${resolution.action.name}'
          : authorization != null && !authorization.allowed
          ? 'Blocked ${resolution.appId} ${resolution.action.name}'
          : resolution.shouldExecuteLocally
          ? 'Local action queued for ${resolution.appId} ${resolution.action.name}'
          : 'Conversation response prepared');
  if (wifiPendingCommand != null) {
    pendingCommand = wifiPendingCommand;
  }

  final transportLabel = offlineMode
      ? 'offline_local'
      : resolution.modelProvider.contains('client_direct_provider_lease')
      ? 'client_direct_provider_lease'
      : 'server_router_fallback';
  final channelStatus =
      'Voice: $voiceProvider | Transport: $transportLabel | Model: ${resolution.modelProvider} | Route: ${resolution.route} | '
      'Confidence: ${resolution.confidence.toStringAsFixed(2)} | Tokens: ${resolution.tokenUsage} | '
      'Free left: ${resolution.bootstrapTokenRemaining}'
      '${!offlineMode && authorization != null ? ' | Scope: ${authorization.bindingScope} ${authorization.allowed ? 'allowed' : 'blocked'}' : ''}'
      '${offlineTarget != null && offlineAction != null ? ' | Control: offline_home_surface' : execution != null ? ' | Control: ${execution.strategyUsed}' : ' | Control: chat_only'}'
      '${resolution.fallbackReason != null ? ' | Fallback: ${resolution.fallbackReason}' : ''}';

  final conversationTurns = <ConversationTurn>[
    ConversationTurn(
      speaker: 'user',
      text: recognizedText,
      metadata: 'voice | $voiceProvider',
    ),
    ConversationTurn(
      speaker: 'assistant',
      text: assistantText,
      metadata: '${resolution.mode} | $transportLabel | ${resolution.modelProvider}',
    ),
  ];

  ControlLogEntry? controlLogEntry;
  if (offlineTarget != null && offlineAction != null) {
    controlLogEntry = ControlLogEntry(
      target: offlineTarget,
      action: offlineAction,
      status: 'ok',
      strategy: 'offline_home_surface',
    );
  } else if (execution != null) {
    controlLogEntry = ControlLogEntry(
      target: resolution.appId,
      action: resolution.queryText == null
          ? resolution.action.name
          : '${resolution.action.name}(${resolution.queryText})',
      status: execution.success ? 'ok' : 'failed',
      strategy: execution.strategyUsed,
    );
  } else if (authorization != null && !authorization.allowed) {
    controlLogEntry = ControlLogEntry(
      target: resolution.appId,
      action: resolution.action.name,
      status: 'blocked',
      strategy: authorization.reason,
    );
  }

  return TvHomeVoicePresentation(
    assistantText: assistantText,
    executionStatus: executionStatus,
    pendingCommand: pendingCommand,
    transportLabel: transportLabel,
    channelStatus: channelStatus,
    conversationTurns: conversationTurns,
    controlLogEntry: controlLogEntry,
  );
}

bool isNavigationAction(ControlAction action) {
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

bool isVolumeAction(ControlAction action) {
  switch (action) {
    case ControlAction.volumeUp:
    case ControlAction.volumeDown:
    case ControlAction.mute:
      return true;
    default:
      return false;
  }
}

TvHomeShortcutPresentation projectShortcutPresentation({
  required TvHomeShortcut shortcut,
  required bool offlineMode,
  required ControlExecutionResult execution,
}) {
  return TvHomeShortcutPresentation(
    executionStatus: execution.success ? 'Control executed' : 'Control failed',
    subtitle: execution.message,
    pendingCommand: execution.success
        ? offlineMode
              ? 'Opening ${shortcut.label} locally while offline.'
              : 'Opening ${shortcut.label} from the TV home surface.'
        : 'Open command failed for ${shortcut.label}.',
    channelStatus:
        'Shortcut: ${shortcut.label} | ${offlineMode ? 'Mode: offline_local' : 'Mode: online'} | ${execution.strategyUsed}',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text: execution.message,
        metadata: 'desktop_shortcut | ${shortcut.appId}',
      ),
    ],
    controlLogEntry: ControlLogEntry(
      target: shortcut.appId,
      action: 'openApp',
      status: execution.success ? 'ok' : 'failed',
      strategy: execution.strategyUsed,
    ),
  );
}

TvHomeWakePresentation? projectWakePresentation(VoiceWakeEvent event) {
  final commandText = event.commandText;
  if (commandText == null || commandText.isEmpty) {
    return null;
  }

  return TvHomeWakePresentation(
    executionStatus: 'Wake event received',
    subtitle: 'Wake received: $commandText',
    pendingCommand: 'Wake source ${event.source}. Preparing to handle "$commandText".',
    channelStatus: 'Wake source: ${event.source}',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text: 'Wake event received for "$commandText". Preparing command routing.',
        metadata: 'standby | ${event.source}',
      ),
    ],
  );
}

TvHomeBillingPresentation projectBillingTopUpPresentation(
  BillingOrderPreview order,
) {
  return TvHomeBillingPresentation(
    executionStatus: 'Top-up order ready',
    subtitle:
        'Top-up order created: ${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} on ${order.chain}',
    pendingCommand:
        'Preparing settlement flow for ${order.stablecoinSymbol} on ${order.chain}.',
    channelStatus:
        'Order ${order.id} | ${order.status} | ${order.confirmations} confirmations',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text:
            'Created a ${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} top-up on ${order.chain}.',
        metadata: 'billing | ${order.status}',
      ),
    ],
  );
}

TvHomeBillingPresentation projectBillingTxHashPresentation(
  BillingOrderPreview order,
) {
  return TvHomeBillingPresentation(
    executionStatus: 'Transaction hash submitted',
    subtitle:
        'Transaction hash submitted for ${order.stablecoinSymbol} on ${order.chain}.',
    pendingCommand:
        'Payment proof submitted. Waiting for blockchain confirmations.',
    channelStatus: 'Order ${order.id} | ${order.status} | Tx ${order.txHash ?? 'pending'}',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text:
            'I recorded the tx hash for ${order.id}. I will keep checking confirmations on ${order.chain}.',
        metadata: 'billing | ${order.status}',
      ),
    ],
  );
}

TvHomeBillingPresentation projectBillingConfirmationPresentation(
  BillingOrderPreview order,
) {
  return TvHomeBillingPresentation(
    executionStatus: 'Confirmations refreshed',
    subtitle:
        'Order ${order.id} is now ${order.status} with ${order.confirmations} confirmations.',
    pendingCommand:
        'Settlement check updated to ${order.confirmations} confirmations.',
    channelStatus:
        'Order ${order.id} | ${order.status} | ${order.confirmations} confirmations',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text:
            'I refreshed the chain status. ${order.stablecoinSymbol} on ${order.chain} now has ${order.confirmations} confirmations.',
        metadata: 'billing | ${order.status}',
      ),
    ],
  );
}

TvHomeRecoveryPresentation projectRecoverySameDevicePresentation({
  required String currentDeviceUserId,
}) {
  return TvHomeRecoveryPresentation(
    executionStatus: 'Recovery not needed',
    subtitle: 'This payment proof already belongs to the current device.',
    pendingCommand: 'No entitlement transfer was needed.',
    channelStatus: 'Recovery proof matched current device user $currentDeviceUserId',
    conversationTurns: const <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text:
            'I checked the payment proof. It already belongs to this device, so there is nothing to transfer.',
        metadata: 'recovery | same_device',
      ),
    ],
    controlLogEntry: null,
  );
}

TvHomeRecoveryPresentation projectRecoveryCancelledPresentation() {
  return const TvHomeRecoveryPresentation(
    executionStatus: 'Recovery cancelled',
    subtitle: 'Rights recovery was cancelled.',
    pendingCommand: 'Waiting for another recovery confirmation.',
    channelStatus: 'Recovery transfer cancelled',
    conversationTurns: <ConversationTurn>[],
    controlLogEntry: null,
  );
}

TvHomeRecoveryPresentation projectRecoveryFailurePresentation() {
  return const TvHomeRecoveryPresentation(
    executionStatus: 'Recovery failed',
    subtitle:
        'I could not verify that payment proof. Try the last successful payment hash again.',
    pendingCommand:
        'Need a valid minimum-payment proof from the old payment account.',
    channelStatus: 'Recovery proof validation failed',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text:
            'I could not match that payment proof to a recoverable device user. Please try the latest successful payment from the old account.',
        metadata: 'recovery | failed',
      ),
    ],
    controlLogEntry: null,
  );
}

TvHomeRecoveryPresentation projectRecoverySuccessPresentation({
  required EntitlementRecoveryResult recovery,
  required EntitlementTransferResult transfer,
}) {
  return TvHomeRecoveryPresentation(
    executionStatus: 'Rights recovered',
    subtitle:
        'Rights moved from ${transfer.fromDeviceUserId} to ${transfer.toDeviceUserId}.',
    pendingCommand:
        'Previous device has lost its entitlement. This device is now active.',
    channelStatus:
        'Recovery transfer completed | ${recovery.chain} | ${recovery.amountUsd.toStringAsFixed(2)} USD',
    conversationTurns: <ConversationTurn>[
      ConversationTurn(
        speaker: 'assistant',
        text:
            'I restored the entitlement from ${recovery.displayName} to this device. The previous device has now lost those rights.',
        metadata: 'recovery | transferred',
      ),
    ],
    controlLogEntry: ControlLogEntry(
      target: 'entitlement',
      action: 'recover',
      status: transfer.transferred ? 'ok' : 'failed',
      strategy: 'payment_proof_transfer',
    ),
  );
}
