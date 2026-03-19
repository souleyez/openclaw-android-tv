import 'package:flutter/material.dart';

class SettingsQuickPanel extends StatelessWidget {
  const SettingsQuickPanel({
    super.key,
    required this.language,
    required this.avatar,
    required this.otaStatus,
    required this.backendStatus,
    required this.voiceStatus,
    required this.paymentPriority,
    required this.orderStatus,
    required this.accessScope,
    required this.onAccessScopeChanged,
    required this.onManageAvatar,
    required this.backgroundStandbyEnabled,
    required this.onBackgroundStandbyChanged,
    required this.onSimulateWake,
    required this.showSimulateWake,
  });

  final String language;
  final String avatar;
  final String otaStatus;
  final String backendStatus;
  final String voiceStatus;
  final String paymentPriority;
  final String orderStatus;
  final String accessScope;
  final ValueChanged<String> onAccessScopeChanged;
  final VoidCallback onManageAvatar;
  final bool backgroundStandbyEnabled;
  final ValueChanged<bool> onBackgroundStandbyChanged;
  final VoidCallback onSimulateWake;
  final bool showSimulateWake;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF322818),
        borderRadius: BorderRadius.circular(20),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Quick Settings', style: TextStyle(fontSize: 18)),
            const SizedBox(height: 8),
            Text('Language: $language', style: const TextStyle(fontSize: 24)),
            const SizedBox(height: 8),
            Text('Avatar: $avatar'),
            Text('OTA: $otaStatus'),
            Text('Backend: $backendStatus'),
            Text('Voice: $voiceStatus'),
            Text('Payment: $paymentPriority first'),
            Text('Latest order: $orderStatus'),
            const SizedBox(height: 10),
            FilledButton.tonal(
              onPressed: onManageAvatar,
              child: const Text('Change Avatar'),
            ),
            const SizedBox(height: 10),
            SwitchListTile(
              value: backgroundStandbyEnabled,
              contentPadding: EdgeInsets.zero,
              title: const Text('Background Standby'),
              subtitle: Text(
                backgroundStandbyEnabled
                    ? 'Wake feedback stays armed for slow command routing.'
                    : 'Standby feedback is off.',
                style: const TextStyle(fontSize: 12, color: Colors.white70),
              ),
              onChanged: onBackgroundStandbyChanged,
            ),
            if (showSimulateWake) ...[
              const SizedBox(height: 8),
              FilledButton.tonal(
                onPressed: onSimulateWake,
                child: const Text('Simulate Hotword'),
              ),
            ],
            const SizedBox(height: 10),
            const Text('Access Scope'),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('household'),
                  selected: accessScope == 'household',
                  onSelected: (_) => onAccessScopeChanged('household'),
                ),
                ChoiceChip(
                  label: const Text('api_key'),
                  selected: accessScope == 'api_key',
                  onSelected: (_) => onAccessScopeChanged('api_key'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
