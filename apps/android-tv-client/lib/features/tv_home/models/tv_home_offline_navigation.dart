import '../../control/control_action.dart';
import 'tv_home_shortcut.dart';

class TvHomeOfflineWifiGuidance {
  const TvHomeOfflineWifiGuidance({
    required this.executionStatus,
    required this.pendingCommand,
    required this.highlightedIndex,
  });

  final String executionStatus;
  final String pendingCommand;
  final int highlightedIndex;
}

class TvHomeOfflineHomeNavigation {
  const TvHomeOfflineHomeNavigation({
    required this.target,
    required this.action,
    required this.assistantText,
    required this.executionStatus,
    required this.pendingCommand,
    required this.focusedActionIndex,
    this.launchAppId,
  });

  final String target;
  final String action;
  final String assistantText;
  final String executionStatus;
  final String pendingCommand;
  final int focusedActionIndex;
  final String? launchAppId;
}

TvHomeOfflineWifiGuidance? buildOfflineWifiNavigationGuidance({
  required bool isOfflineHomeMode,
  required int offlineHomeActionIndex,
  required List<String> visibleNetworks,
  required int currentWifiHighlightIndex,
  required ControlAction action,
  required int Function({
    required List<String> networks,
    required int currentIndex,
  }) normalizeWifiHighlightIndex,
}) {
  if (!isOfflineHomeMode ||
      offlineHomeActionIndex != 0 ||
      visibleNetworks.isEmpty) {
    return null;
  }

  var index = normalizeWifiHighlightIndex(
    networks: visibleNetworks,
    currentIndex: currentWifiHighlightIndex,
  );

  switch (action) {
    case ControlAction.up:
      index = index > 0 ? index - 1 : 0;
      return TvHomeOfflineWifiGuidance(
        executionStatus: 'Wi-Fi highlight moved',
        pendingCommand: 'Highlighted Wi-Fi: ${visibleNetworks[index]}',
        highlightedIndex: index,
      );
    case ControlAction.down:
      index = index < visibleNetworks.length - 1
          ? index + 1
          : visibleNetworks.length - 1;
      return TvHomeOfflineWifiGuidance(
        executionStatus: 'Wi-Fi highlight moved',
        pendingCommand: 'Highlighted Wi-Fi: ${visibleNetworks[index]}',
        highlightedIndex: index,
      );
    case ControlAction.select:
      return TvHomeOfflineWifiGuidance(
        executionStatus: 'Wi-Fi selected',
        pendingCommand:
            'Selected Wi-Fi: ${visibleNetworks[index]}. Continue with the password on the system keyboard.',
        highlightedIndex: index,
      );
    default:
      return null;
  }
}

TvHomeOfflineHomeNavigation? consumeOfflineHomeNavigation({
  required bool isOfflineHomeMode,
  required ControlAction resolutionAction,
  required int currentOfflineHomeActionIndex,
  required List<TvHomeOfflinePrimaryAction> offlinePrimaryActions,
  required int Function({
    required int currentIndex,
    required int actionCount,
  }) normalizeOfflineHomeActionIndex,
}) {
  if (!isOfflineHomeMode) {
    return null;
  }

  switch (resolutionAction) {
    case ControlAction.left:
      final nextIndex = normalizeOfflineHomeActionIndex(
        currentIndex: currentOfflineHomeActionIndex - 1,
        actionCount: offlinePrimaryActions.length,
      );
      final current = offlinePrimaryActions[nextIndex];
      return TvHomeOfflineHomeNavigation(
        target: current.appId,
        action: 'focus',
        assistantText: '${current.label} focused.',
        executionStatus: 'Offline action focused',
        pendingCommand: 'Focused ${current.label}.',
        focusedActionIndex: nextIndex,
      );
    case ControlAction.right:
      final nextIndex = normalizeOfflineHomeActionIndex(
        currentIndex: currentOfflineHomeActionIndex + 1,
        actionCount: offlinePrimaryActions.length,
      );
      final current = offlinePrimaryActions[nextIndex];
      return TvHomeOfflineHomeNavigation(
        target: current.appId,
        action: 'focus',
        assistantText: '${current.label} focused.',
        executionStatus: 'Offline action focused',
        pendingCommand: 'Focused ${current.label}.',
        focusedActionIndex: nextIndex,
      );
    case ControlAction.select:
      final current = offlinePrimaryActions[currentOfflineHomeActionIndex];
      if (current.appId == 'settings') {
        return TvHomeOfflineHomeNavigation(
          target: current.appId,
          action: 'focus',
          assistantText:
              'Connect is focused. Use up and down to choose a Wi-Fi network.',
          executionStatus: 'Wi-Fi list focused',
          pendingCommand: 'Focused Connect. Say up, down, or select.',
          focusedActionIndex: currentOfflineHomeActionIndex,
        );
      }
      return TvHomeOfflineHomeNavigation(
        target: current.appId,
        action: 'open_app',
        assistantText: 'Opening ${current.label} from the offline home surface.',
        executionStatus: 'Offline action selected',
        pendingCommand: 'Executing ${current.label}.',
        focusedActionIndex: currentOfflineHomeActionIndex,
        launchAppId: current.appId,
      );
    default:
      return null;
  }
}
