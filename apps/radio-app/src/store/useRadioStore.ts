import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { BroadcastItem } from '../data/mockRadio';

type ActiveSource = 'station' | 'broadcast';
type PreferredMode = 'music' | 'news' | 'any';

type RadioState = {
  currentStationId: string;
  isPlaying: boolean;
  isRecording: boolean;
  backgroundPresetId: string;
  customBackgroundUri: string | null;
  broadcasts: BroadcastItem[];
  blockedStationIds: string[];
  listeningDurations: Record<string, number>;
  stationSkipCounts: Record<string, number>;
  recentStationIds: string[];
  preferredMode: PreferredMode;
  selectStation: (stationId: string) => void;
  markStationSkipped: (stationId: string) => void;
  setPreferredMode: (mode: PreferredMode) => void;
  startBroadcast: (source: ActiveSource) => void;
  finishBroadcast: () => void;
  stopBroadcast: () => void;
  selectBackgroundPreset: (presetId: string) => void;
  setCustomBackgroundUri: (uri: string | null) => void;
  blockStation: (stationId: string) => void;
  recordListeningTick: (stationId: string, durationMs: number) => void;
  setBroadcasts: (items: BroadcastItem[]) => void;
  prependBroadcast: (item: BroadcastItem) => void;
};

export const useRadioStore = create<RadioState>()(
  persist(
    (set) => ({
      currentStationId: 'station-cnr-voice',
      isPlaying: true,
      isRecording: false,
      backgroundPresetId: 'amber-console',
      customBackgroundUri: null,
      broadcasts: [],
      blockedStationIds: [],
      listeningDurations: {},
      stationSkipCounts: {},
      recentStationIds: [],
      preferredMode: 'any',
      selectStation: (stationId) =>
        set((state) => ({
          currentStationId: stationId,
          isPlaying: true,
          recentStationIds: [stationId, ...state.recentStationIds.filter((item) => item !== stationId)].slice(0, 8),
        })),
      markStationSkipped: (stationId) =>
        set((state) => ({
          stationSkipCounts: {
            ...state.stationSkipCounts,
            [stationId]: (state.stationSkipCounts[stationId] ?? 0) + 1,
          },
        })),
      setPreferredMode: (mode) =>
        set({
          preferredMode: mode,
        }),
      startBroadcast: (source) =>
        set({
          isPlaying: source !== 'broadcast',
          isRecording: source === 'broadcast',
        }),
      finishBroadcast: () =>
        set({
          isRecording: false,
          isPlaying: true,
        }),
      stopBroadcast: () =>
        set({
          isPlaying: false,
          isRecording: false,
        }),
      selectBackgroundPreset: (presetId) =>
        set({
          backgroundPresetId: presetId,
          customBackgroundUri: null,
        }),
      setCustomBackgroundUri: (uri) =>
        set({
          customBackgroundUri: uri,
        }),
      blockStation: (stationId) =>
        set((state) => ({
          blockedStationIds: state.blockedStationIds.includes(stationId)
            ? state.blockedStationIds
            : [...state.blockedStationIds, stationId],
          stationSkipCounts: {
            ...state.stationSkipCounts,
            [stationId]: (state.stationSkipCounts[stationId] ?? 0) + 3,
          },
        })),
      recordListeningTick: (stationId, durationMs) =>
        set((state) => ({
          listeningDurations: {
            ...state.listeningDurations,
            [stationId]: (state.listeningDurations[stationId] ?? 0) + durationMs,
          },
        })),
      setBroadcasts: (items) =>
        set({
          broadcasts: items,
        }),
      prependBroadcast: (item) =>
        set((state) => ({
          broadcasts: [item, ...state.broadcasts.filter((existing) => existing.id !== item.id)].slice(0, 50),
        })),
    }),
    {
      name: 'radio-app-shell',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        currentStationId: state.currentStationId,
        backgroundPresetId: state.backgroundPresetId,
        customBackgroundUri: state.customBackgroundUri,
        broadcasts: state.broadcasts,
        blockedStationIds: state.blockedStationIds,
        listeningDurations: state.listeningDurations,
        stationSkipCounts: state.stationSkipCounts,
        recentStationIds: state.recentStationIds,
        preferredMode: state.preferredMode,
      }),
    },
  ),
);
