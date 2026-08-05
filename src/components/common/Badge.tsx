import React from 'react';
import { View, Text, StyleSheet, ViewStyle } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { FontSizes } from '../../theme/typography';

interface BadgeProps {
  count?: number | string;
  backgroundColor?: string;
  textColor?: string;
  style?: ViewStyle;
}

export const Badge: React.FC<BadgeProps> = ({
  count,
  backgroundColor,
  textColor,
  style,
}) => {
  const { theme } = useAppStore();

  if (count === undefined || count === null || count === '' || count === 0) {
    return null;
  }

  const bg = backgroundColor || theme.colors.badgeBackground;
  const fg = textColor || theme.colors.badgeForeground;

  return (
    <View style={[styles.container, { backgroundColor: bg }, style]}>
      <Text style={[styles.text, { color: fg }]}>{count}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 5,
    paddingVertical: 2,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 16,
  },
  text: {
    fontSize: FontSizes.xs,
    fontWeight: 'bold',
  },
});
