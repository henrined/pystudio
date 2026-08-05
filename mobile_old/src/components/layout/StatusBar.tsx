import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useAppStore } from '../../store/useAppStore';
import { Icon } from '../common/Icon';
import { FontSizes } from '../../theme/typography';

export const StatusBar: React.FC = () => {
  const {
    theme,
    gitBranch,
    errorCount,
    warningCount,
    currentLine,
    currentCol,
    activeLanguage,
    setActiveTab,
  } = useAppStore();

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: theme.colors.statusBarBackground },
      ]}
    >
      <View style={styles.leftSection}>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={() => setActiveTab('git')}
          style={styles.item}
        >
          <Icon name="git" size={12} color={theme.colors.statusBarForeground} style={styles.icon} />
          <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
            {gitBranch}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          activeOpacity={0.7}
          onPress={() => setActiveTab('debug')}
          style={styles.item}
        >
          <Icon name="alert" size={12} color={theme.colors.statusBarForeground} style={styles.icon} />
          <Text style={[styles.text, { color: theme.colors.statusBarForeground }]}>
            {errorCount} ⊗ {warningCount} Δ
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.rightSection}>
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
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    height: 24,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
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
  text: {
    fontSize: FontSizes.xs,
    fontWeight: '500',
  },
});
