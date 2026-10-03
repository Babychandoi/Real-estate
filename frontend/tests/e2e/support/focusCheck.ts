import type { Page } from '@playwright/test';

/**
 * Is the keyboard focus visible (WCAG 2.4.7)? The focused element must LOOK different from the same element unfocused:
 * an outline at least 2 px wide at 50 % opacity or more, or a box-shadow (≥ 50 % opacity) / ::after shadow / background /
 * border that changes on focus — on the element itself, on the label that draws a visually hidden input's focus, or
 * on the wrapper that shows a `focus-within` ring. A transparent outline (Tailwind `outline-none`) or a shadow the
 * control also has at rest does not count (review of PR #25, major 3).
 *
 * Returns null when the focus is visible (or nothing / a programmatic `tabindex=-1` target is focused), else a
 * description of the focused element.
 */
export async function focusProblem(page: Page): Promise<string | null> {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body || el === document.documentElement) return null;
    // Containers focused on purpose (a step heading, a dialog panel) need no ring.
    if (el.tabIndex === -1 && !el.matches('input, select, textarea, button, a[href]')) return null;
    if (!el.matches(':focus-visible')) return null;
    const name = (el.getAttribute('aria-label') || el.textContent || el.tagName).replace(/\s+/g, ' ').trim();
    const label = `${el.tagName.toLowerCase()} "${name.slice(0, 50)}"`;
    const rect = el.getBoundingClientRect();
    const hidden = rect.width <= 2 || rect.height <= 2;
    const drawn = hidden ? ((el as HTMLInputElement).labels?.[0] ?? el.closest('label')) : el;
    const parts = [drawn, el.closest('label'), el.parentElement].filter(Boolean) as Element[];
    const alpha = (color: string) => {
      const m = color.match(/rgba?\(([^)]+)\)/);
      if (!m) return color === 'transparent' ? 0 : 1;
      const values = m[1].split(/[\s,/]+/).filter(Boolean);
      return values.length > 3 ? parseFloat(values[3]) : 1;
    };
    const shadowVisible = (shadow: string) =>
      shadow !== 'none' && (shadow.match(/rgba?\([^)]+\)/g) ?? []).some((color) => alpha(color) >= 0.5);
    const look = (targets = parts) =>
      targets.map((part) => {
        const cs = getComputedStyle(part);
        const after = getComputedStyle(part, '::after');
        const outline =
          cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 2 && alpha(cs.outlineColor) >= 0.5
            ? `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`
            : '';
        return {
          outline,
          shadow: shadowVisible(cs.boxShadow) ? cs.boxShadow : '',
          after: shadowVisible(after.boxShadow) ? after.boxShadow : '',
          background: cs.backgroundColor,
          border: cs.borderColor,
        };
      });
    const focused = look();
    // Date/time inputs have native subfields. Blur/focus resets their selected segment, making the next real Tab
    // revisit the same subfield forever. Compare a focus-free clone in the same CSS context instead; the original
    // input and its native keyboard state stay untouched. Ordinary controls retain the independent blur probe.
    const segmented = el instanceof HTMLInputElement && /^(date|datetime-local|time|month|week)$/.test(el.type);
    let resting: ReturnType<typeof look>;
    if (segmented) {
      resting = parts.map((part) => {
        const clone = part.cloneNode(true) as HTMLElement;
        clone.setAttribute('inert', '');
        clone.setAttribute('aria-hidden', 'true');
        clone.style.position = 'fixed';
        clone.style.top = '-10000px';
        clone.style.pointerEvents = 'none';
        part.parentElement?.append(clone);
        try {
          return look([clone])[0];
        } finally {
          clone.remove();
        }
      });
    } else {
      el.blur();
      resting = look();
      el.focus({ preventScroll: true });
    }
    const changed = focused.some((now, i) => {
      const before = resting[i];
      return (
        (now.outline && now.outline !== before.outline) ||
        (now.shadow && now.shadow !== before.shadow) ||
        (now.after && now.after !== before.after) ||
        now.background !== before.background ||
        now.border !== before.border
      );
    });
    return changed ? null : `no visible focus indicator on ${label}`;
  });
}
