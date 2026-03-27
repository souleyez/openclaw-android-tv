import { useCallback, useState } from 'react';

import { fetchSubscriptionPlan, fetchSubscriptionStatus, type RadioSubscriptionPlan } from '../services/billingApi';

export function useSubscriptionState() {
  const [subscriptionPreviewEnabled, setSubscriptionPreviewEnabled] = useState(false);
  const [subscriptionIsActive, setSubscriptionIsActive] = useState(false);
  const [subscriptionLoading, setSubscriptionLoading] = useState(false);
  const [subscriptionPlan, setSubscriptionPlan] = useState<RadioSubscriptionPlan | null>(null);

  const refreshSubscriptionState = useCallback(async () => {
    setSubscriptionLoading(true);
    try {
      const [plan, subscription] = await Promise.all([
        fetchSubscriptionPlan().catch(() => null),
        fetchSubscriptionStatus().catch(() => null),
      ]);
      setSubscriptionPlan(plan);
      setSubscriptionIsActive(Boolean(subscription?.active));
    } finally {
      setSubscriptionLoading(false);
    }
  }, []);

  return {
    subscriptionPreviewEnabled,
    setSubscriptionPreviewEnabled,
    subscriptionIsActive,
    setSubscriptionIsActive,
    subscriptionLoading,
    setSubscriptionLoading,
    subscriptionPlan,
    setSubscriptionPlan,
    refreshSubscriptionState,
  };
}
