import { Module } from '@nestjs/common';

import { StorageModule } from '../../shared/storage.module';
import { AuthModule } from '../auth/auth.module';
import { DeviceController } from './device.controller';
import { DeviceService } from './device.service';

@Module({
  imports: [StorageModule, AuthModule],
  controllers: [DeviceController],
  providers: [DeviceService],
  exports: [DeviceService],
})
export class DeviceModule {}
