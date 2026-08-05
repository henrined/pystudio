import React from 'react';
import { Text, TextStyle, StyleSheet } from 'react-native';

export type IconName =
  | 'folder'
  | 'search'
  | 'git'
  | 'bug'
  | 'extensions'
  | 'ai'
  | 'settings'
  | 'play'
  | 'stop'
  | 'close'
  | 'menu'
  | 'file'
  | 'chevron-right'
  | 'chevron-down'
  | 'check'
  | 'alert'
  | 'info'
  | 'home';

interface IconProps {
  name: IconName;
  size?: number;
  color?: string;
  style?: TextStyle;
}

const IconSymbolMap: Record<IconName, string> = {
  folder: '📁',
  search: '🔍',
  git: '🌿',
  bug: '🐛',
  extensions: '🧩',
  ai: '🤖',
  settings: '⚙️',
  play: '▶️',
  stop: '⏹️',
  close: '✕',
  menu: '⋮',
  file: '📄',
  'chevron-right': '›',
  'chevron-down': '˅',
  check: '✓',
  alert: '⚠️',
  info: 'ℹ️',
  home: '🏠',
};

export const Icon: React.FC<IconProps> = ({
  name,
  size = 18,
  color = '#FFFFFF',
  style,
}) => {
  const symbol = IconSymbolMap[name] || '•';
  return (
    <Text
      style={[
        styles.iconText,
        { fontSize: size, color },
        style,
      ]}
      accessibilityRole="text"
    >
      {symbol}
    </Text>
  );
};

const styles = StyleSheet.create({
  iconText: {
    textAlign: 'center',
    includeFontPadding: false,
  },
});
