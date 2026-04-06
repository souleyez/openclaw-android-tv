import { useEffect } from 'react';

import { shouldRecoverRadio } from '../services/interactionGuards';
import { inferAmbientMode, type ListenerProfile } from '../services/radioIntelligence';
import {
  duckPlayer,
  pausePlayer,
  replacePlayerSource,
  restorePlayer,
  resumePlayer,
} from '../services/audioCoordinator';

type PlayerLike = {
  pause: () => void;
  play: () => void;
  replace: (source: string | null) => void;
  volume: number;
};

export function useRadioAudioEffects(params: {
  currentStreamUrl: string;
  radioPlayer: PlayerLike;
  aiPlayer: Pick<PlayerLike, 'pause'>;
  isPlaying: boolean;
  isAiSpeaking: boolean;
  isAiBusy: boolean;
  isPreparingCapture: boolean;
  recordingStarted: boolean;
  radioVolumeIdle: number;
  radioVolumeDucked: number;
  currentStationId: string;
  recordListeningTick: (stationId: string, durationMs: number) => void;
  finishAiPlayback: () => void;
  aiDidJustFinish: boolean;
  listenerProfile: ListenerProfile;
  chooseNextStation: (mode?: 'music' | 'news' | 'any') => unknown;
  radioDidJustFinish: boolean;
  radioHasError: boolean;
  radioRecoveryLocked: boolean;
  setRadioRecoveryLocked: (value: boolean) => void;
}) {
  useEffect(() => {
    replacePlayerSource(params.radioPlayer, params.currentStreamUrl);
  }, [params.currentStreamUrl, params.radioPlayer]);

  useEffect(() => {
    if (params.recordingStarted || params.isPreparingCapture || params.isAiBusy || params.isAiSpeaking) {
      duckPlayer(params.radioPlayer, params.radioVolumeDucked);
      return;
    }

    restorePlayer(params.radioPlayer, params.radioVolumeIdle);
  }, [
    params.isAiBusy,
    params.isAiSpeaking,
    params.isPreparingCapture,
    params.radioPlayer,
    params.radioVolumeDucked,
    params.radioVolumeIdle,
    params.recordingStarted,
  ]);

  useEffect(() => {
    if (params.isPlaying && !params.isAiSpeaking) {
      resumePlayer(params.radioPlayer, params.radioVolumeIdle);
      return;
    }

    pausePlayer(params.radioPlayer);
  }, [params.isAiSpeaking, params.isPlaying, params.radioPlayer, params.radioVolumeIdle]);

  useEffect(() => {
    if (!params.isPlaying || params.isAiSpeaking) {
      return;
    }

    const timer = setInterval(() => {
      params.recordListeningTick(params.currentStationId, 5000);
    }, 5000);

    return () => clearInterval(timer);
  }, [params.currentStationId, params.isAiSpeaking, params.isPlaying, params.recordListeningTick]);

  useEffect(() => {
    if (!params.isAiSpeaking || !params.aiDidJustFinish) {
      return;
    }

    params.finishAiPlayback();
  }, [params.aiDidJustFinish, params.finishAiPlayback, params.isAiSpeaking]);

  useEffect(() => {
    if (
      !shouldRecoverRadio({
        isPlaying: params.isPlaying,
        isAiSpeaking: params.isAiSpeaking,
        isPreparingCapture: params.isPreparingCapture,
        recordingStarted: params.recordingStarted,
        hasPlayerError: params.radioHasError,
        didJustFinish: params.radioDidJustFinish,
        recoveryLocked: params.radioRecoveryLocked,
      })
    ) {
      return;
    }

    params.setRadioRecoveryLocked(true);
    const timer = setTimeout(() => {
      const recovered = params.chooseNextStation(inferAmbientMode(params.listenerProfile));
      if (!recovered) {
        resumePlayer(params.radioPlayer, params.radioVolumeIdle);
      }
      params.setRadioRecoveryLocked(false);
    }, 600);

    return () => clearTimeout(timer);
  }, [
    params.chooseNextStation,
    params.isAiSpeaking,
    params.isPlaying,
    params.isPreparingCapture,
    params.listenerProfile,
    params.radioDidJustFinish,
    params.radioHasError,
    params.radioPlayer,
    params.radioRecoveryLocked,
    params.radioVolumeIdle,
    params.recordingStarted,
    params.setRadioRecoveryLocked,
  ]);

  useEffect(() => {
    return () => {
      pausePlayer(params.radioPlayer);
      pausePlayer(params.aiPlayer);
    };
  }, [params.aiPlayer, params.radioPlayer]);
}
