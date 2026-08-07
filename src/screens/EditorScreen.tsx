import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useAppStore } from '../store/useAppStore';

export const EditorScreen: React.FC = () => {
  const { theme, openTabs, activeTabId } = useAppStore();

  const activeFile = openTabs.find((t) => t.id === activeTabId);

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.editorBackground }]}>
      {activeFile ? (
        <View style={styles.editorArea}>
          <View style={[styles.tabBar, { backgroundColor: theme.colors.sideBarBackground, borderBottomColor: theme.colors.border }]}>
            {openTabs.map((tab) => (
              <View
                key={tab.id}
                style={[
                  styles.tab,
                  tab.id === activeTabId && { backgroundColor: theme.colors.editorBackground },
                ]}
              >
                <Text style={[styles.tabText, { color: theme.colors.editorForeground }]}>
                  {tab.title}{tab.isDirty ? ' ●' : ''}
                </Text>
              </View>
            ))}
          </View>
          <View style={styles.content}>
            <Text style={[styles.placeholder, { color: theme.colors.editorForeground }]}>
              {activeFile.path}
            </Text>
            <Text style={[styles.hint, { color: theme.colors.sideBarForeground }]}>
              Monaco Editor integration pending (C-4)
            </Text>
          </View>
        </View>
      ) : (
        <View style={styles.emptyState}>
          <Text style={[styles.emptyTitle, { color: theme.colors.editorForeground }]}>
            No file open
          </Text>
          <Text style={[styles.emptyHint, { color: theme.colors.sideBarForeground }]}>
            Open a file from the Explorer to start editing
          </Text>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  editorArea: {
    flex: 1,
  },
  tabBar: {
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    paddingHorizontal: 4,
  },
  tab: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 0,
  },
  tabText: {
    fontSize: 12,
    fontWeight: '500',
  },
  content: {
    flex: 1,
    padding: 16,
  },
  placeholder: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
  },
  hint: {
    fontSize: 12,
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
  },
  emptyHint: {
    fontSize: 13,
    textAlign: 'center',
  },
});
