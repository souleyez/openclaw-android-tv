import {
  Body,
  Controller,
  Get,
  Headers,
  Header,
  Param,
  ParseIntPipe,
  Post,
  Query,
  Req,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';

import { AuthService } from '../auth/auth.service';
import { RadioService } from './radio.service';

class UploadBroadcastDto {
  stationId?: string;
  title?: string;
  sourceKind?: 'user' | 'ai' | 'system';
  textTranscript?: string;
  durationMs?: string;
}

class AiRespondDto {
  stationId?: string;
  text!: string;
  locale?: string;
}

@Controller('radio')
export class RadioController {
  constructor(
    private readonly radioService: RadioService,
    private readonly authService: AuthService,
  ) {}

  @Get('stations')
  @Header('Cache-Control', 'public, max-age=120, stale-while-revalidate=900')
  async listStations(
    @Headers('x-client-country') clientCountry?: string,
    @Headers('x-client-region') clientRegion?: string,
    @Headers('x-client-preferred-mode') preferredMode?: 'music' | 'news' | 'any',
    @Headers('x-client-language') preferredLanguage?: string,
  ) {
    return this.radioService.listStations({
      countryCode: clientCountry,
      regionCode: clientRegion,
      preferredMode,
      preferredLanguage,
    });
  }

  @Get('stations/:id')
  async getStation(@Param('id') id: string) {
    return this.radioService.getStation(id);
  }

  @Get('broadcasts')
  @Header('Cache-Control', 'public, max-age=15, stale-while-revalidate=120')
  async listBroadcasts(
    @Req() request: { protocol: string; get: (name: string) => string | undefined },
    @Headers('x-client-country') clientCountry?: string,
    @Headers('x-client-region') clientRegion?: string,
    @Headers('x-client-platform') clientPlatform?: string,
    @Query('stationId') stationId?: string,
    @Query('limit', new ParseIntPipe({ optional: true })) limit?: number,
  ) {
    const records = await this.radioService.listBroadcasts({
      stationId,
      limit,
      countryCode: clientCountry,
      regionCode: clientRegion,
      platform: clientPlatform,
    });
    return {
      items: records.map((record) => this.toBroadcastResponse(record, request)),
    };
  }

  @Get('broadcasts/:id')
  @Header('Cache-Control', 'no-store')
  async getBroadcast(
    @Req() request: { protocol: string; get: (name: string) => string | undefined },
    @Param('id') id: string,
  ) {
    const record = await this.radioService.getBroadcast(id);
    return this.toBroadcastResponse(record, request);
  }

  @Post('broadcasts/upload')
  @UseInterceptors(FileInterceptor('audio'))
  async uploadBroadcast(
    @Req() request: { protocol: string; get: (name: string) => string | undefined },
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @UploadedFile() file: Express.Multer.File | undefined,
    @Body() body: UploadBroadcastDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });

    const record = await this.radioService.createBroadcast({
      accountId: account.id,
      stationId: body.stationId,
      title: body.title,
      sourceKind: body.sourceKind ?? 'user',
      textTranscript: body.textTranscript,
      durationMs: body.durationMs == null ? undefined : Number(body.durationMs),
      audioBuffer: file?.buffer,
      originalFilename: file?.originalname,
    });

    return this.toBroadcastResponse(record, request);
  }

  @Post('ai/respond')
  async createAiResponse(
    @Req() request: { protocol: string; get: (name: string) => string | undefined },
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: AiRespondDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });

    const record = await this.radioService.createAiBroadcast({
      accountId: account.id,
      stationId: body.stationId,
      text: body.text.trim(),
      locale: body.locale,
    });

    return this.toBroadcastResponse(record, request);
  }

  private toBroadcastResponse(
    record: {
      id: string;
      stationId?: string;
      accountId: string;
      title: string;
      sourceKind: 'user' | 'ai' | 'system';
      textTranscript?: string;
      audioPath?: string;
      durationMs: number;
      status: 'uploaded' | 'ready' | 'failed';
      createdAt: string;
      updatedAt: string;
    },
    request: { protocol: string; get: (name: string) => string | undefined },
  ) {
    return {
      ...record,
      audioUrl:
        record.audioPath == null
          ? null
          : `${request.protocol}://${request.get('host')}/uploads/${record.audioPath}`,
    };
  }
}
