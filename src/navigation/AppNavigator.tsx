import React from 'react';
import { useAppStore } from '../store/useAppStore';
import { MainLayout } from '../components/layout/MainLayout';
import { HomeScreen } from '../screens/HomeScreen';
import { ExplorerScreen } from '../screens/ExplorerScreen';
import { EditorScreen } from '../screens/EditorScreen';
import { SearchScreen } from '../screens/SearchScreen';
import { GitScreen } from '../screens/GitScreen';
import { DebugScreen } from '../screens/DebugScreen';
import { MarketplaceScreen } from '../screens/MarketplaceScreen';
import { AIScreen } from '../screens/AIScreen';
import { SettingsScreen } from '../screens/SettingsScreen';

export const AppNavigator: React.FC = () => {
  const { activeTab } = useAppStore();

  const renderActiveScreen = () => {
    switch (activeTab) {
      case 'home':
        return <HomeScreen />;
      case 'explorer':
        return <ExplorerScreen />;
      case 'editor':
        return <EditorScreen />;
      case 'search':
        return <SearchScreen />;
      case 'git':
        return <GitScreen />;
      case 'debug':
        return <DebugScreen />;
      case 'marketplace':
        return <MarketplaceScreen />;
      case 'ai':
        return <AIScreen />;
      case 'settings':
        return <SettingsScreen />;
      default:
        return <HomeScreen />;
    }
  };

  return <MainLayout>{renderActiveScreen()}</MainLayout>;
};
