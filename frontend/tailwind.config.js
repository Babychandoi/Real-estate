import { readFileSync } from 'node:fs';
import daisyui from 'daisyui';

/*
 * Theme = app/styles/tokens.css (single token source, DS-01). Every colour below resolves to a CSS custom
 * property, so opacity modifiers (`bg-primary/10`) keep working and existing class names are unchanged.
 * daisyUI needs literal colours, so its theme is derived from the same file at build time.
 */
const tokensCss = readFileSync(new URL('./app/styles/tokens.css', import.meta.url), 'utf8');

/** `rgb(var(--color-<name>) / <alpha-value>)` for Tailwind; fails the build if the token does not exist. */
function color(name) {
  tokenChannels(name);
  return `rgb(var(--color-${name}) / <alpha-value>)`;
}

function tokenChannels(name) {
  const match = tokensCss.match(new RegExp(`--color-${name}:\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*;`));
  if (!match) throw new Error(`Missing colour token --color-${name} in app/styles/tokens.css`);
  return match.slice(1).map(Number);
}

function tokenHex(name) {
  return `#${tokenChannels(name)
    .map((channel) => channel.toString(16).padStart(2, '0'))
    .join('')}`;
}

/** Tailwind colour group: DEFAULT, on, container, on-container (+ optional fixed variants). */
function palette(name, { fixed = false } = {}) {
  return {
    DEFAULT: color(name),
    on: color(`on-${name}`),
    container: color(`${name}-container`),
    'on-container': color(`on-${name}-container`),
    ...(fixed ? { fixed: color(`${name}-fixed`), 'fixed-dim': color(`${name}-fixed-dim`) } : {}),
  };
}

const type = (name, extra = {}) => [`var(--text-${name})`, { lineHeight: `var(--leading-${name})`, ...extra }];

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './app/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: palette('primary', { fixed: true }),
        secondary: palette('secondary', { fixed: true }),
        tertiary: palette('tertiary', { fixed: true }),
        success: palette('success'),
        warning: palette('warning'),
        error: palette('error'),
        info: palette('info'),
        surface: {
          DEFAULT: color('surface'),
          dim: color('surface-dim'),
          bright: color('surface-bright'),
          variant: color('surface-variant'),
          container: color('surface-container'),
          'container-low': color('surface-container-low'),
          'container-lowest': color('surface-container-lowest'),
          'container-high': color('surface-container-high'),
          'container-highest': color('surface-container-highest'),
        },
        'on-surface': color('on-surface'),
        'on-surface-variant': color('on-surface-variant'),
        'inverse-surface': color('inverse-surface'),
        'inverse-on-surface': color('inverse-on-surface'),
        outline: {
          DEFAULT: color('outline'),
          variant: color('outline-variant'),
        },
        focus: color('focus-ring'),
      },
      fontFamily: {
        sans: 'var(--font-sans)',
        display: 'var(--font-sans)',
      },
      fontSize: {
        display: type('display', { letterSpacing: '-0.02em', fontWeight: '700' }),
        'display-mobile': type('display-mobile', { letterSpacing: '-0.02em', fontWeight: '700' }),
        'headline-lg': type('headline-lg', { letterSpacing: '-0.01em', fontWeight: '700' }),
        'headline-md': type('headline-md', { fontWeight: '600' }),
        'headline-sm': type('headline-sm', { fontWeight: '600' }),
        body: type('body'),
        'body-sm': type('body-sm'),
        label: type('label', { fontWeight: '600' }),
      },
      borderRadius: {
        sm: '0.25rem',
        DEFAULT: 'var(--radius-input)',
        md: 'var(--radius-card)',
        lg: 'var(--radius-panel)',
        xl: '1.5rem',
        full: '9999px',
        input: 'var(--radius-input)',
        card: 'var(--radius-card)',
        dialog: 'var(--radius-dialog)',
        panel: 'var(--radius-panel)',
        pill: 'var(--radius-pill)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        'card-hover': 'var(--shadow-card-hover)',
        elevated: 'var(--shadow-elevated)',
        cta: 'var(--shadow-cta)',
      },
      spacing: {
        gutter: '1rem',
        'gutter-mobile': '0.75rem',
        margin: '2rem',
        'margin-mobile': '1rem',
        'space-xs': '0.25rem',
        'space-sm': '0.5rem',
        'space-md': '1rem',
        'space-lg': '1.5rem',
        'space-xl': '2.5rem',
        'control-sm': 'var(--control-sm)',
        'control-md': 'var(--control-md)',
        'control-lg': 'var(--control-lg)',
        'icon-sm': 'var(--icon-sm)',
        'icon-md': 'var(--icon-md)',
        'icon-lg': 'var(--icon-lg)',
      },
      outlineWidth: {
        focus: 'var(--focus-ring-width)',
      },
      outlineOffset: {
        focus: 'var(--focus-ring-offset)',
      },
      zIndex: {
        header: 'var(--z-header)',
        overlay: 'var(--z-overlay)',
        toast: 'var(--z-toast)',
      },
      transitionDuration: {
        fast: 'var(--duration-fast)',
        base: 'var(--duration-base)',
      },
      transitionTimingFunction: {
        standard: 'var(--ease-standard)',
      },
    },
  },
  plugins: [daisyui],
  daisyui: {
    themes: [
      {
        bdsTheme: {
          primary: tokenHex('primary'),
          'primary-content': tokenHex('on-primary'),
          secondary: tokenHex('secondary'),
          'secondary-content': tokenHex('on-secondary'),
          accent: tokenHex('tertiary-container'),
          'accent-content': tokenHex('on-tertiary'),
          neutral: tokenHex('inverse-surface'),
          'neutral-content': tokenHex('inverse-on-surface'),
          'base-100': tokenHex('surface-container-lowest'),
          'base-200': tokenHex('surface'),
          'base-300': tokenHex('surface-container'),
          info: tokenHex('info'),
          success: tokenHex('success'),
          warning: tokenHex('warning'),
          error: tokenHex('error'),
        },
      },
    ],
  },
};
