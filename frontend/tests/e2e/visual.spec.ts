import { expect, test } from '@playwright/test';
import { FIXED_NOW, gotoReady, volatileRegions } from './support/helpers';

// F01.3: screenshots only — accessibility and overflow live in a11y.spec.ts. Determinism comes from the seeded data
// (fixed seed clock), a fixed browser clock, explicit readiness markers instead of sleeps, disabled animations and
// masked volatile regions (map canvas, dates). Baselines are updated only after the diff images were reviewed
// (F01.5); never run --update-snapshots to make a failing check green.

const PAGES = [
  { name: 'home', path: '/' },
  { name: 'search', path: '/search' },
  { name: 'compare', path: '/compare' },
] as const;

for (const { name, path } of PAGES) {
  test(`${name} matches the reviewed baseline`, async ({ page }, testInfo) => {
    await page.clock.setFixedTime(FIXED_NOW);
    await gotoReady(page, path);
    await expect(page).toHaveScreenshot(`${name}-${testInfo.project.name}.png`, {
      fullPage: true,
      mask: volatileRegions(page),
    });
  });
}
