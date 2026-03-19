class RegisteredDevice {
  const RegisteredDevice({
    required this.id,
    required this.deviceUuid,
    required this.deviceName,
    required this.androidVersion,
    required this.isAndroidTv,
    required this.status,
    required this.bindingStatus,
    required this.bootstrapTokenGrant,
    required this.bootstrapTokenRemaining,
  });

  final String id;
  final String deviceUuid;
  final String deviceName;
  final String androidVersion;
  final bool isAndroidTv;
  final String status;
  final String bindingStatus;
  final int bootstrapTokenGrant;
  final int bootstrapTokenRemaining;

  factory RegisteredDevice.fromJson(Map<String, dynamic> json) {
    return RegisteredDevice(
      id: json['id'] as String? ?? 'device_unknown',
      deviceUuid: json['deviceUuid'] as String? ?? 'device_demo_android_tv',
      deviceName: json['deviceName'] as String? ?? 'OpenClaw TV Device',
      androidVersion: json['androidVersion'] as String? ?? '9',
      isAndroidTv: json['isAndroidTv'] as bool? ?? true,
      status: json['status'] as String? ?? 'active',
      bindingStatus: json['bindingStatus'] as String? ?? 'bound',
      bootstrapTokenGrant: json['bootstrapTokenGrant'] as int? ?? 5000,
      bootstrapTokenRemaining: json['bootstrapTokenRemaining'] as int? ?? 5000,
    );
  }
}
