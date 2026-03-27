import { useEffect, useState } from 'react';
import { Alert } from 'react-native';
import * as ImagePicker from 'expo-image-picker';

export function useAppShellChrome(params: {
  markInteraction: () => void;
  setCustomBackgroundUri: (uri: string | null) => void;
  refreshSubscriptionState: () => Promise<void>;
}) {
  const { markInteraction, refreshSubscriptionState, setCustomBackgroundUri } = params;
  const [isBackgroundModalOpen, setIsBackgroundModalOpen] = useState(false);
  const [isSubscriptionModalOpen, setIsSubscriptionModalOpen] = useState(false);

  useEffect(() => {
    if (!isSubscriptionModalOpen) {
      return;
    }

    markInteraction();
    void refreshSubscriptionState();
  }, [isSubscriptionModalOpen, markInteraction, refreshSubscriptionState]);

  const openBackgroundModal = () => {
    markInteraction();
    setIsBackgroundModalOpen(true);
  };

  const closeBackgroundModal = () => {
    markInteraction();
    setIsBackgroundModalOpen(false);
  };

  const openSubscriptionModal = () => {
    markInteraction();
    setIsSubscriptionModalOpen(true);
  };

  const closeSubscriptionModal = () => {
    markInteraction();
    setIsSubscriptionModalOpen(false);
  };

  const onPickCustomBackground = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Photo access needed', 'Allow album access to change the background.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      quality: 1,
      aspect: [9, 16],
    });

    if (result.canceled || !result.assets?.length) {
      return;
    }

    setCustomBackgroundUri(result.assets[0].uri);
    setIsBackgroundModalOpen(false);
    markInteraction();
  };

  return {
    isBackgroundModalOpen,
    isSubscriptionModalOpen,
    openBackgroundModal,
    closeBackgroundModal,
    openSubscriptionModal,
    closeSubscriptionModal,
    onPickCustomBackground,
  };
}
