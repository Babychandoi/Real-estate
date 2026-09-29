import type { Page } from '@playwright/test';

export interface LayoutFinding {
  kind: 'document' | 'spill' | 'text-spill' | 'x-scroll' | 'table-growth' | 'row-height';
  detail: string;
}

/**
 * Layout audit for pages that show user-supplied text. It runs in the page and reports:
 * - `document`: the page scrolls sideways;
 * - `spill`: an element whose box reaches past the box of the card, table cell or dialog that holds it (1 px
 *   tolerance). Anything inside a scroll/clip container that sits between the two (a table's horizontal scroll area,
 *   `overflow-hidden`, `truncate`) is fine, because it is clipped or scrollable on purpose;
 * - `text-spill`: text that overflows the box it is laid out in (`scrollWidth > clientWidth` on an element that neither
 *   clips nor scrolls), which is what "text spilling out of the card" looks like when the box itself does not grow;
 * - `x-scroll`: an element that scrolls sideways although it is not a data table's scroll area or a tab list (a drawer
 *   body, a wrapper around a table in a drawer, a card);
 * - `table-growth`: a table that scrolls sideways because one cell is stretched (a single cell wider than 560 px), i.e.
 *   one unbreakable value widened every row;
 * - `row-height`: cards side by side in one row of a grid/flex container whose heights differ by more than 2 px.
 */
export async function auditLayout(page: Page): Promise<LayoutFinding[]> {
  return page.evaluate(() => {
    const MAX_CELL_WIDTH = 560;
    const findings: LayoutFinding[] = [];
    const push = (kind: LayoutFinding['kind'], detail: string) => {
      if (findings.length < 60) findings.push({ kind, detail });
    };
    const root = document.documentElement;
    if (root.scrollWidth > root.clientWidth + 1) {
      push('document', `scrollWidth ${root.scrollWidth} > clientWidth ${root.clientWidth}`);
    }

    const describe = (el: Element) => {
      const text = (el.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 36);
      const classes = Array.from(el.classList).slice(0, 5).join('.');
      return `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${classes ? `.${classes}` : ''} "${text}"`;
    };
    const style = (el: Element) => getComputedStyle(el);
    const isVisible = (el: Element) => {
      const rect = el.getBoundingClientRect();
      const cs = style(el);
      return rect.width > 1 && rect.height > 1 && cs.visibility !== 'hidden' && cs.display !== 'none';
    };
    const clipsX = (el: Element) => style(el).overflowX !== 'visible';
    const isCard = (el: Element) => {
      const cs = style(el);
      if (cs.display === 'inline' || cs.borderTopLeftRadius === '0px') return false;
      const bordered = parseFloat(cs.borderTopWidth) > 0 || parseFloat(cs.borderLeftWidth) > 0;
      const filled = cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && cs.backgroundColor !== 'transparent';
      return bordered || filled || cs.boxShadow !== 'none';
    };
    const isContainer = (el: Element) => {
      const tag = el.tagName;
      if (tag === 'TD' || tag === 'TH' || tag === 'LI' || tag === 'ARTICLE') return true;
      if (el.getAttribute('role') === 'dialog') return true;
      return isCard(el) && el.getBoundingClientRect().height > 24;
    };

    if (root.scrollWidth > root.clientWidth + 1) {
      // Name the widest culprits: elements that reach past the viewport without a clipping ancestor.
      const culprits = Array.from(document.body.querySelectorAll('*'))
        .filter((el) => {
          const rect = el.getBoundingClientRect();
          if (rect.width <= 1 || rect.right <= root.clientWidth + 1 || style(el).position === 'fixed') return false;
          for (let up = el.parentElement; up && up !== document.body; up = up.parentElement) {
            if (clipsX(up) || style(up).position === 'fixed') return false;
          }
          return true;
        })
        .slice(0, 6);
      for (const el of culprits)
        push('document', `reaches ${Math.round(el.getBoundingClientRect().right)}: ${describe(el)}`);
    }

    const seen = new Set<Element>();
    const everything = Array.from(document.body.querySelectorAll('*')).filter(isVisible);
    for (const el of everything) {
      if (el.closest('.sr-only, [aria-hidden="true"] svg, svg')) continue;
      const rect = el.getBoundingClientRect();
      // Walk up: stop at the first clip/scroll ancestor or fixed-position box; the nearest card that el sticks out of
      // is the finding.
      let ancestor = el.parentElement;
      while (ancestor && ancestor !== document.body) {
        const cs = style(ancestor);
        if (cs.position === 'fixed' || style(el).position === 'fixed') break;
        if (clipsX(ancestor)) break;
        if (isContainer(ancestor)) {
          const box = ancestor.getBoundingClientRect();
          if (rect.right > box.right + 1 || rect.left < box.left - 1) {
            if (!seen.has(ancestor)) {
              seen.add(ancestor);
              push(
                'spill',
                `${describe(el)} [${Math.round(rect.left)}..${Math.round(rect.right)}] sticks out of ${describe(ancestor)} [${Math.round(box.left)}..${Math.round(box.right)}]`,
              );
            }
            break;
          }
        }
        ancestor = ancestor.parentElement;
      }

      // Text that overflows its own box.
      const cs = style(el);
      if (
        cs.display !== 'inline' &&
        cs.overflowX === 'visible' &&
        el.scrollWidth > el.clientWidth + 1 &&
        el.clientWidth > 0 &&
        el.tagName !== 'HTML' &&
        el.tagName !== 'BODY'
      ) {
        let clipped = false;
        for (let up = el.parentElement; up && up !== document.body; up = up.parentElement) {
          if (clipsX(up)) {
            clipped = true;
            break;
          }
        }
        if (!clipped)
          push('text-spill', `${describe(el)} scrollWidth ${el.scrollWidth} > clientWidth ${el.clientWidth}`);
      }
    }

    // Sideways scrolling is by design only in a data table's scroll area and in a tab list. Anywhere else (a drawer
    // body, a card, a wrapper around a table in a drawer) it means text or a table did not fit: the content is there,
    // but the person has to scroll a panel sideways to read it.
    for (const el of everything) {
      const cs = style(el);
      if (cs.overflowX !== 'auto' && cs.overflowX !== 'scroll') continue;
      if (el.scrollWidth <= el.clientWidth + 1) continue;
      const role = el.getAttribute('role');
      if (role === 'region' || role === 'tablist') continue;
      push(
        'x-scroll',
        `${describe(el)} scrolls sideways: scrollWidth ${el.scrollWidth} > clientWidth ${el.clientWidth}`,
      );
    }

    for (const table of Array.from(document.querySelectorAll('table')).filter(isVisible)) {
      let wrapper: Element | null = table.parentElement;
      while (wrapper && wrapper !== document.body && !clipsX(wrapper)) wrapper = wrapper.parentElement;
      const available = wrapper && wrapper !== document.body ? wrapper.clientWidth : root.clientWidth;
      const tableWidth = table.getBoundingClientRect().width;
      // Wider than the scroll area and than the table's own min-width (a phone-sized table that scrolls is by design).
      if (tableWidth <= Math.max(available, parseFloat(style(table).minWidth) || 0) + 1) continue;
      // The table scrolls sideways. That is normal for a dense table on a phone; it is a defect when one cell stretched
      // it (an unbreakable value), which shows as a single cell far wider than any column should be.
      const stretched = Array.from(table.querySelectorAll('td, th')).find(
        (cell) => cell.getBoundingClientRect().width > MAX_CELL_WIDTH,
      );
      if (stretched) {
        push(
          'table-growth',
          `${describe(table)} is ${Math.round(tableWidth)} px wide in ${Math.round(available)} px; ${describe(stretched)} alone is ${Math.round(stretched.getBoundingClientRect().width)} px`,
        );
      }
    }

    for (const parent of Array.from(document.body.querySelectorAll('*')).filter(isVisible)) {
      const display = style(parent).display;
      if (display !== 'grid' && display !== 'flex' && display !== 'inline-grid') continue;
      const cards = Array.from(parent.children).filter(
        (child) =>
          isVisible(child) &&
          isCard(child) &&
          child.getBoundingClientRect().height > 60 &&
          (child.textContent ?? '').trim().length > 20,
      );
      if (cards.length < 2) continue;
      const rows = new Map<number, Element[]>();
      for (const card of cards) {
        const top = Math.round(card.getBoundingClientRect().top / 4) * 4;
        rows.set(top, [...(rows.get(top) ?? []), card]);
      }
      for (const row of rows.values()) {
        if (row.length < 2) continue;
        const heights = row.map((card) => card.getBoundingClientRect().height);
        if (Math.max(...heights) - Math.min(...heights) > 2) {
          push(
            'row-height',
            `${describe(parent)}: heights ${heights.map((h) => Math.round(h)).join(' / ')} in one row`,
          );
        }
      }
    }
    return findings;
  });
}
