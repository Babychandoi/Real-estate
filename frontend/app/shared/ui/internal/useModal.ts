import { useEffect, useLayoutEffect, useRef, type RefObject } from 'react';

/**
 * Modal behaviour shared by Dialog and Sheet: focus moves into the panel, Tab/Shift+Tab stay inside, Escape
 * closes, the page behind does not scroll, and focus returns to the element that opened the modal. Nested modals
 * are stacked: only the top one reacts to keys and focus.
 */
const FOCUSABLE = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  'summary',
  '[contenteditable="true"]',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

const stack: symbol[] = [];
let scrollLocks = 0;
let savedBodyStyle: { overflow: string; paddingRight: string } | null = null;

export function focusableWithin(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => {
    // Native controls can also have tabindex=-1 (roving tabs), and a display:none ancestor hides every descendant
    // even when its own computed display is block. Neither may become the trap's first or last keyboard stop.
    if (
      element.tabIndex < 0 ||
      element.matches(':disabled') ||
      element.closest('[hidden],[inert],[aria-hidden="true"]')
    )
      return false;
    const style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.visibility === 'collapse') return false;
    for (let node: HTMLElement | null = element; node; node = node.parentElement) {
      if (window.getComputedStyle(node).display === 'none') return false;
      if (node === container) break;
    }
    return true;
  });
}

function lockScroll() {
  if (scrollLocks === 0) {
    const { body, documentElement } = document;
    savedBodyStyle = { overflow: body.style.overflow, paddingRight: body.style.paddingRight };
    const scrollbar = window.innerWidth - documentElement.clientWidth;
    body.style.overflow = 'hidden';
    if (scrollbar > 0) body.style.paddingRight = `${scrollbar}px`;
  }
  scrollLocks += 1;
}

function unlockScroll() {
  scrollLocks = Math.max(0, scrollLocks - 1);
  if (scrollLocks === 0 && savedBodyStyle) {
    document.body.style.overflow = savedBodyStyle.overflow;
    document.body.style.paddingRight = savedBodyStyle.paddingRight;
    savedBodyStyle = null;
  }
}

/*
 * Everything outside the open modal is made `inert` (and `aria-hidden`, for assistive tech that predates inert): a
 * screen reader's virtual cursor, Tab and the pointer cannot reach the page behind it (WCAG 1.3.1/2.4.3, review of
 * PR #25). The modal's layer is its nearest fixed-position ancestor (the overlay that also holds the backdrop); every
 * sibling of that layer and of each of its ancestors up to <body> is hidden. Marks are reference-counted, so nested
 * modals hide the lower modal too and closing the top one gives it back; an element that was already inert or
 * aria-hidden before any modal opened is left as it was. `[data-modal-keep]` (the toast live region) stays reachable.
 */
interface Mark {
  /** Open modals that hide this element. */
  hide: number;
  /** Open modals that live inside it (a modal rendered inside the page while another modal hid the page). */
  lift: number;
  /** What the element had before any modal touched it. */
  inert: boolean;
  ariaHidden: string | null;
}
const marks = new Map<Element, Mark>();
const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'LINK', 'TEMPLATE', 'NOSCRIPT']);

function markOf(element: Element): Mark {
  let mark = marks.get(element);
  if (!mark) {
    mark = { hide: 0, lift: 0, inert: element.hasAttribute('inert'), ariaHidden: element.getAttribute('aria-hidden') };
    marks.set(element, mark);
  }
  return mark;
}

function apply(element: Element, mark: Mark) {
  if (mark.hide > 0 && mark.lift === 0) {
    element.setAttribute('inert', '');
    element.setAttribute('aria-hidden', 'true');
  } else {
    if (!mark.inert) element.removeAttribute('inert');
    if (mark.ariaHidden == null) element.removeAttribute('aria-hidden');
    else element.setAttribute('aria-hidden', mark.ariaHidden);
  }
  if (mark.hide === 0 && mark.lift === 0) marks.delete(element);
}

function layerOf(panel: HTMLElement): HTMLElement {
  for (let node: HTMLElement | null = panel; node && node !== document.body; node = node.parentElement) {
    if (window.getComputedStyle(node).position === 'fixed') return node;
  }
  return panel;
}

interface HiddenState {
  hidden: Element[];
  lifted: Element[];
}

function hideOutside(panel: HTMLElement): HiddenState {
  const state: HiddenState = { hidden: [], lifted: [] };
  const layer = layerOf(panel);
  // A modal opened inside a subtree that a lower modal hid (the login dialog lives in the page, the filter Sheet is
  // portalled): its own ancestors are made reachable again while it is open.
  for (let node: Element | null = layer; node && node !== document.body; node = node.parentElement) {
    const mark = marks.get(node);
    if (!mark || mark.hide === 0) continue;
    mark.lift += 1;
    apply(node, mark);
    state.lifted.push(node);
  }
  for (let node: Element | null = layer; node && node !== document.body; node = node.parentElement) {
    const parent: Element | null = node.parentElement;
    if (!parent) break;
    for (const sibling of Array.from(parent.children)) {
      if (sibling === node || SKIP_TAGS.has(sibling.tagName) || sibling.hasAttribute('data-modal-keep')) continue;
      const mark = markOf(sibling);
      mark.hide += 1;
      apply(sibling, mark);
      state.hidden.push(sibling);
    }
  }
  return state;
}

function restore(state: HiddenState) {
  for (const element of state.hidden) {
    const mark = marks.get(element);
    if (!mark) continue;
    mark.hide -= 1;
    apply(element, mark);
  }
  for (const element of state.lifted) {
    const mark = marks.get(element);
    if (!mark) continue;
    mark.lift -= 1;
    apply(element, mark);
  }
}

export interface UseModalOptions {
  open: boolean;
  onClose: () => void;
  panelRef: RefObject<HTMLElement | null>;
  /** Element to focus first; defaults to the first focusable element, then the panel itself. */
  initialFocusRef?: RefObject<HTMLElement | null>;
}

export function useModal({ open, onClose, panelRef, initialFocusRef }: UseModalOptions): void {
  const onCloseRef = useRef(onClose);
  useLayoutEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return undefined;
    const panel = panelRef.current;
    if (!panel) return undefined;
    const id = Symbol('modal');
    stack.push(id);
    const isTop = () => stack[stack.length - 1] === id;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    lockScroll();
    const hidden = hideOutside(panel);

    const first = initialFocusRef?.current ?? focusableWithin(panel)[0] ?? panel;
    first.focus({ preventScroll: true });

    const onKeyDown = (event: KeyboardEvent) => {
      if (!isTop()) return;
      if (event.key === 'Escape') {
        event.stopPropagation();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;
      const items = focusableWithin(panel);
      if (!items.length) {
        event.preventDefault();
        panel.focus();
        return;
      }
      const firstItem = items[0];
      const lastItem = items[items.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === firstItem || active === panel)) {
        event.preventDefault();
        lastItem.focus();
      } else if (!event.shiftKey && active === lastItem) {
        event.preventDefault();
        firstItem.focus();
      }
    };
    // Focus that escapes (mouse click on the page, assistive tech) is brought back into the panel.
    const onFocusIn = (event: FocusEvent) => {
      if (!isTop() || !(event.target instanceof Node) || panel.contains(event.target)) return;
      (focusableWithin(panel)[0] ?? panel).focus({ preventScroll: true });
    };

    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('focusin', onFocusIn);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('focusin', onFocusIn);
      const index = stack.indexOf(id);
      if (index >= 0) stack.splice(index, 1);
      unlockScroll();
      restore(hidden);
      if (opener && opener.isConnected) opener.focus({ preventScroll: true });
    };
  }, [open, panelRef, initialFocusRef]);
}
