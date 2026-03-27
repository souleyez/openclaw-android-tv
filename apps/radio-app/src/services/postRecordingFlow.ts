type UploadBroadcastInput = {
  audioUri: string;
  stationId: string;
  durationMs?: number;
  title: string;
};

export async function uploadCapturedBroadcast(params: {
  audioUri?: string | null;
  durationMs?: number | null;
  stationId: string;
  setUploading: (value: boolean) => void;
  uploadBroadcast: (input: UploadBroadcastInput) => Promise<unknown>;
  refreshBroadcastTimeline: () => void;
}) {
  if (!params.audioUri) {
    return;
  }

  params.setUploading(true);
  try {
    await params.uploadBroadcast({
      audioUri: params.audioUri,
      stationId: params.stationId,
      durationMs: params.durationMs ?? undefined,
      title: 'voice message',
    });
    params.refreshBroadcastTimeline();
  } finally {
    params.setUploading(false);
  }
}

export async function runPostRecordingAssistant(params: {
  hasLease: boolean;
  setAiBusy: (value: boolean) => void;
  chatWithLeasedModel: () => Promise<string | null | undefined>;
  playAiBroadcast: (text: string, resumeBroadcast: boolean) => Promise<void>;
  startRadio: () => void;
  fallbackReply?: string;
}) {
  if (!params.hasLease) {
    params.startRadio();
    return;
  }

  params.setAiBusy(true);
  try {
    const reply = (await params.chatWithLeasedModel()) || params.fallbackReply || 'received.';
    await params.playAiBroadcast(reply, true);
  } catch {
    params.startRadio();
  } finally {
    params.setAiBusy(false);
  }
}
