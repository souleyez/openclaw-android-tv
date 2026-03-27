type PlayerLike = {
  pause: () => void;
  play: () => void;
  volume: number;
};

export function duckPlayer(player: PlayerLike, volume = 0.2) {
  player.volume = volume;
}

export function muteAndPausePlayer(player: PlayerLike) {
  player.volume = 0;
  player.pause();
}

export function pausePlayer(player: PlayerLike) {
  player.pause();
}

export function restorePlayer(player: PlayerLike, volume = 0.92) {
  player.volume = volume;
}

export function resumePlayer(player: PlayerLike, volume = 0.92) {
  player.volume = volume;
  player.play();
}
