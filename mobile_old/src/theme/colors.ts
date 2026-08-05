export interface ThemeColors {
  // Activity Bar
  activityBarBackground: string;
  activityBarForeground: string;
  activityBarActiveBorder: string;
  activityBarBadgeBackground: string;
  activityBarBadgeForeground: string;

  // Title Bar
  titleBarActiveBackground: string;
  titleBarActiveForeground: string;

  // Status Bar
  statusBarBackground: string;
  statusBarForeground: string;
  statusBarDebuggingBackground: string;
  statusBarNoFolderBackground: string;

  // Sidebar / Content
  sideBarBackground: string;
  sideBarForeground: string;
  sideBarBorder: string;
  sideBarTitleForeground: string;

  // Editor
  editorBackground: string;
  editorForeground: string;
  editorLineNumberForeground: string;
  editorCursorForeground: string;
  editorSelectionBackground: string;
  editorActiveLineBackground: string;

  // Command Palette & Overlays
  quickInputBackground: string;
  quickInputForeground: string;
  quickInputListHoverBackground: string;
  quickInputFocusBorder: string;
  modalOverlayBackground: string;

  // UI Components
  buttonBackground: string;
  buttonForeground: string;
  buttonHoverBackground: string;
  badgeBackground: string;
  badgeForeground: string;

  // Statuses
  errorForeground: string;
  warningForeground: string;
  infoForeground: string;
  successForeground: string;

  // Borders & Separators
  border: string;
  separator: string;
}

export const DarkPlusTheme: ThemeColors = {
  activityBarBackground: '#333333',
  activityBarForeground: '#FFFFFF',
  activityBarActiveBorder: '#007ACC',
  activityBarBadgeBackground: '#007ACC',
  activityBarBadgeForeground: '#FFFFFF',

  titleBarActiveBackground: '#3C3C3C',
  titleBarActiveForeground: '#CCCCCC',

  statusBarBackground: '#007ACC',
  statusBarForeground: '#FFFFFF',
  statusBarDebuggingBackground: '#CC6633',
  statusBarNoFolderBackground: '#68217A',

  sideBarBackground: '#252526',
  sideBarForeground: '#CCCCCC',
  sideBarBorder: '#1E1E1E',
  sideBarTitleForeground: '#BBBBBB',

  editorBackground: '#1E1E1E',
  editorForeground: '#D4D4D4',
  editorLineNumberForeground: '#858585',
  editorCursorForeground: '#AEAFAD',
  editorSelectionBackground: '#264F78',
  editorActiveLineBackground: '#282828',

  quickInputBackground: '#252526',
  quickInputForeground: '#CCCCCC',
  quickInputListHoverBackground: '#2A2D2E',
  quickInputFocusBorder: '#007ACC',
  modalOverlayBackground: 'rgba(0, 0, 0, 0.65)',

  buttonBackground: '#0E639C',
  buttonForeground: '#FFFFFF',
  buttonHoverBackground: '#1177BB',
  badgeBackground: '#4D4D4D',
  badgeForeground: '#FFFFFF',

  errorForeground: '#F48771',
  warningForeground: '#CCA700',
  infoForeground: '#75BEFF',
  successForeground: '#89D185',

  border: '#3C3C3C',
  separator: '#2B2B2B',
};

export const LightPlusTheme: ThemeColors = {
  activityBarBackground: '#2C2C2C',
  activityBarForeground: '#FFFFFF',
  activityBarActiveBorder: '#007ACC',
  activityBarBadgeBackground: '#007ACC',
  activityBarBadgeForeground: '#FFFFFF',

  titleBarActiveBackground: '#DDDDDD',
  titleBarActiveForeground: '#333333',

  statusBarBackground: '#007ACC',
  statusBarForeground: '#FFFFFF',
  statusBarDebuggingBackground: '#CC6633',
  statusBarNoFolderBackground: '#68217A',

  sideBarBackground: '#F3F3F3',
  sideBarForeground: '#333333',
  sideBarBorder: '#E5E5E5',
  sideBarTitleForeground: '#666666',

  editorBackground: '#FFFFFF',
  editorForeground: '#000000',
  editorLineNumberForeground: '#2B91AF',
  editorCursorForeground: '#000000',
  editorSelectionBackground: '#ADD6FF',
  editorActiveLineBackground: '#F3F3F3',

  quickInputBackground: '#F3F3F3',
  quickInputForeground: '#333333',
  quickInputListHoverBackground: '#E8E8E8',
  quickInputFocusBorder: '#007ACC',
  modalOverlayBackground: 'rgba(0, 0, 0, 0.4)',

  buttonBackground: '#007ACC',
  buttonForeground: '#FFFFFF',
  buttonHoverBackground: '#0062A3',
  badgeBackground: '#C4C4C4',
  badgeForeground: '#333333',

  errorForeground: '#CD3131',
  warningForeground: '#BF8803',
  infoForeground: '#1A85FF',
  successForeground: '#388A34',

  border: '#CCCCCC',
  separator: '#E5E5E5',
};
