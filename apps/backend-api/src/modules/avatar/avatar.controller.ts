import { Body, Controller, Get, Param, Post } from '@nestjs/common';

import { AvatarService } from './avatar.service';

class UpsertAvatarProfileDto {
  id?: string;
  name!: string;
  avatarLabel!: string;
  gender?: 'female' | 'male' | 'child' | 'senior' | 'neutral';
  ageGroup?: 'youth' | 'adult' | 'senior';
  primaryColorHex?: string;
  secondaryColorHex?: string;
  accentColorHex?: string;
  assetUrl?: string;
  active?: boolean;
}

@Controller('avatars')
export class AvatarController {
  constructor(private readonly avatarService: AvatarService) {}

  @Get()
  async listProfiles() {
    return {
      items: await this.avatarService.listProfiles(),
    };
  }

  @Get('active')
  async getActiveProfile() {
    return this.avatarService.getActiveProfile();
  }

  @Post()
  async upsertProfile(@Body() body: UpsertAvatarProfileDto) {
    return this.avatarService.upsertProfile(body);
  }

  @Post(':id/activate')
  async activateProfile(@Param('id') id: string) {
    return this.avatarService.activateProfile(id);
  }
}
