class CreateBillingOrderRequest {
  const CreateBillingOrderRequest({
    required this.stablecoinSymbol,
    required this.chain,
    required this.amountUsd,
  });

  final String stablecoinSymbol;
  final String chain;
  final double amountUsd;

  Map<String, dynamic> toJson() {
    return {
      'stablecoinSymbol': stablecoinSymbol,
      'chain': chain,
      'amountUsd': amountUsd,
    };
  }
}
