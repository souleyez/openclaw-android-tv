export type LocalVoiceRecognitionResult = {
  transcript: string;
  isFinal: boolean;
};

export interface LocalVoiceAdapter {
  isAvailable(): Promise<boolean>;
  start(onResult: (result: LocalVoiceRecognitionResult) => void): Promise<void>;
  stop(): Promise<void>;
}

export class PlaceholderLocalVoiceAdapter implements LocalVoiceAdapter {
  async isAvailable() {
    return false;
  }

  async start() {
    throw new Error('Local ASR adapter is not connected yet.');
  }

  async stop() {
    return;
  }
}

let singletonAdapter: LocalVoiceAdapter | null = null;

export function getLocalVoiceAdapter(): LocalVoiceAdapter {
  if (singletonAdapter == null) {
    singletonAdapter = new PlaceholderLocalVoiceAdapter();
  }

  return singletonAdapter;
}
