import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  testMatch: [
    "**/ui/*.spec.ts",
    "**/e2e/authenticated-flows.spec.ts",
    "**/e2e/public-navigation.spec.ts",
  ],
  forbidOnly: !!process.env.CI,
  fullyParallel: true,
  workers: 4,
  retries: 0,
  expect: { timeout: 10_000 },
  reporter: [
    ["list"],
    ["json", { outputFile: "test-results/ui-results.json" }],
  ],
  use: {
    baseURL: "http://127.0.0.1:4173",
    browserName: "chromium",
    locale: "vi-VN",
    timezoneId: "Asia/Ho_Chi_Minh",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  projects: [360, 768, 1024, 1440].map((width) => ({
    name: `ui-${width}`,
    use: { viewport: { width, height: 960 } },
  })),
  webServer: {
    command: "node scripts/preview-ui.mjs",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !process.env.CI,
  },
});
