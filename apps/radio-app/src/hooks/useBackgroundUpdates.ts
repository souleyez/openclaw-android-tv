import { useEffect, useRef } from 'react';

import type { InteractionPhase } from './useInteractionActivity';
import {
  fetchUpdateBootstrap,
  reportQueuedOtaRelease,
  setAppliedConfigVersion,
  stashPendingConfigRelease,
  type UpdateBootstrapPayload,
} from '../services/updatesApi';

export function useBackgroundUpdates(params: {
  enabled: boolean;
  interactionPhase: InteractionPhase;
  preferredMode: 'music' | 'news' | 'any';
  customBackgroundUri: string | null;
  setPreferredMode: (mode: 'music' | 'news' | 'any') => void;
  setCustomBackgroundUri: (uri: string | null) => void;
  refreshBroadcastTimeline: () => Promise<void>;
}) {
  const {
    enabled,
    interactionPhase,
    preferredMode,
    customBackgroundUri,
    setPreferredMode,
    setCustomBackgroundUri,
    refreshBroadcastTimeline,
  } = params;
  const updateRef = useRef<UpdateBootstrapPayload | null>(null);
  const fetchedRef = useRef(false);

  useEffect(() => {
    if (!enabled || fetchedRef.current) {
      return;
    }

    fetchedRef.current = true;
    let cancelled = false;

    const timer = setTimeout(() => {
      void (async () => {
        try {
          const payload = await fetchUpdateBootstrap(preferredMode);
          if (cancelled) {
            return;
          }

          updateRef.current = payload;

          if (payload.ota.available || payload.config.available) {
            await refreshBroadcastTimeline();
          }
        } catch {
          // keep local-first mode
        }
      })();
    }, 3500);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [enabled, preferredMode, refreshBroadcastTimeline]);

  useEffect(() => {
    if (interactionPhase !== 'idle') {
      return;
    }

    const payload = updateRef.current;
    if (payload == null) {
      return;
    }

    void (async () => {
      if (payload.config.available && payload.config.release && payload.config.payload) {
        const configPayload = payload.config.payload;
        if (
          payload.config.release.applyPolicy === 'idle_apply' &&
          (configPayload.preferredMode === 'music' ||
            configPayload.preferredMode === 'news' ||
            configPayload.preferredMode === 'any')
        ) {
          setPreferredMode(configPayload.preferredMode);
        }

        if (
          payload.config.release.applyPolicy === 'idle_apply' &&
          !customBackgroundUri &&
          typeof configPayload.backgroundImageUrl === 'string' &&
          configPayload.backgroundImageUrl.trim().length > 0
        ) {
          setCustomBackgroundUri(configPayload.backgroundImageUrl.trim());
        }

        if (payload.config.release.applyPolicy === 'next_boot') {
          await stashPendingConfigRelease({
            releaseId: payload.config.release.id,
            versionCode: payload.config.release.versionCode,
            payload: configPayload,
          });
        } else {
          await setAppliedConfigVersion(payload.config.release.versionCode);
        }
        updateRef.current = {
          ...payload,
          config: { available: false },
        };
      }

      if (payload.ota.available && payload.ota.release) {
        await reportQueuedOtaRelease({
          releaseId: payload.ota.release.id,
          targetVersionCode: payload.ota.release.versionCode,
        }).catch(() => undefined);

        updateRef.current = {
          ...(updateRef.current ?? payload),
          ota: { available: false },
        };
      }
    })();
  }, [
    customBackgroundUri,
    interactionPhase,
    setCustomBackgroundUri,
    setPreferredMode,
  ]);
}
