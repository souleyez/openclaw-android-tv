import 'control_action.dart';
import 'intent_resolution.dart';
import '../network/network_snapshot.dart';

class OfflineLocalRouter {
  const OfflineLocalRouter();

  IntentResolution resolve(
    String text, {
    NetworkSnapshot snapshot = NetworkSnapshot.fallback,
  }) {
    final normalized = text.trim();
    final lowered = normalized.toLowerCase();
    final matchedNetwork = _matchVisibleNetwork(lowered, snapshot.visibleNetworks);
    final passwordValue = _extractPassword(normalized, lowered);

    if (matchedNetwork != null) {
      return _control(
        appId: 'settings',
        action: ControlAction.openApp,
        assistantText:
            'Offline mode: I found Wi-Fi "$matchedNetwork". Opening network settings now. Use up and down to highlight it, then say select.',
        route: 'offline_wifi_network_match',
        confidence: 0.93,
      );
    }

    if (passwordValue != null) {
      return _message(
        requestId: 'offline_wifi_password',
        assistantText:
            'Offline mode: I heard the Wi-Fi password as "$passwordValue". When the TV keyboard is focused, keep using voice or the directional keys to enter and confirm it.',
        route: 'offline_wifi_password_hint',
        confidence: 0.88,
      );
    }

    if (_matchesAny(lowered, const [
      'wifi',
      'wi-fi',
      'network',
      'internet',
      'connect wifi',
      'connect network',
    ])) {
      return _control(
        appId: 'settings',
        action: ControlAction.openApp,
        assistantText:
            'Offline mode: opening network settings so you can connect Wi-Fi locally.',
        route: 'offline_wifi_settings',
        confidence: 0.95,
      );
    }

    if (_matchesAny(lowered, const [
      'cast',
      'screen cast',
      'cast screen',
      'mirror screen',
      'screen mirror',
    ])) {
      return _control(
        appId: 'cast',
        action: ControlAction.openApp,
        assistantText: 'Offline mode: opening the cast entry on this TV.',
        route: 'offline_cast_entry',
      );
    }

    if (_matchesAny(lowered, const [
      'local file',
      'local files',
      'local video',
      'downloads',
      'usb',
      'play local',
    ])) {
      return _control(
        appId: 'local_files',
        action: ControlAction.openApp,
        assistantText: 'Offline mode: opening local files on this device.',
        route: 'offline_local_files',
      );
    }

    if (_matchesAny(lowered, const ['youtube', 'open youtube'])) {
      return _control(
        appId: 'youtube',
        action: ControlAction.openApp,
        assistantText: 'Offline mode: opening YouTube locally.',
        route: 'offline_launch_youtube',
      );
    }

    if (_matchesAny(lowered, const ['spotify', 'open spotify'])) {
      return _control(
        appId: 'spotify',
        action: ControlAction.openApp,
        assistantText: 'Offline mode: opening Spotify locally.',
        route: 'offline_launch_spotify',
      );
    }

    if (_matchesAny(lowered, const ['vlc', 'open vlc'])) {
      return _control(
        appId: 'vlc',
        action: ControlAction.openApp,
        assistantText: 'Offline mode: opening VLC locally.',
        route: 'offline_launch_vlc',
      );
    }

    if (_matchesAny(lowered, const ['settings', 'open settings'])) {
      return _control(
        appId: 'settings',
        action: ControlAction.openApp,
        assistantText: 'Offline mode: opening system settings.',
        route: 'offline_launch_settings',
      );
    }

    if (_matchesAny(lowered, const [
      'volume up',
      'turn up',
      'louder',
      'increase volume',
    ])) {
      return _control(
        appId: 'system',
        action: ControlAction.volumeUp,
        assistantText: 'Offline mode: turning the volume up.',
        route: 'offline_volume_up',
      );
    }

    if (_matchesAny(lowered, const [
      'volume down',
      'turn down',
      'quieter',
      'decrease volume',
    ])) {
      return _control(
        appId: 'system',
        action: ControlAction.volumeDown,
        assistantText: 'Offline mode: turning the volume down.',
        route: 'offline_volume_down',
      );
    }

    if (_matchesAny(lowered, const ['mute', 'silent'])) {
      return _control(
        appId: 'system',
        action: ControlAction.mute,
        assistantText: 'Offline mode: muting the current audio.',
        route: 'offline_mute',
      );
    }

    if (_matchesAny(lowered, const ['play', 'resume', 'continue'])) {
      return _control(
        appId: 'media',
        action: ControlAction.play,
        assistantText: 'Offline mode: resuming playback locally.',
        route: 'offline_media_play',
      );
    }

    if (_matchesAny(lowered, const ['pause', 'stop playback'])) {
      return _control(
        appId: 'media',
        action: ControlAction.pause,
        assistantText: 'Offline mode: pausing playback locally.',
        route: 'offline_media_pause',
      );
    }

    if (_matchesAny(lowered, const ['next', 'next track', 'next song'])) {
      return _control(
        appId: 'media',
        action: ControlAction.next,
        assistantText: 'Offline mode: skipping to the next item.',
        route: 'offline_media_next',
      );
    }

    if (_matchesAny(lowered, const ['previous', 'last track', 'previous song'])) {
      return _control(
        appId: 'media',
        action: ControlAction.previous,
        assistantText: 'Offline mode: going back to the previous item.',
        route: 'offline_media_previous',
      );
    }

    if (_matchesAny(lowered, const ['back', 'go back'])) {
      return _control(
        appId: 'system',
        action: ControlAction.back,
        assistantText: 'Offline mode: going back.',
        route: 'offline_back',
      );
    }

    if (_matchesAny(lowered, const ['home', 'go home'])) {
      return _control(
        appId: 'system',
        action: ControlAction.home,
        assistantText: 'Offline mode: returning to the home screen.',
        route: 'offline_home',
      );
    }

    if (_matchesAny(lowered, const ['menu', 'open menu'])) {
      return _control(
        appId: 'system',
        action: ControlAction.menu,
        assistantText: 'Offline mode: opening the menu.',
        route: 'offline_menu',
      );
    }

    if (_matchesAny(lowered, const ['select', 'confirm', 'ok'])) {
      return _control(
        appId: 'system',
        action: ControlAction.select,
        assistantText: 'Offline mode: selecting the current item.',
        route: 'offline_select',
      );
    }

    if (_matchesAny(lowered, const ['up', 'move up'])) {
      return _control(
        appId: 'system',
        action: ControlAction.up,
        assistantText: 'Offline mode: moving up.',
        route: 'offline_nav_up',
      );
    }

    if (_matchesAny(lowered, const ['down', 'move down'])) {
      return _control(
        appId: 'system',
        action: ControlAction.down,
        assistantText: 'Offline mode: moving down.',
        route: 'offline_nav_down',
      );
    }

    if (_matchesAny(lowered, const ['left', 'move left'])) {
      return _control(
        appId: 'system',
        action: ControlAction.left,
        assistantText: 'Offline mode: moving left.',
        route: 'offline_nav_left',
      );
    }

    if (_matchesAny(lowered, const ['right', 'move right'])) {
      return _control(
        appId: 'system',
        action: ControlAction.right,
        assistantText: 'Offline mode: moving right.',
        route: 'offline_nav_right',
      );
    }

    return _message(
      requestId: 'offline_chat_fallback',
      assistantText:
          'I am offline right now, so only local controls are available. Try Wi-Fi, settings, volume, cast, local files, playback, or navigation commands.',
      route: 'offline_local_router',
      confidence: 0.62,
    );
  }

  bool _matchesAny(String lowered, List<String> phrases) {
    return phrases.any(lowered.contains);
  }

  String? _matchVisibleNetwork(String lowered, List<String> visibleNetworks) {
    for (final network in visibleNetworks) {
      if (network.isEmpty) {
        continue;
      }
      final normalizedNetwork = network.toLowerCase();
      if (lowered.contains(normalizedNetwork)) {
        return network;
      }
    }

    return null;
  }

  String? _extractPassword(String normalized, String lowered) {
    const prefixes = <String>[
      'password is ',
      'password ',
      'wifi password is ',
      'wifi password ',
      'passcode ',
      'pin ',
    ];

    for (final prefix in prefixes) {
      if (lowered.startsWith(prefix)) {
        return normalized.substring(prefix.length).trim();
      }
    }

    return null;
  }

  IntentResolution _control({
    required String appId,
    required ControlAction action,
    required String assistantText,
    String route = 'offline_local_router',
    double confidence = 0.9,
  }) {
    return IntentResolution(
      requestId: 'offline_${DateTime.now().millisecondsSinceEpoch}',
      appId: appId,
      action: action,
      queryText: null,
      replyText: assistantText,
      assistantText: assistantText,
      shouldExecuteLocally: true,
      mode: 'control',
      route: route,
      modelProvider: 'offline_local_router',
      confidence: confidence,
      fallbackReason: 'offline_local_only',
      tokenUsage: 0,
      bootstrapTokenRemaining: 0,
    );
  }

  IntentResolution _message({
    required String requestId,
    required String assistantText,
    required String route,
    required double confidence,
  }) {
    return IntentResolution(
      requestId: requestId,
      appId: 'assistant',
      action: ControlAction.openApp,
      queryText: null,
      replyText: assistantText,
      assistantText: assistantText,
      shouldExecuteLocally: false,
      mode: 'chat',
      route: route,
      modelProvider: 'offline_local_router',
      confidence: confidence,
      fallbackReason: 'offline_local_only',
      tokenUsage: 0,
      bootstrapTokenRemaining: 0,
    );
  }
}
