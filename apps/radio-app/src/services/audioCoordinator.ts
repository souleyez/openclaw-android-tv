type PauseablePlayer = {
  pause: () => void;
};

type PlayablePlayer = {
  play: () => void;
};

type ReplaceablePlayer = {
  replace?: (source: string | null) => void;
};

type VolumePlayer = {
  volume: number;
};

function isPromiseLike(value: unknown): value is Promise<unknown> {
  return !!value && typeof (value as Promise<unknown>).then === 'function';
}

function isReleasedSharedObjectError(error: unknown) {
  if (!(error instanceof Error)) {
    return false;
  }

  const message = error.message.toLowerCase();
  return message.includes('shared object') && message.includes('released');
}

function reportAudioError(error: unknown) {
  if (isReleasedSharedObjectError(error)) {
    return;
  }

  console.warn('Audio action failed.', error);
}

function runAudioAction(action: () => unknown) {
  try {
    const result = action();
    if (isPromiseLike(result)) {
      void result.catch(reportAudioError);
    }
  } catch (error) {
    reportAudioError(error);
  }
}

function setPlayerVolume(player: VolumePlayer, volume: number) {
  runAudioAction(() => {
    player.volume = volume;
  });
}

export function duckPlayer(player: VolumePlayer, volume = 0.2) {
  setPlayerVolume(player, volume);
}

export function muteAndPausePlayer(player: PauseablePlayer & VolumePlayer) {
  setPlayerVolume(player, 0);
  runAudioAction(() => player.pause());
}

export function pausePlayer(player: PauseablePlayer) {
  runAudioAction(() => player.pause());
}

export function restorePlayer(player: VolumePlayer, volume = 0.92) {
  setPlayerVolume(player, volume);
}

export function resumePlayer(player: PlayablePlayer & VolumePlayer, volume = 0.92) {
  setPlayerVolume(player, volume);
  runAudioAction(() => player.play());
}

export function replacePlayerSource(player: ReplaceablePlayer, source: string | null) {
  if (!player.replace) {
    return;
  }

  runAudioAction(() => player.replace?.(source));
}
