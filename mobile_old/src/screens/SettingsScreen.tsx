import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useAppStore } from '../store/useAppStore';
import { Button } from '../components/common/Button';
import { FontSizes } from '../theme/typography';

export const SettingsScreen: React.FC = () => {
  const { theme, themeMode, setThemeMode } = useAppStore();

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.sideBarBackground }]}>
      <Text style={[styles.title, { color: theme.colors.sideBarTitleForeground }]}>
        SETTINGS
      </Text>
      <View style={styles.content}>
        <Text style={[styles.label, { color: theme.colors.sideBarForeground }]}>
          Appearance
        </Text>
        <Button
          title={`Theme: ${themeMode === 'dark' ? 'Dark+ (VS Code)' : 'Light+'}`}
          variant="secondary"
          icon="settings"
          onPress={() => setThemeMode(themeMode === 'dark' ? 'light' : 'dark')}
        />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 12,
  },
  title: {
    fontSize: FontSizes.xs,
    fontWeight: 'bold',
    letterSpacing: 1,
    marginBottom: 12,
  },
  content: {
    padding: 8,
  },
  label: {
    fontSize: FontSizes.sm,
    fontWeight: '600',
    marginBottom: 8,
  },
});
