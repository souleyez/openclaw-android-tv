import { Module } from '@nestjs/common';

import { AuthModule } from '../auth/auth.module';
import { DeviceModule } from '../device/device.module';
import { ModelRouterModule } from '../model-router/model-router.module';
import { ChatController } from './chat.controller';

@Module({
  imports: [AuthModule, DeviceModule, ModelRouterModule],
  controllers: [ChatController],
})
export class ChatModule {}
