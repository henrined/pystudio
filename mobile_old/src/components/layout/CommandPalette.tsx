import React, { useState } from 'react';
import {
  Modal,
  View,
  TextInput,
  FlatList,
  Text,
  TouchableOpacity,
  StyleSheet,
  TouchableWithoutFeedback,
} from 'react-native';
import { useAppStore, ActivityTab } from '../../store/useAppStore';
import { Icon, IconName } from '../common/Icon';
import { FontSizes } from '../../theme/typography';

interface CommandItem {
  id: string;
  title: string;
  category: string;
  shortcut?: string;
  icon?: IconName;
  action: () => void;
}

export const CommandPalette: React.FC = () => {
  const {
    isCommandPaletteOpen,
    setCommandPaletteOpen,
    theme,
    setActiveTab,
    setThemeMode,
    themeMode,
  } = useAppStore();

  const [query, setQuery] = useState('');

  const commands: CommandItem[] = [
    {
      id: 'nav-home',
      title: 'Go to Home Screen',
      category: 'View',
      icon: 'home',
      action: () => setActiveTab('home'),
    },
    {
      id: 'nav-explorer',
      title: 'Go to File Explorer',
      category: 'View',
      icon: 'folder',
      action: () => setActiveTab('explorer'),
    },
    {
      id: 'nav-search',
      title: 'Go to Search',
      category: 'View',
      icon: 'search',
      action: () => setActiveTab('search'),
    },
    {
      id: 'nav-git',
      title: 'Go to Source Control (Git)',
      category: 'View',
      icon: 'git',
      action: () => setActiveTab('git'),
    },
    {
      id: 'nav-debug',
      title: 'Go to Debugger',
      category: 'View',
      icon: 'bug',
      action: () => setActiveTab('debug'),
    },
    {
      id: 'nav-marketplace',
      title: 'Go to Extension Marketplace',
      category: 'View',
      icon: 'extensions',
      action: () => setActiveTab('marketplace'),
    },
    {
      id: 'nav-ai',
      title: 'Open AI Assistant',
      category: 'View',
      icon: 'ai',
      action: () => setActiveTab('ai'),
    },
    {
      id: 'toggle-theme',
      title: `Toggle Color Theme (Current: ${themeMode})`,
      category: 'Preferences',
      icon: 'settings',
      action: () => setThemeMode(themeMode === 'dark' ? 'light' : 'dark'),
    },
  ];

  const filteredCommands = commands.filter(
    (cmd) =>
      cmd.title.toLowerCase().includes(query.toLowerCase()) ||
      cmd.category.toLowerCase().includes(query.toLowerCase())
  );

  const handleSelect = (cmd: CommandItem) => {
    setCommandPaletteOpen(false);
    setQuery('');
    cmd.action();
  };

  if (!isCommandPaletteOpen) return null;

  return (
    <Modal
      transparent
      visible={isCommandPaletteOpen}
      onRequestClose={() => setCommandPaletteOpen(false)}
      animationType="fade"
    >
      <TouchableWithoutFeedback onPress={() => setCommandPaletteOpen(false)}>
        <View
          style={[
            styles.overlay,
            { backgroundColor: theme.colors.modalOverlayBackground },
          ]}
        >
          <TouchableWithoutFeedback>
            <View
              style={[
                styles.paletteContainer,
                {
                  backgroundColor: theme.colors.quickInputBackground,
                  borderColor: theme.colors.quickInputFocusBorder,
                },
              ]}
            >
              <TextInput
                autoFocus
                placeholder="Type a command or search..."
                placeholderTextColor="#888888"
                value={query}
                onChangeText={setQuery}
                style={[
                  styles.input,
                  {
                    color: theme.colors.quickInputForeground,
                    borderColor: theme.colors.border,
                  },
                ]}
              />

              <FlatList
                data={filteredCommands}
                keyExtractor={(item) => item.id}
                keyboardShouldPersistTaps="handled"
                renderItem={({ item }) => (
                  <TouchableOpacity
                    activeOpacity={0.7}
                    onPress={() => handleSelect(item)}
                    style={styles.itemRow}
                  >
                    {item.icon && (
                      <Icon
                        name={item.icon}
                        size={16}
                        color={theme.colors.quickInputForeground}
                        style={styles.icon}
                      />
                    )}
                    <View style={styles.textContainer}>
                      <Text
                        style={[
                          styles.itemTitle,
                          { color: theme.colors.quickInputForeground },
                        ]}
                      >
                        {item.title}
                      </Text>
                      <Text style={styles.itemCategory}>{item.category}</Text>
                    </View>
                    {item.shortcut && (
                      <Text style={styles.shortcut}>{item.shortcut}</Text>
                    )}
                  </TouchableOpacity>
                )}
              />
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
    paddingTop: 40,
    alignItems: 'center',
  },
  paletteContainer: {
    width: '90%',
    maxHeight: 320,
    borderRadius: 6,
    borderWidth: 1,
    padding: 8,
    elevation: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 5.84,
  },
  input: {
    height: 36,
    borderWidth: 1,
    borderRadius: 4,
    paddingHorizontal: 10,
    fontSize: FontSizes.sm,
    marginBottom: 8,
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 8,
    borderRadius: 4,
  },
  icon: {
    marginRight: 10,
  },
  textContainer: {
    flex: 1,
  },
  itemTitle: {
    fontSize: FontSizes.sm,
    fontWeight: '500',
  },
  itemCategory: {
    fontSize: FontSizes.xs,
    color: '#888888',
  },
  shortcut: {
    fontSize: FontSizes.xs,
    color: '#aaaaaa',
  },
});
