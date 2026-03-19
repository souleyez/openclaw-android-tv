class SubmitTxHashRequest {
  const SubmitTxHashRequest({
    required this.orderId,
    required this.txHash,
  });

  final String orderId;
  final String txHash;
}
