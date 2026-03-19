class BillingOrderPreview {
  const BillingOrderPreview({
    required this.id,
    required this.stablecoinSymbol,
    required this.chain,
    required this.amountUsd,
    required this.status,
    required this.confirmations,
    required this.walletAddress,
    this.txHash,
    this.reviewNote,
    this.expiresAt,
  });

  final String id;
  final String stablecoinSymbol;
  final String chain;
  final double amountUsd;
  final String status;
  final int confirmations;
  final String walletAddress;
  final String? txHash;
  final String? reviewNote;
  final String? expiresAt;

  factory BillingOrderPreview.fromJson(Map<String, dynamic> json) {
    return BillingOrderPreview(
      id: json['id'] as String? ?? 'order_demo',
      stablecoinSymbol: json['stablecoinSymbol'] as String? ?? 'USDC',
      chain: json['chain'] as String? ?? 'Polygon',
      amountUsd: (json['amountUsd'] as num?)?.toDouble() ?? 0,
      status: json['status'] as String? ?? 'pending',
      confirmations: (json['confirmations'] as num?)?.round() ?? 0,
      walletAddress: json['walletAddress'] as String? ?? 'demo_wallet',
      txHash: json['txHash'] as String?,
      reviewNote: json['reviewNote'] as String?,
      expiresAt: json['expiresAt'] as String?,
    );
  }
}
