class RouterStatus {
  const RouterStatus({
    required this.provider,
    required this.configured,
    required this.baseUrl,
    required this.model,
    this.transportMode,
  });

  final String provider;
  final bool configured;
  final String baseUrl;
  final String model;
  final String? transportMode;

  factory RouterStatus.fromJson(Map<String, dynamic> json) {
    return RouterStatus(
      provider: json['provider'] as String? ?? 'mock_llm_router',
      configured: json['configured'] as bool? ?? false,
      baseUrl: json['baseUrl'] as String? ?? '',
      model: json['model'] as String? ?? 'local_mock',
      transportMode: json['transportMode'] as String?,
    );
  }
}
