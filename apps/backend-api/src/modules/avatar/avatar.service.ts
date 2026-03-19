import { Injectable, NotFoundException } from '@nestjs/common';

import { AvatarProfileRecord, StorageService } from '../../shared/storage.service';

export interface UpsertAvatarProfileInput {
  id?: string;
  name: string;
  avatarLabel: string;
  gender?: AvatarProfileRecord['gender'];
  ageGroup?: AvatarProfileRecord['ageGroup'];
  primaryColorHex?: string;
  secondaryColorHex?: string;
  accentColorHex?: string;
  assetUrl?: string;
  active?: boolean;
}

@Injectable()
export class AvatarService {
  constructor(private readonly storageService: StorageService) {}

  async listProfiles(): Promise<AvatarProfileRecord[]> {
    const profiles = await this.storageService.getAvatarProfiles();
    return profiles;
  }

  async getActiveProfile(): Promise<AvatarProfileRecord> {
    const active = await this.storageService.getActiveAvatarProfile();
    if (active != null) {
      return active;
    }

    const profiles = await this.listProfiles();
    if (profiles.length === 0) {
      throw new NotFoundException('No avatar profiles configured');
    }

    return profiles[0];
  }

  async upsertProfile(input: UpsertAvatarProfileInput): Promise<AvatarProfileRecord> {
    const now = new Date().toISOString();
    const currentProfiles = await this.listProfiles();
    const existing = input.id == null
      ? undefined
      : currentProfiles.find((profile) => profile.id === input.id);

    const profile: AvatarProfileRecord = {
      id: input.id ?? this.buildProfileId(input.name),
      name: input.name,
      avatarLabel: input.avatarLabel,
      gender: input.gender ?? existing?.gender ?? 'neutral',
      ageGroup: input.ageGroup ?? existing?.ageGroup ?? 'adult',
      primaryColorHex: input.primaryColorHex ?? existing?.primaryColorHex ?? '#6AE6D8',
      secondaryColorHex: input.secondaryColorHex ?? existing?.secondaryColorHex ?? '#12656A',
      accentColorHex: input.accentColorHex ?? existing?.accentColorHex ?? '#B0FFF4',
      assetUrl: input.assetUrl ?? existing?.assetUrl,
      active: input.active ?? existing?.active ?? currentProfiles.length === 0,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };

    await this.storageService.appendAvatarProfile(profile);
    return profile;
  }

  async activateProfile(id: string): Promise<AvatarProfileRecord> {
    const profiles = await this.listProfiles();
    const profile = profiles.find((item) => item.id === id);
    if (profile == null) {
      throw new NotFoundException(`Avatar profile ${id} not found`);
    }

    await this.storageService.activateAvatarProfile(id);
    return {
      ...profile,
      active: true,
      updatedAt: new Date().toISOString(),
    };
  }

  private buildProfileId(name: string): string {
    const slug = name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
    return `avatar_${slug || Date.now().toString()}`;
  }
}
