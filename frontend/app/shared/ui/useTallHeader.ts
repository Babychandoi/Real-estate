import { useEffect, useRef, useState } from 'react';

/** Above this share of the viewport height a sticky header covers too much of the page (DS-06, WCAG 1.4.10/2.4.11). */
export const MAX_STICKY_SHARE = 0.25;

/** True when a header of `height` px would cover more than a quarter of a `viewportHeight` px window. */
export function isTooTallToStick(height: number, viewportHeight: number): boolean {
  return viewportHeight > 0 && height > viewportHeight * MAX_STICKY_SHARE;
}

/**
 * DS-06: the public header is sticky, which is fine at its normal 80 px. With enlarged text (200 % text size), a narrow
 * zoomed window or a phone in landscape it wraps or the window is short, and a sticky header would hide a large part
 * of every page and of the focused element. This watches the header's size and the window's height and reports when it
 * should scroll away with the page instead (`data-tall` → `position: static`, index.css).
 */
export function useTallHeader<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [tall, setTall] = useState(false);
  useEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    const measure = () => setTall(isTooTallToStick(element.getBoundingClientRect().height, window.innerHeight));
    measure();
    window.addEventListener('resize', measure);
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(element);
    return () => {
      window.removeEventListener('resize', measure);
      observer?.disconnect();
    };
  }, []);
  return { ref, tall };
}
