import { DarkPlusTheme, LightPlusTheme, ThemeColors } from './colors';
import { Fonts, FontSizes, Typography } from './typography';

export type ThemeMode = 'dark' | 'light';

export interface Theme {
  mode: ThemeMode;
  colors: ThemeColors;
  typography: typeof Typography;
  spacing: {
    xs: number;
    sm: number;
    md: number;
    lg: number;
    xl: number;
  };
}

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
};

export const getTheme = (mode: ThemeMode): Theme => ({
  mode,
  colors: mode === 'dark' ? DarkPlusTheme : LightPlusTheme,
  typography: Typography,
  spacing: Spacing,
});

export * from './colors';
export * from './typography';
