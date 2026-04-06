import {
  duckPlayer,
  muteAndPausePlayer,
  pausePlayer,
  replacePlayerSource,
  restorePlayer,
  resumePlayer,
} from './audioCoordinator';

type PlayerLike = {
  pause: () => void;
  play: () => void;
  volume: number;
};

type ReplaceablePlayerLike = PlayerLike & {
  replace: (source: string | null) => void;
};

export async function prepareForVoiceCapture(params: {
  radioPlayer: PlayerLike;
  aiPlayer: PlayerLike;
  settleMs: number;
  sleep: (ms: number) => Promise<unknown>;
}) {
  muteAndPausePlayer(params.radioPlayer);
  pausePlayer(params.aiPlayer);
  await params.sleep(params.settleMs);
}

export function startAiVoicePlayback(params: {
  radioPlayer: PlayerLike;
  aiPlayer: ReplaceablePlayerLike;
  audioUri: string;
  duckedVolume: number;
}) {
  duckPlayer(params.radioPlayer, params.duckedVolume);
  pausePlayer(params.radioPlayer);
  pausePlayer(params.aiPlayer);
  replacePlayerSource(params.aiPlayer, params.audioUri);
  resumePlayer(params.aiPlayer, 1);
}

export function finishAiVoicePlayback(params: {
  radioPlayer: PlayerLike;
  aiPlayer: PlayerLike;
  idleVolume: number;
  shouldResumeRadio: boolean;
  isRecording: boolean;
}) {
  pausePlayer(params.aiPlayer);
  restorePlayer(params.radioPlayer, params.idleVolume);

  if (params.shouldResumeRadio && !params.isRecording) {
    resumePlayer(params.radioPlayer, params.idleVolume);
  }
}
