import 'package:flutter/material.dart';

import '../account/entitlement_recovery_result.dart';
import '../avatar/avatar_profile.dart';
import '../billing/billing_order_preview.dart';
import '../billing/create_billing_order_request.dart';

class TvHomeRecoveryInput {
  const TvHomeRecoveryInput({
    required this.txHash,
    required this.chain,
    required this.amountUsd,
  });

  final String txHash;
  final String? chain;
  final double? amountUsd;
}

Future<TvHomeRecoveryInput?> showRecoverRightsDialog(BuildContext context) async {
  final txHashController = TextEditingController();
  final amountController = TextEditingController();
  String chain = 'Any';

  return showDialog<TvHomeRecoveryInput>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('Recover Rights'),
        content: SizedBox(
          width: 420,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Use the latest minimum verification payment from the old payment account to restore rights onto this TV.',
              ),
              const SizedBox(height: 12),
              TextField(
                controller: txHashController,
                decoration: const InputDecoration(
                  labelText: 'Payment Tx Hash',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: chain,
                decoration: const InputDecoration(
                  labelText: 'Chain',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'Any', child: Text('Any')),
                  DropdownMenuItem(value: 'Polygon', child: Text('Polygon')),
                  DropdownMenuItem(value: 'Base', child: Text('Base')),
                  DropdownMenuItem(value: 'Solana', child: Text('Solana')),
                ],
                onChanged: (value) {
                  chain = value ?? 'Any';
                },
              ),
              const SizedBox(height: 12),
              TextField(
                controller: amountController,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(
                  labelText: 'Amount USD (optional)',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final txHash = txHashController.text.trim();
              if (txHash.isEmpty) {
                return;
              }
              Navigator.of(context).pop(
                TvHomeRecoveryInput(
                  txHash: txHash,
                  chain: chain == 'Any' ? null : chain,
                  amountUsd: double.tryParse(amountController.text.trim()),
                ),
              );
            },
            child: const Text('Verify'),
          ),
        ],
      );
    },
  );
}

Future<bool> showRecoveryTransferConfirmDialog({
  required BuildContext context,
  required EntitlementRecoveryResult recovery,
  required String currentDeviceUserId,
  required String formattedPlan,
}) async {
  return (await showDialog<bool>(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text('Confirm Rights Transfer'),
            content: SizedBox(
              width: 420,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Recovered source: ${recovery.displayName}'),
                  Text('Source device user: ${recovery.deviceUserId}'),
                  Text('Current device user: $currentDeviceUserId'),
                  Text('Plan: $formattedPlan'),
                  Text(
                    'Payment proof: ${recovery.amountUsd.toStringAsFixed(2)} USD on ${recovery.chain}',
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'If you continue, the previous device will lose its entitlement and this TV will inherit it.',
                    style: TextStyle(color: Colors.white70),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: const Text('Transfer Here'),
              ),
            ],
          );
        },
      )) ??
      false;
}

Future<CreateBillingOrderRequest?> showCreateTopUpDialog(
  BuildContext context,
) async {
  String stablecoin = 'USDC';
  String chain = 'Polygon';
  double amountUsd = 10;

  return showDialog<CreateBillingOrderRequest>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('Create Top-Up Order'),
        content: StatefulBuilder(
          builder: (context, setDialogState) {
            return Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                DropdownButton<String>(
                  value: stablecoin,
                  isExpanded: true,
                  items: const [
                    DropdownMenuItem(value: 'USDC', child: Text('USDC')),
                    DropdownMenuItem(value: 'USDT', child: Text('USDT')),
                  ],
                  onChanged: (value) {
                    if (value == null) {
                      return;
                    }
                    setDialogState(() {
                      stablecoin = value;
                      if (stablecoin == 'USDT' && chain == 'Base') {
                        chain = 'TRON';
                      }
                      if (stablecoin == 'USDC' && chain == 'TRON') {
                        chain = 'Polygon';
                      }
                    });
                  },
                ),
                const SizedBox(height: 12),
                DropdownButton<String>(
                  value: chain,
                  isExpanded: true,
                  items: (stablecoin == 'USDC'
                          ? const ['Polygon', 'Base']
                          : const ['TRON', 'BSC'])
                      .map(
                        (item) =>
                            DropdownMenuItem(value: item, child: Text(item)),
                      )
                      .toList(),
                  onChanged: (value) {
                    if (value == null) {
                      return;
                    }
                    setDialogState(() {
                      chain = value;
                    });
                  },
                ),
                const SizedBox(height: 16),
                const Text('Amount (USD)'),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: [5.0, 10.0, 20.0, 50.0]
                      .map(
                        (value) => ChoiceChip(
                          label: Text('\$${value.toStringAsFixed(0)}'),
                          selected: amountUsd == value,
                          onSelected: (_) {
                            setDialogState(() {
                              amountUsd = value;
                            });
                          },
                        ),
                      )
                      .toList(),
                ),
              ],
            );
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(context).pop(
                CreateBillingOrderRequest(
                  stablecoinSymbol: stablecoin,
                  chain: chain,
                  amountUsd: amountUsd,
                ),
              );
            },
            child: const Text('Create'),
          ),
        ],
      );
    },
  );
}

Future<String?> showSubmitTxHashDialog({
  required BuildContext context,
  required BillingOrderPreview order,
}) async {
  final controller = TextEditingController(text: order.txHash ?? '');

  return showDialog<String>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('Report Tx Hash'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} on ${order.chain}',
            ),
            const SizedBox(height: 8),
            Text(
              'Deposit address: ${order.walletAddress}',
              style: const TextStyle(fontSize: 12, color: Colors.white70),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              decoration: const InputDecoration(
                labelText: 'Tx Hash',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: const Text('Submit'),
          ),
        ],
      );
    },
  );
}

Future<AvatarProfile?> showAvatarPickerDialog({
  required BuildContext context,
  required List<AvatarProfile> items,
}) async {
  return showDialog<AvatarProfile>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('Select Avatar Profile'),
        content: SizedBox(
          width: 460,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: items
                  .map(
                    (profile) => Container(
                      margin: const EdgeInsets.only(bottom: 10),
                      decoration: BoxDecoration(
                        color: Colors.white10,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: profile.active
                              ? Colors.white54
                              : Colors.transparent,
                        ),
                      ),
                      child: ListTile(
                        leading: _AvatarSwatch(profile: profile),
                        title: Text(profile.avatarLabel),
                        subtitle: Text('${profile.gender} | ${profile.ageGroup}'),
                        trailing: profile.active
                            ? const Text('Active')
                            : const Icon(Icons.chevron_right),
                        onTap: () => Navigator.of(context).pop(profile),
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      );
    },
  );
}

class _AvatarSwatch extends StatelessWidget {
  const _AvatarSwatch({required this.profile});

  final AvatarProfile profile;

  @override
  Widget build(BuildContext context) {
    Color parseColor(String value, Color fallback) {
      final sanitized = value.replaceFirst('#', '');
      if (sanitized.length != 6 && sanitized.length != 8) {
        return fallback;
      }
      final buffer = StringBuffer();
      if (sanitized.length == 6) {
        buffer.write('ff');
      }
      buffer.write(sanitized);
      try {
        return Color(int.parse(buffer.toString(), radix: 16));
      } on FormatException {
        return fallback;
      }
    }

    final primary = parseColor(profile.primaryColorHex, const Color(0xFF6AE6D8));
    final secondary = parseColor(profile.secondaryColorHex, const Color(0xFF12656A));

    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: [primary, secondary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
    );
  }
}
