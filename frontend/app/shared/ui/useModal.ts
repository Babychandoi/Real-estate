/**
 * Public entry point of the kit's modal stack (M2). `Dialog` and `Sheet` use this internally; any other
 * hand-rolled modal in the app (the login dialog, a lead form, an admin review panel…) should call the same hook
 * instead of writing its own focus trap, so every open modal shares one stack: Escape and the Tab trap only ever
 * apply to the top-most one, and closing it returns focus to whatever opened it.
 *
 * ```tsx
 * const panelRef = useRef<HTMLDivElement>(null);
 * useModal({ open, onClose, panelRef });
 * return open && <div ref={panelRef} role="dialog" aria-modal="true" tabIndex={-1}>…</div>;
 * ```
 */
export { focusableWithin, useModal, type UseModalOptions } from './internal/useModal';
