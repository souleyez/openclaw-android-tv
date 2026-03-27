export function canTogglePlayback(params: {
  isAiSpeaking: boolean;
  isRecording: boolean;
  isAiBusy: boolean;
  isUploadingBroadcast: boolean;
  isPreparingCapture: boolean;
}) {
  return !(
    params.isAiSpeaking ||
    params.isRecording ||
    params.isAiBusy ||
    params.isUploadingBroadcast ||
    params.isPreparingCapture
  );
}

export function canStartRecording(params: {
  recordingStarted: boolean;
  isRecording: boolean;
  isAiSpeaking: boolean;
  isAiBusy: boolean;
  isUploadingBroadcast: boolean;
  isPreparingCapture: boolean;
}) {
  return !(
    params.recordingStarted ||
    params.isRecording ||
    params.isAiSpeaking ||
    params.isAiBusy ||
    params.isUploadingBroadcast ||
    params.isPreparingCapture
  );
}

export function shouldRecoverRadio(params: {
  isPlaying: boolean;
  isAiSpeaking: boolean;
  isPreparingCapture: boolean;
  recordingStarted: boolean;
  hasPlayerError: boolean;
  didJustFinish: boolean;
  recoveryLocked: boolean;
}) {
  if (
    !params.isPlaying ||
    params.isAiSpeaking ||
    params.isPreparingCapture ||
    params.recordingStarted
  ) {
    return false;
  }

  if (!params.hasPlayerError && !params.didJustFinish) {
    return false;
  }

  return !params.recoveryLocked;
}
