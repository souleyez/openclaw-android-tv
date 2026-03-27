import type { BroadcastItem, Station } from '../data/mockRadio';
import { MOCK_STATIONS } from '../data/mockRadio';
import { fetchSubscriptionPlan, fetchSubscriptionStatus, type RadioSubscriptionPlan, type SubscriptionStatus } from './billingApi';
import { fetchBroadcasts, fetchStations } from './radioApi';
import { resolvePreferredChineseVoice, type SpeechProfile } from './speechProfile';

export type AppBootstrapPayload = {
  stations: Station[];
  broadcasts: BroadcastItem[];
  voiceProfile: SpeechProfile;
  subscriptionPlan: RadioSubscriptionPlan | null;
  subscriptionStatus: SubscriptionStatus | null;
};

export async function loadAppBootstrap(
  preferredMode: 'music' | 'news' | 'any' = 'any',
): Promise<AppBootstrapPayload> {
  const [stations, broadcasts, voiceProfile, subscriptionPlan, subscriptionStatus] =
    await Promise.all([
      fetchStations(preferredMode).catch(() => MOCK_STATIONS),
      fetchBroadcasts().catch(() => []),
      resolvePreferredChineseVoice(),
      fetchSubscriptionPlan().catch(() => null),
      fetchSubscriptionStatus().catch(() => null),
    ]);

  return {
    stations: stations.length ? stations : MOCK_STATIONS,
    broadcasts,
    voiceProfile,
    subscriptionPlan,
    subscriptionStatus,
  };
}
