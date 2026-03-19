class UpdateConfirmationsRequest {
  const UpdateConfirmationsRequest({
    required this.orderId,
    required this.confirmations,
  });

  final String orderId;
  final int confirmations;
}
