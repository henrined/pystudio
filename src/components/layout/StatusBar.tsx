import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { Icon } from '../common/Icon';

export const StatusBar: React.FC = () => {
  const {
    theme,
    gitBranch,
    errorCount,
    warningCount,
    currentLine,
    currentCol,
    activeLanguage,
    encoding,
    eolMode,
    setActiveTab,
  } = useAppStore();

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: theme.colors.statusBarBackground },
      ]}
      accessibilityRole="summary"
      accessibilityLabel="Status Bar"
    >
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
      >
        <View style={styles.leftSection}>
          <TouchableOpacity
            activeOpacity={0.7}
            onPress={() => setActiveTab('git')}
            style={styles.item}
            accessibilityRole="button"
            accessibilityLabel={`Git branch ${gitBranch}`}
          >
            <Icon
              name="git"
              size={12}
              color={theme.colors.statusBarForeground}
              style={styles.icon}
            />
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
              {gitBranch}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            activeOpacity={0.7}
            onPress={() => setActiveTab('debug')}
            style={styles.item}
            accessibilityRole="button"
            accessibilityLabel={`Diagnostics: ${errorCount} errors, ${warningCount} warnings`}
          >
            <Icon
              name="error"
              size={12}
              color={theme.colors.statusBarForeground}
              style={styles.icon}
            />
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }, styles.countMargin]}>
              {errorCount}
            </Text>
            <Icon
              name="warning"
              size={12}
              color={theme.colors.statusBarForeground}
              style={styles.icon}
            />
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
              {warningCount}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.rightSection}>
          <View style={styles.item}>
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
              {encoding || 'UTF-8'}
            </Text>
          </View>

          <View style={styles.item}>
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
              {eolMode || 'LF'}
            </Text>
          </View>

          <View style={styles.item}>
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
              Ln {currentLine}, Col {currentCol}
            </Text>
          </View>

          <View style={styles.item}>
            <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
              {activeLanguage}
            </Text>
          </View>

          <View style={styles.item}>
            <Icon
              name="bell"
              size={12}
              color={theme.colors.statusBarForeground}
            />
          </View>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    height: 24,
    width: '100%',
  },
  scrollContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minWidth: '100%',
    paddingHorizontal: 8,
    height: 24,
  },
  leftSection: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  rightSection: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 6,
  },
  icon: {
    marginRight: 4,
  },
  countMargin: {
    marginRight: 8,
  },
  text: {
    fontSize: 11,
    fontWeight: '500',
  },
});
