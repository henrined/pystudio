import React from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  TouchableWithoutFeedback,
} from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { Icon, IconName } from './Icon';
import { FontSizes } from '../../theme/typography';

export interface ContextMenuItem {
  id: string;
  label: string;
  icon?: IconName;
  destructive?: boolean;
  onPress: () => void;
}

interface ContextMenuProps {
  visible: boolean;
  items: ContextMenuItem[];
  onClose: () => void;
  x?: number;
  y?: number;
}

export const ContextMenu: React.FC<ContextMenuProps> = ({
  visible,
  items,
  onClose,
  x = 50,
  y = 50,
}) => {
  const { theme } = useAppStore();

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} onRequestClose={onClose} animationType="none">
      <TouchableWithoutFeedback onPress={onClose}>
        <View style={styles.overlay}>
          <TouchableWithoutFeedback>
            <View
              style={[
                styles.menuContainer,
                {
                  backgroundColor: theme.colors.quickInputBackground,
                  borderColor: theme.colors.border,
                  top: y,
                  left: x,
                },
              ]}
            >
              {items.map((item) => (
                <TouchableOpacity
                  key={item.id}
                  style={styles.menuItem}
                  onPress={() => {
                    onClose();
                    item.onPress();
                  }}
                >
                  {item.icon && (
                    <Icon
                      name={item.icon}
                      size={14}
                      color={
                        item.destructive
                          ? theme.colors.errorForeground
                          : theme.colors.quickInputForeground
                      }
                      style={styles.icon}
                    />
                  )}
                  <Text
                    style={[
                      styles.label,
                      {
                        color: item.destructive
                          ? theme.colors.errorForeground
                          : theme.colors.quickInputForeground,
                      },
                    ]}
                  >
                    {item.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </TouchableWithoutFeedback>
        </View>
      </TouchableWithoutFeedback>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.2)',
  },
  menuContainer: {
    position: 'absolute',
    minWidth: 160,
    borderRadius: 6,
    borderWidth: 1,
    paddingVertical: 4,
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 4.65,
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  icon: {
    marginRight: 8,
  },
  label: {
    fontSize: FontSizes.sm,
  },
});
