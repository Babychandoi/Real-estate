import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("authentication dialog is keyboard accessible", async ({ page }) => {
  await page.goto("/");
  const menu = page.getByRole("button", { name: "Mở menu", exact: true });
  if (await menu.isVisible()) {
    await menu.click();
    await page.getByRole("button", { name: "Đăng nhập / Đăng ký" }).click();
  } else {
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  }
  const dialog = page.getByRole("dialog", { name: "Đăng nhập", exact: true });
  await expect(dialog).toBeVisible();
  expect(
    await dialog.evaluate((node) => node.contains(document.activeElement)),
  ).toBeTruthy();
  const results = await new AxeBuilder({ page })
    .include("[role=dialog]")
    .withTags(["wcag2a", "wcag2aa"])
    .analyze();
  expect(results.violations).toEqual([]);
  for (let step = 0; step < 15; step += 1) {
    await page.keyboard.press("Tab");
    expect(
      await dialog.evaluate((node) => node.contains(document.activeElement)),
    ).toBeTruthy();
  }
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
});
