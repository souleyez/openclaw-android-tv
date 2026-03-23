import 'package:flutter/material.dart';

import '../models/tv_home_shortcut.dart';

class TvHomeTopToolbar extends StatelessWidget {
  const TvHomeTopToolbar({
    super.key,
    required this.tools,
    required this.onTap,
  });

  final List<TvHomeShortcut> tools;
  final ValueChanged<TvHomeShortcut> onTap;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: tools.map((tool) {
        return Padding(
          padding: const EdgeInsets.only(left: 10),
          child: InkWell(
            canRequestFocus: true,
            borderRadius: BorderRadius.circular(22),
            onTap: () => onTap(tool),
            child: Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(22),
                border: Border.all(
                  color: tool.accentColor.withValues(alpha: 0.35),
                ),
              ),
              child: Icon(tool.icon, color: tool.accentColor, size: 26),
            ),
          ),
        );
      }).toList(),
    );
  }
}
