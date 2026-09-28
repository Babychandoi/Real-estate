// ESLint flat config (F01.7): `npm run lint` is ESLint only; `npm run typecheck` is tsc.
import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import designSystem from './eslint-design-system-plugin.js';

// Emoji/pictograph/symbol ranges (m6): dingbats, arrows, misc technical/symbols, enclosed alphanumerics
// (covers the ● U+25CF this rule used to miss), the wide misc-symbols-and-arrows block (⬆ U+2B06, ⭐ U+2B50…),
// the emoji presentation selector, and the three surrogate-pair lead bytes that cover essentially all emoji.
const EMOJI_PATTERN =
  '[\\u2190-\\u21FF\\u2300-\\u24FF\\u25A0-\\u27BF\\u2900-\\u29FF\\u2B00-\\u2BFF\\uFE0F]|\\uD83C[\\uDC00-\\uDFFF]|\\uD83D[\\uDC00-\\uDFFF]|\\uD83E[\\uDC00-\\uDFFF]';

export default defineConfig(
  {
    ignores: [
      'dist/**',
      'coverage/**',
      'playwright-report/**',
      'test-results/**',
      'node_modules/**',
      // S9 (F22.4): generated from the backend's OpenAPI snapshot (npm run gen:api); never hand-edited.
      'app/shared/api/generated/**',
    ],
  },
  {
    files: ['**/*.{js,mjs,cjs,ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
    },
    linterOptions: {
      reportUnusedDisableDirectives: 'error',
    },
    rules: {
      // tsc (noUnusedLocals/noUnusedParameters) already reports unused code; keep one source of truth
      // but still allow the conventional `_` prefix for intentionally unused bindings.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' },
      ],
      '@typescript-eslint/consistent-type-imports': ['error', { fixStyle: 'inline-type-imports' }],
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      'no-console': ['error', { allow: ['warn', 'error'] }],
    },
  },
  {
    // Application code runs in the browser and renders React.
    files: ['app/**/*.{ts,tsx}'],
    extends: [jsxA11y.flatConfigs.recommended],
    plugins: { 'react-hooks': reactHooks, local: designSystem },
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      // Design-system guard rails (DS-03, docs/design-system.md). Emoji/symbol text is checked wherever it could
      // appear as an "icon" — JSX text, string literals (`aria-label="⭐"`, `const label = '🏠 …'`) and template
      // literals — not only JSXText (m6). Text-size checks (px/rem/em arbitrary classes, inline fontSize) are
      // local/no-tiny-text and local/no-tiny-inline-font-size below, which do the arithmetic a regex cannot.
      'no-restricted-syntax': [
        'error',
        {
          selector: `JSXText[value=/${EMOJI_PATTERN}/]`,
          message: 'Use a Lucide icon (with aria-hidden or a label) instead of an emoji or symbol character.',
        },
        {
          selector: `Literal[value=/${EMOJI_PATTERN}/]`,
          message: 'Use a Lucide icon (with aria-hidden or a label) instead of an emoji or symbol character.',
        },
        {
          selector: `TemplateElement[value.raw=/${EMOJI_PATTERN}/]`,
          message: 'Use a Lucide icon (with aria-hidden or a label) instead of an emoji or symbol character.',
        },
      ],
      'local/no-tiny-text': 'error',
      'local/no-tiny-inline-font-size': 'error',
    },
  },
  {
    // Build tooling, Playwright specs and scripts run in Node.
    files: ['*.config.{js,ts}', 'scripts/**/*.{js,mjs}', 'tests/**/*.ts'],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    // Command-line scripts report on stdout.
    files: ['scripts/**/*.{js,mjs}'],
    rules: { 'no-console': 'off' },
  },
  {
    // Unit tests run in Vitest with jsdom.
    files: ['app/**/*.test.{ts,tsx}'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
  },
);
