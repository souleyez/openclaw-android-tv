import { Module } from '@nestjs/common';

import { RadioModule } from '../radio/radio.module';
import { PlatformIntegrationController } from './platform-integration.controller';
import { PlatformIntegrationService } from './platform-integration.service';

@Module({
  imports: [RadioModule],
  controllers: [PlatformIntegrationController],
  providers: [PlatformIntegrationService],
})
export class PlatformIntegrationModule {}
