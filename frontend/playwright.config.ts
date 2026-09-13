import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir:'./tests/e2e', fullyParallel:true, forbidOnly:!!process.env.CI, retries:process.env.CI?2:0,
  reporter:[['html',{open:'never'}],['junit',{outputFile:'test-results/junit.xml'}]],
  expect:{toHaveScreenshot:{maxDiffPixelRatio:0.015,animations:'disabled'}},
  use:{baseURL:process.env.PLAYWRIGHT_BASE_URL||(process.env.PLAYWRIGHT_EXTERNAL_SERVER?'http://127.0.0.1:3000':'http://127.0.0.1:5173'),trace:'retain-on-failure',screenshot:'only-on-failure',locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh'},
  projects:[
    {name:'chromium-320',use:{...devices['Desktop Chrome'],viewport:{width:320,height:800}}},
    {name:'chromium-768',use:{...devices['Desktop Chrome'],viewport:{width:768,height:1024}}},
    {name:'chromium-1440',use:{...devices['Desktop Chrome'],viewport:{width:1440,height:1000}}},
    {name:'android-chrome',use:{...devices['Pixel 7']}},
    {name:'ios-safari',use:{...devices['iPhone 15 Pro']}},
    {name:'webkit-desktop',use:{...devices['Desktop Safari'],viewport:{width:1440,height:1000}}},
    {name:'firefox-desktop',use:{...devices['Desktop Firefox'],viewport:{width:1440,height:1000}}}
  ],
  webServer:process.env.PLAYWRIGHT_EXTERNAL_SERVER?undefined:{command:'npx vite --host 127.0.0.1 --port 5173',url:'http://127.0.0.1:5173',reuseExistingServer:true},
});
