import { useEffect, useState } from 'react';

const IDLE_PHASE_AFTER_MS = 30000;

export type InteractionPhase = 'idle' | 'active_local' | 'active_leased';

export function useInteractionActivity(hasLease: boolean) {
  const [lastInteractionAt, setLastInteractionAt] = useState(Date.now());
  const [phase, setPhase] = useState<InteractionPhase>(hasLease ? 'active_leased' : 'active_local');

  const markInteraction = (timestamp = Date.now()) => {
    setLastInteractionAt(timestamp);
    setPhase(hasLease ? 'active_leased' : 'active_local');
  };

  useEffect(() => {
    if (Date.now() - lastInteractionAt >= IDLE_PHASE_AFTER_MS) {
      setPhase('idle');
      return;
    }

    setPhase(hasLease ? 'active_leased' : 'active_local');

    const timer = setTimeout(() => {
      setPhase('idle');
    }, Math.max(0, IDLE_PHASE_AFTER_MS - (Date.now() - lastInteractionAt)));

    return () => clearTimeout(timer);
  }, [hasLease, lastInteractionAt]);

  return {
    interactionPhase: phase,
    lastInteractionAt,
    markInteraction,
  };
}
