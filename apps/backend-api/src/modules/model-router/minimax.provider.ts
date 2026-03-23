import { Injectable, Logger } from '@nestjs/common';

export interface MinimaxIntentResult {
  appId: string;
  queryText?: string;
  action:
    | 'open_app'
    | 'search'
    | 'play'
    | 'pause'
    | 'resume'
    | 'next'
    | 'previous'
    | 'fast_forward'
    | 'rewind'
    | 'back'
    | 'up'
    | 'down'
    | 'left'
    | 'right'
    | 'select'
    | 'home'
    | 'menu'
    | 'volume_up'
    | 'volume_down'
    | 'mute'
    | 'unsupported';
  replyText: string;
  shouldExecuteLocally: boolean;
  mode: 'control' | 'chat';
  route: string;
}

@Injectable()
export class MinimaxProvider {
  private readonly logger = new Logger(MinimaxProvider.name);
  private readonly apiKey = process.env.MINIMAX_API_KEY;
  private readonly baseUrl =
    process.env.MINIMAX_BASE_URL ?? 'https://api.minimaxi.com/v1';
  private readonly model = process.env.MINIMAX_MODEL ?? 'MiniMax-M2.5';

  isConfigured(): boolean {
    return Boolean(this.apiKey);
  }

  getStatus() {
    return {
      provider: this.isConfigured()
        ? 'minimax_openai_compatible'
        : 'mock_llm_router',
      configured: this.isConfigured(),
      baseUrl: this.baseUrl,
      model: this.model,
    };
  }

  async resolveVoiceTurn(
    text: string,
    locale: string,
  ): Promise<MinimaxIntentResult | null> {
    if (!this.apiKey) {
      return null;
    }

    const systemPrompt = [
      'You are an Android TV voice assistant intent router.',
      'Decide whether the user is asking for local device control or normal chat.',
      'Supported appId values: youtube, netflix, prime_video, disney_plus, plex, vlc, spotify, settings, cast, local_files, media, system, assistant.',
      'Supported action values: open_app, search, play, pause, resume, next, previous, fast_forward, rewind, back, up, down, left, right, select, home, menu, volume_up, volume_down, mute, unsupported.',
      'Return strict JSON only with keys: appId, action, queryText, replyText, shouldExecuteLocally, mode, route.',
      'mode must be control or chat.',
      'If request is conversation, set appId=assistant, action=unsupported, shouldExecuteLocally=false, mode=chat.',
      `User locale is ${locale}. Keep replyText in the same language when possible.`,
    ].join(' ');

    return this.postForIntent(systemPrompt, text);
  }

  async respondChat(text: string, locale: string): Promise<string | null> {
    if (!this.apiKey) {
      return null;
    }

    const systemPrompt = [
      'You are OpenClaw, a multilingual assistant for Android TV and low-version Android devices.',
      'Be concise, warm, and helpful.',
      'If the user is asking for TV control, explain the action clearly.',
      `Reply in the user locale when possible. Locale: ${locale}.`,
    ].join(' ');

    try {
      const data = await this.postChat(systemPrompt, text, 0.5);
      return data.choices?.[0]?.message?.content?.trim() ?? null;
    } catch (error) {
      this.logger.warn(`MiniMax chat request failed: ${(error as Error).message}`);
      return null;
    }
  }

  private async postForIntent(
    systemPrompt: string,
    text: string,
  ): Promise<MinimaxIntentResult | null> {
    try {
      const data = await this.postChat(systemPrompt, text, 0.2);
      const content = data.choices?.[0]?.message?.content?.trim();
      if (!content) {
        return null;
      }

      return this.parseIntentResponse(content);
    } catch (error) {
      this.logger.warn(`MiniMax intent request failed: ${(error as Error).message}`);
      return null;
    }
  }

  private async postChat(systemPrompt: string, text: string, temperature: number) {
    const response = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.apiKey}`,
      },
      body: JSON.stringify({
        model: this.model,
        temperature,
        messages: [
          {
            role: 'system',
            content: systemPrompt,
          },
          {
            role: 'user',
            content: text,
          },
        ],
      }),
    });

    if (!response.ok) {
      throw new Error(`status ${response.status}`);
    }

    return (await response.json()) as {
      choices?: Array<{
        message?: {
          content?: string;
        };
      }>;
    };
  }

  private parseIntentResponse(content: string): MinimaxIntentResult | null {
    try {
      const normalized = content
        .replace(/^```json\s*/i, '')
        .replace(/^```\s*/i, '')
        .replace(/\s*```$/i, '');
      const parsed = JSON.parse(normalized) as Partial<MinimaxIntentResult>;

      const action = this.normalizeAction(parsed.action);
      const mode = parsed.mode === 'control' && action !== 'unsupported' ? 'control' : 'chat';
      const shouldExecuteLocally =
        mode === 'control' && parsed.shouldExecuteLocally !== false;

      return {
        appId: parsed.appId ?? 'assistant',
        queryText: parsed.queryText,
        action,
        replyText: parsed.replyText ?? 'I understood your request.',
        shouldExecuteLocally,
        mode,
        route: parsed.route ?? 'minimax_openai_compatible',
      };
    } catch (error) {
      this.logger.warn(`Failed to parse MiniMax JSON response: ${(error as Error).message}`);
      return null;
    }
  }

  private normalizeAction(action: string | undefined): MinimaxIntentResult['action'] {
    switch (action) {
      case 'open_app':
      case 'search':
      case 'play':
      case 'pause':
      case 'resume':
      case 'next':
      case 'previous':
      case 'fast_forward':
      case 'rewind':
      case 'back':
      case 'up':
      case 'down':
      case 'left':
      case 'right':
      case 'select':
      case 'home':
      case 'menu':
      case 'volume_up':
      case 'volume_down':
      case 'mute':
      case 'unsupported':
        return action;
      default:
        return 'unsupported';
    }
  }
}
