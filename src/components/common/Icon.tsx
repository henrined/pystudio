import React from 'react';
import { TextStyle } from 'react-native';
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';

export type IconName =
  | 'home'
  | 'folder'
  | 'folder-open'
  | 'code'
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
  | 'bell'
  | 'plus'
  | 'git-clone'
  | 'run'
  | 'terminal'
  | 'error'
  | 'warning';

type MaterialCommunityIconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];

const IconGlyphMap: Record<IconName, MaterialCommunityIconName> = {
  'home': 'home-outline',
  'folder': 'file-multiple-outline',
  'folder-open': 'folder-open-outline',
  'code': 'code-tags',
  'search': 'magnify',
  'git': 'source-branch',
  'bug': 'bug-play-outline',
  'extensions': 'puzzle-outline',
  'ai': 'creation-outline',
  'settings': 'cog-outline',
  'play': 'play',
  'stop': 'stop',
  'close': 'close',
  'menu': 'dots-vertical',
  'file': 'file-outline',
  'chevron-right': 'chevron-right',
  'chevron-down': 'chevron-down',
  'check': 'check',
  'alert': 'alert-circle-outline',
  'info': 'information-outline',
  'bell': 'bell-outline',
  'plus': 'plus',
  'git-clone': 'source-repository',
  'run': 'play-circle-outline',
  'terminal': 'console-line',
  'error': 'close-circle-outline',
  'warning': 'alert-outline',
};

interface IconProps {
  name: IconName;
  size?: number;
  color?: string;
  style?: TextStyle;
}

export const Icon: React.FC<IconProps> = ({
  name,
  size = 18,
  color = '#FFFFFF',
  style,
}) => {
  const glyphName = IconGlyphMap[name] || 'help-circle-outline';
  return (
    <MaterialCommunityIcons
      name={glyphName}
      size={size}
      color={color}
      style={style}
    />
  );
};
