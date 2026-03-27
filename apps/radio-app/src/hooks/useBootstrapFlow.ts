import { useEffect } from 'react';

import type { Station } from '../data/mockRadio';
import { MOCK_STATIONS } from '../data/mockRadio';
import { loadAppBootstrap } from '../services/appBootstrap';
import type { ListenerProfile } from '../services/radioIntelligence';

export function useBootstrapFlow(params: {
  enabled: boolean;
  listenerProfile: ListenerProfile;
  startupSelectionResolved: boolean;
  setStartupSelectionResolved: (value: boolean) => void;
  onComplete: () => void;
  selectStation: (stationId: string) => void;
  setStations: (stations: Station[]) => void;
  setBroadcasts: (items: any[]) => void;
  setPreferredSpeechLanguage: (language: string) => void;
  setSubscriptionPlan: (plan: any) => void;
  setSubscriptionIsActive: (value: boolean) => void;
  setSubscriptionLoading: (value: boolean) => void;
  setPreferredMode: (mode: 'music' | 'news' | 'any') => void;
  setCustomBackgroundUri: (uri: string | null) => void;
  startBroadcast: (source: 'station' | 'broadcast') => void;
  recommendStartupStation: (params: { stations: Station[]; profile: ListenerProfile }) => Station | undefined;
}) {
  useEffect(() => {
    if (!params.enabled) {
      return;
    }

    let active = true;

    const runBootSequence = async () => {
      try {
        params.setSubscriptionLoading(true);
        const bootstrap = await loadAppBootstrap(params.listenerProfile.preferredMode);

        if (!active) {
          return;
        }

        params.setStations(bootstrap.stations);
        params.setBroadcasts(bootstrap.broadcasts);
        params.setPreferredSpeechLanguage(bootstrap.voiceProfile.language || 'zh-CN');
        params.setSubscriptionPlan(bootstrap.subscriptionPlan);
        params.setSubscriptionIsActive(Boolean(bootstrap.subscriptionStatus?.active));
        if (bootstrap.pendingConfig?.preferredMode) {
          params.setPreferredMode(bootstrap.pendingConfig.preferredMode);
        }
        if (bootstrap.pendingConfig?.backgroundImageUrl) {
          params.setCustomBackgroundUri(bootstrap.pendingConfig.backgroundImageUrl);
        }
        params.setSubscriptionLoading(false);

        if (!params.startupSelectionResolved) {
          const nextStation = params.recommendStartupStation({
            stations: bootstrap.stations.length ? bootstrap.stations : MOCK_STATIONS,
            profile: params.listenerProfile,
          });
          if (nextStation) {
            params.selectStation(nextStation.id);
          }
          params.setStartupSelectionResolved(true);
        }
        params.onComplete();
        params.startBroadcast('station');
      } catch {
        if (active) {
          params.setSubscriptionLoading(false);
          params.onComplete();
          params.startBroadcast('station');
        }
      }
    };

    void runBootSequence();

    return () => {
      active = false;
    };
  }, [
    params.enabled,
    params.listenerProfile,
    params.onComplete,
    params.recommendStartupStation,
    params.selectStation,
    params.setBroadcasts,
    params.setPreferredSpeechLanguage,
    params.setPreferredMode,
    params.setStartupSelectionResolved,
    params.setStations,
    params.setCustomBackgroundUri,
    params.setSubscriptionIsActive,
    params.setSubscriptionLoading,
    params.setSubscriptionPlan,
    params.startBroadcast,
    params.startupSelectionResolved,
  ]);
}
