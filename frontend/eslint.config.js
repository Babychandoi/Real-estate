// ESLint flat config (F01.7): `npm run lint` is ESLint only; `npm run typecheck` is tsc.
import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig(
  {
    ignores: ['dist/**', 'coverage/**', 'playwright-report/**', 'test-results/**', 'node_modules/**'],
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
    plugins: { 'react-hooks': reactHooks },
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      // Design-system guard rails (DS-03, docs/design-system.md).
      'no-restricted-syntax': [
        'error',
        {
          selector:
            'JSXText[value=/[\\u2600-\\u27BF\\u2B50\\u2B55]|\\uD83C[\\uDF00-\\uDFFF]|\\uD83D[\\uDC00-\\uDE4F\\uDE80-\\uDEFF]|\\uD83E[\\uDD00-\\uDFFF]/]',
          message: 'Use a Lucide icon (with aria-hidden or a label) instead of an emoji or symbol character.',
        },
        {
          selector: 'Literal[value=/text-\\[(8|9|10|11)px\\]/]',
          message: 'Text below 12px is not allowed for information; use text-xs/text-label (12px) or larger.',
        },
        {
          selector: 'TemplateElement[value.raw=/text-\\[(8|9|10|11)px\\]/]',
          message: 'Text below 12px is not allowed for information; use text-xs/text-label (12px) or larger.',
        },
      ],
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
