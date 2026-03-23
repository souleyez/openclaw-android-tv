import { Injectable } from '@nestjs/common';

import { DeviceService } from '../device/device.service';
import { AssistantLogRecord, StorageService } from '../../shared/storage.service';
import { ApiPoolLeaseService } from './api-pool-lease.service';
import { MinimaxIntentResult, MinimaxProvider } from './minimax.provider';

interface VoiceTurnInput {
  accountId: string;
  text: string;
  locale: string;
  deviceId?: string;
}

interface ControlBlockLogInput {
  accountId: string;
  locale: string;
  deviceId?: string;
  userText: string;
  assistantText: string;
  appId: string;
  action: string;
  route: string;
}

interface DirectTransportLogInput {
  accountId: string;
  locale: string;
  deviceId?: string;
  userText: string;
  assistantText: string;
  appId: string;
  action: string;
  route: string;
  modelProvider: string;
  transportMode: string;
}

interface ModelIntent extends MinimaxIntentResult {}

@Injectable()
export class ModelRouterService {
  constructor(
    private readonly deviceService: DeviceService,
    private readonly minimaxProvider: MinimaxProvider,
    private readonly storageService: StorageService,
    private readonly apiPoolLeaseService: ApiPoolLeaseService,
  ) {}

  async resolveVoiceTurn(input: VoiceTurnInput) {
    const usingConfiguredModel = this.minimaxProvider.isConfigured();
    const intent =
      (await this.minimaxProvider.resolveVoiceTurn(input.text, input.locale)) ??
      this.detectIntent(input.text, input.locale);
    const consumedTokens = this.estimateUsage(input.text, intent.mode);
    await this.deviceService.consumeBootstrapTokens(
      input.accountId,
      input.deviceId,
      consumedTokens,
    );
    const bootstrapTokenRemaining =
      await this.deviceService.getRemainingBootstrapTokens(
        input.accountId,
        input.deviceId,
      );

    const response = {
      requestId: `voice_turn_${Date.now()}`,
      appId: intent.appId,
      action: intent.action,
      queryText: intent.queryText ?? null,
      replyText: intent.replyText,
      assistantText: intent.replyText,
      shouldExecuteLocally: intent.shouldExecuteLocally,
      mode: intent.mode,
      route: intent.route,
      modelProvider: usingConfiguredModel
        ? 'minimax_openai_compatible'
        : 'mock_llm_router',
      confidence: usingConfiguredModel ? 0.92 : 0.58,
      fallbackReason: usingConfiguredModel
        ? null
        : 'model_unconfigured_or_parse_fallback',
      tokenUsage: consumedTokens,
      bootstrapTokenRemaining,
      locale: input.locale,
    };

    await this.storageService.appendLog({
      id: response.requestId,
      accountId: input.accountId,
      kind: 'voice_turn',
      deviceId: input.deviceId,
      locale: input.locale,
      userText: input.text,
      assistantText: response.assistantText,
      appId: response.appId,
      action: response.action,
      queryText: response.queryText,
      mode: response.mode,
      modelProvider: response.modelProvider,
      route: response.route,
      tokenUsage: response.tokenUsage,
      bootstrapTokenRemaining: response.bootstrapTokenRemaining,
      createdAt: new Date().toISOString(),
    });

    return response;
  }

  async respondChat(input: VoiceTurnInput) {
    const usingConfiguredModel = this.minimaxProvider.isConfigured();
    const response =
      (await this.minimaxProvider.respondChat(input.text, input.locale)) ??
      this.buildChatReply(input.text, input.locale);
    const consumedTokens = this.estimateUsage(input.text, 'chat');
    await this.deviceService.consumeBootstrapTokens(
      input.accountId,
      input.deviceId,
      consumedTokens,
    );
    const bootstrapTokenRemaining =
      await this.deviceService.getRemainingBootstrapTokens(
        input.accountId,
        input.deviceId,
      );

    const payload = {
      requestId: `chat_turn_${Date.now()}`,
      assistantText: response,
      mode: 'chat' as const,
      modelProvider: usingConfiguredModel
        ? 'minimax_openai_compatible'
        : 'mock_llm_chat',
      tokenUsage: consumedTokens,
      bootstrapTokenRemaining,
      locale: input.locale,
    };

    await this.storageService.appendLog({
      id: payload.requestId,
      accountId: input.accountId,
      kind: 'chat_turn',
      deviceId: input.deviceId,
      locale: input.locale,
      userText: input.text,
      assistantText: payload.assistantText,
      mode: payload.mode,
      modelProvider: payload.modelProvider,
      tokenUsage: payload.tokenUsage,
      bootstrapTokenRemaining: payload.bootstrapTokenRemaining,
      createdAt: new Date().toISOString(),
    });

    return payload;
  }

  async getRecentLogs(accountId: string): Promise<AssistantLogRecord[]> {
    return this.storageService.getLogs(accountId);
  }

  async logControlBlock(input: ControlBlockLogInput) {
    await this.storageService.appendLog({
      id: `control_block_${Date.now()}`,
      accountId: input.accountId,
      kind: 'voice_turn',
      deviceId: input.deviceId,
      locale: input.locale,
      userText: input.userText,
      assistantText: input.assistantText,
      appId: input.appId,
      action: input.action,
      mode: 'control',
      modelProvider: 'client_policy_guard',
      route: input.route,
      tokenUsage: 0,
      bootstrapTokenRemaining: 0,
      createdAt: new Date().toISOString(),
    });

    return {
      ok: true,
    };
  }

  async logDirectTransport(input: DirectTransportLogInput) {
    await this.storageService.appendLog({
      id: `direct_transport_${Date.now()}`,
      accountId: input.accountId,
      kind: 'voice_turn',
      deviceId: input.deviceId,
      locale: input.locale,
      userText: input.userText,
      assistantText: input.assistantText,
      appId: input.appId,
      action: input.action,
      mode: input.appId === 'assistant' ? 'chat' : 'control',
      modelProvider: input.modelProvider,
      route: input.route,
      transportMode: input.transportMode,
      tokenUsage: 0,
      bootstrapTokenRemaining: 0,
      createdAt: new Date().toISOString(),
    });

    return {
      ok: true,
    };
  }

  private detectIntent(text: string, locale: string): ModelIntent {
    const lowered = text.toLowerCase().trim();
    const appAwareIntent = this.detectAppAwareIntent(text, lowered);
    if (appAwareIntent != null) {
      return appAwareIntent;
    }

    if (lowered.includes('settings') || lowered.includes('system settings')) {
      return this.controlIntent('settings', 'open_app', 'Opening system settings');
    }

    if (
      lowered.includes('cast') ||
      lowered.includes('screen cast') ||
      lowered.includes('project screen') ||
      lowered.includes('mirror screen')
    ) {
      return this.controlIntent('cast', 'open_app', 'Opening screen cast settings');
    }

    if (
      lowered.includes('local file') ||
      lowered.includes('local files') ||
      lowered.includes('downloads') ||
      lowered.includes('usb file') ||
      lowered.includes('play a local video')
    ) {
      return this.controlIntent('local_files', 'open_app', 'Opening local files');
    }

    if (lowered.includes('spotify')) {
      return this.controlIntent('spotify', 'open_app', 'Opening Spotify');
    }

    if (lowered.includes('netflix')) {
      return this.controlIntent('netflix', 'open_app', 'Opening Netflix');
    }

    if (
      lowered.includes('prime video') ||
      lowered.includes('amazon prime') ||
      lowered.includes('primevideo')
    ) {
      return this.controlIntent('prime_video', 'open_app', 'Opening Prime Video');
    }

    if (
      lowered.includes('disney+') ||
      lowered.includes('disney plus') ||
      lowered.includes('disneyplus')
    ) {
      return this.controlIntent('disney_plus', 'open_app', 'Opening Disney+');
    }

    if (lowered.includes('plex')) {
      return this.controlIntent('plex', 'open_app', 'Opening Plex');
    }

    if (lowered.includes('youtube')) {
      return this.controlIntent('youtube', 'open_app', 'Opening YouTube');
    }

    if (lowered.includes('vlc')) {
      return this.controlIntent('vlc', 'open_app', 'Opening VLC');
    }

    if (lowered.includes('pause')) {
      return this.controlIntent('media', 'pause', 'Pausing playback');
    }

    if (lowered.includes('resume') || lowered.includes('continue')) {
      return this.controlIntent('media', 'resume', 'Resuming playback');
    }

    if (lowered.includes('play')) {
      return this.controlIntent('media', 'play', 'Starting playback');
    }

    if (
      lowered.includes('next song') ||
      lowered.includes('next episode') ||
      lowered.includes('skip') ||
      lowered === 'next' ||
      lowered.includes(' next')
    ) {
      return this.controlIntent('media', 'next', 'Skipping to the next item');
    }

    if (
      lowered.includes('previous') ||
      lowered.includes('last song') ||
      lowered.includes('go to previous')
    ) {
      return this.controlIntent(
        'media',
        'previous',
        'Returning to the previous item',
      );
    }

    if (lowered.includes('fast forward') || lowered.includes('forward')) {
      return this.controlIntent('media', 'fast_forward', 'Fast forwarding');
    }

    if (lowered.includes('rewind')) {
      return this.controlIntent('media', 'rewind', 'Rewinding');
    }

    if (lowered.includes('back') || lowered.includes('go back')) {
      return this.controlIntent('system', 'back', 'Going back');
    }

    if (
      lowered.includes('move up') ||
      lowered === 'up' ||
      lowered.includes('go up')
    ) {
      return this.controlIntent('system', 'up', 'Moving up');
    }

    if (
      lowered.includes('move down') ||
      lowered === 'down' ||
      lowered.includes('go down')
    ) {
      return this.controlIntent('system', 'down', 'Moving down');
    }

    if (
      lowered.includes('move left') ||
      lowered === 'left' ||
      lowered.includes('go left')
    ) {
      return this.controlIntent('system', 'left', 'Moving left');
    }

    if (
      lowered.includes('move right') ||
      lowered === 'right' ||
      lowered.includes('go right')
    ) {
      return this.controlIntent('system', 'right', 'Moving right');
    }

    if (
      lowered.includes('select') ||
      lowered.includes('confirm') ||
      lowered == 'ok' ||
      lowered == 'enter'
    ) {
      return this.controlIntent('system', 'select', 'Selecting the current item');
    }

    if (lowered.includes('go home') || lowered == 'home') {
      return this.controlIntent('system', 'home', 'Going to the home screen');
    }

    if (lowered.includes('open menu') || lowered == 'menu') {
      return this.controlIntent('system', 'menu', 'Opening the menu');
    }

    if (
      lowered.includes('volume up') ||
      lowered.includes('turn it up') ||
      lowered.includes('louder')
    ) {
      return this.controlIntent('system', 'volume_up', 'Turning the volume up');
    }

    if (
      lowered.includes('volume down') ||
      lowered.includes('turn it down') ||
      lowered.includes('quieter')
    ) {
      return this.controlIntent('system', 'volume_down', 'Turning the volume down');
    }

    if (
      lowered.includes('mute') ||
      lowered.includes('silence') ||
      lowered.includes('turn off sound')
    ) {
      return this.controlIntent('system', 'mute', 'Muting the volume');
    }

    return {
      appId: 'assistant',
      action: 'unsupported',
      replyText: this.buildChatReply(text, locale),
      shouldExecuteLocally: false,
      mode: 'chat',
      route: 'llm_chat_fallback',
    };
  }

  private detectAppAwareIntent(text: string, lowered: string): ModelIntent | null {
    const targets: Record<string, string> = {
      youtube: 'YouTube',
      netflix: 'Netflix',
      prime_video: 'Prime Video',
      disney_plus: 'Disney+',
      plex: 'Plex',
      vlc: 'VLC',
      spotify: 'Spotify',
    };

    for (const [appId, displayName] of Object.entries(targets)) {
      if (!lowered.includes(appId)) {
        continue;
      }

      const searchQuery = this.extractSearchQuery(text, appId);
      if (searchQuery !== null) {
        return this.controlIntent(
          appId,
          'search',
          `Searching ${displayName} for ${searchQuery}`,
          searchQuery,
        );
      }

      if (lowered.includes('pause')) {
        return this.controlIntent(appId, 'pause', `Pausing ${displayName}`);
      }
      if (lowered.includes('resume') || lowered.includes('continue')) {
        return this.controlIntent(appId, 'resume', `Resuming ${displayName}`);
      }
      if (lowered.includes('play')) {
        return this.controlIntent(appId, 'play', `Playing ${displayName}`);
      }
      if (
        lowered.includes('next') ||
        lowered.includes('skip') ||
        lowered.includes('next episode')
      ) {
        return this.controlIntent(
          appId,
          'next',
          `Skipping forward in ${displayName}`,
        );
      }
      if (lowered.includes('previous')) {
        return this.controlIntent(
          appId,
          'previous',
          `Going to previous item in ${displayName}`,
        );
      }
      if (lowered.includes('fast forward') || lowered.includes('forward')) {
        return this.controlIntent(
          appId,
          'fast_forward',
          `Fast forwarding ${displayName}`,
        );
      }
      if (lowered.includes('rewind')) {
        return this.controlIntent(appId, 'rewind', `Rewinding ${displayName}`);
      }
      if (lowered.includes('back') || lowered.includes('go back')) {
        return this.controlIntent(appId, 'back', `Going back in ${displayName}`);
      }
      if (lowered.includes('move up') || lowered === `up ${appId}`) {
        return this.controlIntent(appId, 'up', `Moving up in ${displayName}`);
      }
      if (lowered.includes('move down') || lowered === `down ${appId}`) {
        return this.controlIntent(appId, 'down', `Moving down in ${displayName}`);
      }
      if (lowered.includes('move left') || lowered === `left ${appId}`) {
        return this.controlIntent(appId, 'left', `Moving left in ${displayName}`);
      }
      if (lowered.includes('move right') || lowered === `right ${appId}`) {
        return this.controlIntent(appId, 'right', `Moving right in ${displayName}`);
      }
      if (
        lowered.includes('select') ||
        lowered.includes('confirm') ||
        lowered === `${appId} ok`
      ) {
        return this.controlIntent(appId, 'select', `Selecting in ${displayName}`);
      }
      if (lowered.includes('home')) {
        return this.controlIntent(appId, 'home', `Going home from ${displayName}`);
      }
      if (lowered.includes('menu')) {
        return this.controlIntent(appId, 'menu', `Opening the menu in ${displayName}`);
      }
      if (
        lowered.includes('volume up') ||
        lowered.includes('louder') ||
        lowered.includes(`raise ${appId} volume`)
      ) {
        return this.controlIntent(appId, 'volume_up', `Turning up ${displayName}`);
      }
      if (
        lowered.includes('volume down') ||
        lowered.includes('quieter') ||
        lowered.includes(`lower ${appId} volume`)
      ) {
        return this.controlIntent(appId, 'volume_down', `Turning down ${displayName}`);
      }
      if (lowered.includes('mute')) {
        return this.controlIntent(appId, 'mute', `Muting ${displayName}`);
      }

      return this.controlIntent(appId, 'open_app', `Opening ${displayName}`);
    }

    return null;
  }

  private extractSearchQuery(text: string, appId: string): string | null {
    const normalized = text.trim();
    const patterns = [
      new RegExp(`search\\s+${appId}\\s+for\\s+(.+)`, 'i'),
      new RegExp(`search\\s+for\\s+(.+)\\s+on\\s+${appId}`, 'i'),
      new RegExp(`${appId}\\s+search\\s+(.+)`, 'i'),
      new RegExp(`find\\s+(.+)\\s+on\\s+${appId}`, 'i'),
    ];

    for (const pattern of patterns) {
      const match = normalized.match(pattern);
      if (match !== null) {
        const query = match[1]?.trim();
        if (query && query.length > 0) {
          return query;
        }
      }
    }

    return null;
  }

  private controlIntent(
    appId: ModelIntent['appId'],
    action: ModelIntent['action'],
    replyText: string,
    queryText?: string,
  ): ModelIntent {
    return {
      appId,
      action,
      queryText,
      replyText,
      shouldExecuteLocally: true,
      mode: 'control',
      route: 'mock_llm_intent_router',
    };
  }

  private buildChatReply(text: string, locale: string): string {
    if (locale.toLowerCase().startsWith('zh')) {
      return `I received your question: "${text}". The current device assistant is already in local conversation mode and can continue helping with TV app control, feature explanations, or short answers.`;
    }

    return `I heard "${text}". The current assistant is in local conversation mode, so I can keep chatting, explain features, or control TV apps when your request maps to a device action.`;
  }

  private estimateUsage(text: string, mode: 'control' | 'chat'): number {
    const base = mode === 'control' ? 18 : 36;
    return Math.max(base, Math.ceil(text.length * 1.4));
  }

  getStatus() {
    return Promise.all([
      this.storageService.getSchemaInfo(),
      this.apiPoolLeaseService.getPoolSnapshot(),
    ]).then(([schema, pool]) => ({
      ...this.minimaxProvider.getStatus(),
      storage: 'sqlite',
      schemaVersion: schema.version,
      apiPool: {
        accounts: pool.length,
        totalApis: pool.reduce((sum, item) => sum + item.totalApis, 0),
        inUseApis: pool.reduce((sum, item) => sum + item.inUseApis, 0),
        idleApis: pool.reduce((sum, item) => sum + item.idleApis, 0),
        leaseTtlMinutes: 20,
        maxActiveLeasesPerDevice: 1,
      },
    }));
  }

  async leaseProviderCredential(input: {
    deviceUserId: string;
    deviceUuid: string;
    provider: string;
  }) {
    return this.apiPoolLeaseService.leaseCredential(input);
  }

  async releaseProviderCredential(leaseId: string) {
    return this.apiPoolLeaseService.releaseLease({ leaseId });
  }
}
