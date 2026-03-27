import { Module } from '@nestjs/common';

import { AuthModule } from '../auth/auth.module';
import { StorageModule } from '../../shared/storage.module';
import { RadioController } from './radio.controller';
import { RadioTaskQueueService } from './radio-task-queue.service';
import { RadioService } from './radio.service';

@Module({
  imports: [AuthModule, StorageModule],
  controllers: [RadioController],
  providers: [RadioService, RadioTaskQueueService],
  exports: [RadioService],
})
export class RadioModule {}
