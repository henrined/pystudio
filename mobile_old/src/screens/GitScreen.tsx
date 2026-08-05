import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useAppStore } from '../store/useAppStore';
import { FontSizes } from '../theme/typography';

export const GitScreen: React.FC = () => {
  const { theme } = useAppStore();

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.sideBarBackground }]}>
      <Text style={[styles.title, { color: theme.colors.sideBarTitleForeground }]}>
        SOURCE CONTROL
      </Text>
      <View style={styles.content}>
        <Text style={{ color: theme.colors.sideBarForeground, fontSize: FontSizes.sm }}>
          Git source control status & changes
        </Text>
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
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
