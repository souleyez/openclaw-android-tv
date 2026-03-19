class AvatarProfile {
  const AvatarProfile({
    required this.id,
    required this.name,
    required this.avatarLabel,
    required this.gender,
    required this.ageGroup,
    required this.primaryColorHex,
    required this.secondaryColorHex,
    required this.accentColorHex,
    required this.active,
    this.assetUrl,
  });

  final String id;
  final String name;
  final String avatarLabel;
  final String gender;
  final String ageGroup;
  final String primaryColorHex;
  final String secondaryColorHex;
  final String accentColorHex;
  final bool active;
  final String? assetUrl;

  factory AvatarProfile.fromJson(Map<String, dynamic> json) {
    return AvatarProfile(
      id: json['id'] as String? ?? 'avatar_default',
      name: json['name'] as String? ?? 'Default Host',
      avatarLabel: json['avatarLabel'] as String? ?? 'Default Host',
      gender: json['gender'] as String? ?? 'neutral',
      ageGroup: json['ageGroup'] as String? ?? 'adult',
      primaryColorHex: json['primaryColorHex'] as String? ?? '#6AE6D8',
      secondaryColorHex: json['secondaryColorHex'] as String? ?? '#12656A',
      accentColorHex: json['accentColorHex'] as String? ?? '#B0FFF4',
      active: json['active'] as bool? ?? false,
      assetUrl: json['assetUrl'] as String?,
    );
  }
}
