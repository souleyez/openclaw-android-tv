class EntitlementRecoveryResult {
  const EntitlementRecoveryResult({
    required this.deviceUserId,
    required this.displayName,
    required this.planCode,
    required this.orderId,
    required this.chain,
    required this.amountUsd,
    required this.txHash,
  });

  final String deviceUserId;
  final String displayName;
  final String planCode;
  final String orderId;
  final String chain;
  final double amountUsd;
  final String txHash;

  factory EntitlementRecoveryResult.fromJson(Map<String, dynamic> json) {
    final deviceUser = json['deviceUser'] as Map<String, dynamic>? ?? const {};
    final order = json['order'] as Map<String, dynamic>? ?? const {};

    return EntitlementRecoveryResult(
      deviceUserId: deviceUser['id'] as String? ?? 'unknown_device_user',
      displayName: deviceUser['displayName'] as String? ?? 'Recovered Device User',
      planCode: deviceUser['planCode'] as String? ?? 'basic',
      orderId: order['id'] as String? ?? 'unknown_order',
      chain: order['chain'] as String? ?? 'Unknown',
      amountUsd: (order['amountUsd'] as num?)?.toDouble() ?? 0,
      txHash: order['txHash'] as String? ?? '',
    );
  }
}

class EntitlementTransferResult {
  const EntitlementTransferResult({
    required this.fromDeviceUserId,
    required this.toDeviceUserId,
    required this.transferred,
  });

  final String fromDeviceUserId;
  final String toDeviceUserId;
  final bool transferred;

  factory EntitlementTransferResult.fromJson(Map<String, dynamic> json) {
    return EntitlementTransferResult(
      fromDeviceUserId: json['fromDeviceUser'] as String? ?? 'unknown_source',
      toDeviceUserId: json['toDeviceUser'] as String? ?? 'unknown_target',
      transferred: json['transferred'] as bool? ?? false,
    );
  }
}
