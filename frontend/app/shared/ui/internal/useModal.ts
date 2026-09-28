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
    if (element.closest('[hidden],[inert]') || element.getAttribute('aria-hidden') === 'true') return false;
    const style = window.getComputedStyle(element);
    return style.display !== 'none' && style.visibility !== 'hidden';
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
      if (opener && opener.isConnected) opener.focus({ preventScroll: true });
    };
  }, [open, panelRef, initialFocusRef]);
}
