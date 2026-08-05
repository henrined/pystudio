import React from 'react';
import { View, Text, StyleSheet, Modal, TouchableWithoutFeedback } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { FontSizes } from '../../theme/typography';

interface TooltipProps {
  visible: boolean;
  text: string;
  onClose: () => void;
  x?: number;
  y?: number;
}

export const Tooltip: React.FC<TooltipProps> = ({
  visible,
  text,
  onClose,
  x = 0,
  y = 0,
}) => {
  const { theme } = useAppStore();

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} onRequestClose={onClose} animationType="fade">
      <TouchableWithoutFeedback onPress={onClose}>
        <View style={styles.overlay}>
          <View
            style={[
              styles.container,
              {
                backgroundColor: theme.colors.quickInputBackground,
                borderColor: theme.colors.border,
                top: y + 20,
                left: Math.max(10, x - 20),
              },
            ]}
          >
            <Text style={[styles.text, { color: theme.colors.quickInputForeground }]}>
              {text}
            </Text>
          </View>
        </View>
      </TouchableWithoutFeedback>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
  },
  container: {
    position: 'absolute',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
    zIndex: 9999,
  },
  text: {
    fontSize: FontSizes.xs,
  },
});
