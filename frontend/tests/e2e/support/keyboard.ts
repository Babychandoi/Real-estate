import { expect, type Locator, type Page } from '@playwright/test';

/**
 * Keyboard-only driving for R-7 / DS-04: every move is a Tab / Shift+Tab / Enter / Space / arrow / typed key, never a
 * click. While tabbing, each focused element is checked:
 *  - it shows a focus indicator (outline or ring, WCAG 2.4.7);
 *  - it is not hidden behind a sticky header, a fixed bar or an overlay (WCAG 2.4.11: the centre of the element is the
 *    topmost thing on screen once it is scrolled into view);
 *  - while a modal is open, focus stays inside it (no focus leak).
 * Problems are collected per journey and asserted at the end, so one report lists every step that failed.
 */
export class KeyboardWalker {
  readonly problems: string[] = [];
  steps = 0;

  constructor(
    private readonly page: Page,
    private readonly journey: string,
  ) {}

  /** Tabs (forward, or backward with `reverse`) until `target` has focus; fails after `max` presses. */
  async tabTo(target: Locator, options: { max?: number; reverse?: boolean; within?: Locator } = {}) {
    const max = options.max ?? 150;
    for (let press = 0; press <= max; press += 1) {
      if (await isFocused(target)) return;
      await this.page.keyboard.press(options.reverse ? 'Shift+Tab' : 'Tab');
      this.steps += 1;
      await this.check(options.within);
    }
    throw new Error(`${this.journey}: ${max} Tab presses did not reach ${target}`);
  }

  async press(key: string, within?: Locator) {
    await this.page.keyboard.press(key);
    this.steps += 1;
    await this.check(within);
  }

  /**
   * Picks an option of the focused native <select> with the arrow keys. Where the platform opens the OS popup on an
   * arrow key instead (macOS), Playwright cannot press keys inside that popup; the value is then set with
   * selectOption on the focused element and the step is annotated — operating a native select is the browser's own
   * keyboard support, not the page's.
   */
  async chooseOption(select: Locator, value: string, within?: Locator) {
    for (let i = 0; i < 12 && (await select.inputValue()) !== value; i += 1) await this.press('ArrowDown', within);
    if ((await select.inputValue()) !== value) {
      await select.selectOption(value);
      this.notes.push(`native select ${value}: OS popup, set with selectOption`);
    }
  }

  readonly notes: string[] = [];

  /** Replaces the focused field's text by typing (select-all first). */
  async type(text: string) {
    await this.page.keyboard.press('ControlOrMeta+A');
    await this.page.keyboard.press('Backspace');
    await this.page.keyboard.type(text);
  }

  async check(within?: Locator) {
    // A focus transition (`transition-all` animates the outline) needs to finish before the style is read.
    await this.page.waitForTimeout(120);
    // Smooth scrolling brings the focused element into view over a few frames: wait until the page stops moving.
    await this.page.evaluate(async () => {
      let last = -1;
      for (let frame = 0; frame < 60; frame += 1) {
        await new Promise((resolve) => requestAnimationFrame(resolve));
        const now = window.scrollY + (document.querySelector('[aria-modal="true"]')?.scrollTop ?? 0);
        if (now === last) return;
        last = now;
      }
    });
    const problem = await this.page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body || el === document.documentElement) return null;
      const name = (el.getAttribute('aria-label') || el.textContent || el.tagName).replace(/\s+/g, ' ').trim();
      const label = `${el.tagName.toLowerCase()} "${name.slice(0, 50)}"`;
      // Containers focused on purpose (a page heading or dialog after a step change) need no ring.
      const programmatic = el.tabIndex === -1;
      const rect = el.getBoundingClientRect();
      // A visually hidden input (custom radio/checkbox) shows its focus on its label; its own outline is invisible.
      const hidden = rect.width <= 2 || rect.height <= 2;
      const drawn: Element | null = hidden ? ((el as HTMLInputElement).labels?.[0] ?? el.closest('label')) : el;
      if (!drawn) return `visually hidden ${label} has no label to show its focus`;
      const cs = getComputedStyle(drawn);
      const outline = cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 1;
      const ring = cs.boxShadow !== 'none';
      const after = getComputedStyle(drawn, '::after');
      const stretched = after.content !== 'none' && after.boxShadow !== 'none';
      if (!programmatic && el.matches(':focus-visible') && !outline && !ring && !stretched) {
        return `no visible focus indicator on ${label}`;
      }
      if (hidden) return null;
      if (rect.bottom <= 0 || rect.top >= window.innerHeight) return `focused ${label} is off screen`;
      const x = Math.min(Math.max(rect.left + rect.width / 2, 0), window.innerWidth - 1);
      const y = Math.min(Math.max(rect.top + Math.min(rect.height / 2, 20), 0), window.innerHeight - 1);
      const top = document.elementFromPoint(x, y);
      if (top && top !== el && !el.contains(top) && !top.contains(el) && !(top as HTMLElement).closest('label')) {
        const cover = top.closest('header, [role="dialog"], [class*="fixed"], [class*="sticky"]') ?? top;
        return `focused ${label} is covered by ${cover.tagName.toLowerCase()}.${String(cover.className).split(' ').slice(0, 3).join('.')}`;
      }
      return null;
    });
    if (problem && !this.problems.includes(problem)) this.problems.push(problem);
    if (within) {
      const inside = await within.evaluate((el) => el.contains(document.activeElement)).catch(() => false);
      if (!inside) {
        const what = await this.page.evaluate(() => document.activeElement?.outerHTML.slice(0, 80) ?? 'nothing');
        this.problems.push(`focus left the open dialog: ${what}`);
      }
    }
  }

  assertClean() {
    expect(this.problems, `${this.journey}: keyboard problems after ${this.steps} key presses`).toEqual([]);
  }
}

async function isFocused(target: Locator): Promise<boolean> {
  return target
    .first()
    .evaluate((el) => el === document.activeElement || el.contains(document.activeElement))
    .catch(() => false);
}
