import { ImageBackground, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { BACKGROUND_PRESETS } from '../data/mockRadio';

type Props = {
  visible: boolean;
  onClose: () => void;
  onSelectPreset: (presetId: string) => void;
  onPickCustom: () => void;
  onInteract?: () => void;
};

export function BackgroundPickerModal(props: Props) {
  return (
    <Modal animationType="fade" transparent visible={props.visible} onRequestClose={props.onClose}>
      <View style={styles.modalBackdrop}>
        <Pressable
          style={styles.modalDismissLayer}
          onPress={() => {
            props.onInteract?.();
            props.onClose();
          }}
        />
        <View style={styles.backgroundSheet}>
          {BACKGROUND_PRESETS.map((preset) => (
            <Pressable
              key={preset.id}
              onPress={() => {
                props.onInteract?.();
                props.onSelectPreset(preset.id);
                props.onClose();
              }}
              style={styles.backgroundOption}
            >
              <ImageBackground
                source={{ uri: preset.imageUri }}
                style={styles.backgroundPreview}
                imageStyle={styles.backgroundPreviewImage}
              />
            </Pressable>
          ))}
          <Pressable
            onPress={() => {
              props.onInteract?.();
              props.onPickCustom();
            }}
            style={[styles.backgroundOption, styles.backgroundUpload]}
          >
            <Text style={styles.modalText}>Album</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalBackdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(5,7,13,0.5)' },
  modalDismissLayer: { ...StyleSheet.absoluteFillObject },
  backgroundSheet: {
    paddingHorizontal: 20,
    paddingTop: 18,
    paddingBottom: 28,
    backgroundColor: 'rgba(10, 14, 24, 0.96)',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  backgroundOption: { width: '47%', aspectRatio: 0.72, borderRadius: 20, overflow: 'hidden' },
  backgroundPreview: { flex: 1 },
  backgroundPreviewImage: { borderRadius: 20 },
  backgroundUpload: { backgroundColor: 'rgba(255,255,255,0.08)', alignItems: 'center', justifyContent: 'center' },
  modalText: { color: '#fff', fontSize: 15, fontWeight: '600' },
});
