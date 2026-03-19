enum ControlAction {
  openApp,
  search,
  play,
  pause,
  resume,
  next,
  previous,
  fastForward,
  rewind,
  back,
  up,
  down,
  left,
  right,
  select,
  home,
  menu,
  volumeUp,
  volumeDown,
  mute,
}

class ControlIntent {
  const ControlIntent({
    required this.appId,
    required this.action,
    this.queryText,
  });

  final String appId;
  final ControlAction action;
  final String? queryText;
}
