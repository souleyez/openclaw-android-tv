import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import 'billing_order_preview.dart';

class PaymentStatusCard extends StatelessWidget {
  const PaymentStatusCard({
    super.key,
    required this.order,
    required this.isSubmittingTxHash,
    required this.isRefreshingConfirmations,
    required this.onCopyAddress,
  });

  final BillingOrderPreview? order;
  final bool isSubmittingTxHash;
  final bool isRefreshingConfirmations;
  final VoidCallback? onCopyAddress;

  Color _statusColor(String status) {
    switch (status) {
      case 'confirmed':
        return const Color(0xFF59D6A3);
      case 'failed':
      case 'expired':
        return const Color(0xFFF28B82);
      case 'reviewing':
        return const Color(0xFFFFD166);
      case 'confirming':
        return const Color(0xFF7CC6FE);
      default:
        return Colors.white70;
    }
  }

  String _statusHint(BillingOrderPreview order) {
    switch (order.status) {
      case 'failed':
        return 'Payment failed. Check the chain, token, and tx hash, then create a new order or contact support.';
      case 'expired':
        return 'Deposit window expired. Create a fresh order before sending funds again.';
      case 'reviewing':
        return 'This payment is under manual review. Keep the tx hash ready for support verification.';
      case 'confirming':
        return 'The payment is on-chain and waiting for final confirmations.';
      case 'confirmed':
        return 'Funds confirmed and token balance updated successfully.';
      default:
        return 'Create an order, fund the address, then submit the tx hash.';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF1D233C),
        borderRadius: BorderRadius.circular(20),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Payment Status', style: TextStyle(fontSize: 18)),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0x227CC6FE),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Text(
                'Test mode only. Stablecoin payments in this build are for beta verification, entitlement recovery, and operator review.',
                style: TextStyle(fontSize: 13, color: Colors.white70),
              ),
            ),
            const SizedBox(height: 12),
            if (order == null) ...[
              const Text(
                'No active top-up order yet. Create one to see deposit details here.',
                style: TextStyle(fontSize: 15, color: Colors.white70),
              ),
            ] else ...[
              Text(
                '${order!.stablecoinSymbol} ${order!.amountUsd.toStringAsFixed(2)}',
                style: const TextStyle(fontSize: 26),
              ),
              const SizedBox(height: 8),
              Text('Chain: ${order!.chain}'),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: _statusColor(order!.status).withValues(alpha: 0.16),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  'Status: ${order!.status}',
                  style: TextStyle(
                    color: _statusColor(order!.status),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text('Confirmations: ${order!.confirmations}'),
              const SizedBox(height: 10),
              Container(
                height: 108,
                width: 108,
                decoration: BoxDecoration(
                  color: Colors.white10,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: Colors.white24),
                ),
                alignment: Alignment.center,
                child: QrImageView(
                  data: order!.walletAddress,
                  version: QrVersions.auto,
                  size: 88,
                  backgroundColor: Colors.white,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                'Deposit: ${order!.walletAddress}',
                style: const TextStyle(fontSize: 14, color: Colors.white70),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: onCopyAddress,
                child: const Text('Copy Deposit Address'),
              ),
              const SizedBox(height: 8),
              Text(
                order!.txHash == null
                    ? 'Tx hash not submitted yet'
                    : 'Tx: ${order!.txHash}',
                style: const TextStyle(fontSize: 14, color: Colors.white70),
              ),
              if (order!.reviewNote != null) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: _statusColor(order!.status).withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'Note: ${order!.reviewNote}',
                    style: TextStyle(
                      fontSize: 14,
                      color: _statusColor(order!.status),
                    ),
                  ),
                ),
              ],
              if (order!.expiresAt != null) ...[
                const SizedBox(height: 6),
                Text(
                  'Expires: ${order!.expiresAt}',
                  style: const TextStyle(fontSize: 13, color: Colors.white54),
                ),
              ],
              const SizedBox(height: 10),
              Text(
                isSubmittingTxHash
                    ? 'Submitting tx hash to backend'
                    : isRefreshingConfirmations
                        ? 'Polling chain confirmations'
                        : _statusHint(order!),
                style: const TextStyle(fontSize: 14, color: Colors.white54),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
