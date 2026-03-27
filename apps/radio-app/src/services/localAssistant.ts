import type { Station } from '../data/mockRadio';
import {
  buildBootPrompt,
  buildFollowupReplyPrompt,
  detectVoiceIntent,
  type ListenerProfile,
  type VoiceIntent,
} from './radioIntelligence';

export type LocalAssistantContext = {
  profile: ListenerProfile;
  station?: Station;
};

export type LocalAssistantIntent =
  | { route: 'local_command'; intent: Exclude<VoiceIntent, { kind: 'unknown' }> }
  | { route: 'model_or_server'; intent: VoiceIntent };

export type LocalAssistantBridge = {
  provider: 'rules' | 'local_model';
  supportsOnDeviceModel: boolean;
  classifyText: (input: string, context: LocalAssistantContext) => Promise<LocalAssistantIntent>;
  buildBootPrompt: (context: LocalAssistantContext) => string;
  buildFollowupPrompt: (context: LocalAssistantContext) => string;
};

function normalizeAssistantInput(input: string) {
  return input
    .trim()
    .toLowerCase()
    .replace(/[，。！？、,.!?]/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/播放点?音乐|来点?音乐|听点?音乐/g, '\u542c\u6b4c')
    .replace(/播放点?新闻|来点?新闻|听点?新闻/g, '\u542c\u65b0\u95fb')
    .replace(/下一(个|台|首)|切下一个|切台/g, '\u4e0b\u4e00\u4e2a')
    .replace(/以后别放这个|别放这个|不要这个/g, '\u4ee5\u540e\u4e0d\u542c\u8fd9\u4e2a');
}

class RuleBasedLocalAssistantBridge implements LocalAssistantBridge {
  readonly provider = 'rules' as const;
  readonly supportsOnDeviceModel = false;

  async classifyText(input: string): Promise<LocalAssistantIntent> {
    const normalized = normalizeAssistantInput(input);
    const intent = detectVoiceIntent(normalized);
    if (intent.kind === 'unknown') {
      return {
        route: 'model_or_server',
        intent,
      };
    }

    return {
      route: 'local_command',
      intent,
    };
  }

  buildBootPrompt(context: LocalAssistantContext) {
    return buildBootPrompt(context);
  }

  buildFollowupPrompt(context: LocalAssistantContext) {
    return buildFollowupReplyPrompt(context);
  }
}

let singletonBridge: LocalAssistantBridge | null = null;

export function getLocalAssistantBridge(): LocalAssistantBridge {
  if (singletonBridge == null) {
    singletonBridge = new RuleBasedLocalAssistantBridge();
  }

  return singletonBridge;
}
