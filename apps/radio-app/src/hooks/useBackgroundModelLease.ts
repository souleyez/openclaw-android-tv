import { useEffect, useRef } from 'react';

import {
  getCachedModelSession,
  releaseModelLease,
  requestModelLease,
  type BootstrapSessionPayload,
} from '../services/modelLease';
const LEASE_RETRY_MS = 20000;
const LEASE_MAX_HOLD_MS = 5 * 60 * 1000;
const LEASE_IDLE_RELEASE_MS = 90 * 1000;

export function useBackgroundModelLease(params: {
  enabled: boolean;
  currentSession: BootstrapSessionPayload | null;
  setModelSession: (session: BootstrapSessionPayload | null) => void;
  clearModelLease: () => void;
  setSubscriptionPreviewEnabled: (value: boolean) => void;
  playAiBroadcast: (text: string, resumeBroadcast: boolean) => Promise<void>;
  chatWithLease: (session: BootstrapSessionPayload) => Promise<string>;
  isBusy: boolean;
  isPreparingCapture: boolean;
  isRecording: boolean;
  markInteraction: (timestamp: number) => void;
  lastInteractionAt: number;
}) {
  const queuedRef = useRef(false);
  const greetedLeaseIdRef = useRef<string | null>(null);
  const acquiredAtRef = useRef<number | null>(null);

  const clearLeaseState = () => {
    acquiredAtRef.current = null;
    greetedLeaseIdRef.current = null;
    params.clearModelLease();
    params.setSubscriptionPreviewEnabled(false);
  };

  const releaseActiveLease = (leaseId?: string | null) => {
    if (leaseId) {
      void releaseModelLease(leaseId);
    }
    clearLeaseState();
  };

  useEffect(() => {
    void (async () => {
      const cached = await getCachedModelSession();
      if (cached?.lease != null) {
        acquiredAtRef.current = Date.now();
        params.setModelSession(cached);
        params.setSubscriptionPreviewEnabled(true);
      }
    })();
  }, []);

  useEffect(() => {
    if (!params.enabled || params.currentSession?.lease != null || queuedRef.current) {
      return;
    }

    queuedRef.current = true;
    let cancelled = false;

    const loop = async () => {
      while (!cancelled) {
        try {
          const session = await requestModelLease();
          if (session?.lease != null) {
            acquiredAtRef.current = Date.now();
            params.setModelSession(session);
            params.setSubscriptionPreviewEnabled(true);
            queuedRef.current = false;
            return;
          }
        } catch {
          // keep local mode and retry later
        }

        await new Promise((resolve) => setTimeout(resolve, LEASE_RETRY_MS));
      }
    };

    void loop();

    return () => {
      cancelled = true;
      queuedRef.current = false;
    };
  }, [params.currentSession?.lease, params.enabled]);

  useEffect(() => {
    if (
      !params.enabled ||
      params.currentSession?.lease == null ||
      greetedLeaseIdRef.current === params.currentSession.lease.leaseId ||
      params.isBusy ||
      params.isPreparingCapture ||
      params.isRecording
    ) {
      return;
    }

    greetedLeaseIdRef.current = params.currentSession.lease.leaseId;
    params.markInteraction(Date.now());

    void (async () => {
      try {
        const reply = await params.chatWithLease(params.currentSession!);
        if (reply.trim()) {
          await params.playAiBroadcast(reply, true);
        }
      } catch {
        // ignore and keep radio playing
      }
    })();
  }, [
    params.chatWithLease,
    params.currentSession,
    params.enabled,
    params.isBusy,
    params.isPreparingCapture,
    params.isRecording,
    params.markInteraction,
    params.playAiBroadcast,
  ]);

  useEffect(() => {
    if (params.currentSession?.lease == null) {
      return;
    }

    const timer = setInterval(() => {
      const now = Date.now();
      const acquiredAt = acquiredAtRef.current ?? now;
      const idleFor = now - params.lastInteractionAt;
      if (now - acquiredAt >= LEASE_MAX_HOLD_MS || idleFor >= LEASE_IDLE_RELEASE_MS) {
        releaseActiveLease(params.currentSession?.lease?.leaseId);
      }
    }, 10000);

    return () => clearInterval(timer);
  }, [params.currentSession, params.lastInteractionAt]);
}
