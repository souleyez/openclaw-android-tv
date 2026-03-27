import { useCallback } from 'react';

import type { BroadcastItem } from '../data/mockRadio';
import { fetchBroadcasts } from '../services/radioApi';

export function useBroadcastTimeline(params: {
  setBroadcasts: (items: BroadcastItem[]) => void;
  prependBroadcast: (item: BroadcastItem) => void;
  preferredMode: 'music' | 'news' | 'any';
}) {
  const refreshBroadcastTimeline = useCallback(async () => {
    try {
      const items = await fetchBroadcasts(params.preferredMode);
      params.setBroadcasts(items);
    } catch {
      // ignore network errors and keep local timeline
    }
  }, [params]);

  const pushBroadcast = useCallback(
    (item: BroadcastItem) => {
      params.prependBroadcast(item);
    },
    [params],
  );

  return {
    refreshBroadcastTimeline,
    pushBroadcast,
  };
}
