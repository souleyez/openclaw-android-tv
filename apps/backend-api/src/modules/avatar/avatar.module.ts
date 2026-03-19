import { Module } from '@nestjs/common';

import { StorageModule } from '../../shared/storage.module';
import { AvatarController } from './avatar.controller';
import { AvatarService } from './avatar.service';

@Module({
  imports: [StorageModule],
  controllers: [AvatarController],
  providers: [AvatarService],
  exports: [AvatarService],
})
export class AvatarModule {}
