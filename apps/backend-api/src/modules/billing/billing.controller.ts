import { Body, Controller, Get, Headers, Param, Post } from '@nestjs/common';

import { AuthService } from '../auth/auth.service';
import { BillingService } from './billing.service';

class CreateStablecoinOrderDto {
  stablecoinSymbol!: 'USDC' | 'USDT';
  chain!: 'Polygon' | 'Base' | 'TRON' | 'BSC';
  amountUsd!: number;
}

class SubmitStablecoinTransactionDto {
  txHash!: string;
}

class UpdateStablecoinConfirmationsDto {
  confirmations!: number;
}

class UpdateStablecoinOrderStatusDto {
  status!: 'failed' | 'reviewing' | 'expired';
  reviewNote?: string;
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
      priority: 'stablecoin',
      items: await this.billingService.listPaymentMethods(),
    };
  }

  @Get('plans')
  async listPlans() {
    return {
      items: await this.billingService.listPlans(),
    };
  }

  @Get('orders')
  async listStablecoinOrders(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      accountId: account.id,
      paymentPriority: 'stablecoin',
      items: await this.billingService.listStablecoinOrders(account.id),
    };
  }

  @Post('orders')
  async createStablecoinOrder(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: CreateStablecoinOrderDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.billingService.createStablecoinOrder({
      accountId: account.id,
      stablecoinSymbol: body.stablecoinSymbol,
      chain: body.chain,
      amountUsd: body.amountUsd,
    });
  }

  @Post('orders/poll')
  async pollStablecoinOrders(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      accountId: account.id,
      items: await this.billingService.pollStablecoinOrders(account.id),
    };
  }

  @Post('orders/:orderId/tx')
  async submitStablecoinTransaction(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Param('orderId') orderId: string,
    @Body() body: SubmitStablecoinTransactionDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.billingService.submitStablecoinTransaction({
      accountId: account.id,
      orderId,
      txHash: body.txHash,
    });
  }

  @Post('orders/:orderId/confirmations')
  async updateStablecoinConfirmations(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Param('orderId') orderId: string,
    @Body() body: UpdateStablecoinConfirmationsDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.billingService.updateStablecoinConfirmations({
      accountId: account.id,
      orderId,
      confirmations: body.confirmations,
    });
  }

  @Post('orders/:orderId/status')
  async updateStablecoinOrderStatus(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Param('orderId') orderId: string,
    @Body() body: UpdateStablecoinOrderStatusDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.billingService.updateStablecoinOrderStatus({
      accountId: account.id,
      orderId,
      status: body.status,
      reviewNote: body.reviewNote,
    });
  }
}
