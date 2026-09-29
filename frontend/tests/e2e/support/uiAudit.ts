import type { Page } from '@playwright/test';

/** Minimum distance between content and the viewport edge: 16 px on phones, 24 px from 768 px up. */
export const gutterFor = (viewportWidth: number) => (viewportWidth >= 768 ? 24 : 16);

/**
 * Gutter audit: no visible content box may sit closer than the gutter to the left or right edge of the viewport
 * (the vertical scrollbar is not part of it: the measurement uses `documentElement.clientWidth`).
 * A "content box" is text, media, a form control, a table or scroll region, or a bordered/filled/shadowed card with
 * rounded corners. Full-bleed bands (header, footer, hero background) have no text of their own and are not content
 * boxes; what they hold is checked. Anything inside a dialog is left to the dialog check, anything inside a sideways
 * scroll area is judged by that area's own frame, and a box marked (or inside one marked) `data-full-bleed` is exempt
 * (map canvases, carousels).
 */
export async function auditGutters(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const root = document.documentElement;
    const viewport = root.clientWidth;
    const min = viewport >= 768 ? 24 : 16;
    const findings: string[] = [];
    const style = (el: Element) => getComputedStyle(el);
    const describe = (el: Element) => {
      const text = (el.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 32);
      const classes = Array.from(el.classList).slice(0, 4).join('.');
      return `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${classes ? `.${classes}` : ''} "${text}"`;
    };
    const isVisible = (el: Element) => {
      const rect = el.getBoundingClientRect();
      const cs = style(el);
      return rect.width > 1 && rect.height > 1 && cs.visibility !== 'hidden' && cs.display !== 'none';
    };
    const hasOwnText = (el: Element) =>
      Array.from(el.childNodes).some((node) => node.nodeType === 3 && (node.textContent ?? '').trim().length > 0);
    const isCard = (el: Element) => {
      const cs = style(el);
      if (cs.display === 'inline' || cs.borderTopLeftRadius === '0px') return false;
      const bordered = parseFloat(cs.borderLeftWidth) > 0 && parseFloat(cs.borderRightWidth) > 0;
      const filled = cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && cs.backgroundColor !== 'transparent';
      return bordered || filled || cs.boxShadow !== 'none';
    };
    const clips = (el: Element) => ['auto', 'scroll', 'hidden', 'clip'].includes(style(el).overflowX);
    const candidates = Array.from(document.body.querySelectorAll('*')).filter((el) => {
      if (!isVisible(el)) return false;
      if (
        el.closest('[role="dialog"], .sr-only, .skip-link, svg, [data-full-bleed], .maplibregl-map, .ndc-admin-sidebar')
      )
        return false;
      const tag = el.tagName;
      if (['SCRIPT', 'STYLE', 'OPTION', 'PATH', 'BR'].includes(tag)) return false;
      const contentful =
        hasOwnText(el) ||
        ['IMG', 'INPUT', 'SELECT', 'TEXTAREA', 'TABLE', 'VIDEO', 'CANVAS', 'IFRAME'].includes(tag) ||
        el.getAttribute('role') === 'region' ||
        isCard(el);
      if (!contentful) return false;
      // Inside a sideways scroll area only the area's own frame counts (its items are scrolled by design).
      for (let up = el.parentElement; up && up !== document.body; up = up.parentElement) {
        if (up.scrollWidth > up.clientWidth + 1 && ['auto', 'scroll'].includes(style(up).overflowX)) return false;
      }
      return true;
    });
    /** Box of what is drawn: a card, control or table by its border box, plain text by the lines of its own text. */
    const drawnBox = (el: Element): DOMRect => {
      if (isCard(el) || !hasOwnText(el) || ['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON'].includes(el.tagName)) {
        return el.getBoundingClientRect();
      }
      const range = document.createRange();
      const box = { left: Infinity, right: -Infinity };
      for (const node of Array.from(el.childNodes)) {
        if (node.nodeType !== 3 || !(node.textContent ?? '').trim()) continue;
        range.selectNodeContents(node);
        for (const rect of Array.from(range.getClientRects())) {
          if (rect.width < 1) continue;
          box.left = Math.min(box.left, rect.left);
          box.right = Math.max(box.right, rect.right);
        }
      }
      if (box.left === Infinity) return el.getBoundingClientRect();
      // A truncated line (`text-overflow: ellipsis`) has text rectangles far wider than what is drawn: the element's own
      // box is what shows.
      const own = el.getBoundingClientRect();
      if (clips(el)) {
        box.left = Math.max(box.left, own.left);
        box.right = Math.min(box.right, own.right);
      }
      return new DOMRect(box.left, 0, Math.max(0, box.right - box.left), 1);
    };
    for (const el of candidates) {
      // Effective box: clipped by ancestors that clip (an overflow-hidden card), so decorative overflow never counts.
      const drawn = drawnBox(el);
      let left = drawn.left;
      let right = drawn.right;
      for (let up = el.parentElement; up && up !== document.body; up = up.parentElement) {
        if (!clips(up)) continue;
        const box = up.getBoundingClientRect();
        left = Math.max(left, box.left);
        right = Math.min(right, box.right);
      }
      if (right <= left) continue;
      const rightGap = viewport - right;
      if (left < min - 1 || rightGap < min - 1) {
        findings.push(
          `${describe(el)} left ${Math.round(left)} / right ${Math.round(rightGap)} px from the edge (need ${min})`,
        );
      }
    }
    return findings.slice(0, 12);
  });
}

/**
 * Field-row audit: controls (inputs, selects, textareas) whose fields sit side by side in one row of a grid or flex
 * container must start at the same y (±1 px). A hint printed between label and control used to push one control
 * lower than its neighbours; the description now sits under the control.
 */
export async function auditFieldRows(page: Page, scope?: string): Promise<string[]> {
  return page.evaluate((selector) => {
    const container: ParentNode = (selector && document.querySelector(selector)) || document.body;
    const style = (el: Element) => getComputedStyle(el);
    const visible = (el: Element) => {
      const rect = el.getBoundingClientRect();
      return rect.width > 1 && rect.height > 1 && style(el).visibility !== 'hidden' && style(el).display !== 'none';
    };
    const label = (el: Element) => {
      const input = el as HTMLInputElement;
      const named =
        input.getAttribute('aria-label') ||
        (input.labels && input.labels[0]?.textContent) ||
        input.getAttribute('placeholder') ||
        input.name ||
        input.id;
      return `${el.tagName.toLowerCase()}[${(named ?? '').replace(/\s+/g, ' ').trim().slice(0, 30)}]`;
    };
    const controls = Array.from(
      container.querySelectorAll(
        'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]):not([type="file"]), select, textarea',
      ),
    ).filter((el) => visible(el) && !el.closest('.sr-only'));
    const rows = new Map<Element, Array<{ cell: Element; control: Element }>>();
    for (const control of controls) {
      let cell: Element = control;
      let found: Element | null = null;
      while (cell.parentElement && cell !== document.body) {
        const parent: Element = cell.parentElement;
        const display = style(parent).display;
        if (display === 'grid' || display === 'flex' || display === 'inline-flex') {
          const box = cell.getBoundingClientRect();
          const sideBySide = Array.from(parent.children).some((sibling) => {
            if (sibling === cell || !visible(sibling)) return false;
            const other = sibling.getBoundingClientRect();
            const overlap = Math.min(box.bottom, other.bottom) - Math.max(box.top, other.top);
            const apart = other.left >= box.right - 1 || other.right <= box.left + 1;
            return apart && overlap > 4;
          });
          if (sideBySide) {
            found = parent;
            break;
          }
        }
        cell = parent;
      }
      if (!found) continue;
      const list = rows.get(found) ?? [];
      // One control per cell: the first one is the cell's own field.
      if (!list.some((entry) => entry.cell === cell)) list.push({ cell, control });
      rows.set(found, list);
    }
    const findings: string[] = [];
    for (const list of rows.values()) {
      for (let i = 0; i < list.length; i += 1) {
        for (let j = i + 1; j < list.length; j += 1) {
          const a = list[i].cell.getBoundingClientRect();
          const b = list[j].cell.getBoundingClientRect();
          const overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
          const apart = b.left >= a.right - 1 || b.right <= a.left + 1;
          if (!apart || overlap <= 4) continue;
          const topA = list[i].control.getBoundingClientRect().top;
          const topB = list[j].control.getBoundingClientRect().top;
          if (Math.abs(topA - topB) > 1) {
            findings.push(
              `${label(list[i].control)} at y=${Math.round(topA)} vs ${label(list[j].control)} at y=${Math.round(topB)}`,
            );
          }
        }
      }
    }
    return findings.slice(0, 12);
  }, scope);
}
