import '../device_sync/device_token_usage_event.dart';
import '../device_sync/device_token_usage_summary.dart';

class CurrentUserProfile {
  const CurrentUserProfile({
    required this.id,
    required this.email,
    required this.displayName,
    required this.plan,
    required this.tokenBalance,
    required this.bootstrapTokenPool,
    required this.deviceLimit,
    required this.activeDevices,
    required this.paymentPriority,
    required this.sharedDeviceBindings,
    required this.entitlementExpiresAt,
    required this.latestStablecoinOrderStatus,
    required this.recentBootstrapTokenUsage,
    required this.bootstrapTokenUsageSummary,
  });

  final String id;
  final String email;
  final String displayName;
  final String plan;
  final int tokenBalance;
  final int bootstrapTokenPool;
  final int deviceLimit;
  final int activeDevices;
  final String paymentPriority;
  final int sharedDeviceBindings;
  final String? entitlementExpiresAt;
  final String? latestStablecoinOrderStatus;
  final List<DeviceTokenUsageEvent> recentBootstrapTokenUsage;
  final DeviceTokenUsageSummary bootstrapTokenUsageSummary;

  factory CurrentUserProfile.fromJson(Map<String, dynamic> json) {
    return CurrentUserProfile(
      id: json['id'] as String? ?? 'user_demo',
      email: json['email'] as String? ?? 'demo@openclaw.local',
      displayName: json['displayName'] as String? ?? 'OpenClaw Demo User',
      plan: json['plan'] as String? ?? 'family',
      tokenBalance: (json['tokenBalance'] as num?)?.round() ?? 128000,
      bootstrapTokenPool: (json['bootstrapTokenPool'] as num?)?.round() ?? 5000,
      deviceLimit: (json['deviceLimit'] as num?)?.round() ?? 3,
      activeDevices: (json['activeDevices'] as num?)?.round() ?? 2,
      paymentPriority: json['paymentPriority'] as String? ?? 'stablecoin',
      sharedDeviceBindings: (json['sharedDeviceBindings'] as num?)?.round() ?? 0,
      entitlementExpiresAt: json['entitlementExpiresAt'] as String?,
      latestStablecoinOrderStatus: json['latestStablecoinOrderStatus'] as String?,
      recentBootstrapTokenUsage:
          (json['recentBootstrapTokenUsage'] as List<dynamic>? ?? const [])
              .whereType<Map<String, dynamic>>()
              .map(DeviceTokenUsageEvent.fromJson)
              .toList(),
      bootstrapTokenUsageSummary: DeviceTokenUsageSummary.fromJson(
        json['bootstrapTokenUsageSummary'] as Map<String, dynamic>? ?? const {},
      ),
    );
  }
}
