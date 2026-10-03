import type { Page } from '@playwright/test';

/**
 * DS-03 measurement, run in the page on whatever is visible right now (a page, or a page with a dialog/sheet open):
 *
 * - `tiny-text`: visible text rendered below 12 px;
 * - `small-important-text`: price, status, error or action text below 14 px. "Price" is text inside a Money element
 *   (`data-price`), "status" a live region (`role=status`), "error" an alert or an error message (`role=alert`,
 *   `data-error`), "action" the label of a control-sized (≤ 64 px tall) button, tab, menu item or a link styled
 *   as a button (`data-action`).
 *   Badges (`data-badge`) are the design system's 12 px label size (audit §8.2 "label 12/16", DESIGN_SYSTEM "Badge/
 *   caption: 12 px") and are not "status text" in this sense, even inside a button;
 * - `small-target`: an interactive element smaller than the target size. Touch viewports: 44 × 44 CSS px (the product
 *   rule, audit §8.2 "Control 44–48px trên touch"). Desktop: 24 × 24 CSS px (WCAG 2.2 SC 2.5.8) — a smaller target
 *   passes only with the SC's spacing exception: a 24 px circle centred on it overlaps no other target and no other
 *   undersized target's circle;
 * - `non-lucide-icon`: a visible inline `<svg>` that is not a Lucide icon (Lucide renders `svg.lucide`);
 * - `emoji`: a character drawn as an emoji in visible text (© ™ ↔ and other text symbols are typography).
 *
 * Exceptions written into the measurement itself (WCAG 2.5.8 wording): a link inside a sentence (inline exception),
 * a disabled control, a control inside an `inert` or `aria-hidden` subtree, a visually hidden input whose label is the
 * target (the label box is measured instead), and the map's own canvas controls are measured like everything else.
 * Anything else that is accepted must be in the caller's allowlist with a written reason.
 */
export type UiFindingKind = 'tiny-text' | 'small-important-text' | 'small-target' | 'non-lucide-icon' | 'emoji';

export interface UiFinding {
  kind: UiFindingKind;
  detail: string;
}

export interface UiAllowRule {
  /** Matched with `element.closest(selector)`. */
  selector: string;
  kinds: UiFindingKind[];
  /** Why this is accepted (shows up in the spec next to the rule; required). */
  reason: string;
}

export interface UiAuditOptions {
  /** Touch viewport: targets must be 44 × 44; otherwise 24 × 24 with the spacing exception. */
  touch: boolean;
  allow?: UiAllowRule[];
  /** Only look inside this element (e.g. the open dialog). */
  scope?: string;
  /** Skip the text checks (used when the root font size is changed on purpose). */
  skipText?: boolean;
}

export async function auditUi(page: Page, options: UiAuditOptions): Promise<UiFinding[]> {
  return page.evaluate(
    ({ touch, allow, scope, skipText }) => {
      const findings: UiFinding[] = [];
      const seen = new Set<string>();
      const root: Element = (scope && document.querySelector(scope)) || document.body;
      const push = (kind: UiFindingKind, el: Element, detail: string) => {
        if (allow.some((rule) => rule.kinds.includes(kind) && el.closest(rule.selector))) return;
        const line = `${describe(el)} ${detail}`;
        const key = `${kind}|${line}`;
        if (seen.has(key) || findings.length >= 80) return;
        seen.add(key);
        findings.push({ kind, detail: line });
      };
      function describe(el: Element): string {
        const text = (el.getAttribute('aria-label') || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 40);
        const classes = Array.from(el.classList)
          .filter((name) => !name.includes(':'))
          .slice(0, 4)
          .join('.');
        return `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${classes ? `.${classes}` : ''} "${text}"`;
      }
      const style = (el: Element) => getComputedStyle(el);
      // Only a modal that is actually on screen covers the page (a display:none one does not, review minor 6).
      const openModals = () =>
        Array.from(document.querySelectorAll('[aria-modal="true"]')).filter((modal) => {
          const rect = modal.getBoundingClientRect();
          const cs = getComputedStyle(modal);
          return rect.width > 1 && rect.height > 1 && cs.visibility !== 'hidden';
        });
      const modals = openModals();
      const hiddenTree = (el: Element) =>
        Boolean(el.closest('[inert], [aria-hidden="true"], .sr-only, template, noscript')) ||
        // A modal is open and this element is behind it: not reachable, not measured.
        (modals.length > 0 &&
          !modals.some((modal) => modal.contains(el)) &&
          !el.closest('[role="alert"], [role="status"], [data-modal-keep]'));
      const visible = (el: Element) => {
        const rect = el.getBoundingClientRect();
        if (rect.width < 2 || rect.height < 2) return false;
        for (let up: Element | null = el; up; up = up.parentElement) {
          const cs = style(up);
          if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
        }
        return true;
      };

      // --- text --------------------------------------------------------------------------------------------------
      // Characters drawn as emoji: those with emoji presentation by default (✅ ❌ ⭐ 🏠 …) and any pictograph forced
      // into emoji style with VS16. Text symbols such as © ™ ↔ → stay typography and are not flagged.
      const emoji = /\p{Emoji_Presentation}|\p{Extended_Pictographic}\uFE0F/u;
      if (!skipText) {
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        const checked = new Set<Element>();
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
          const text = (node.textContent ?? '').trim();
          if (!text) continue;
          const parent = node.parentElement;
          if (!parent || checked.has(parent)) continue;
          if (['SCRIPT', 'STYLE', 'NOSCRIPT', 'OPTION', 'TITLE'].includes(parent.tagName)) continue;
          if (parent.closest('.sr-only, .maplibregl-ctrl-attrib, svg')) continue;
          if (!visible(parent) || hiddenTree(parent)) continue;
          checked.add(parent);
          const size = parseFloat(style(parent).fontSize);
          if (emoji.test(text)) push('emoji', parent, `contains an emoji: "${text.slice(0, 30)}"`);
          if (size < 12 - 0.01) {
            push('tiny-text', parent, `${size}px`);
            continue;
          }
          if (parent.closest('[data-badge]')) continue;
          // Action text is the label of a control-sized target; a whole card made clickable (a row button 150 px
          // tall) holds content, judged as content.
          const control = parent.closest(
            'button, [role="button"], [role="tab"], [role="menuitem"], [role="menuitemradio"], [data-action]',
          );
          const important =
            (parent.closest('[data-price]') && 'price') ||
            (parent.closest('[role="alert"], [data-error]') && 'error') ||
            (parent.closest('[role="status"]') && 'status') ||
            (control && control.getBoundingClientRect().height <= 64 && 'action');
          if (important && size < 14 - 0.01) push('small-important-text', parent, `${important} text ${size}px`);
        }
        // Text inside form controls (a typed value, a placeholder, the chosen option) is not a text node: measure the
        // control's own font size (review minor 6). Inputs are information as well as action: 12 px minimum.
        const fields = root.querySelectorAll(
          'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]):not([type="file"]):not([type="range"]):not([type="color"]), select, textarea',
        );
        for (const field of Array.from(fields)) {
          if (!visible(field) || hiddenTree(field)) continue;
          const size = parseFloat(style(field).fontSize);
          if (size < 12 - 0.01) push('tiny-text', field, `form control text ${size}px`);
        }
      }

      // --- icons -------------------------------------------------------------------------------------------------
      for (const svg of Array.from(root.querySelectorAll('svg'))) {
        if (svg.parentElement?.closest('svg')) continue;
        if (!visible(svg) || svg.closest('.maplibregl-map, [data-allow-svg]')) continue;
        if (!svg.classList.contains('lucide')) push('non-lucide-icon', svg, 'is not a Lucide icon');
      }

      // --- targets -----------------------------------------------------------------------------------------------
      const SELECTOR = [
        'a[href]',
        'button',
        'input:not([type="hidden"])',
        'select',
        'textarea',
        'summary',
        '[role="button"]',
        '[role="link"]',
        '[role="tab"]',
        '[role="checkbox"]',
        '[role="radio"]',
        '[role="switch"]',
        '[role="menuitem"]',
        '[role="option"]',
      ].join(',');
      const min = touch ? 44 : 24;
      interface Target {
        el: Element;
        box: DOMRect;
      }
      const targets: Target[] = [];
      const hasText = (node: Node | null) =>
        Boolean(
          node &&
          (node.textContent ?? '').trim() &&
          (node.nodeType === Node.TEXT_NODE ||
            (node.nodeType === Node.ELEMENT_NODE && style(node as Element).display === 'inline')),
        );
      const isInlineLink = (el: Element) => {
        if (el.tagName !== 'A' || style(el).display !== 'inline') return false;
        // Inline exception: the link sits in a sentence — text runs right before or after it on the same line
        // (walking out through inline wrappers such as <strong>). A link alone on its own line is not inline.
        const meaningful = (start: Node | null, step: 'previousSibling' | 'nextSibling') => {
          let node = start;
          while (node && node.nodeType === Node.TEXT_NODE && !(node.textContent ?? '').trim()) node = node[step];
          return node;
        };
        for (let node: Element | null = el; node && style(node).display === 'inline'; node = node.parentElement) {
          if (hasText(meaningful(node.previousSibling, 'previousSibling'))) return true;
          if (hasText(meaningful(node.nextSibling, 'nextSibling'))) return true;
        }
        return false;
      };
      for (const el of Array.from(root.querySelectorAll(SELECTOR))) {
        if ((el as HTMLButtonElement).disabled || el.getAttribute('aria-disabled') === 'true') continue;
        if (hiddenTree(el)) continue;
        let box = el.getBoundingClientRect();
        const input = el as HTMLInputElement;
        if (el.tagName === 'INPUT' && (input.type === 'checkbox' || input.type === 'radio' || input.type === 'file')) {
          // The label is part of the target: measure the union with its labels (a visually hidden input counts only
          // through its label).
          const labels = Array.from(input.labels ?? []).filter(visible);
          const boxes = labels.map((label) => label.getBoundingClientRect());
          if (visible(el)) boxes.push(box);
          if (boxes.length === 0) continue;
          const left = Math.min(...boxes.map((b) => b.left));
          const top = Math.min(...boxes.map((b) => b.top));
          const right = Math.max(...boxes.map((b) => b.right));
          const bottom = Math.max(...boxes.map((b) => b.bottom));
          box = new DOMRect(left, top, right - left, bottom - top);
          // A wrapping label holds the input: the label itself is the measured target, not counted twice.
        } else if (!visible(el)) {
          continue;
        }
        if (el.tagName === 'LABEL') continue;
        // A "stretched link" (`after:absolute after:inset-0`) is clickable over its whole positioned ancestor (the
        // listing card): that box is the target.
        const after = getComputedStyle(el, '::after');
        if (after.content !== 'none' && after.position === 'absolute') {
          let holder: Element | null = el.parentElement;
          while (holder && getComputedStyle(holder).position === 'static') holder = holder.parentElement;
          if (holder) {
            const h = holder.getBoundingClientRect();
            if (h.width * h.height > box.width * box.height) box = h;
          }
        }
        if (isInlineLink(el)) continue;
        targets.push({ el, box });
      }
      // Every target is measured, a small button nested inside a large link or [role=button] too (review minor 6).
      const outer = targets;
      const small = outer.filter((t) => t.box.width < min - 0.5 || t.box.height < min - 0.5);
      for (const t of small) {
        const size = `${Math.round(t.box.width)}×${Math.round(t.box.height)}`;
        if (touch) {
          push('small-target', t.el, `${size} < 44×44 (touch)`);
          continue;
        }
        // WCAG 2.5.8 spacing exception: a 24 px circle on the target's centre may not intersect another target or
        // another undersized target's circle.
        const cx = t.box.left + t.box.width / 2;
        const cy = t.box.top + t.box.height / 2;
        const clash = outer.find((o) => {
          if (o === t || o.el.contains(t.el) || t.el.contains(o.el)) return false;
          const nx = Math.max(o.box.left, Math.min(cx, o.box.right));
          const ny = Math.max(o.box.top, Math.min(cy, o.box.bottom));
          if (Math.hypot(nx - cx, ny - cy) < 12) return true;
          if (small.includes(o)) {
            const ox = o.box.left + o.box.width / 2;
            const oy = o.box.top + o.box.height / 2;
            return Math.hypot(ox - cx, oy - cy) < 24;
          }
          return false;
        });
        if (clash) push('small-target', t.el, `${size} < 24×24 and too close to ${describe(clash.el)}`);
      }
      return findings;
    },
    { touch: options.touch, allow: options.allow ?? [], scope: options.scope, skipText: options.skipText ?? false },
  );
}

/**
 * DS-06 reflow: content must fit the viewport without sideways scrolling of the page (data tables and tab lists may
 * scroll inside their own frame), text must not be clipped by its box, and a sticky/fixed bar must not cover content
 * that has nowhere else to go.
 */
export interface ReflowFinding {
  kind: 'page-scroll' | 'x-scroll' | 'clipped-text' | 'sticky-overlap';
  detail: string;
}

export async function auditReflow(page: Page, allow: Array<{ selector: string; reason: string }> = []) {
  return page.evaluate((allowed) => {
    const findings: ReflowFinding[] = [];
    const style = (el: Element) => getComputedStyle(el);
    const ok = (el: Element) => allowed.some((rule) => el.closest(rule.selector));
    const describe = (el: Element) => {
      const text = (el.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 40);
      const classes = Array.from(el.classList)
        .filter((name) => !name.includes(':'))
        .slice(0, 4)
        .join('.');
      return `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${classes ? `.${classes}` : ''} "${text}"`;
    };
    const visible = (el: Element) => {
      const rect = el.getBoundingClientRect();
      const cs = style(el);
      return rect.width > 1 && rect.height > 1 && cs.visibility !== 'hidden' && cs.display !== 'none';
    };
    const doc = document.documentElement;
    if (doc.scrollWidth > doc.clientWidth + 1) {
      const culprits = Array.from(document.body.querySelectorAll('*'))
        .filter((el) => {
          const rect = el.getBoundingClientRect();
          if (rect.width <= 1 || rect.right <= doc.clientWidth + 1) return false;
          for (let up = el.parentElement; up && up !== document.body; up = up.parentElement) {
            if (style(up).overflowX !== 'visible') return false;
          }
          return true;
        })
        .slice(0, 4)
        .map(describe);
      findings.push({
        kind: 'page-scroll',
        detail: `scrollWidth ${doc.scrollWidth} > ${doc.clientWidth}: ${culprits.join(' | ')}`,
      });
    }
    const all = Array.from(document.body.querySelectorAll('*')).filter(visible);
    for (const el of all) {
      if (el.closest('.sr-only, svg, .maplibregl-map, [aria-hidden="true"]') || ok(el)) continue;
      const cs = style(el);
      // A sideways scroll area that is not a data table's frame or a tab list.
      if (['auto', 'scroll'].includes(cs.overflowX) && el.scrollWidth > el.clientWidth + 1) {
        const isTableFrame = el.querySelector('table') !== null || el.getAttribute('role') === 'region';
        const isTabList =
          el.getAttribute('role') === 'tablist' ||
          el.querySelector('[role="tablist"]') !== null ||
          el.closest('[role="tablist"]') !== null ||
          el.tagName === 'NAV' ||
          el.closest('nav') !== null;
        if (!isTableFrame && !isTabList) findings.push({ kind: 'x-scroll', detail: describe(el) });
      }
      // Text cut by its own box: overflow hidden/clip, no ellipsis, and the content is wider/taller than the box.
      const hasText = Array.from(el.childNodes).some((n) => n.nodeType === 3 && (n.textContent ?? '').trim());
      if (hasText && (cs.overflowX === 'hidden' || cs.overflowX === 'clip')) {
        const ellipsis = cs.textOverflow === 'ellipsis' || cs.webkitLineClamp !== 'none';
        if (!ellipsis && el.scrollWidth > el.clientWidth + 2) {
          findings.push({ kind: 'clipped-text', detail: `${describe(el)} ${el.scrollWidth} > ${el.clientWidth}` });
        }
        if (
          !ellipsis &&
          cs.overflowY !== 'visible' &&
          el.scrollHeight > el.clientHeight + 2 &&
          cs.overflowY !== 'auto'
        ) {
          findings.push({
            kind: 'clipped-text',
            detail: `${describe(el)} height ${el.scrollHeight} > ${el.clientHeight}`,
          });
        }
      }
    }
    // Sticky/fixed bars: at most a third of the viewport height together, so content stays readable and focusable
    // (WCAG 2.4.11 focus not obscured relies on the page scrolling content out from under them).
    const bars = all.filter((el) => {
      const cs = style(el);
      if (cs.position !== 'fixed' && cs.position !== 'sticky') return false;
      if (el.closest('[role="dialog"], [aria-modal="true"]') || ok(el)) return false;
      const rect = el.getBoundingClientRect();
      return rect.width > window.innerWidth * 0.5 && rect.height > 0;
    });
    const covered = bars
      .filter((el) => !bars.some((other) => other !== el && other.contains(el)))
      .reduce((sum, el) => sum + Math.min(el.getBoundingClientRect().height, window.innerHeight), 0);
    if (covered > window.innerHeight / 3 + 1) {
      findings.push({
        kind: 'sticky-overlap',
        detail: `sticky/fixed bars cover ${Math.round(covered)} of ${window.innerHeight} px: ${bars.map(describe).join(' | ')}`,
      });
    }
    return findings.slice(0, 40);
  }, allow);
}
