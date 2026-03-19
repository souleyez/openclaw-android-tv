import { Injectable } from '@nestjs/common';

import { StorageService, WalletLedgerRecord } from '../../shared/storage.service';

@Injectable()
export class WalletService {
  constructor(private readonly storageService: StorageService) {}

  async getLedger(accountId: string): Promise<WalletLedgerRecord[]> {
    return this.storageService.getWalletLedger(accountId);
  }

  async getTokenBalance(accountId: string): Promise<number> {
    const ledger = await this.getLedger(accountId);
    return ledger
        .filter((entry) => entry.currency === 'token')
        .reduce((sum, entry) => sum + entry.amount, 0);
  }

  async grantStablecoinTopup(params: {
    accountId: string;
    orderId: string;
    amountToken: number;
    stablecoinSymbol: string;
    chain: string;
  }): Promise<WalletLedgerRecord> {
    const ledger = await this.getLedger(params.accountId);
    const existing = ledger.find(
      (entry) =>
        entry.referenceId === params.orderId &&
        entry.entryType === 'topup' &&
        entry.currency === 'token',
    );

    if (existing != null) {
      return existing;
    }

    const entry: WalletLedgerRecord = {
      id: `ledger_${params.orderId}`,
      accountId: params.accountId,
      entryType: 'topup',
      amount: params.amountToken,
      currency: 'token',
      source: 'stablecoin_settlement',
      referenceId: params.orderId,
      note: `${params.stablecoinSymbol} on ${params.chain} settled to token balance`,
      createdAt: new Date().toISOString(),
    };

    await this.storageService.appendWalletLedger(entry);
    return entry;
  }
}
