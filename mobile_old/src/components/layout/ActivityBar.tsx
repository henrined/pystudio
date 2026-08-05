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

  const items: ActivityItem[] = [
    { id: 'home', icon: 'home', label: 'Home' },
    { id: 'explorer', icon: 'folder', label: 'Explorer' },
    { id: 'search', icon: 'search', label: 'Search' },
    { id: 'git', icon: 'git', label: 'Git' },
    { id: 'debug', icon: 'bug', label: 'Debug', badgeCount: errorCount },
    { id: 'marketplace', icon: 'extensions', label: 'Marketplace' },
    { id: 'ai', icon: 'ai', label: 'AI Assistant' },
    { id: 'settings', icon: 'settings', label: 'Settings' },
  ];

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: theme.colors.activityBarBackground },
      ]}
    >
      {items.map((item) => {
        const isActive = activeTab === item.id;
        return (
          <TouchableOpacity
            key={item.id}
            activeOpacity={0.7}
            onPress={() => setActiveTab(item.id)}
            style={[
              styles.item,
              isActive && {
                borderLeftColor: theme.colors.activityBarActiveBorder,
                borderLeftWidth: 3,
                backgroundColor: 'rgba(255, 255, 255, 0.05)',
              },
            ]}
          >
            <Icon
              name={item.icon}
              size={20}
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
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: 48,
    height: '100%',
    alignItems: 'center',
    paddingVertical: 8,
  },
  item: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    marginVertical: 2,
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
  },
});
