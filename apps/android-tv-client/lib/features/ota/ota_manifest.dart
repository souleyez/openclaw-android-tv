class OtaManifest {
  const OtaManifest({
    required this.available,
    this.releaseId,
    this.versionName,
    this.versionCode,
    this.notificationMode,
    this.downloadPolicy,
    this.installPolicy,
    this.reportPolicy,
    this.reportDelayMinutes,
    this.latestStatus,
  });

  factory OtaManifest.fromJson(Map<String, dynamic> json) {
    final release = json['release'] as Map<String, dynamic>?;
    final policy = json['policy'] as Map<String, dynamic>?;
    final latestReport = json['latestReport'] as Map<String, dynamic>?;

    return OtaManifest(
      available: json['available'] == true,
      releaseId: release?['id'] as String?,
      versionName: release?['versionName'] as String?,
      versionCode: (release?['versionCode'] as num?)?.toInt(),
      notificationMode: policy?['notificationMode'] as String?,
      downloadPolicy: policy?['downloadPolicy'] as String?,
      installPolicy: policy?['installPolicy'] as String?,
      reportPolicy: policy?['reportPolicy'] as String?,
      reportDelayMinutes: (policy?['reportDelayMinutes'] as num?)?.toInt(),
      latestStatus: latestReport?['status'] as String?,
    );
  }

  static const OtaManifest unavailable = OtaManifest(available: false);

  final bool available;
  final String? releaseId;
  final String? versionName;
  final int? versionCode;
  final String? notificationMode;
  final String? downloadPolicy;
  final String? installPolicy;
  final String? reportPolicy;
  final int? reportDelayMinutes;
  final String? latestStatus;
}
