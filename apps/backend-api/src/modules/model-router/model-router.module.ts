import { Module } from '@nestjs/common';

import { DeviceModule } from '../device/device.module';
import { AuthModule } from '../auth/auth.module';
import { StorageModule } from '../../shared/storage.module';
import { ApiPoolLeaseService } from './api-pool-lease.service';
import { MinimaxProvider } from './minimax.provider';
import { ModelRouterController } from './model-router.controller';
import { ModelRouterService } from './model-router.service';

@Module({
  imports: [AuthModule, DeviceModule, StorageModule],
  controllers: [ModelRouterController],
  providers: [ModelRouterService, MinimaxProvider, ApiPoolLeaseService],
  exports: [ModelRouterService],
})
export class ModelRouterModule {}
