import { Injectable } from '@nestjs/common';

import {
  BillingPlanRecord,
  DeviceUserProfileRecord,
  StablecoinPaymentOrderRecord,
  StorageService,
} from '../../shared/storage.service';

type SupportedStorePlatform = 'ios' | 'android';

export type OfficialChannelCode = 'apple_iap' | 'google_play' | 'wechat_web';

export type StoreVerificationPayload = {
  accountId: string;
  platform: SupportedStorePlatform;
  productId: string;
  transactionId: string;
  receiptData?: string;
  purchaseToken?: string;
  originalTransactionId?: string;
};

type RadioSubscriptionPlan = BillingPlanRecord & {
  displayPrice: string;
  monthlyPriceCny: number;
  currency: 'CNY';
  appId: 'shenglin-radio';
  officialChannels: OfficialChannelCode[];
  iosProductId: string;
  androidProductId: string;
};

type SubscriptionStatus = {
  accountId: string;
  active: boolean;
  planCode: string;
  expiresAt?: string;
  graceUntil?: string;
  officialChannelRequired: boolean;
  availablePlatforms: SupportedStorePlatform[];
  verificationState: 'not_subscribed' | 'active' | 'expired' | 'pending_sdk';
};

@Injectable()
export class BillingService {
  private readonly radioPlan: RadioSubscriptionPlan = {
    id: 'plan_shenglin_monthly',
    code: 'shenglin_monthly',
    displayName: '声临月订阅',
    monthlyPriceUsd: 0.83,
    tokenGrantMonthly: 120000,
    deviceLimit: 2,
    billingCycle: 'monthly',
    active: true,
    displayPrice: '6元/月',
    monthlyPriceCny: 6,
    currency: 'CNY',
    appId: 'shenglin-radio',
    officialChannels: ['apple_iap', 'google_play', 'wechat_web'],
    iosProductId: 'com.shenglin.radio.monthly',
    androidProductId: 'com.shenglin.radio.monthly',
  };

  constructor(private readonly storageService: StorageService) {}

  async listPaymentMethods() {
    return [
      {
        code: 'apple_iap',
        type: 'app_store_subscription',
        platform: 'ios',
        enabled: true,
        official: true,
        priority: 1,
      },
      {
        code: 'google_play',
        type: 'play_store_subscription',
        platform: 'android',
        enabled: true,
        official: true,
        priority: 2,
      },
      {
        code: 'wechat_web',
        type: 'web_payment',
        platform: 'web',
        enabled: true,
        official: false,
        priority: 3,
      },
    ];
  }

  async listPlans(): Promise<RadioSubscriptionPlan[]> {
    return [this.radioPlan];
  }

  async getPlanByCode(code: string): Promise<RadioSubscriptionPlan | undefined> {
    return code === this.radioPlan.code ? this.radioPlan : undefined;
  }

  async getClientConfig() {
    return {
      appId: this.radioPlan.appId,
      planCode: this.radioPlan.code,
      displayPrice: this.radioPlan.displayPrice,
      officialChannels: this.radioPlan.officialChannels,
      iosProductId: this.radioPlan.iosProductId,
      androidProductId: this.radioPlan.androidProductId,
      note: 'Store verification must be completed with Apple IAP or Google Play Billing.',
    };
  }

  async getSubscriptionStatus(accountId: string): Promise<SubscriptionStatus> {
    const profile = await this.requireDeviceProfile(accountId);
    const expiresAt = profile.entitlementExpiresAt;
    const expiresAtMillis = expiresAt == null ? 0 : Date.parse(expiresAt);
    const active = expiresAtMillis > Date.now() && profile.planCode === this.radioPlan.code;

    return {
      accountId,
      active,
      planCode: profile.planCode,
      expiresAt,
      officialChannelRequired: true,
      availablePlatforms: ['ios', 'android'],
      verificationState: active
        ? 'active'
        : expiresAtMillis > 0 && expiresAtMillis <= Date.now()
          ? 'expired'
          : 'not_subscribed',
    };
  }

  async submitStoreVerification(
    input: StoreVerificationPayload,
  ): Promise<{
    accepted: true;
    verified: false;
    platform: SupportedStorePlatform;
    verificationState: 'pending_sdk';
    subscription: SubscriptionStatus;
  }> {
    this.assertProductId(input.platform, input.productId);
    if (input.transactionId.trim().length === 0) {
      throw new Error('transactionId is required');
    }
    if (input.platform === 'ios' && (input.receiptData?.trim().length ?? 0) === 0) {
      throw new Error('receiptData is required for iOS verification');
    }
    if (input.platform === 'android' && (input.purchaseToken?.trim().length ?? 0) === 0) {
      throw new Error('purchaseToken is required for Android verification');
    }

    return {
      accepted: true,
      verified: false,
      platform: input.platform,
      verificationState: 'pending_sdk',
      subscription: await this.getSubscriptionStatus(input.accountId),
    };
  }

  async listStablecoinOrders(
    _accountId: string,
  ): Promise<StablecoinPaymentOrderRecord[]> {
    return [];
  }

  private async requireDeviceProfile(
    accountId: string,
  ): Promise<DeviceUserProfileRecord> {
    const profile = await this.storageService.getDeviceUserProfile(accountId);
    if (profile != null) {
      return profile;
    }

    const now = new Date().toISOString();
    const createdProfile: DeviceUserProfileRecord = {
      id: accountId,
      displayName: `Device User ${accountId.slice(0, 8)}`,
      planCode: 'free',
      status: 'active',
      recoveryHint: 'restore with official store account',
      createdAt: now,
      updatedAt: now,
    };
    await this.storageService.upsertDeviceUserProfile(createdProfile);
    return createdProfile;
  }

  private assertProductId(platform: SupportedStorePlatform, productId: string) {
    const expected =
      platform === 'ios' ? this.radioPlan.iosProductId : this.radioPlan.androidProductId;

    if (productId !== expected) {
      throw new Error(`Unexpected productId: ${productId}`);
    }
  }
}
