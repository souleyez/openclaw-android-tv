import { Injectable, NotFoundException } from '@nestjs/common';

import {
  DeviceTokenUsageEventRecord,
  DeviceShareBindingRecord,
  StorageService,
} from '../../shared/storage.service';

export interface RegisteredDeviceRecord {
  id: string;
  accountId: string;
  deviceUuid: string;
  deviceName: string;
  androidVersion: string;
  isAndroidTv: boolean;
  status: string;
  bindingStatus: string;
  bootstrapTokenGrant: number;
  bootstrapTokenRemaining: number;
}

export interface RegisterDeviceInput {
  deviceUuid: string;
  deviceName: string;
  androidVersion: string;
  isAndroidTv: boolean;
}

export interface CreateDeviceShareInput {
  ownerAccountId: string;
  sharedAccountId: string;
  deviceUuid: string;
  bindingScope: DeviceShareBindingRecord['bindingScope'];
}

export interface SharePolicyRule {
  scope: DeviceShareBindingRecord['bindingScope'];
  canUseVoiceControl: boolean;
  canRunPlaybackActions: boolean;
  canOpenApps: boolean;
  canManageBilling: boolean;
  canManageShares: boolean;
}

export interface ControlAuthorizationResult {
  allowed: boolean;
  bindingScope: DeviceShareBindingRecord['bindingScope'];
  appId: string;
  action: string;
  reason: string;
  policy: SharePolicyRule;
}

export interface DeviceTokenUsageSummary {
  todayConsumed: number;
  totalConsumed: number;
}

@Injectable()
export class DeviceService {
  private readonly bootstrapTokenGrant = 5000;
  private readonly seedDevices: RegisteredDeviceRecord[] = [
    {
      id: 'device_device_demo_android_tv',
      accountId: 'user_demo',
      deviceUuid: 'device_demo_android_tv',
      deviceName: 'Living Room TV',
      androidVersion: '9',
      isAndroidTv: true,
      status: 'active',
      bindingStatus: 'bound',
      bootstrapTokenGrant: this.bootstrapTokenGrant,
      bootstrapTokenRemaining: 4872,
    },
    {
      id: 'device_device_demo_speaker',
      accountId: 'user_demo',
      deviceUuid: 'device_demo_speaker',
      deviceName: 'Bedroom Speaker',
      androidVersion: '11',
      isAndroidTv: false,
      status: 'idle',
      bindingStatus: 'shared',
      bootstrapTokenGrant: this.bootstrapTokenGrant,
      bootstrapTokenRemaining: 5000,
    },
  ];

  constructor(private readonly storageService: StorageService) {}

  async listDevices(accountId: string): Promise<RegisteredDeviceRecord[]> {
    const devices = await this.storageService.getDevices(accountId);
    if (devices.length > 0) {
      return devices;
    }

    const seeded = this.seedDevices.map((device) => ({
      ...device,
      accountId,
    }));
    await this.storageService.saveDevices(accountId, seeded);
    return seeded;
  }

  async registerDevice(
    accountId: string,
    input: RegisterDeviceInput,
  ): Promise<RegisteredDeviceRecord> {
    const devices = await this.listDevices(accountId);
    const existing = devices.find((device) => device.deviceUuid === input.deviceUuid);

    let updatedDevices: RegisteredDeviceRecord[];
    let result: RegisteredDeviceRecord;

    if (existing) {
      result = {
        ...existing,
        accountId,
        ...input,
        status: 'active',
        bindingStatus: 'bound',
      };
      updatedDevices = devices.map((device) =>
        device.deviceUuid === input.deviceUuid ? result : device,
      );
    } else {
      result = {
        id: `device_${input.deviceUuid}`,
        accountId,
        status: 'active',
        bindingStatus: 'bound',
        bootstrapTokenGrant: this.bootstrapTokenGrant,
        bootstrapTokenRemaining: this.bootstrapTokenGrant,
        ...input,
      };
      updatedDevices = [result, ...devices];
    }

    await this.storageService.saveDevices(accountId, updatedDevices);
    return result;
  }

  async consumeBootstrapTokens(
    accountId: string,
    deviceUuid: string | undefined,
    amount: number,
  ): Promise<number> {
    if (!deviceUuid) {
      return 0;
    }

    const devices = await this.listDevices(accountId);
    const device = devices.find((item) => item.deviceUuid == deviceUuid);
    if (!device) {
      return 0;
    }

    const consumed = Math.max(0, Math.min(device.bootstrapTokenRemaining, amount));
    const updatedDevices = devices.map((item) =>
      item.deviceUuid == deviceUuid
        ? {
            ...item,
            bootstrapTokenRemaining: item.bootstrapTokenRemaining - consumed,
          }
        : item,
    );
    await this.storageService.saveDevices(accountId, updatedDevices);
    if (consumed > 0) {
      const updatedDevice = updatedDevices.find((item) => item.deviceUuid === deviceUuid);
      if (updatedDevice != null) {
        await this.storageService.appendDeviceTokenUsageEvent({
          id: `device_token_usage_${Date.now()}_${deviceUuid}`,
          accountId,
          deviceUuid,
          amountConsumed: consumed,
          triggerSource: 'voice_turn',
          bootstrapTokenRemaining: updatedDevice.bootstrapTokenRemaining,
          createdAt: new Date().toISOString(),
        });
      }
    }
    return consumed;
  }

  async getRemainingBootstrapTokens(
    accountId: string,
    deviceUuid: string | undefined,
  ): Promise<number> {
    if (!deviceUuid) {
      return 0;
    }

    const devices = await this.listDevices(accountId);
    return (
      devices.find((device) => device.deviceUuid == deviceUuid)
        ?.bootstrapTokenRemaining ?? 0
    );
  }

  async listShareBindings(
    ownerAccountId: string,
  ): Promise<DeviceShareBindingRecord[]> {
    return this.storageService.getDeviceShareBindings(ownerAccountId);
  }

  async listTokenUsageEvents(
    accountId: string,
    deviceUuid?: string,
  ): Promise<DeviceTokenUsageEventRecord[]> {
    return this.storageService.getDeviceTokenUsageEvents(accountId, deviceUuid);
  }

  async getTokenUsageSummary(
    accountId: string,
    deviceUuid?: string,
  ): Promise<DeviceTokenUsageSummary> {
    const events = await this.listTokenUsageEvents(accountId, deviceUuid);
    const todayPrefix = new Date().toISOString().slice(0, 10);

    return {
      todayConsumed: events
          .filter((event) => event.createdAt.startsWith(todayPrefix))
          .reduce((sum, event) => sum + event.amountConsumed, 0),
      totalConsumed: events.reduce((sum, event) => sum + event.amountConsumed, 0),
    };
  }

  async getShareScopeSummary(ownerAccountId: string): Promise<Record<string, number>> {
    const bindings = await this.listShareBindings(ownerAccountId);
    return bindings.reduce<Record<string, number>>((summary, binding) => {
      summary[binding.bindingScope] = (summary[binding.bindingScope] ?? 0) + 1;
      return summary;
    }, {});
  }

  getSharePolicyRules(): SharePolicyRule[] {
    return [
      {
        scope: 'household',
        canUseVoiceControl: true,
        canRunPlaybackActions: true,
        canOpenApps: true,
        canManageBilling: false,
        canManageShares: false,
      },
      {
        scope: 'api_key',
        canUseVoiceControl: true,
        canRunPlaybackActions: true,
        canOpenApps: false,
        canManageBilling: false,
        canManageShares: false,
      },
    ];
  }

  authorizeControlAction(params: {
    bindingScope: DeviceShareBindingRecord['bindingScope'];
    appId: string;
    action: string;
  }): ControlAuthorizationResult {
    const policy =
      this.getSharePolicyRules().find((item) => item.scope === params.bindingScope) ??
      this.getSharePolicyRules()[0];

    const isAppLaunchAction =
      params.action === 'open_app' || params.action === 'search';
    const isPlaybackAction = !isAppLaunchAction;

    if (isAppLaunchAction && !policy.canOpenApps) {
      return {
        allowed: false,
        bindingScope: params.bindingScope,
        appId: params.appId,
        action: params.action,
        reason: `${params.bindingScope} scope cannot launch apps`,
        policy,
      };
    }

    if (isPlaybackAction && !policy.canRunPlaybackActions) {
      return {
        allowed: false,
        bindingScope: params.bindingScope,
        appId: params.appId,
        action: params.action,
        reason: `${params.bindingScope} scope cannot run playback controls`,
        policy,
      };
    }

    return {
      allowed: true,
      bindingScope: params.bindingScope,
      appId: params.appId,
      action: params.action,
      reason: 'authorized',
      policy,
    };
  }

  async createShareBinding(
    input: CreateDeviceShareInput,
  ): Promise<DeviceShareBindingRecord> {
    const devices = await this.listDevices(input.ownerAccountId);
    const device = devices.find((item) => item.deviceUuid === input.deviceUuid);
    if (!device) {
      throw new NotFoundException(`Device ${input.deviceUuid} is not registered`);
    }

    const existingBindings = await this.listShareBindings(input.ownerAccountId);
    const existing = existingBindings.find(
      (binding) =>
        binding.sharedAccountId === input.sharedAccountId &&
        binding.deviceUuid === input.deviceUuid &&
        binding.bindingScope === input.bindingScope,
    );

    const binding: DeviceShareBindingRecord =
      existing ?? {
        id: `share_${input.deviceUuid}_${input.sharedAccountId}_${input.bindingScope}`,
        ownerAccountId: input.ownerAccountId,
        sharedAccountId: input.sharedAccountId,
        deviceUuid: input.deviceUuid,
        bindingScope: input.bindingScope,
        status: 'active',
        createdAt: new Date().toISOString(),
      };

    await this.storageService.appendDeviceShareBinding(binding);
    return binding;
  }
}
