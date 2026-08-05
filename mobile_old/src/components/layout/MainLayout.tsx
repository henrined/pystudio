import React from 'react';
import { View, StyleSheet, SafeAreaView } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { TitleBar } from './TitleBar';
import { ActivityBar } from './ActivityBar';
import { StatusBar } from './StatusBar';
import { CommandPalette } from './CommandPalette';

interface MainLayoutProps {
  children: React.ReactNode;
}

export const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const { theme } = useAppStore();

  return (
    <SafeAreaView
      style={[
        styles.safeArea,
        { backgroundColor: theme.colors.editorBackground },
      ]}
    >
      <View style={styles.container}>
        <TitleBar />
        <View style={styles.body}>
          <ActivityBar />
          <View style={[styles.content, { backgroundColor: theme.colors.sideBarBackground }]}>
            {children}
          </View>
        </View>
        <StatusBar />
        <CommandPalette />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  container: {
    flex: 1,
    flexDirection: 'column',
  },
  body: {
    flex: 1,
    flexDirection: 'row',
  },
  content: {
    flex: 1,
  },
});
