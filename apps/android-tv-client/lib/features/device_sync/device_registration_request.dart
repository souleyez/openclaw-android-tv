class DeviceRegistrationRequest {
  const DeviceRegistrationRequest({
    required this.deviceUuid,
    required this.deviceName,
    required this.androidVersion,
    required this.isAndroidTv,
  });

  final String deviceUuid;
  final String deviceName;
  final String androidVersion;
  final bool isAndroidTv;

  Map<String, dynamic> toJson() {
    return {
      'deviceUuid': deviceUuid,
      'deviceName': deviceName,
      'androidVersion': androidVersion,
      'isAndroidTv': isAndroidTv,
    };
  }
}
