class TvHomeRemoteConfig {
  const TvHomeRemoteConfig({
    required this.id,
    required this.countryCode,
    required this.regionCode,
    required this.backgroundImageUrl,
    required this.featuredAppIds,
    required this.version,
    required this.updatedAt,
  });

  final String id;
  final String countryCode;
  final String regionCode;
  final String? backgroundImageUrl;
  final List<String> featuredAppIds;
  final int version;
  final String updatedAt;

  factory TvHomeRemoteConfig.fromJson(Map<String, dynamic> json) {
    final rawFeaturedAppIds = json['featuredAppIds'] as List<dynamic>? ?? const [];

    return TvHomeRemoteConfig(
      id: json['id'] as String? ?? 'tv_home_empty',
      countryCode: json['countryCode'] as String? ?? 'GLOBAL',
      regionCode: json['regionCode'] as String? ?? 'GLOBAL',
      backgroundImageUrl: json['backgroundImageUrl'] as String?,
      featuredAppIds: rawFeaturedAppIds
          .map((item) => item.toString().trim())
          .where((item) => item.isNotEmpty)
          .toList(),
      version: json['version'] as int? ?? 0,
      updatedAt: json['updatedAt'] as String? ?? '',
    );
  }
}
