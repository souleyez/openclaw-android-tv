import 'package:flutter/services.dart';

class VoiceCommandResult {
  const VoiceCommandResult({
    required this.recognizedText,
    required this.feedbackText,
    required this.provider,
  });

  final String recognizedText;
  final String feedbackText;
  final String provider;
}

class VoiceStandbyStatus {
  const VoiceStandbyStatus({
    required this.enabled,
    required this.running,
    required this.statusText,
    required this.systemHotwordPrivilege,
    required this.pendingWakeCommand,
    required this.pendingWakeSource,
  });

  final bool enabled;
  final bool running;
  final String statusText;
  final bool systemHotwordPrivilege;
  final String? pendingWakeCommand;
  final String? pendingWakeSource;

  factory VoiceStandbyStatus.fromJson(Map<String, dynamic> json) {
    return VoiceStandbyStatus(
      enabled: json['enabled'] as bool? ?? false,
      running: json['running'] as bool? ?? false,
      statusText: json['statusText'] as String? ?? 'Background standby disabled',
      systemHotwordPrivilege: json['systemHotwordPrivilege'] as bool? ?? false,
      pendingWakeCommand: json['pendingWakeCommand'] as String?,
      pendingWakeSource: json['pendingWakeSource'] as String?,
    );
  }
}

class VoiceWakeEvent {
  const VoiceWakeEvent({
    required this.commandText,
    required this.source,
  });

  final String? commandText;
  final String source;
}

class VoiceCommandService {
  const VoiceCommandService();

  static const MethodChannel _channel = MethodChannel(
    'com.openclaw.assistant/voice',
  );

  Future<Map<String, dynamic>> getSpeechStatus() async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>('getSpeechStatus');
      return raw ?? <String, dynamic>{};
    } on PlatformException {
      return <String, dynamic>{};
    }
  }

  Future<VoiceCommandResult> pressToTalk({
    required String locale,
  }) async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>(
        'pressToTalk',
        <String, dynamic>{
          'locale': locale,
        },
      );
      if (raw == null) {
        return _fallbackResult();
      }

      return VoiceCommandResult(
        recognizedText: raw['recognizedText'] as String? ?? 'Open YouTube',
        feedbackText: raw['feedbackText'] as String? ?? 'Opening YouTube',
        provider: raw['provider'] as String? ?? 'android_stub',
      );
    } on PlatformException {
      return _fallbackResult();
    }
  }

  Future<VoiceStandbyStatus> getStandbyStatus() async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>('getStandbyStatus');
      return VoiceStandbyStatus.fromJson(raw ?? <String, dynamic>{});
    } on PlatformException {
      return const VoiceStandbyStatus(
        enabled: false,
        running: false,
        statusText: 'Background standby unavailable',
        systemHotwordPrivilege: false,
        pendingWakeCommand: null,
        pendingWakeSource: 'none',
      );
    }
  }

  Future<VoiceStandbyStatus> setBackgroundStandby({
    required bool enabled,
    required String statusText,
  }) async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>(
        'setBackgroundStandby',
        <String, dynamic>{
          'enabled': enabled,
          'statusText': statusText,
        },
      );
      return VoiceStandbyStatus.fromJson(raw ?? <String, dynamic>{});
    } on PlatformException {
      return VoiceStandbyStatus(
        enabled: enabled,
        running: false,
        statusText: 'Background standby request failed',
        systemHotwordPrivilege: false,
        pendingWakeCommand: null,
        pendingWakeSource: 'none',
      );
    }
  }

  Future<VoiceStandbyStatus> updateStandbyExecutionState(String statusText) async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>(
        'updateStandbyExecutionState',
        <String, dynamic>{
          'statusText': statusText,
        },
      );
      return VoiceStandbyStatus.fromJson(raw ?? <String, dynamic>{});
    } on PlatformException {
      return const VoiceStandbyStatus(
        enabled: false,
        running: false,
        statusText: 'Standby update unavailable',
        systemHotwordPrivilege: false,
        pendingWakeCommand: null,
        pendingWakeSource: 'none',
      );
    }
  }

  Future<VoiceStandbyStatus> simulateHotwordTrigger(String commandText) async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>(
        'simulateHotwordTrigger',
        <String, dynamic>{
          'commandText': commandText,
        },
      );
      return VoiceStandbyStatus.fromJson(raw ?? <String, dynamic>{});
    } on PlatformException {
      return const VoiceStandbyStatus(
        enabled: false,
        running: false,
        statusText: 'Hotword simulation unavailable',
        systemHotwordPrivilege: false,
        pendingWakeCommand: null,
        pendingWakeSource: 'none',
      );
    }
  }

  Future<VoiceWakeEvent> consumeWakeEvent() async {
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>('consumeWakeEvent');
      return VoiceWakeEvent(
        commandText: raw?['commandText'] as String?,
        source: raw?['source'] as String? ?? 'none',
      );
    } on PlatformException {
      return const VoiceWakeEvent(commandText: null, source: 'none');
    }
  }

  VoiceCommandResult _fallbackResult() {
    return const VoiceCommandResult(
      recognizedText: 'Open YouTube',
      feedbackText: 'Opening YouTube',
      provider: 'flutter_fallback',
    );
  }
}
