import 'package:flutter/material.dart';

import '../models/tv_home_shortcut.dart';

class TvHomeShortcutsSurface extends StatelessWidget {
  const TvHomeShortcutsSurface({
    super.key,
    required this.isOffline,
    required this.shortcuts,
    required this.offlineActions,
    required this.focusedShortcutIndex,
    required this.highlightedOfflineActionIndex,
    required this.onShortcutTap,
    required this.onShortcutFocus,
    required this.onOfflineActionTap,
  });

  final bool isOffline;
  final List<TvHomeShortcut> shortcuts;
  final List<TvHomeOfflinePrimaryAction> offlineActions;
  final int focusedShortcutIndex;
  final int highlightedOfflineActionIndex;
  final ValueChanged<TvHomeShortcut> onShortcutTap;
  final ValueChanged<int> onShortcutFocus;
  final ValueChanged<int> onOfflineActionTap;

  @override
  Widget build(BuildContext context) {
    if (isOffline) {
      return Align(
        alignment: Alignment.bottomCenter,
        child: SizedBox(
          height: 138,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: List<Widget>.generate(offlineActions.length, (index) {
              final action = offlineActions[index];
              final isHighlighted = index == highlightedOfflineActionIndex;
              return Padding(
                padding: EdgeInsets.only(left: index == 0 ? 0 : 18),
                child: InkWell(
                  canRequestFocus: true,
                  borderRadius: BorderRadius.circular(32),
                  onTap: () => onOfflineActionTap(index),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    width: 96,
                    height: 96,
                    decoration: BoxDecoration(
                      color: isHighlighted
                          ? action.accentColor.withValues(alpha: 0.18)
                          : Colors.white.withValues(alpha: 0.05),
                      borderRadius: BorderRadius.circular(30),
                      border: Border.all(
                        color: isHighlighted
                            ? action.accentColor
                            : Colors.white.withValues(alpha: 0.12),
                        width: isHighlighted ? 2 : 1,
                      ),
                    ),
                    child: Icon(
                      action.icon,
                      color: action.accentColor,
                      size: 42,
                    ),
                  ),
                ),
              );
            }),
          ),
        ),
      );
    }

    return Align(
      alignment: Alignment.bottomCenter,
      child: SizedBox(
        height: 138,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: shortcuts.asMap().entries.map((entry) {
            final index = entry.key;
            final shortcut = entry.value;
            return Padding(
              padding: EdgeInsets.only(left: index == 0 ? 0 : 18),
              child: FocusableActionDetector(
                onShowFocusHighlight: (focused) {
                  if (focused) {
                    onShortcutFocus(index);
                  }
                },
                child: InkWell(
                  canRequestFocus: true,
                  borderRadius: BorderRadius.circular(32),
                  onTap: () => onShortcutTap(shortcut),
                  child: _IconOnlyShortcutTile(
                    shortcut: shortcut,
                    isFocused: focusedShortcutIndex == index,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

class _IconOnlyShortcutTile extends StatelessWidget {
  const _IconOnlyShortcutTile({
    required this.shortcut,
    required this.isFocused,
  });

  final TvHomeShortcut shortcut;
  final bool isFocused;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      width: 96,
      height: 96,
      decoration: BoxDecoration(
        color: isFocused
            ? shortcut.accentColor.withValues(alpha: 0.18)
            : Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(30),
        border: Border.all(
          color: isFocused
              ? shortcut.accentColor
              : Colors.white.withValues(alpha: 0.12),
          width: isFocused ? 2 : 1,
        ),
      ),
      child: Icon(
        shortcut.icon,
        color: shortcut.enabled ? shortcut.accentColor : Colors.white38,
        size: 42,
      ),
    );
  }
}
