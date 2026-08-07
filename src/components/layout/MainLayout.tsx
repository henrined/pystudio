import React, { useEffect } from 'react';
import { View, StyleSheet, SafeAreaView, Keyboard } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { TitleBar } from './TitleBar';
import { ActivityBar } from './ActivityBar';
import { StatusBar } from './StatusBar';
import { CommandPalette } from './CommandPalette';

interface MainLayoutProps {
  children: React.ReactNode;
}

export const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const { theme, isKeyboardVisible, setKeyboardVisible } = useAppStore();

  useEffect(() => {
    const showSubscription = Keyboard.addListener('keyboardDidShow', () => {
      setKeyboardVisible(true);
    });
    const hideSubscription = Keyboard.addListener('keyboardDidHide', () => {
      setKeyboardVisible(false);
    });

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, [setKeyboardVisible]);

  return (
    <SafeAreaView
      style={[
        styles.safeArea,
        { backgroundColor: theme.colors.editorBackground },
      ]}
      accessibilityRole="none"
      accessibilityLabel="Main Layout"
    >
      <View style={styles.container} accessibilityRole="none">
        <TitleBar />
        <View style={styles.body} accessibilityRole="none">
          {!isKeyboardVisible && <ActivityBar />}
          <View
            style={[
              styles.content,
              { backgroundColor: theme.colors.sideBarBackground },
            ]}
            accessibilityRole="none"
          >
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
