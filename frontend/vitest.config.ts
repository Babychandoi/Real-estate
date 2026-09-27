import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

// Unit tests for pure modules and hooks (`npm run test:unit`). Browser journeys live in tests/e2e (Playwright).
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      include: ['app/**/*.test.{ts,tsx}', 'scripts/**/*.test.mjs'],
      environment: 'jsdom',
      restoreMocks: true,
      unstubGlobals: true,
      unstubEnvs: true,
    },
  }),
);
