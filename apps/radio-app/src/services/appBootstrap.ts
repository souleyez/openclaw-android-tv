import type { BroadcastItem, Station } from '../data/mockRadio';
import { MOCK_STATIONS } from '../data/mockRadio';
import { fetchSubscriptionPlan, fetchSubscriptionStatus, type RadioSubscriptionPlan, type SubscriptionStatus } from './billingApi';
import { fetchBroadcasts, fetchStations } from './radioApi';
import { resolvePreferredChineseVoice, type SpeechProfile } from './speechProfile';
import { consumePendingConfigRelease, setAppliedConfigVersion } from './updatesApi';

export type AppBootstrapPayload = {
  stations: Station[];
  broadcasts: BroadcastItem[];
  voiceProfile: SpeechProfile;
  subscriptionPlan: RadioSubscriptionPlan | null;
  subscriptionStatus: SubscriptionStatus | null;
  pendingConfig: {
    preferredMode?: 'music' | 'news' | 'any';
    backgroundImageUrl?: string;
    versionCode?: number;
  } | null;
};

export async function loadAppBootstrap(
  preferredMode: 'music' | 'news' | 'any' = 'any',
): Promise<AppBootstrapPayload> {
  const [stations, broadcasts, voiceProfile, subscriptionPlan, subscriptionStatus, pendingConfigRelease] =
    await Promise.all([
      fetchStations(preferredMode).catch(() => MOCK_STATIONS),
      fetchBroadcasts(preferredMode).catch(() => []),
      resolvePreferredChineseVoice(),
      fetchSubscriptionPlan().catch(() => null),
      fetchSubscriptionStatus().catch(() => null),
      consumePendingConfigRelease().catch(() => null),
    ]);

  if (pendingConfigRelease?.versionCode != null) {
    await setAppliedConfigVersion(pendingConfigRelease.versionCode).catch(() => undefined);
  }

  return {
    stations: stations.length ? stations : MOCK_STATIONS,
    broadcasts,
    voiceProfile,
    subscriptionPlan,
    subscriptionStatus,
    pendingConfig:
      pendingConfigRelease == null
        ? null
        : {
            preferredMode:
              pendingConfigRelease.payload.preferredMode === 'music' ||
              pendingConfigRelease.payload.preferredMode === 'news' ||
              pendingConfigRelease.payload.preferredMode === 'any'
                ? pendingConfigRelease.payload.preferredMode
                : undefined,
            backgroundImageUrl:
              typeof pendingConfigRelease.payload.backgroundImageUrl === 'string'
                ? pendingConfigRelease.payload.backgroundImageUrl.trim()
                : undefined,
            versionCode: pendingConfigRelease.versionCode,
          },
  };
}
