class ControlLogEntry {
  const ControlLogEntry({
    required this.target,
    required this.action,
    required this.status,
    required this.strategy,
  });

  final String target;
  final String action;
  final String status;
  final String strategy;
}
