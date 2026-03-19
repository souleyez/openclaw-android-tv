class ProviderLease {
  const ProviderLease({
    required this.provider,
    this.leaseId,
    this.baseUrl,
    this.model,
    this.apiKey,
    this.expiresAt,
    this.leaseMode,
    this.maxConcurrency,
    this.denied = false,
    this.denyReason,
  });

  factory ProviderLease.fromJson(Map<String, dynamic> json) {
    return ProviderLease(
      provider: json['provider'] as String? ?? 'unknown',
      leaseId: json['leaseId'] as String?,
      baseUrl: json['baseUrl'] as String?,
      model: json['model'] as String?,
      apiKey: json['apiKey'] as String?,
      expiresAt: json['expiresAt'] as String?,
      leaseMode: json['leaseMode'] as String?,
      maxConcurrency: (json['maxConcurrency'] as num?)?.toInt(),
      denied: json['denied'] as bool? ?? false,
      denyReason: json['denyReason'] as String?,
    );
  }

  bool get isUsable =>
      !denied &&
      leaseId != null &&
      baseUrl != null &&
      model != null &&
      apiKey != null;

  final String provider;
  final String? leaseId;
  final String? baseUrl;
  final String? model;
  final String? apiKey;
  final String? expiresAt;
  final String? leaseMode;
  final int? maxConcurrency;
  final bool denied;
  final String? denyReason;
}
