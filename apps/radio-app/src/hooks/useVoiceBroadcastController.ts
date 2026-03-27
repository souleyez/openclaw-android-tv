import { type MutableRefObject } from 'react';
import { Alert } from 'react-native';
import {
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  type AudioPlayer,
  type AudioRecorder,
} from 'expo-audio';

import type { BroadcastItem, Station } from '../data/mockRadio';
import { waitForAiBroadcastAudio } from '../services/aiBroadcast';
import { chatWithLeasedModel } from '../services/directModel';
import { canStartRecording } from '../services/interactionGuards';
import type { LocalAssistantBridge } from '../services/localAssistant';
import type { BootstrapSessionPayload } from '../services/modelLease';
import { uploadCapturedBroadcast, runPostRecordingAssistant } from '../services/postRecordingFlow';
import { createAiBroadcast, uploadBroadcast } from '../services/radioApi';
import type { ListenerProfile } from '../services/radioIntelligence';
import { finishAiVoicePlayback, prepareForVoiceCapture, startAiVoicePlayback } from '../services/voiceSession';

export function useVoiceBroadcastController(params: {
  radioPlayer: AudioPlayer;
  aiPlayer: AudioPlayer;
  recorder: AudioRecorder;
  recorderState: {
    isRecording: boolean;
    url?: string | null;
    durationMillis?: number | null;
  };
  recordingStarted: boolean;
  isPreparingCapture: boolean;
  isAiSpeaking: boolean;
  isAiBusy: boolean;
  isUploadingBroadcast: boolean;
  isPlaying: boolean;
  currentStation: Station;
  preferredSpeechLanguage: string;
  modelSession: BootstrapSessionPayload | null;
  localAssistant: LocalAssistantBridge;
  listenerProfile: ListenerProfile;
  recordStopLockRef: MutableRefObject<boolean>;
  shouldResumeRadioAfterAiRef: MutableRefObject<boolean>;
  setRecordingStarted: (value: boolean) => void;
  setIsPreparingCapture: (value: boolean) => void;
  setIsAiSpeaking: (value: boolean) => void;
  setIsAiBusy: (value: boolean) => void;
  setIsUploadingBroadcast: (value: boolean) => void;
  finishBroadcast: () => void;
  startBroadcast: (source: 'station' | 'broadcast') => void;
  refreshBroadcastTimeline: () => Promise<void>;
  prependBroadcast: (item: BroadcastItem) => void;
  markInteraction: (timestamp?: number) => void;
  radioVolumeIdle: number;
  radioVolumeDucked: number;
  captureSettleMs: number;
  aiPollAttempts: number;
  aiPollIntervalMs: number;
  sleep: (ms: number) => Promise<unknown>;
}) {
  const finishAiPlayback = () => {
    params.setIsAiSpeaking(false);
    finishAiVoicePlayback({
      radioPlayer: params.radioPlayer,
      aiPlayer: params.aiPlayer,
      idleVolume: params.radioVolumeIdle,
      shouldResumeRadio: params.shouldResumeRadioAfterAiRef.current,
      isRecording: params.recorderState.isRecording,
    });
    params.shouldResumeRadioAfterAiRef.current = false;
  };

  const playAiBroadcast = async (text: string, resumeBroadcast: boolean) => {
    const content = text.trim();
    if (!content) {
      return;
    }

    const record = await createAiBroadcast({
      text: content,
      stationId: params.currentStation.id,
      locale: params.preferredSpeechLanguage,
    });

    params.prependBroadcast(record);
    void params.refreshBroadcastTimeline();

    const playableRecord =
      record.audioUri
        ? record
        : await waitForAiBroadcastAudio({
            broadcastId: record.id,
            attempts: params.aiPollAttempts,
            intervalMs: params.aiPollIntervalMs,
            sleep: params.sleep,
            onProgress: params.prependBroadcast,
          });

    if (!playableRecord?.audioUri) {
      return;
    }

    params.markInteraction();
    params.shouldResumeRadioAfterAiRef.current = resumeBroadcast && params.isPlaying;
    params.setIsAiSpeaking(true);
    startAiVoicePlayback({
      radioPlayer: params.radioPlayer,
      aiPlayer: params.aiPlayer,
      audioUri: playableRecord.audioUri,
      duckedVolume: params.radioVolumeDucked,
    });
  };

  const beginRecording = async () => {
    if (
      !canStartRecording({
        recordingStarted: params.recordingStarted,
        isRecording: params.recorderState.isRecording,
        isAiSpeaking: params.isAiSpeaking,
        isAiBusy: params.isAiBusy,
        isUploadingBroadcast: params.isUploadingBroadcast,
        isPreparingCapture: params.isPreparingCapture,
      })
    ) {
      return;
    }

    try {
      const permission = await requestRecordingPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Microphone needed', 'Allow microphone access first.');
        return;
      }

      params.setIsPreparingCapture(true);
      params.markInteraction();
      params.setIsAiSpeaking(false);
      await prepareForVoiceCapture({
        radioPlayer: params.radioPlayer,
        aiPlayer: params.aiPlayer,
        settleMs: params.captureSettleMs,
        sleep: params.sleep,
      });

      await setAudioModeAsync({
        allowsRecording: true,
        playsInSilentMode: true,
        shouldPlayInBackground: false,
        interruptionMode: 'duckOthers',
      });

      await params.recorder.prepareToRecordAsync();
      params.recorder.record();
      params.startBroadcast('broadcast');
      params.setRecordingStarted(true);
      params.setIsPreparingCapture(false);
    } catch (error) {
      params.setIsPreparingCapture(false);
      Alert.alert('Recording failed', error instanceof Error ? error.message : 'Unknown error');
    }
  };

  const endRecording = async () => {
    if (params.recordStopLockRef.current || !params.recordingStarted) {
      return;
    }

    params.recordStopLockRef.current = true;

    try {
      if (params.recorderState.isRecording) {
        await params.recorder.stop();
      }

      const status = params.recorder.getStatus();
      const audioUri = status.url ?? params.recorderState.url;
      const durationMs = status.durationMillis ?? params.recorderState.durationMillis;

      params.finishBroadcast();
      params.setRecordingStarted(false);
      params.setIsPreparingCapture(false);
      params.markInteraction();

      await setAudioModeAsync({
        allowsRecording: false,
        playsInSilentMode: true,
        shouldPlayInBackground: true,
        interruptionMode: 'duckOthers',
      });

      const followupPrompt = params.localAssistant.buildFollowupPrompt({
        profile: params.listenerProfile,
        station: params.currentStation,
      });
      const activeLease = params.modelSession?.lease ?? null;
      const activeLocale = params.modelSession?.locale ?? params.preferredSpeechLanguage;

      await uploadCapturedBroadcast({
        audioUri,
        stationId: params.currentStation.id,
        durationMs: durationMs ?? undefined,
        setUploading: params.setIsUploadingBroadcast,
        uploadBroadcast,
        refreshBroadcastTimeline: () => {
          void params.refreshBroadcastTimeline();
        },
      });

      await runPostRecordingAssistant({
        hasLease: activeLease != null,
        setAiBusy: params.setIsAiBusy,
        chatWithLeasedModel: () =>
          chatWithLeasedModel({
            lease: activeLease!,
            locale: activeLocale,
            text: followupPrompt,
          }),
        playAiBroadcast,
        startRadio: () => params.startBroadcast('station'),
        fallbackReply: 'received.',
      });
    } catch (error) {
      params.finishBroadcast();
      params.setRecordingStarted(false);
      params.setIsPreparingCapture(false);
      params.setIsUploadingBroadcast(false);
      params.startBroadcast('station');
      Alert.alert('Send failed', error instanceof Error ? error.message : 'Unknown error');
    } finally {
      params.recordStopLockRef.current = false;
    }
  };

  return {
    beginRecording,
    endRecording,
    finishAiPlayback,
    playAiBroadcast,
  };
}
