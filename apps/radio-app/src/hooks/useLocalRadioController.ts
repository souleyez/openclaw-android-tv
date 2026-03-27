import { useCallback } from 'react';

import type { Station } from '../data/mockRadio';
import { canTogglePlayback } from '../services/interactionGuards';
import {
  recommendNextStation,
  type ListenerProfile,
} from '../services/radioIntelligence';

type LocalRadioAction = 'next' | 'play_music' | 'play_news' | 'ban_current';

export function useLocalRadioController(params: {
  currentStation: Station;
  stations: Station[];
  listenerProfile: ListenerProfile;
  isPlaying: boolean;
  isAiSpeaking: boolean;
  isAiBusy: boolean;
  isUploadingBroadcast: boolean;
  isPreparingCapture: boolean;
  isRecording: boolean;
  markInteraction: () => void;
  selectStation: (stationId: string) => void;
  setPreferredMode: (mode: 'any' | 'music' | 'news') => void;
  markStationSkipped: (stationId: string) => void;
  blockStation: (stationId: string) => void;
  startBroadcast: (source: 'station') => void;
  stopBroadcast: () => void;
}) {
  const chooseNextStation = useCallback(
    (mode: 'any' | 'music' | 'news' = 'any') => {
      params.setPreferredMode(mode);
      params.markStationSkipped(params.currentStation.id);

      const replacement = recommendNextStation({
        currentStationId: params.currentStation.id,
        stations: params.stations,
        profile: params.listenerProfile,
        requestedMode: mode,
      });

      if (replacement) {
        params.selectStation(replacement.id);
        return replacement;
      }

      return null;
    },
    [
      params.currentStation.id,
      params.listenerProfile,
      params.markStationSkipped,
      params.selectStation,
      params.setPreferredMode,
      params.stations,
    ],
  );

  const executeLocalAssistantIntent = useCallback(
    (kind: LocalRadioAction) => {
      params.markInteraction();

      switch (kind) {
        case 'next':
          chooseNextStation('any');
          return true;
        case 'play_music':
          chooseNextStation('music');
          return true;
        case 'play_news':
          chooseNextStation('news');
          return true;
        case 'ban_current':
          params.blockStation(params.currentStation.id);
          chooseNextStation('any');
          return true;
        default:
          return false;
      }
    },
    [chooseNextStation, params.blockStation, params.currentStation.id, params.markInteraction],
  );

  const onTogglePlayback = useCallback(() => {
    if (
      !canTogglePlayback({
        isAiSpeaking: params.isAiSpeaking,
        isRecording: params.isRecording,
        isAiBusy: params.isAiBusy,
        isUploadingBroadcast: params.isUploadingBroadcast,
        isPreparingCapture: params.isPreparingCapture,
      })
    ) {
      return;
    }

    params.markInteraction();

    if (params.isPlaying) {
      params.stopBroadcast();
      return;
    }

    params.startBroadcast('station');
  }, [
    params.isAiBusy,
    params.isAiSpeaking,
    params.isPlaying,
    params.isPreparingCapture,
    params.isRecording,
    params.isUploadingBroadcast,
    params.markInteraction,
    params.startBroadcast,
    params.stopBroadcast,
  ]);

  return {
    chooseNextStation,
    executeLocalAssistantIntent,
    onTogglePlayback,
  };
}
