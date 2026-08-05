import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { Icon } from '../common/Icon';
import { FontSizes } from '../../theme/typography';

interface TitleBarProps {
  title?: string;
  onMenuPress?: () => void;
}

export const TitleBar: React.FC<TitleBarProps> = ({
  title = 'PyStudio Mobile',
  onMenuPress,
}) => {
  const { theme, toggleCommandPalette } = useAppStore();

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
        <Text style={[styles.title, { color: theme.colors.titleBarActiveForeground }]}>
          {title}
        </Text>
      </View>

      <View style={styles.rightSection}>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={toggleCommandPalette}
          style={styles.iconButton}
          accessibilityLabel="Open Command Palette"
        >
          <Icon name="search" size={16} color={theme.colors.titleBarActiveForeground} />
        </TouchableOpacity>

        {onMenuPress && (
          <TouchableOpacity
            activeOpacity={0.7}
            onPress={onMenuPress}
            style={styles.iconButton}
            accessibilityLabel="Open Menu"
          >
            <Icon name="menu" size={18} color={theme.colors.titleBarActiveForeground} />
          </TouchableOpacity>
        )}
      </View>
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
});
