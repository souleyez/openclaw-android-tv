import AsyncStorage from '@react-native-async-storage/async-storage';
import * as IntentLauncher from 'expo-intent-launcher';
import { getLocales } from 'expo-localization';
import * as Speech from 'expo-speech';
import { Platform } from 'react-native';

type ExpoVoice = Awaited<ReturnType<typeof Speech.getAvailableVoicesAsync>>[number];

const REGION_LANGUAGE_PRIORITY: Record<string, string[]> = {
  CN: ['zh-CN', 'zh'],
  HK: ['zh-HK', 'zh-TW', 'zh-CN', 'zh'],
  MO: ['zh-HK', 'zh-TW', 'zh-CN', 'zh'],
  TW: ['zh-TW', 'zh-HK', 'zh-CN', 'zh'],
  SG: ['zh-CN', 'zh-SG', 'zh'],
};

const ANDROID_TTS_PROMPT_KEY = 'radio-app-android-tts-prompted';

export type SpeechProfile = {
  language: string;
  identifier?: string;
  name?: string;
  region: string;
  foundLocalVoice: boolean;
  platformPolicy: 'android_tts_settings' | 'ios_system_voice' | 'web_browser_voice' | 'default';
  availableVoiceCount: number;
};

export async function resolvePreferredChineseVoice(): Promise<SpeechProfile> {
  const locale = getLocales()[0];
  const region = locale?.regionCode?.toUpperCase() ?? 'CN';
  const preferredLanguages = REGION_LANGUAGE_PRIORITY[region] ?? ['zh-CN', 'zh'];

  try {
    const voices = await Speech.getAvailableVoicesAsync();
    const chineseVoices = voices.filter((voice) =>
      preferredLanguages.some((language) =>
        voice.language.toLowerCase().startsWith(language.toLowerCase()),
      ),
    );

    const bestVoice = pickBestVoice(chineseVoices, preferredLanguages) ?? pickBestVoice(voices, preferredLanguages);

    return {
      language: bestVoice?.language ?? preferredLanguages[0],
      identifier: bestVoice?.identifier,
      name: bestVoice?.name,
      region,
      foundLocalVoice: bestVoice != null,
      platformPolicy: resolvePlatformPolicy(),
      availableVoiceCount: voices.length,
    };
  } catch {
    return {
      language: preferredLanguages[0],
      identifier: undefined,
      name: undefined,
      region,
      foundLocalVoice: false,
      platformPolicy: resolvePlatformPolicy(),
      availableVoiceCount: 0,
    };
  }
}

export async function shouldPromptAndroidTtsSetup(profile: SpeechProfile) {
  if (Platform.OS !== 'android') {
    return false;
  }

  if (profile.foundLocalVoice) {
    return false;
  }

  const prompted = await AsyncStorage.getItem(ANDROID_TTS_PROMPT_KEY);
  return prompted !== '1';
}

export async function markAndroidTtsSetupPrompted() {
  if (Platform.OS !== 'android') {
    return;
  }

  await AsyncStorage.setItem(ANDROID_TTS_PROMPT_KEY, '1');
}

export async function openAndroidTtsSettings() {
  if (Platform.OS !== 'android') {
    return false;
  }

  try {
    await IntentLauncher.startActivityAsync(IntentLauncher.ActivityAction.TTS_SETTINGS);
    return true;
  } catch {
    return false;
  }
}

function resolvePlatformPolicy(): SpeechProfile['platformPolicy'] {
  if (Platform.OS === 'android') {
    return 'android_tts_settings';
  }
  if (Platform.OS === 'ios') {
    return 'ios_system_voice';
  }
  if (Platform.OS === 'web') {
    return 'web_browser_voice';
  }
  return 'default';
}

function pickBestVoice(voices: ExpoVoice[], preferredLanguages: string[]) {
  for (const language of preferredLanguages) {
    const exact = voices.find((voice) => voice.language.toLowerCase() === language.toLowerCase());
    if (exact) {
      return exact;
    }

    const startsWith = voices.find((voice) =>
      voice.language.toLowerCase().startsWith(language.toLowerCase()),
    );
    if (startsWith) {
      return startsWith;
    }
  }

  return voices[0];
}
