import { Pressable, StyleSheet, View } from 'react-native';

type Props = {
  onOpenSubscription: () => void;
  onOpenBackground: () => void;
  onInteract?: () => void;
};

export function TopControls(props: Props) {
  return (
    <View style={styles.topBar}>
      <Pressable
        accessibilityLabel="subscription"
        delayLongPress={500}
        onLongPress={() => {
          props.onInteract?.();
          props.onOpenSubscription();
        }}
        style={styles.iconButton}
      >
        <View style={styles.dotGrid}>
          <View style={styles.dot} />
          <View style={styles.dot} />
          <View style={styles.dot} />
          <View style={styles.dot} />
        </View>
      </Pressable>

      <Pressable
        accessibilityLabel="background"
        onPress={() => {
          props.onInteract?.();
          props.onOpenBackground();
        }}
        style={styles.iconButton}
      >
        <View style={styles.frameIcon}>
          <View style={styles.frameCore} />
        </View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  topBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  iconButton: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: 'rgba(255,255,255,0.08)',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
  },
  dotGrid: { width: 18, height: 18, flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#fff' },
  frameIcon: {
    width: 18,
    height: 18,
    borderRadius: 5,
    borderWidth: 2,
    borderColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  frameCore: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#fff' },
});
