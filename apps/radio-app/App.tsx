import { useMemo, useRef, useState } from 'react';
import { ImageBackground, SafeAreaView, StyleSheet } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { LinearGradient } from 'expo-linear-gradient';
import {
  RecordingPresets,
  useAudioPlayer,
  useAudioPlayerStatus,
  useAudioRecorder,
  useAudioRecorderState,
} from 'expo-audio';

import { ActionDock } from './src/components/ActionDock';
import { BackgroundPickerModal } from './src/components/BackgroundPickerModal';
import { ControlOrb } from './src/components/ControlOrb';
import { SubscriptionModal } from './src/components/SubscriptionModal';
import { TopControls } from './src/components/TopControls';
import { MOCK_STATIONS, formatDuration, type Station } from './src/data/mockRadio';
import { useAppShellChrome } from './src/hooks/useAppShellChrome';
import { useAppViewModel } from './src/hooks/useAppViewModel';
import { useBackgroundModelLease } from './src/hooks/useBackgroundModelLease';
import { useBackgroundUpdates } from './src/hooks/useBackgroundUpdates';
import { useBootstrapFlow } from './src/hooks/useBootstrapFlow';
import { useBroadcastTimeline } from './src/hooks/useBroadcastTimeline';
import { useInteractionActivity } from './src/hooks/useInteractionActivity';
import { useLeaseVisualState } from './src/hooks/useLeaseVisualState';
import { useLocalRadioController } from './src/hooks/useLocalRadioController';
import { useRadioAudioEffects } from './src/hooks/useRadioAudioEffects';
import { useSubscriptionState } from './src/hooks/useSubscriptionState';
import { useVoiceBroadcastController } from './src/hooks/useVoiceBroadcastController';
import { chatWithLeasedModel } from './src/services/directModel';
import { getLocalAssistantBridge } from './src/services/localAssistant';
import { type BootstrapSessionPayload } from './src/services/modelLease';
import { recommendStartupStation, resolveListenerProfile } from './src/services/radioIntelligence';
import { useRadioStore } from './src/store/useRadioStore';

const RADIO_VOLUME_IDLE = 0.92;
const RADIO_VOLUME_DUCKED = 0.2;
const COMMAND_CAPTURE_SETTLE_MS = 180;
const BROADCAST_POLL_INTERVAL_MS = 1200;
const BROADCAST_POLL_ATTEMPTS = 8;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export default function App() {
  const [stations, setStations] = useState<Station[]>(MOCK_STATIONS);
  const [modelSession, setModelSession] = useState<BootstrapSessionPayload | null>(null);
  const [preferredSpeechLanguage, setPreferredSpeechLanguage] = useState('zh-CN');
  const [isAiBusy, setIsAiBusy] = useState(false);
  const [isAiSpeaking, setIsAiSpeaking] = useState(false);
  const [isUploadingBroadcast, setIsUploadingBroadcast] = useState(false);
  const [bootSequenceFinished, setBootSequenceFinished] = useState(false);
  const [recordingStarted, setRecordingStarted] = useState(false);
  const [isPreparingCapture, setIsPreparingCapture] = useState(false);

  const recordStopLockRef = useRef(false);
  const shouldResumeRadioAfterAiRef = useRef(false);
  const startupSelectionResolvedRef = useRef(false);
  const radioRecoveryLockRef = useRef(false);

  const {
    backgroundPresetId,
    blockedStationIds,
    customBackgroundUri,
    currentStationId,
    isPlaying,
    listeningDurations,
    preferredMode,
    recentStationIds,
    blockStation,
    finishBroadcast,
    markStationSkipped,
    prependBroadcast,
    recordListeningTick,
    selectBackgroundPreset,
    selectStation,
    setPreferredMode,
    setBroadcasts,
    stationSkipCounts,
    setCustomBackgroundUri,
    startBroadcast,
    stopBroadcast,
  } = useRadioStore();

  const localAssistant = useMemo(() => getLocalAssistantBridge(), []);
  const { interactionPhase, lastInteractionAt, markInteraction } = useInteractionActivity(modelSession?.lease != null);
  const {
    subscriptionPreviewEnabled,
    setSubscriptionPreviewEnabled,
    subscriptionIsActive,
    setSubscriptionIsActive,
    subscriptionLoading,
    setSubscriptionLoading,
    subscriptionPlan,
    setSubscriptionPlan,
    refreshSubscriptionState,
  } = useSubscriptionState();

  const listenerProfile = useMemo(
    () =>
      resolveListenerProfile(
        listeningDurations,
        blockedStationIds,
        stations,
        stationSkipCounts,
        recentStationIds,
        preferredMode,
      ),
    [blockedStationIds, listeningDurations, preferredMode, recentStationIds, stationSkipCounts, stations],
  );

  const { showModelGlow } = useLeaseVisualState({
    subscriptionIsActive,
    subscriptionPreviewEnabled,
    hasLease: modelSession?.lease != null,
    interactionPhase,
  });

  const { currentStation, backgroundSource, orbScale } = useAppViewModel({
    stations,
    currentStationId,
    backgroundPresetId,
    customBackgroundUri,
    recordingStarted,
    isAiSpeaking,
    isPlaying,
    showModelGlow,
  });

  const radioPlayer = useAudioPlayer(currentStation?.streamUrl ?? null);
  const aiPlayer = useAudioPlayer(null);
  const radioPlayerStatus = useAudioPlayerStatus(radioPlayer) as { didJustFinish?: boolean; error?: unknown };
  const aiPlayerStatus = useAudioPlayerStatus(aiPlayer) as { didJustFinish?: boolean };
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder);

  const { refreshBroadcastTimeline, pushBroadcast } = useBroadcastTimeline({
    setBroadcasts,
    prependBroadcast,
    preferredMode,
  });

  const {
    isBackgroundModalOpen,
    isSubscriptionModalOpen,
    openBackgroundModal,
    closeBackgroundModal,
    openSubscriptionModal,
    closeSubscriptionModal,
    onPickCustomBackground,
  } = useAppShellChrome({
    markInteraction,
    setCustomBackgroundUri,
    refreshSubscriptionState,
  });

  const { chooseNextStation, executeLocalAssistantIntent, onTogglePlayback } = useLocalRadioController({
    currentStation,
    stations,
    listenerProfile,
    isPlaying,
    isAiSpeaking,
    isAiBusy,
    isUploadingBroadcast,
    isPreparingCapture,
    isRecording: recorderState.isRecording,
    markInteraction,
    selectStation,
    setPreferredMode,
    markStationSkipped,
    blockStation,
    startBroadcast,
    stopBroadcast,
  });

  const { beginRecording, endRecording, finishAiPlayback, playAiBroadcast } = useVoiceBroadcastController({
    radioPlayer,
    aiPlayer,
    recorder,
    recorderState,
    recordingStarted,
    isPreparingCapture,
    isAiSpeaking,
    isAiBusy,
    isUploadingBroadcast,
    isPlaying,
    currentStation,
    preferredSpeechLanguage,
    modelSession,
    localAssistant,
    listenerProfile,
    recordStopLockRef,
    shouldResumeRadioAfterAiRef,
    setRecordingStarted,
    setIsPreparingCapture,
    setIsAiSpeaking,
    setIsAiBusy,
    setIsUploadingBroadcast,
    finishBroadcast,
    startBroadcast,
    refreshBroadcastTimeline,
    prependBroadcast: pushBroadcast,
    markInteraction,
    radioVolumeIdle: RADIO_VOLUME_IDLE,
    radioVolumeDucked: RADIO_VOLUME_DUCKED,
    captureSettleMs: COMMAND_CAPTURE_SETTLE_MS,
    aiPollAttempts: BROADCAST_POLL_ATTEMPTS,
    aiPollIntervalMs: BROADCAST_POLL_INTERVAL_MS,
    sleep,
  });

  useRadioAudioEffects({
    currentStreamUrl: currentStation.streamUrl,
    radioPlayer,
    aiPlayer,
    isPlaying,
    isAiSpeaking,
    isAiBusy,
    isPreparingCapture,
    recordingStarted,
    radioVolumeIdle: RADIO_VOLUME_IDLE,
    radioVolumeDucked: RADIO_VOLUME_DUCKED,
    currentStationId: currentStation.id,
    recordListeningTick,
    finishAiPlayback,
    aiDidJustFinish: !!aiPlayerStatus.didJustFinish,
    listenerProfile,
    chooseNextStation,
    radioDidJustFinish: !!radioPlayerStatus.didJustFinish,
    radioHasError: !!radioPlayerStatus.error,
    radioRecoveryLocked: radioRecoveryLockRef.current,
    setRadioRecoveryLocked: (value) => {
      radioRecoveryLockRef.current = value;
    },
  });

  useBootstrapFlow({
    enabled: bootSequenceFinished === false,
    listenerProfile,
    startupSelectionResolved: startupSelectionResolvedRef.current,
    setStartupSelectionResolved: (value) => {
      startupSelectionResolvedRef.current = value;
    },
    onComplete: () => setBootSequenceFinished(true),
    selectStation,
    setStations,
    setBroadcasts,
    setPreferredSpeechLanguage,
    setPreferredMode,
    setSubscriptionPlan,
    setSubscriptionIsActive,
    setSubscriptionLoading,
    setCustomBackgroundUri,
    startBroadcast,
    recommendStartupStation,
  });

  useBackgroundModelLease({
    enabled: true,
    currentSession: modelSession,
    setModelSession,
    clearModelLease: () => {
      setModelSession((prev) => (prev ? { ...prev, lease: null } : prev));
    },
    setSubscriptionPreviewEnabled,
    playAiBroadcast,
    chatWithLease: async (session) =>
      chatWithLeasedModel({
        lease: session.lease!,
        locale: session.locale,
        text: localAssistant.buildBootPrompt({
          profile: listenerProfile,
          station: currentStation,
        }),
      }) || 'Hello.',
    isBusy: isAiBusy || isAiSpeaking,
    isPreparingCapture,
    isRecording: recorderState.isRecording || recordingStarted,
    markInteraction,
    lastInteractionAt,
  });

  useBackgroundUpdates({
    enabled: bootSequenceFinished,
    interactionPhase,
    preferredMode,
    customBackgroundUri,
    setPreferredMode,
    setCustomBackgroundUri,
    refreshBroadcastTimeline,
  });

  return (
    <ImageBackground source={backgroundSource} style={styles.background} resizeMode="cover">
      <StatusBar style="light" />
      <LinearGradient colors={['rgba(4, 7, 14, 0.24)', 'rgba(4, 7, 14, 0.72)']} style={styles.overlay}>
        <SafeAreaView style={styles.safeArea}>
          <TopControls
            onInteract={markInteraction}
            onOpenSubscription={openSubscriptionModal}
            onOpenBackground={openBackgroundModal}
          />

          <ControlOrb
            recordingStarted={recordingStarted}
            isRecording={recorderState.isRecording}
            isPlaying={isPlaying}
            isAiSpeaking={isAiSpeaking}
            showModelGlow={showModelGlow}
            orbScale={orbScale}
            onTogglePlayback={onTogglePlayback}
            onBeginRecording={beginRecording}
            onEndRecording={() => {
              void endRecording();
            }}
          />

          <ActionDock
            onNext={() => executeLocalAssistantIntent('next')}
            onMusic={() => executeLocalAssistantIntent('play_music')}
            onNews={() => executeLocalAssistantIntent('play_news')}
            onBan={() => executeLocalAssistantIntent('ban_current')}
          />
        </SafeAreaView>
      </LinearGradient>

      <BackgroundPickerModal
        visible={isBackgroundModalOpen}
        onInteract={markInteraction}
        onClose={closeBackgroundModal}
        onSelectPreset={selectBackgroundPreset}
        onPickCustom={onPickCustomBackground}
      />

      <SubscriptionModal
        visible={isSubscriptionModalOpen}
        onInteract={markInteraction}
        onClose={closeSubscriptionModal}
        title="声临"
        price={subscriptionPlan?.displayPrice ?? '6 CNY / month'}
        caption={
          subscriptionLoading
            ? 'Checking...'
            : subscriptionIsActive
              ? 'Subscribed'
              : 'Official store billing only'
        }
        footnote="Long press entry. One monthly subscription."
        meta={`${currentStation.name} · ${formatDuration(30000)}`}
      />
    </ImageBackground>
  );
}

const styles = StyleSheet.create({
  background: { flex: 1, backgroundColor: '#05070d' },
  overlay: { flex: 1 },
  safeArea: { flex: 1, justifyContent: 'space-between', paddingHorizontal: 22, paddingVertical: 18 },
});
