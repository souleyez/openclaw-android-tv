class ControlAuthorizationResult {
  const ControlAuthorizationResult({
    required this.allowed,
    required this.bindingScope,
    required this.appId,
    required this.action,
    required this.reason,
  });

  final bool allowed;
  final String bindingScope;
  final String appId;
  final String action;
  final String reason;

  factory ControlAuthorizationResult.fromJson(Map<String, dynamic> json) {
    return ControlAuthorizationResult(
      allowed: json['allowed'] as bool? ?? true,
      bindingScope: json['bindingScope'] as String? ?? 'household',
      appId: json['appId'] as String? ?? 'system',
      action: json['action'] as String? ?? 'open_app',
      reason: json['reason'] as String? ?? 'authorized',
    );
  }
}
