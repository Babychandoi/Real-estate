import { defineConfig, devices } from '@playwright/test';

/*
 * E2E suites (tests/e2e): navigation, a11y, visual, auth-dialog, authenticated. CI runs them as separate
 * invocations with PLAYWRIGHT_SUITE=<name>, so every suite writes its own HTML report, JUnit file and failure
 * artefacts (traces, screenshot diffs, videos) and one failing suite never hides another.
 *
 * Target server:
 * - PLAYWRIGHT_BASE_URL=http://127.0.0.1:5311  → an app already running (scripts/e2e-local.sh, CI stack on :3000)
 * - PLAYWRIGHT_EXTERNAL_SERVER=1                → legacy switch for http://127.0.0.1:3000
 * - neither                                     → Playwright starts `vite` on :5173 (needs a backend behind /api)
 */
const suite = process.env.PLAYWRIGHT_SUITE ?? 'all';
const baseURL =
  process.env.PLAYWRIGHT_BASE_URL ??
  (process.env.PLAYWRIGHT_EXTERNAL_SERVER ? 'http://127.0.0.1:3000' : 'http://127.0.0.1:5173');
const managedServer = !process.env.PLAYWRIGHT_BASE_URL && !process.env.PLAYWRIGHT_EXTERNAL_SERVER;

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: `playwright-report/${suite}` }],
    ['junit', { outputFile: `test-results/${suite}/junit.xml` }],
  ],
  outputDir: `test-results/${suite}/artifacts`,
  // PLAYWRIGHT_SNAPSHOT_DIR lets the determinism check (scripts/e2e-local.sh --visual-determinism) compare two runs
  // against a throw-away directory without touching the reviewed baselines.
  snapshotPathTemplate: process.env.PLAYWRIGHT_SNAPSHOT_DIR
    ? `${process.env.PLAYWRIGHT_SNAPSHOT_DIR}/{testFilePath}/{arg}{ext}`
    : '{testDir}/{testFilePath}-snapshots/{arg}{ext}',
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.03, animations: 'disabled', caret: 'hide', scale: 'css' },
  },
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
  },
  projects: [
    { name: 'chromium-320', use: { ...devices['Desktop Chrome'], viewport: { width: 320, height: 800 } } },
    { name: 'chromium-768', use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 } } },
    { name: 'chromium-1440', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } },
    { name: 'android-chrome', use: { ...devices['Pixel 7'] } },
    { name: 'ios-safari', use: { ...devices['iPhone 15 Pro'] } },
    { name: 'webkit-desktop', use: { ...devices['Desktop Safari'], viewport: { width: 1440, height: 1000 } } },
    { name: 'firefox-desktop', use: { ...devices['Desktop Firefox'], viewport: { width: 1440, height: 1000 } } },
  ],
  webServer: managedServer
    ? { command: 'npx vite --host 127.0.0.1 --port 5173', url: 'http://127.0.0.1:5173', reuseExistingServer: true }
    : undefined,
});
