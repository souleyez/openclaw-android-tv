import { Pressable, StyleSheet, View } from 'react-native';

type Props = {
  recordingStarted: boolean;
  isRecording: boolean;
  isPlaying: boolean;
  isAiSpeaking: boolean;
  showModelGlow: boolean;
  orbScale: number;
  onTogglePlayback: () => void;
  onBeginRecording: () => void;
  onEndRecording: () => void;
};

export function ControlOrb(props: Props) {
  return (
    <View style={styles.centerStage}>
      <Pressable
        accessibilityLabel="main-control"
        delayLongPress={180}
        onLongPress={props.onBeginRecording}
        onPressOut={() => {
          if (props.recordingStarted || props.isRecording) {
            props.onEndRecording();
          }
        }}
        onPress={() => {
          if (props.recordingStarted || props.isRecording) {
            return;
          }
          props.onTogglePlayback();
        }}
        style={styles.orbWrap}
      >
        <View
          style={[
            styles.outerRing,
            props.showModelGlow ? styles.outerRingModel : null,
            props.recordingStarted ? styles.outerRingRecording : null,
            { transform: [{ scale: props.orbScale }] },
          ]}
        >
          <View style={[styles.midRing, props.isAiSpeaking ? styles.midRingSpeaking : null]}>
            <View style={[styles.core, !props.isPlaying && !props.recordingStarted ? styles.coreIdle : null]} />
          </View>
        </View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  centerStage: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  orbWrap: { width: 240, height: 240, alignItems: 'center', justifyContent: 'center' },
  outerRing: {
    width: 220,
    height: 220,
    borderRadius: 110,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    backgroundColor: 'rgba(255,255,255,0.04)',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 30,
    shadowOffset: { width: 0, height: 16 },
  },
  outerRingModel: { borderColor: 'rgba(255, 216, 155, 0.6)', backgroundColor: 'rgba(255, 214, 153, 0.08)' },
  outerRingRecording: { borderColor: 'rgba(255, 124, 124, 0.9)', backgroundColor: 'rgba(255, 87, 87, 0.12)' },
  midRing: {
    width: 154,
    height: 154,
    borderRadius: 77,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.14)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  midRingSpeaking: { borderColor: 'rgba(152, 214, 255, 0.8)' },
  core: { width: 88, height: 88, borderRadius: 44, backgroundColor: '#f4f7fb' },
  coreIdle: { backgroundColor: 'rgba(244,247,251,0.72)' },
});
