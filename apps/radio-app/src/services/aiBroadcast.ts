import type { BroadcastItem } from '../data/mockRadio';
import { fetchBroadcastById } from './radioApi';

export async function waitForAiBroadcastAudio(params: {
  broadcastId: string;
  attempts: number;
  intervalMs: number;
  sleep: (ms: number) => Promise<unknown>;
  onProgress?: (item: BroadcastItem) => void;
}) {
  for (let attempt = 0; attempt < params.attempts; attempt += 1) {
    await params.sleep(params.intervalMs);
    try {
      const next = await fetchBroadcastById(params.broadcastId);
      params.onProgress?.(next);
      if (next.audioUri || next.status === 'failed') {
        return next;
      }
    } catch {
      return null;
    }
  }

  return null;
}
