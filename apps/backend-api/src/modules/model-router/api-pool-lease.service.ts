import { Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';

import {
  ApiPoolCredentialRecord,
  ApiPoolLeaseRecord,
  StorageService,
} from '../../shared/storage.service';

@Injectable()
export class ApiPoolLeaseService {
  constructor(private readonly storageService: StorageService) {}

  private readonly leaseTtlMinutes = 20;
  private readonly maxActiveLeasesPerDevice = 1;

  async getPoolSnapshot() {
    await this.expireStaleLeases();
    const [accounts, credentials, leases] = await Promise.all([
      this.storageService.listApiPoolAccounts(),
      this.storageService.listApiPoolCredentials(),
      this.storageService.listApiPoolLeases(),
    ]);

    const activeLeases = leases.filter(
      (item) =>
        item.status === 'active' &&
        new Date(item.expiresAt).getTime() > Date.now(),
    );

    return accounts.map((account) => {
      const accountCredentials = credentials.filter(
        (item) => item.accountId === account.id && item.status === 'active',
      );
      const accountLeases = activeLeases.filter(
        (item) => item.accountId === account.id,
      );

      return {
        ...account,
        totalApis: accountCredentials.length,
        inUseApis: accountLeases.length,
        idleApis: Math.max(0, accountCredentials.length - accountLeases.length),
        bootReservedApis: Math.min(accountCredentials.length, 1),
      };
    });
  }

  async importCredentials(input: {
    accountId: string;
    provider: string;
    baseUrl: string;
    model: string;
    rawKeys: string;
  }) {
    const now = new Date().toISOString();
    const keys = input.rawKeys
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);

    const created: ApiPoolCredentialRecord[] = [];
    for (let index = 0; index < keys.length; index += 1) {
      const credential: ApiPoolCredentialRecord = {
        id: `pool_credential_${randomUUID()}`,
        accountId: input.accountId,
        provider: input.provider,
        label: `${input.provider.toLowerCase()}_${index + 1}`,
        baseUrl: input.baseUrl,
        model: input.model,
        apiKey: keys[index],
        status: 'active',
        createdAt: now,
        updatedAt: now,
      };
      await this.storageService.upsertApiPoolCredential(credential);
      created.push(credential);
    }

    return {
      imported: created.length,
      accountId: input.accountId,
      provider: input.provider,
    };
  }

  async leaseCredential(input: {
    deviceUserId: string;
    deviceUuid: string;
    provider: string;
  }) {
    await this.expireStaleLeases();
    const now = Date.now();
    const [accounts, credentials, leases] = await Promise.all([
      this.storageService.listApiPoolAccounts(),
      this.storageService.listApiPoolCredentials(),
      this.storageService.listApiPoolLeases(),
    ]);

    const activeLeases = leases.filter(
      (item) =>
        item.status === 'active' &&
        new Date(item.expiresAt).getTime() > now,
    );

    const reusableLease = activeLeases.find(
      (item) =>
        item.deviceUserId === input.deviceUserId &&
        item.deviceUuid === input.deviceUuid &&
        item.provider.toLowerCase() === input.provider.toLowerCase(),
    );
    if (reusableLease != null) {
      const credential = credentials.find(
        (item) => item.id === reusableLease.credentialId,
      );
      if (credential != null) {
        return this.buildLeasePayload(reusableLease, credential);
      }
    }

    const deviceActiveLeases = activeLeases.filter(
      (item) =>
        item.deviceUserId === input.deviceUserId &&
        item.deviceUuid === input.deviceUuid,
    );
    if (deviceActiveLeases.length >= this.maxActiveLeasesPerDevice) {
      return {
        leaseId: null,
        provider: input.provider,
        denied: true,
        denyReason: 'device_concurrency_limit',
        maxConcurrency: this.maxActiveLeasesPerDevice,
      };
    }

    const candidateAccounts = accounts.filter(
      (item) =>
        item.provider.toLowerCase() === input.provider.toLowerCase() &&
        item.status !== 'expired' &&
        item.status !== 'paused',
    );

    for (const account of candidateAccounts) {
      const accountCredentials = credentials.filter(
        (item) => item.accountId === account.id && item.status === 'active',
      );
      const leasedCredentialIds = new Set(
        activeLeases
          .filter((item) => item.accountId === account.id)
          .map((item) => item.credentialId),
      );
      const freeCredential = accountCredentials.find(
        (item) => !leasedCredentialIds.has(item.id),
      );
      if (freeCredential == null) {
        continue;
      }

      const lease: ApiPoolLeaseRecord = {
        id: `pool_lease_${randomUUID()}`,
        accountId: account.id,
        credentialId: freeCredential.id,
        deviceUserId: input.deviceUserId,
        deviceUuid: input.deviceUuid,
        provider: freeCredential.provider,
        baseUrl: freeCredential.baseUrl,
        model: freeCredential.model,
        status: 'active',
        leasedAt: new Date(now).toISOString(),
        expiresAt: new Date(
          now + this.leaseTtlMinutes * 60 * 1000,
        ).toISOString(),
      };
      await this.storageService.upsertApiPoolLease(lease);
      return this.buildLeasePayload(lease, freeCredential);
    }

    return null;
  }

  async releaseLease(input: { leaseId: string }) {
    await this.expireStaleLeases();
    const leases = await this.storageService.listApiPoolLeases();
    const existing = leases.find((item) => item.id === input.leaseId);
    if (existing == null) {
      return { released: false };
    }

    await this.storageService.upsertApiPoolLease({
      ...existing,
      status: 'released',
      releasedAt: new Date().toISOString(),
    });

    return { released: true };
  }

  private buildLeasePayload(
    lease: ApiPoolLeaseRecord,
    credential: ApiPoolCredentialRecord,
  ) {
    return {
      leaseId: lease.id,
      provider: lease.provider,
      baseUrl: lease.baseUrl,
      model: lease.model,
      apiKey: credential.apiKey,
      expiresAt: lease.expiresAt,
      leaseMode: 'direct_provider_temporary',
      maxConcurrency: 1,
    };
  }

  private async expireStaleLeases(): Promise<void> {
    const leases = await this.storageService.listApiPoolLeases();
    const now = Date.now();

    for (const lease of leases) {
      if (
        lease.status === 'active' &&
        new Date(lease.expiresAt).getTime() <= now
      ) {
        await this.storageService.upsertApiPoolLease({
          ...lease,
          status: 'expired',
          releasedAt: new Date(now).toISOString(),
        });
      }
    }
  }
}
