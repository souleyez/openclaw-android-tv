class AssistantLogRecord {
  const AssistantLogRecord({
    required this.kind,
    required this.locale,
    required this.userText,
    required this.assistantText,
    required this.mode,
    required this.modelProvider,
    required this.tokenUsage,
    required this.bootstrapTokenRemaining,
    required this.createdAt,
    this.deviceId,
    this.appId,
    this.action,
    this.queryText,
    this.route,
    this.transportMode,
  });

  final String kind;
  final String locale;
  final String userText;
  final String assistantText;
  final String mode;
  final String modelProvider;
  final int tokenUsage;
  final int bootstrapTokenRemaining;
  final String createdAt;
  final String? deviceId;
  final String? appId;
  final String? action;
  final String? queryText;
  final String? route;
  final String? transportMode;

  factory AssistantLogRecord.fromJson(Map<String, dynamic> json) {
    return AssistantLogRecord(
      kind: json['kind'] as String? ?? 'voice_turn',
      locale: json['locale'] as String? ?? 'en-US',
      userText: json['userText'] as String? ?? '',
      assistantText: json['assistantText'] as String? ?? '',
      mode: json['mode'] as String? ?? 'chat',
      modelProvider: json['modelProvider'] as String? ?? 'unknown_model',
      tokenUsage: json['tokenUsage'] as int? ?? 0,
      bootstrapTokenRemaining: json['bootstrapTokenRemaining'] as int? ?? 0,
      createdAt: json['createdAt'] as String? ?? '',
      deviceId: json['deviceId'] as String?,
      appId: json['appId'] as String?,
      action: json['action'] as String?,
      queryText: json['queryText'] as String?,
      route: json['route'] as String?,
      transportMode: json['transportMode'] as String?,
    );
  }
}
