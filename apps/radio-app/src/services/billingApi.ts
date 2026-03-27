import { API_BASE_URL } from './radioApi';

export type RadioSubscriptionPlan = {
  id: string;
  code: string;
  displayName: string;
  displayPrice: string;
  monthlyPriceCny: number;
  iosProductId: string;
  androidProductId: string;
  officialChannels: string[];
};

export type SubscriptionStatus = {
  accountId: string;
  active: boolean;
  planCode: string;
  expiresAt?: string;
  officialChannelRequired: boolean;
  availablePlatforms: Array<'ios' | 'android'>;
  verificationState: 'not_subscribed' | 'active' | 'expired' | 'pending_sdk';
};

export async function fetchSubscriptionPlan() {
  const response = await fetch(`${API_BASE_URL}/billing/plans`);
  if (!response.ok) {
    throw new Error(`Failed to fetch billing plans: ${response.status}`);
  }

  const payload = (await response.json()) as {
    items: RadioSubscriptionPlan[];
  };

  return payload.items[0] ?? null;
}

export async function fetchSubscriptionStatus() {
  const response = await fetch(`${API_BASE_URL}/billing/subscription`, {
    headers: {
      'x-device-user-id': 'user_demo',
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch subscription status: ${response.status}`);
  }

  return (await response.json()) as SubscriptionStatus;
}
