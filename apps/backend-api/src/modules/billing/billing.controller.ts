import { Body, Controller, Get, Headers, Post } from '@nestjs/common';

import { AuthService } from '../auth/auth.service';
import { BillingService } from './billing.service';

class VerifyAppleSubscriptionDto {
  productId!: string;
  transactionId!: string;
  receiptData!: string;
  originalTransactionId?: string;
}

class VerifyGoogleSubscriptionDto {
  productId!: string;
  transactionId!: string;
  purchaseToken!: string;
}

@Controller('billing')
export class BillingController {
  constructor(
    private readonly billingService: BillingService,
    private readonly authService: AuthService,
  ) {}

  @Get('payment-methods')
  async listPaymentMethods() {
    return {
      priority: 'official_store',
      items: await this.billingService.listPaymentMethods(),
    };
  }

  @Get('plans')
  async listPlans() {
    return {
      items: await this.billingService.listPlans(),
    };
  }

  @Get('client-config')
  async getClientConfig() {
    return await this.billingService.getClientConfig();
  }

  @Get('subscription')
  async getSubscriptionStatus(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });

    return await this.billingService.getSubscriptionStatus(account.id);
  }

  @Post('subscription/apple/verify')
  async verifyAppleSubscription(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: VerifyAppleSubscriptionDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });

    return await this.billingService.submitStoreVerification({
      accountId: account.id,
      platform: 'ios',
      productId: body.productId,
      transactionId: body.transactionId,
      receiptData: body.receiptData,
      originalTransactionId: body.originalTransactionId,
    });
  }

  @Post('subscription/google/verify')
  async verifyGoogleSubscription(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: VerifyGoogleSubscriptionDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });

    return await this.billingService.submitStoreVerification({
      accountId: account.id,
      platform: 'android',
      productId: body.productId,
      transactionId: body.transactionId,
      purchaseToken: body.purchaseToken,
    });
  }
}
