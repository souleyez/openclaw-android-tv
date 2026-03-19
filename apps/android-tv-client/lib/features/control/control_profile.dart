import 'control_action.dart';

class ControlProfile {
  const ControlProfile({
    required this.appId,
    required this.displayName,
    required this.packageNames,
    required this.supportedActions,
    required this.primaryStrategy,
    required this.fallbackStrategy,
  });

  final String appId;
  final String displayName;
  final List<String> packageNames;
  final Set<ControlAction> supportedActions;
  final String primaryStrategy;
  final String fallbackStrategy;

  bool supports(ControlAction action) => supportedActions.contains(action);
}
