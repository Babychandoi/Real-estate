import type { Page } from '@playwright/test';
import { focusProblem } from './focusCheck';

/** A stable id for the focused element (set once as data-ux-stop), its label and whether it sits in the overlay. */
export async function focusStop(
  page: Page,
): Promise<{ id: string; label: string; inside: boolean; segmented: boolean }> {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body) return { id: 'body', label: 'body', inside: false, segmented: false };
    if (!el.dataset.uxStop) el.dataset.uxStop = String(Math.random()).slice(2, 10);
    const name = (el.getAttribute('aria-label') || el.textContent || el.tagName).replace(/\s+/g, ' ').trim();
    return {
      id: el.dataset.uxStop,
      label: `${el.tagName.toLowerCase()} "${name.slice(0, 40)}"`,
      inside: Boolean(el.closest('[data-ux-scope]')),
      segmented: el instanceof HTMLInputElement && /^(date|datetime-local|time|month|week)$/.test(el.type),
    };
  });
}

/** Walk native Tab stops without collapsing a date/time input's internal subfields into a false modal wrap. */
export async function modalTabCycle(page: Page, modal: boolean) {
  const leaks: string[] = [];
  const invisible: string[] = [];
  const order: string[] = [];
  let repeated: string | null = null;
  for (let step = 0; step < 150; step += 1) {
    await page.keyboard.press('Tab');
    await page.waitForTimeout(120);
    const stop = await focusStop(page);
    if (modal && !stop.inside) leaks.push(`Tab #${step + 1} left the overlay to ${stop.label}`);
    const problem = await focusProblem(page);
    if (problem && !invisible.includes(problem)) invisible.push(problem);
    if (!modal) break;
    // Consecutive date/time segments share document.activeElement. They must progress naturally to the next
    // external control; all 150 native Tabs count toward the same bound, so a reset or stuck widget still fails.
    if (stop.segmented && stop.id === order.at(-1)) {
      if (step === 149) leaks.push('no wrap-around after 150 Tabs');
      continue;
    }
    if (order.includes(stop.id)) {
      repeated = stop.id;
      break;
    }
    order.push(stop.id);
    if (step === 149) leaks.push('no wrap-around after 150 Tabs');
  }
  let back: string | null = null;
  if (modal && order.length > 1) {
    await page.keyboard.press('Shift+Tab');
    back = (await focusStop(page)).id;
  }
  return { order, repeated, back, leaks, invisible };
}
