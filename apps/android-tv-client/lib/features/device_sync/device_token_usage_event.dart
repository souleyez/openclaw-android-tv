class DeviceTokenUsageEvent {
  const DeviceTokenUsageEvent({
    required this.id,
    required this.deviceUuid,
    required this.amountConsumed,
    required this.triggerSource,
    required this.bootstrapTokenRemaining,
    required this.createdAt,
  });

  final String id;
  final String deviceUuid;
  final int amountConsumed;
  final String triggerSource;
  final int bootstrapTokenRemaining;
  final String createdAt;

  factory DeviceTokenUsageEvent.fromJson(Map<String, dynamic> json) {
    return DeviceTokenUsageEvent(
      id: json['id'] as String? ?? 'usage_event',
      deviceUuid: json['deviceUuid'] as String? ?? 'device_demo_android_tv',
      amountConsumed: (json['amountConsumed'] as num?)?.round() ?? 0,
      triggerSource: json['triggerSource'] as String? ?? 'voice_turn',
      bootstrapTokenRemaining:
          (json['bootstrapTokenRemaining'] as num?)?.round() ?? 0,
      createdAt: json['createdAt'] as String? ?? '',
    );
  }
}
