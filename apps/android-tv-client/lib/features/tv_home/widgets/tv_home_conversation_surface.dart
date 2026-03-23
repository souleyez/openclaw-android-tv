import 'package:flutter/material.dart';

class TvHomeConversationSurface extends StatelessWidget {
  const TvHomeConversationSurface({
    super.key,
    required this.subtitle,
    required this.pendingCommand,
    required this.showOverlay,
  });

  final String subtitle;
  final String pendingCommand;
  final bool showOverlay;

  @override
  Widget build(BuildContext context) {
    return Container(
      alignment: Alignment.centerLeft,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AnimatedOpacity(
            duration: const Duration(milliseconds: 220),
            opacity: showOverlay ? 1.0 : 0.0,
            child: Text(
              subtitle,
              maxLines: 4,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 46,
                height: 1.16,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const SizedBox(height: 14),
          AnimatedOpacity(
            duration: const Duration(milliseconds: 220),
            opacity: showOverlay ? 0.88 : 0.0,
            child: Text(
              pendingCommand,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 18, color: Colors.white70),
            ),
          ),
        ],
      ),
    );
  }
}
