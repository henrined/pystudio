import { create } from 'zustand';
import { ThemeMode, Theme, getTheme } from '../theme';

export type ActivityTab =
  | 'home'
  | 'explorer'
  | 'editor'
  | 'search'
  | 'git'
  | 'debug'
  | 'marketplace'
  | 'ai'
  | 'settings';

export interface TabItem {
  id: string;
  title: string;
  path: string;
  isDirty?: boolean;
  language?: string;
}

export interface AppState {
  themeMode: ThemeMode;
  theme: Theme;
  activeTab: ActivityTab;
  isCommandPaletteOpen: boolean;
  isKeyboardVisible: boolean;
  
  // Status Bar state
  gitBranch: string;
  errorCount: number;
  warningCount: number;
  currentLine: number;
  currentCol: number;
  activeLanguage: string;
  encoding: string;
  eolMode: string;
  pythonVersion: string;
  
  // Open Editor Tabs
  openTabs: TabItem[];
  activeTabId: string | null;

  // Actions
  setThemeMode: (mode: ThemeMode) => void;
  setActiveTab: (tab: ActivityTab) => void;
  toggleCommandPalette: () => void;
  setCommandPaletteOpen: (open: boolean) => void;
  setKeyboardVisible: (visible: boolean) => void;
  setGitBranch: (branch: string) => void;
  setDiagnostics: (errors: number, warnings: number) => void;
  setCursorPosition: (line: number, col: number) => void;
  setActiveLanguage: (lang: string) => void;
  
  openFileTab: (tab: TabItem) => void;
  closeFileTab: (id: string) => void;
  setActiveFileTab: (id: string) => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  themeMode: 'dark',
  theme: getTheme('dark'),
  activeTab: 'home',
  isCommandPaletteOpen: false,
  isKeyboardVisible: false,

  gitBranch: 'main',
  errorCount: 0,
  warningCount: 0,
  currentLine: 1,
  currentCol: 1,
  activeLanguage: 'Python',
  encoding: 'UTF-8',
  eolMode: 'LF',
  pythonVersion: 'Python 3.11',

  openTabs: [],
  activeTabId: null,

  setThemeMode: (mode: ThemeMode) =>
    set({
      themeMode: mode,
      theme: getTheme(mode),
    }),

  setActiveTab: (tab: ActivityTab) => set({ activeTab: tab }),

  toggleCommandPalette: () =>
    set((state) => ({ isCommandPaletteOpen: !state.isCommandPaletteOpen })),

  setCommandPaletteOpen: (open: boolean) => set({ isCommandPaletteOpen: open }),

  setKeyboardVisible: (visible: boolean) => set({ isKeyboardVisible: visible }),

  setGitBranch: (branch: string) => set({ gitBranch: branch }),

  setDiagnostics: (errors: number, warnings: number) =>
    set({ errorCount: errors, warningCount: warnings }),

  setCursorPosition: (line: number, col: number) =>
    set({ currentLine: line, currentCol: col }),

  setActiveLanguage: (lang: string) => set({ activeLanguage: lang }),

  openFileTab: (tab: TabItem) => {
    const { openTabs } = get();
    const exists = openTabs.find((t) => t.id === tab.id || t.path === tab.path);
    if (exists) {
      set({ activeTabId: exists.id });
    } else {
      set({
        openTabs: [...openTabs, tab],
        activeTabId: tab.id,
      });
    }
  },

  closeFileTab: (id: string) => {
    const { openTabs, activeTabId } = get();
    const updated = openTabs.filter((t) => t.id !== id);
    let nextActiveId = activeTabId;
    if (activeTabId === id) {
      nextActiveId = updated.length > 0 ? updated[updated.length - 1].id : null;
    }
    set({ openTabs: updated, activeTabId: nextActiveId });
  },

  setActiveFileTab: (id: string) => set({ activeTabId: id }),
}));

export default useAppStore;
