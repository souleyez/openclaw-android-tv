import 'package:flutter/material.dart';

class TvHomeFooterStatusBar extends StatelessWidget {
  const TvHomeFooterStatusBar({
    super.key,
    required this.networkLabel,
    required this.modeLabel,
    required this.voiceLabel,
    required this.planLabel,
    required this.scopeLabel,
  });

  final String networkLabel;
  final String modeLabel;
  final String voiceLabel;
  final String planLabel;
  final String scopeLabel;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Wrap(
        spacing: 12,
        runSpacing: 10,
        children: [
          _FooterChip(label: 'Network', value: networkLabel),
          _FooterChip(label: 'Mode', value: modeLabel),
          _FooterChip(label: 'Voice', value: voiceLabel),
          _FooterChip(label: 'Plan', value: planLabel),
          _FooterChip(label: 'Scope', value: scopeLabel),
        ],
      ),
    );
  }
}

class _FooterChip extends StatelessWidget {
  const _FooterChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(999),
      ),
      child: RichText(
        text: TextSpan(
          style: const TextStyle(fontSize: 13, color: Colors.white70),
          children: [
            TextSpan(
              text: '$label ',
              style: const TextStyle(color: Colors.white38),
            ),
            TextSpan(
              text: value,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
