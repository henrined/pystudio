import { TextStyle } from 'react-native';

export const Fonts = {
  monospace: 'JetBrains Mono, Fira Code, monospace',
  sansSerif: 'System, sans-serif',
};

export const FontSizes = {
  xs: 10,
  sm: 12,
  md: 14,
  lg: 16,
  xl: 18,
  xxl: 22,
};

export const Typography: Record<string, TextStyle> = {
  title: {
    fontFamily: Fonts.sansSerif,
    fontSize: FontSizes.lg,
    fontWeight: '600',
  },
  subtitle: {
    fontFamily: Fonts.sansSerif,
    fontSize: FontSizes.md,
    fontWeight: '500',
  },
  body: {
    fontFamily: Fonts.sansSerif,
    fontSize: FontSizes.sm,
    fontWeight: '400',
  },
  code: {
    fontFamily: Fonts.monospace,
    fontSize: FontSizes.sm,
    fontWeight: '400',
  },
  caption: {
    fontFamily: Fonts.sansSerif,
    fontSize: FontSizes.xs,
    fontWeight: '400',
  },
  statusBarText: {
    fontFamily: Fonts.sansSerif,
    fontSize: FontSizes.xs,
    fontWeight: '500',
  },
};

export default FontSizes;
