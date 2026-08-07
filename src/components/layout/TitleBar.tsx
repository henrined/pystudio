import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Modal,
  TouchableWithoutFeedback,
  StyleSheet,
} from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { Icon, IconName } from '../common/Icon';
import { FontSizes } from '../../theme/typography';

interface TitleBarProps {
  title?: string;
  onMenuPress?: () => void;
}

interface MenuItem {
  label: string;
  icon: IconName;
  action: () => void;
}

export const TitleBar: React.FC<TitleBarProps> = ({
  title = 'PyStudio Mobile',
  onMenuPress,
}) => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const { theme, themeMode, setThemeMode, toggleCommandPalette } = useAppStore();

  const handleToggleTheme = () => {
    setThemeMode(themeMode === 'dark' ? 'light' : 'dark');
  };

  const menuItems: MenuItem[] = [
    {
      label: 'New File',
      icon: 'file',
      action: () => {},
    },
    {
      label: 'Open Folder',
      icon: 'folder-open',
      action: () => {},
    },
    {
      label: 'Save',
      icon: 'check',
      action: () => {},
    },
    {
      label: 'Toggle Theme',
      icon: 'settings',
      action: handleToggleTheme,
    },
  ];

  const handleMenuButtonPress = () => {
    setIsMenuOpen(true);
    if (onMenuPress) {
      onMenuPress();
    }
  };

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor: theme.colors.titleBarActiveBackground,
          borderBottomColor: theme.colors.border,
        },
      ]}
    >
      <View style={styles.leftSection}>
        <Text
          style={[styles.title, { color: theme.colors.titleBarActiveForeground }]}
          accessibilityRole="header"
        >
          {title}
        </Text>
      </View>

      <View style={styles.rightSection}>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={toggleCommandPalette}
          style={styles.iconButton}
          accessibilityLabel="Search - Open Command Palette"
          accessibilityRole="button"
        >
          <Icon name="search" size={16} color={theme.colors.titleBarActiveForeground} />
        </TouchableOpacity>

        <TouchableOpacity
          activeOpacity={0.7}
          onPress={handleMenuButtonPress}
          style={styles.iconButton}
          accessibilityLabel="Open Menu"
          accessibilityRole="button"
        >
          <Icon name="menu" size={18} color={theme.colors.titleBarActiveForeground} />
        </TouchableOpacity>
      </View>

      <Modal
        visible={isMenuOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setIsMenuOpen(false)}
      >
        <TouchableWithoutFeedback onPress={() => setIsMenuOpen(false)}>
          <View style={styles.modalOverlay}>
            <TouchableWithoutFeedback>
              <View
                style={[
                  styles.menuContainer,
                  {
                    backgroundColor: theme.colors.quickInputBackground,
                    borderColor: theme.colors.border,
                  },
                ]}
              >
                {menuItems.map((item) => (
                  <TouchableOpacity
                    key={item.label}
                    style={styles.menuItem}
                    activeOpacity={0.7}
                    onPress={() => {
                      setIsMenuOpen(false);
                      item.action();
                    }}
                    accessibilityLabel={item.label}
                    accessibilityRole="button"
                  >
                    <Icon
                      name={item.icon}
                      size={16}
                      color={theme.colors.quickInputForeground}
                    />
                    <Text
                      style={[
                        styles.menuItemText,
                        { color: theme.colors.quickInputForeground },
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
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    height: 38,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    borderBottomWidth: 1,
  },
  leftSection: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  title: {
    fontSize: FontSizes.sm,
    fontWeight: '600',
  },
  rightSection: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  iconButton: {
    padding: 6,
    marginLeft: 6,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'transparent',
  },
  menuContainer: {
    position: 'absolute',
    top: 40,
    right: 8,
    minWidth: 160,
    borderRadius: 6,
    borderWidth: 1,
    paddingVertical: 4,
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 14,
  },
  menuItemText: {
    marginLeft: 10,
    fontSize: FontSizes.sm,
  },
});

