import { Injectable, NotFoundException } from '@nestjs/common';

import {
  BillingPlanRecord,
  StablecoinPaymentOrderRecord,
  StorageService,
} from '../../shared/storage.service';
import { WalletService } from '../wallet/wallet.service';

export interface CreateStablecoinOrderInput {
  accountId: string;
  stablecoinSymbol: 'USDC' | 'USDT';
  chain: 'Polygon' | 'Base' | 'TRON' | 'BSC';
  amountUsd: number;
}

export interface SubmitStablecoinTransactionInput {
  accountId: string;
  orderId: string;
  txHash: string;
}

export interface UpdateStablecoinConfirmationsInput {
  accountId: string;
  orderId: string;
  confirmations: number;
}

export interface UpdateStablecoinOrderStatusInput {
  accountId: string;
  orderId: string;
  status: 'failed' | 'reviewing' | 'expired';
  reviewNote?: string;
}

@Injectable()
export class BillingService {
  constructor(
    private readonly storageService: StorageService,
    private readonly walletService: WalletService,
  ) {}

  async listPaymentMethods() {
    return [
      {
        code: 'stablecoin_usdc',
        type: 'stablecoin',
        stablecoinSymbol: 'USDC',
        chains: ['Polygon', 'Base'],
        priority: 1,
        enabled: true,
      },
      {
        code: 'stablecoin_usdt',
        type: 'stablecoin',
        stablecoinSymbol: 'USDT',
        chains: ['TRON', 'BSC'],
        priority: 2,
        enabled: true,
      },
      {
        code: 'credit_card',
        type: 'credit_card',
        priority: 99,
        enabled: false,
      },
    ];
  }

  async listPlans(): Promise<BillingPlanRecord[]> {
    return this.storageService.getBillingPlans();
  }

  async getPlanByCode(code: string): Promise<BillingPlanRecord | undefined> {
    const plans = await this.listPlans();
    return plans.find((plan) => plan.code === code);
  }

  async listStablecoinOrders(
    accountId: string,
  ): Promise<StablecoinPaymentOrderRecord[]> {
    await this.expireOverdueOrders(accountId);
    return this.storageService.getStablecoinOrders(accountId);
  }

  async createStablecoinOrder(
    input: CreateStablecoinOrderInput,
  ): Promise<StablecoinPaymentOrderRecord> {
    const order: StablecoinPaymentOrderRecord = {
      id: `order_${Date.now()}`,
      accountId: input.accountId,
      paymentMethod: 'stablecoin',
      stablecoinSymbol: input.stablecoinSymbol,
      chain: input.chain,
      walletAddress: this.resolveDepositWallet(input.chain, input.stablecoinSymbol),
      amountUsd: input.amountUsd,
      amountToken: input.amountUsd,
      status: 'pending',
      confirmations: 0,
      expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    await this.storageService.appendStablecoinOrder(order);
    return order;
  }

  async submitStablecoinTransaction(
    input: SubmitStablecoinTransactionInput,
  ): Promise<StablecoinPaymentOrderRecord> {
    const order = await this.requireStablecoinOrder(input.accountId, input.orderId);
    const updated: StablecoinPaymentOrderRecord = {
      ...order,
      txHash: input.txHash,
      status: 'confirming',
      updatedAt: new Date().toISOString(),
    };

    await this.storageService.appendStablecoinOrder(updated);
    return updated;
  }

  async updateStablecoinConfirmations(
    input: UpdateStablecoinConfirmationsInput,
  ): Promise<StablecoinPaymentOrderRecord> {
    const order = await this.requireStablecoinOrder(input.accountId, input.orderId);
    const normalizedConfirmations = Math.max(0, input.confirmations);
    const nextStatus =
      normalizedConfirmations >= this.requiredConfirmations(order.chain)
        ? 'confirmed'
        : normalizedConfirmations > 0 || order.txHash != null
            ? 'confirming'
            : 'pending';

    const updated: StablecoinPaymentOrderRecord = {
      ...order,
      confirmations: normalizedConfirmations,
      status: nextStatus,
      updatedAt: new Date().toISOString(),
    };

    await this.storageService.appendStablecoinOrder(updated);

    if (updated.status === 'confirmed') {
      await this.walletService.grantStablecoinTopup({
        accountId: updated.accountId,
        orderId: updated.id,
        amountToken: updated.amountToken,
        stablecoinSymbol: updated.stablecoinSymbol,
        chain: updated.chain,
      });
    }

    return updated;
  }

  async updateStablecoinOrderStatus(
    input: UpdateStablecoinOrderStatusInput,
  ): Promise<StablecoinPaymentOrderRecord> {
    const order = await this.requireStablecoinOrder(input.accountId, input.orderId);
    const updated: StablecoinPaymentOrderRecord = {
      ...order,
      status: input.status,
      reviewNote: input.reviewNote ?? order.reviewNote,
      updatedAt: new Date().toISOString(),
    };

    await this.storageService.appendStablecoinOrder(updated);
    return updated;
  }

  async pollStablecoinOrders(
    accountId: string,
  ): Promise<StablecoinPaymentOrderRecord[]> {
    const orders = await this.listStablecoinOrders(accountId);

    for (const order of orders) {
      if (order.txHash == null || order.status !== 'confirming') {
        continue;
      }

      const target = this.requiredConfirmations(order.chain);
      const increment = order.chain === 'TRON' ? 3 : 2;
      const nextConfirmations =
        order.confirmations >= target
          ? target
          : Math.min(target, order.confirmations + increment);

      await this.updateStablecoinConfirmations({
        accountId,
        orderId: order.id,
        confirmations: nextConfirmations,
      });
    }

    return this.listStablecoinOrders(accountId);
  }

  private async requireStablecoinOrder(
    accountId: string,
    orderId: string,
  ): Promise<StablecoinPaymentOrderRecord> {
    await this.expireOverdueOrders(accountId);
    const order = await this.storageService.getStablecoinOrderById(accountId, orderId);
    if (order == null) {
      throw new NotFoundException(`Stablecoin order ${orderId} was not found`);
    }
    return order;
  }

  private async expireOverdueOrders(accountId: string): Promise<void> {
    const orders = await this.storageService.getStablecoinOrders(accountId);
    const now = Date.now();

    for (const order of orders) {
      if (
        (order.status === 'pending' || order.status === 'reviewing') &&
        order.expiresAt != null &&
        Date.parse(order.expiresAt) < now
      ) {
        await this.storageService.appendStablecoinOrder({
          ...order,
          status: 'expired',
          reviewNote: order.reviewNote ?? 'Order expired before settlement',
          updatedAt: new Date().toISOString(),
        });
      }
    }
  }

  private requiredConfirmations(chain: string): number {
    switch (chain) {
      case 'TRON':
        return 12;
      case 'Polygon':
      case 'Base':
      case 'BSC':
      default:
        return 8;
    }
  }

  private resolveDepositWallet(
    chain: CreateStablecoinOrderInput['chain'],
    stablecoinSymbol: CreateStablecoinOrderInput['stablecoinSymbol'],
  ): string {
    return `demo_${stablecoinSymbol.toLowerCase()}_${chain.toLowerCase()}_wallet`;
  }
}
