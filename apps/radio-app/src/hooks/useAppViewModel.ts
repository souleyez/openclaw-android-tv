import { useMemo } from 'react';

import { BACKGROUND_PRESETS, MOCK_STATIONS, type Station } from '../data/mockRadio';

export function useAppViewModel(params: {
  stations: Station[];
  currentStationId: string;
  backgroundPresetId: string;
  customBackgroundUri: string | null;
  recordingStarted: boolean;
  isAiSpeaking: boolean;
  isPlaying: boolean;
  showModelGlow: boolean;
}) {
  const currentStation = useMemo(
    () =>
      params.stations.find((station) => station.id === params.currentStationId) ??
      params.stations[0] ??
      MOCK_STATIONS[0],
    [params.currentStationId, params.stations],
  );

  const selectedBackground = useMemo(
    () =>
      BACKGROUND_PRESETS.find((preset) => preset.id === params.backgroundPresetId) ?? BACKGROUND_PRESETS[0],
    [params.backgroundPresetId],
  );

  const backgroundSource = params.customBackgroundUri
    ? { uri: params.customBackgroundUri }
    : { uri: selectedBackground.imageUri };

  const orbScale = params.recordingStarted ? 1.14 : params.isAiSpeaking ? 1.08 : params.isPlaying ? 1.02 : 1;

  return {
    currentStation,
    backgroundSource,
    orbScale,
    showModelGlow: params.showModelGlow,
  };
}
