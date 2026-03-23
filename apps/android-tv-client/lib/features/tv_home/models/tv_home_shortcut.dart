import 'package:flutter/material.dart';

class TvHomeShortcut {
  const TvHomeShortcut({
    required this.appId,
    required this.label,
    required this.icon,
    required this.enabled,
    required this.accentColor,
    this.backgroundAssetUrl,
  });

  final String appId;
  final String label;
  final IconData icon;
  final bool enabled;
  final Color accentColor;
  final String? backgroundAssetUrl;
}

class TvHomeOfflinePrimaryAction {
  const TvHomeOfflinePrimaryAction({
    required this.appId,
    required this.label,
    required this.hint,
    required this.icon,
    required this.accentColor,
  });

  final String appId;
  final String label;
  final String hint;
  final IconData icon;
  final Color accentColor;
}
