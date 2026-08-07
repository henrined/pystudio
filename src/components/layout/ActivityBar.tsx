import React from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import { useAppStore, ActivityTab } from '../../store/useAppStore';
import { Icon, IconName } from '../common/Icon';
import { Badge } from '../common/Badge';

interface ActivityItem {
  id: ActivityTab;
  icon: IconName;
  label: string;
  badgeCount?: number;
}

export const ActivityBar: React.FC = () => {
  const { activeTab, setActiveTab, theme, errorCount } = useAppStore();

  const mainItems: ActivityItem[] = [
    { id: 'home', icon: 'home', label: 'Accueil' },
    { id: 'explorer', icon: 'folder', label: 'Explorateur' },
    { id: 'editor', icon: 'code', label: 'Éditeur' },
    { id: 'search', icon: 'search', label: 'Recherche' },
    { id: 'git', icon: 'git', label: 'Source Control' },
    { id: 'debug', icon: 'bug', label: 'Débogage', badgeCount: errorCount },
    { id: 'marketplace', icon: 'extensions', label: 'Extensions' },
    { id: 'ai', icon: 'ai', label: 'IA Assistant' },
  ];

  const bottomItems: ActivityItem[] = [
    { id: 'settings', icon: 'settings', label: 'Paramètres' },
  ];

  const renderItem = (item: ActivityItem) => {
    const isActive = activeTab === item.id;
    return (
      <TouchableOpacity
        key={item.id}
        activeOpacity={0.7}
        onPress={() => setActiveTab(item.id)}
        accessibilityRole="tab"
        accessibilityLabel={item.label}
        accessibilityState={{ selected: isActive }}
        style={[
          styles.item,
          isActive && {
            borderLeftColor: theme.colors.activityBarActiveBorder,
            borderLeftWidth: 2,
            backgroundColor: 'rgba(255, 255, 255, 0.05)',
          },
        ]}
      >
        <Icon
          name={item.icon}
          size={24}
          color={
            isActive
              ? theme.colors.activityBarForeground
              : 'rgba(255, 255, 255, 0.5)'
          }
        />
        {item.badgeCount ? (
          <Badge
            count={item.badgeCount}
            backgroundColor={theme.colors.activityBarBadgeBackground}
            textColor={theme.colors.activityBarBadgeForeground}
            style={styles.badge}
          />
        ) : null}
      </TouchableOpacity>
    );
  };

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: theme.colors.activityBarBackground },
      ]}
    >
      <View style={styles.mainItems}>
        {mainItems.map(renderItem)}
      </View>
      <View style={styles.spacer} />
      <View style={styles.bottomItems}>
        {bottomItems.map(renderItem)}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: 48,
    height: '100%',
    flexDirection: 'column',
    alignItems: 'center',
    paddingVertical: 4,
  },
  mainItems: {
    width: '100%',
    alignItems: 'center',
  },
  spacer: {
    flex: 1,
  },
  bottomItems: {
    width: '100%',
    alignItems: 'center',
  },
  item: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    borderLeftWidth: 2,
    borderLeftColor: 'transparent',
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
  },
});
