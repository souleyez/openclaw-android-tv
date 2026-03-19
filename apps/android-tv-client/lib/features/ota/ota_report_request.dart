class OtaReportRequest {
  const OtaReportRequest({
    required this.deviceUuid,
    required this.releaseId,
    required this.currentVersionCode,
    required this.targetVersionCode,
    required this.status,
    this.progressPercent,
    this.note,
  });

  Map<String, dynamic> toJson() {
    return {
      'deviceUuid': deviceUuid,
      'releaseId': releaseId,
      'currentVersionCode': currentVersionCode,
      'targetVersionCode': targetVersionCode,
      'status': status,
      if (progressPercent != null) 'progressPercent': progressPercent,
      if (note != null && note!.isNotEmpty) 'note': note,
    };
  }

  final String deviceUuid;
  final String releaseId;
  final int currentVersionCode;
  final int targetVersionCode;
  final String status;
  final int? progressPercent;
  final String? note;
}
