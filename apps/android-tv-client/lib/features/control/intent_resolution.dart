import 'control_action.dart';

class IntentResolution {
  const IntentResolution({
    required this.requestId,
    required this.appId,
    required this.action,
    required this.queryText,
    required this.replyText,
    required this.assistantText,
    required this.shouldExecuteLocally,
    required this.mode,
    required this.route,
    required this.modelProvider,
    required this.confidence,
    required this.fallbackReason,
    required this.tokenUsage,
    required this.bootstrapTokenRemaining,
  });

  final String requestId;
  final String appId;
  final ControlAction action;
  final String? queryText;
  final String replyText;
  final String assistantText;
  final bool shouldExecuteLocally;
  final String mode;
  final String route;
  final String modelProvider;
  final double confidence;
  final String? fallbackReason;
  final int tokenUsage;
  final int bootstrapTokenRemaining;

  factory IntentResolution.fromJson(Map<String, dynamic> json) {
    return IntentResolution(
      requestId: json['requestId'] as String? ?? 'unknown_request',
      appId: json['appId'] as String? ?? 'unknown',
      action: _parseAction(json['action'] as String?),
      queryText: json['queryText'] as String?,
      replyText: json['replyText'] as String? ?? 'No reply text',
      assistantText:
          json['assistantText'] as String? ??
          json['replyText'] as String? ??
          'No assistant text',
      shouldExecuteLocally: json['shouldExecuteLocally'] as bool? ?? false,
      mode: json['mode'] as String? ?? 'control',
      route: json['route'] as String? ?? 'unknown',
      modelProvider: json['modelProvider'] as String? ?? 'unknown_model',
      confidence: (json['confidence'] as num?)?.toDouble() ?? 0,
      fallbackReason: json['fallbackReason'] as String?,
      tokenUsage: json['tokenUsage'] as int? ?? 0,
      bootstrapTokenRemaining: json['bootstrapTokenRemaining'] as int? ?? 0,
    );
  }

  static ControlAction _parseAction(String? rawAction) {
    switch (rawAction) {
      case 'open_app':
        return ControlAction.openApp;
      case 'search':
        return ControlAction.search;
      case 'play':
        return ControlAction.play;
      case 'pause':
        return ControlAction.pause;
      case 'resume':
        return ControlAction.resume;
      case 'next':
        return ControlAction.next;
      case 'previous':
        return ControlAction.previous;
      case 'fast_forward':
        return ControlAction.fastForward;
      case 'rewind':
        return ControlAction.rewind;
      case 'back':
        return ControlAction.back;
      case 'up':
        return ControlAction.up;
      case 'down':
        return ControlAction.down;
      case 'left':
        return ControlAction.left;
      case 'right':
        return ControlAction.right;
      case 'select':
        return ControlAction.select;
      case 'home':
        return ControlAction.home;
      case 'menu':
        return ControlAction.menu;
      case 'volume_up':
        return ControlAction.volumeUp;
      case 'volume_down':
        return ControlAction.volumeDown;
      case 'mute':
        return ControlAction.mute;
      default:
        return ControlAction.openApp;
    }
  }
}
