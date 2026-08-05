import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useAppStore } from '../store/useAppStore';
import { Button } from '../components/common/Button';
import { FontSizes } from '../theme/typography';

export const HomeScreen: React.FC = () => {
  const { theme, setActiveTab } = useAppStore();

  return (
    <ScrollView
      style={[
        styles.container,
        { backgroundColor: theme.colors.editorBackground },
      ]}
      contentContainerStyle={styles.content}
    >
      <Text style={[styles.heading, { color: theme.colors.editorForeground }]}>
        PyStudio Mobile
      </Text>
      <Text style={[styles.subheading, { color: theme.colors.sideBarForeground }]}>
        Native Mobile IDE inspired by VS Code (Python & C/C++)
      </Text>

      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: theme.colors.editorForeground }]}>
          Start
        </Text>
        <View style={styles.buttonGroup}>
          <Button
            title="New File / Project"
            icon="file"
            onPress={() => setActiveTab('explorer')}
            style={styles.button}
          />
          <Button
            title="Open Workspace Folder"
            icon="folder"
            variant="secondary"
            onPress={() => setActiveTab('explorer')}
            style={styles.button}
          />
          <Button
            title="Clone Git Repository"
            icon="git"
            variant="secondary"
            onPress={() => setActiveTab('git')}
            style={styles.button}
          />
        </View>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: 20,
  },
  heading: {
    fontSize: FontSizes.xxl,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  subheading: {
    fontSize: FontSizes.md,
    marginBottom: 24,
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: FontSizes.lg,
    fontWeight: '600',
    marginBottom: 12,
  },
  buttonGroup: {
    gap: 10,
  },
  button: {
    alignSelf: 'flex-start',
    minWidth: 220,
  },
});
