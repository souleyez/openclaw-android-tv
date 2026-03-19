import 'package:flutter/material.dart';

import 'device_token_usage_event.dart';
import 'device_token_usage_summary.dart';
import 'registered_device.dart';

class DeviceStatusCard extends StatelessWidget {
  const DeviceStatusCard({
    super.key,
    required this.activeDevices,
    required this.deviceLimit,
    required this.devices,
    required this.sharedBindings,
    required this.recentTokenUsage,
    required this.tokenUsageSummary,
  });

  final int activeDevices;
  final int deviceLimit;
  final List<RegisteredDevice> devices;
  final int sharedBindings;
  final List<DeviceTokenUsageEvent> recentTokenUsage;
  final DeviceTokenUsageSummary tokenUsageSummary;

  @override
  Widget build(BuildContext context) {
    final previewDevices = devices.take(2).toList();

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF182A43),
        borderRadius: BorderRadius.circular(20),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Devices', style: TextStyle(fontSize: 18)),
            const SizedBox(height: 8),
            Text(
              '$activeDevices of $deviceLimit linked',
              style: const TextStyle(fontSize: 28),
            ),
            const SizedBox(height: 12),
            Text(
              previewDevices.isEmpty
                  ? 'No device registrations loaded yet'
                  : previewDevices
                      .map(
                        (device) =>
                            '${device.deviceName} | Android ${device.androidVersion} | Free ${device.bootstrapTokenRemaining}/${device.bootstrapTokenGrant}',
                      )
                      .join('\n'),
              style: const TextStyle(fontSize: 15, color: Colors.white70),
            ),
            const SizedBox(height: 12),
            ...previewDevices.map(
              (device) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${device.deviceName} token usage',
                      style: const TextStyle(fontSize: 13, color: Colors.white70),
                    ),
                    const SizedBox(height: 6),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(999),
                      child: LinearProgressIndicator(
                        minHeight: 8,
                        value: device.bootstrapTokenGrant == 0
                            ? 0
                            : device.bootstrapTokenRemaining / device.bootstrapTokenGrant,
                        backgroundColor: Colors.white12,
                        valueColor: const AlwaysStoppedAnimation<Color>(
                          Color(0xFF59D6A3),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Shared bindings: $sharedBindings',
              style: const TextStyle(fontSize: 15, color: Colors.white70),
            ),
            const SizedBox(height: 6),
            Text(
              recentTokenUsage.isEmpty
                  ? 'Recent free-token usage: none'
                  : 'Recent free-token usage: '
                      '${recentTokenUsage.take(2).map((event) => '${event.deviceUuid} -${event.amountConsumed}').join(' | ')}',
              style: const TextStyle(fontSize: 13, color: Colors.white54),
            ),
            const SizedBox(height: 6),
            Text(
              'Today used: ${tokenUsageSummary.todayConsumed} | Total used: ${tokenUsageSummary.totalConsumed}',
              style: const TextStyle(fontSize: 13, color: Colors.white54),
            ),
            const SizedBox(height: 6),
            const Text(
              'API sharing and device concurrency are managed from the account layer',
            ),
          ],
        ),
      ),
    );
  }
}
