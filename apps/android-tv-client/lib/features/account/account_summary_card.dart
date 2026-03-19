import 'package:flutter/material.dart';

import '../billing/billing_order_preview.dart';

class AccountSummaryCard extends StatelessWidget {
  const AccountSummaryCard({
    super.key,
    required this.displayName,
    required this.planName,
    required this.tokenBalance,
    required this.bootstrapTokenPool,
    required this.paymentPriority,
    required this.latestOrderStatus,
    required this.orders,
    required this.isCreatingTopUp,
    required this.onCreateTopUp,
    required this.isSubmittingTxHash,
    required this.onSubmitTxHash,
    required this.isRefreshingConfirmations,
    required this.onRefreshConfirmations,
    required this.isRecoveringRights,
    required this.onRecoverRights,
  });

  final String displayName;
  final String planName;
  final int tokenBalance;
  final int bootstrapTokenPool;
  final String paymentPriority;
  final String latestOrderStatus;
  final List<BillingOrderPreview> orders;
  final bool isCreatingTopUp;
  final VoidCallback? onCreateTopUp;
  final bool isSubmittingTxHash;
  final VoidCallback? onSubmitTxHash;
  final bool isRefreshingConfirmations;
  final VoidCallback? onRefreshConfirmations;
  final bool isRecoveringRights;
  final VoidCallback? onRecoverRights;

  @override
  Widget build(BuildContext context) {
    final previewOrders = orders.take(2).toList();

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF133136),
        borderRadius: BorderRadius.circular(20),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Membership', style: TextStyle(fontSize: 18)),
            const SizedBox(height: 8),
            Text(displayName, style: const TextStyle(fontSize: 16, color: Colors.white70)),
            const SizedBox(height: 8),
            Text(planName, style: const TextStyle(fontSize: 28)),
            const SizedBox(height: 12),
            Text('Token balance: $tokenBalance'),
            const SizedBox(height: 8),
            Text('Free device tokens: $bootstrapTokenPool'),
            const SizedBox(height: 8),
            Text('Payment priority: $paymentPriority'),
            Text('Latest order: $latestOrderStatus'),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0x22FFD166),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: const Color(0x55FFD166)),
              ),
              child: const Text(
                'Closed beta billing mode: top-up and recovery flows are for testing and operator verification only.',
                style: TextStyle(fontSize: 13, color: Colors.white70),
              ),
            ),
            const SizedBox(height: 12),
            FilledButton.tonal(
              onPressed: isCreatingTopUp ? null : onCreateTopUp,
              child: Text(isCreatingTopUp ? 'Creating order...' : 'Create Top-Up Order'),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: isSubmittingTxHash ? null : onSubmitTxHash,
              child: Text(isSubmittingTxHash ? 'Submitting tx...' : 'Report Tx Hash'),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: isRefreshingConfirmations ? null : onRefreshConfirmations,
              child: Text(
                isRefreshingConfirmations
                    ? 'Refreshing confirmations...'
                    : 'Refresh Confirmations',
              ),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: isRecoveringRights ? null : onRecoverRights,
              child: Text(
                isRecoveringRights ? 'Recovering rights...' : 'Recover Rights',
              ),
            ),
            const SizedBox(height: 12),
            Text(
              previewOrders.isEmpty
                  ? 'No top-up orders yet'
                  : previewOrders
                      .map(
                        (order) =>
                            '${order.stablecoinSymbol} ${order.amountUsd.toStringAsFixed(2)} on ${order.chain} | ${order.status} | ${order.confirmations} conf'
                            '${order.txHash != null ? '\nTx: ${order.txHash}' : '\nDeposit: ${order.walletAddress}'}',
                      )
                      .join('\n'),
              style: const TextStyle(fontSize: 14, color: Colors.white70),
            ),
          ],
        ),
      ),
    );
  }
}
