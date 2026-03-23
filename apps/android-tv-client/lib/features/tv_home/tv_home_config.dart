import 'package:flutter/material.dart';

import '../device_sync/device_registration_request.dart';
import 'models/tv_home_shortcut.dart';

const String tvHomeDeviceUuid = 'device_demo_android_tv';
const int tvHomeCurrentVersionCode = 18;

const DeviceRegistrationRequest tvHomeDeviceRegistration =
    DeviceRegistrationRequest(
      deviceUuid: tvHomeDeviceUuid,
      deviceName: 'Living Room TV',
      androidVersion: '9',
      isAndroidTv: true,
    );

const List<String> tvHomeDefaultFeaturedAppIds = [
  'youtube',
  'netflix',
  'prime_video',
  'disney_plus',
  'plex',
];

const List<TvHomeShortcut> tvHomeShortcutCatalog = [
  TvHomeShortcut(
    appId: 'youtube',
    label: 'YouTube',
    icon: Icons.ondemand_video_rounded,
    enabled: true,
    accentColor: Color(0xFFFF6B6B),
  ),
  TvHomeShortcut(
    appId: 'netflix',
    label: 'Netflix',
    icon: Icons.live_tv_rounded,
    enabled: true,
    accentColor: Color(0xFFE05263),
  ),
  TvHomeShortcut(
    appId: 'prime_video',
    label: 'Prime Video',
    icon: Icons.movie_creation_outlined,
    enabled: true,
    accentColor: Color(0xFF4FA6FF),
  ),
  TvHomeShortcut(
    appId: 'disney_plus',
    label: 'Disney+',
    icon: Icons.auto_awesome_rounded,
    enabled: true,
    accentColor: Color(0xFF6D83FF),
  ),
  TvHomeShortcut(
    appId: 'plex',
    label: 'Plex',
    icon: Icons.video_library_rounded,
    enabled: true,
    accentColor: Color(0xFFFFC857),
  ),
  TvHomeShortcut(
    appId: 'spotify',
    label: 'Spotify',
    icon: Icons.graphic_eq_rounded,
    enabled: true,
    accentColor: Color(0xFF6BE28D),
  ),
  TvHomeShortcut(
    appId: 'vlc',
    label: 'VLC',
    icon: Icons.play_circle_fill_rounded,
    enabled: true,
    accentColor: Color(0xFFFFB347),
  ),
  TvHomeShortcut(
    appId: 'settings',
    label: 'Settings',
    icon: Icons.settings_rounded,
    enabled: true,
    accentColor: Color(0xFF7CC6FE),
  ),
  TvHomeShortcut(
    appId: 'cast',
    label: 'Cast',
    icon: Icons.cast_connected_rounded,
    enabled: true,
    accentColor: Color(0xFF8BE9FD),
  ),
  TvHomeShortcut(
    appId: 'local_files',
    label: 'Local Files',
    icon: Icons.folder_open_rounded,
    enabled: true,
    accentColor: Color(0xFFFFD166),
  ),
  TvHomeShortcut(
    appId: 'kodi',
    label: 'Kodi',
    icon: Icons.view_in_ar_rounded,
    enabled: false,
    accentColor: Color(0xFF69A7FF),
  ),
];

List<TvHomeShortcut> resolveTvHomeFeaturedShortcuts(List<String> appIds) {
  final normalizedIds = appIds.isEmpty ? tvHomeDefaultFeaturedAppIds : appIds;
  final resolved = <TvHomeShortcut>[];

  for (final appId in normalizedIds) {
    for (final shortcut in tvHomeShortcutCatalog) {
      if (shortcut.appId == appId) {
        resolved.add(shortcut);
        break;
      }
    }
  }

  return resolved.isEmpty
      ? tvHomeShortcutCatalog
          .where((shortcut) => tvHomeDefaultFeaturedAppIds.contains(shortcut.appId))
          .toList()
      : resolved;
}

const List<TvHomeOfflinePrimaryAction> tvHomeOfflinePrimaryActions = [
  TvHomeOfflinePrimaryAction(
    appId: 'settings',
    label: 'Connect',
    hint: 'Default focus. Use up/down on Wi-Fi list.',
    icon: Icons.wifi_rounded,
    accentColor: Color(0xFFFF8C69),
  ),
  TvHomeOfflinePrimaryAction(
    appId: 'cast',
    label: 'Cast',
    hint: 'Move right, then say select.',
    icon: Icons.cast_connected_rounded,
    accentColor: Color(0xFF8BE9FD),
  ),
  TvHomeOfflinePrimaryAction(
    appId: 'local_files',
    label: 'Open USB',
    hint: 'Move right again, then say select.',
    icon: Icons.usb_rounded,
    accentColor: Color(0xFFFFD166),
  ),
];
