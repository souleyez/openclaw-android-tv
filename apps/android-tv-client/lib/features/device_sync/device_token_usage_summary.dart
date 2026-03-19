class DeviceTokenUsageSummary {
  const DeviceTokenUsageSummary({
    required this.todayConsumed,
    required this.totalConsumed,
  });

  final int todayConsumed;
  final int totalConsumed;

  factory DeviceTokenUsageSummary.fromJson(Map<String, dynamic> json) {
    return DeviceTokenUsageSummary(
      todayConsumed: (json['todayConsumed'] as num?)?.round() ?? 0,
      totalConsumed: (json['totalConsumed'] as num?)?.round() ?? 0,
    );
  }
}
