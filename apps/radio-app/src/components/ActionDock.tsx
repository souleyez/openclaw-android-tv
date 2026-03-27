import { Pressable, StyleSheet, View } from 'react-native';

type Props = {
  onNext: () => void;
  onMusic: () => void;
  onNews: () => void;
  onBan: () => void;
};

export function ActionDock(props: Props) {
  return (
    <View style={styles.bottomBar}>
      <Pressable accessibilityLabel="next-station" onPress={props.onNext} style={styles.bottomButton}>
        <View style={styles.chevron} />
      </Pressable>

      <Pressable accessibilityLabel="play-music" onPress={props.onMusic} style={styles.bottomButton}>
        <View style={styles.musicIcon}>
          <View style={styles.musicStem} />
          <View style={styles.musicNoteLeft} />
          <View style={styles.musicNoteRight} />
        </View>
      </Pressable>

      <Pressable accessibilityLabel="play-news" onPress={props.onNews} style={styles.bottomButton}>
        <View style={styles.newsIcon}>
          <View style={styles.newsLine} />
          <View style={styles.newsLine} />
          <View style={styles.newsLineShort} />
        </View>
      </Pressable>

      <Pressable accessibilityLabel="ban-current" onPress={props.onBan} style={styles.bottomButton}>
        <View style={styles.banIcon}>
          <View style={styles.banCircle} />
          <View style={styles.banSlash} />
        </View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  bottomBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 10 },
  bottomButton: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: 'rgba(255,255,255,0.08)',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  chevron: {
    width: 16,
    height: 16,
    borderTopWidth: 2,
    borderRightWidth: 2,
    borderColor: '#fff',
    transform: [{ rotate: '45deg' }],
    marginLeft: -6,
  },
  musicIcon: { width: 22, height: 24 },
  musicStem: { position: 'absolute', right: 6, top: 2, width: 2, height: 14, backgroundColor: '#fff' },
  musicNoteLeft: { position: 'absolute', right: 0, top: 12, width: 10, height: 10, borderRadius: 5, backgroundColor: '#fff' },
  musicNoteRight: { position: 'absolute', left: 2, top: 15, width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.85)' },
  newsIcon: { width: 20, gap: 4 },
  newsLine: { width: 20, height: 2, borderRadius: 1, backgroundColor: '#fff' },
  newsLineShort: { width: 12, height: 2, borderRadius: 1, backgroundColor: '#fff' },
  banIcon: { width: 22, height: 22, alignItems: 'center', justifyContent: 'center' },
  banCircle: { position: 'absolute', width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: '#fff' },
  banSlash: { width: 14, height: 2, backgroundColor: '#fff', transform: [{ rotate: '-45deg' }] },
});
