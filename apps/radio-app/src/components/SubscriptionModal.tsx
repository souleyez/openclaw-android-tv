import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';

type Props = {
  visible: boolean;
  onClose: () => void;
  onInteract?: () => void;
  title: string;
  price: string;
  caption: string;
  footnote: string;
  meta: string;
};

export function SubscriptionModal(props: Props) {
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
        <View style={styles.subscriptionSheet}>
          <Text style={styles.subscriptionTitle}>{props.title}</Text>
          <Text style={styles.subscriptionPrice}>{props.price}</Text>
          <Text style={styles.subscriptionCaption}>{props.caption}</Text>
          <Text style={styles.subscriptionFootnote}>{props.footnote}</Text>
          <Text style={styles.subscriptionMeta}>{props.meta}</Text>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalBackdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(5,7,13,0.5)' },
  modalDismissLayer: { ...StyleSheet.absoluteFillObject },
  subscriptionSheet: {
    paddingHorizontal: 24,
    paddingVertical: 28,
    backgroundColor: 'rgba(10, 14, 24, 0.98)',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    gap: 10,
  },
  subscriptionTitle: { color: '#fff', fontSize: 24, fontWeight: '700' },
  subscriptionPrice: { color: '#ffe0a6', fontSize: 22, fontWeight: '700' },
  subscriptionCaption: { color: 'rgba(255,255,255,0.82)', fontSize: 14 },
  subscriptionFootnote: { color: 'rgba(255,255,255,0.56)', fontSize: 12 },
  subscriptionMeta: { color: 'rgba(255,255,255,0.42)', fontSize: 12 },
});
