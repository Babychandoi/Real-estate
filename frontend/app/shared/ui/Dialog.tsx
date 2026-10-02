import React, { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from './cn';
import { IconButton } from './IconButton';
import { useModal } from './internal/useModal';

/**
 * `sm` confirmations, `md` short forms, `lg` forms and detail views (max-w-2xl), `xl` comparison and diff views
 * (max-w-4xl). A dialog is never wider than the viewport minus its 16/24 px margin, whatever the size.
 */
const widths = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-4xl' } as const;

export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: React.ReactNode;
  description?: React.ReactNode;
  children?: React.ReactNode;
  /** Actions, right-aligned; keep destructive and confirming actions apart on touch screens. */
  footer?: React.ReactNode;
  size?: keyof typeof widths;
  /** Element focused on open (default: the first focusable element). */
  initialFocusRef?: React.RefObject<HTMLElement | null>;
  /** Close when the dimmed backdrop is clicked (default true). Escape always closes. */
  closeOnBackdrop?: boolean;
  closeLabel?: string;
  className?: string;
}

/**
 * Modal dialog, centred in the viewport (about 90 % of its height at most): role="dialog" + aria-modal, labelled by
 * its title, focus trapped inside, Escape, the backdrop and the close button close it, the page does not scroll behind
 * it and focus returns to the opener. The title bar and the `footer` (the actions) stay put; only the body scrolls.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = 'md',
  initialFocusRef,
  closeOnBackdrop = true,
  closeLabel = 'Đóng hộp thoại',
  className,
}: DialogProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const generated = useId().replace(/:/g, '');
  const titleId = `dialog${generated}-title`;
  const descriptionId = description ? `dialog${generated}-description` : undefined;
  // Default initial focus goes to the title, not the close button (NIT): the close button happens to be the
  // first focusable element in DOM order, but it is the least meaningful thing to land on when a dialog opens —
  // a screen reader user hears the title read out and a sighted user's focus ring appears somewhere they can
  // actually orient from, rather than on a control whose only job is to leave.
  useModal({ open, onClose, panelRef, initialFocusRef: initialFocusRef ?? titleRef });
  // WCAG 2.1.1 (axe scrollable-region-focusable): when the body scrolls, a keyboard user can focus it and scroll with
  // the arrow keys even if it holds no control (a history list, a long text). It is a tab stop only while it scrolls.
  const bodyRef = useRef<HTMLDivElement>(null);
  const [bodyScrolls, setBodyScrolls] = useState(false);
  useEffect(() => {
    const body = bodyRef.current;
    if (!open || !body) return undefined;
    const measure = () => setBodyScrolls(body.scrollHeight > body.clientHeight + 1);
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(body);
    if (body.firstElementChild) observer?.observe(body.firstElementChild);
    return () => observer?.disconnect();
  }, [open, children]);

  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-overlay flex items-center justify-center p-4 sm:p-6" role="presentation">
      <div
        className="absolute inset-0 bg-inverse-surface/60"
        aria-hidden="true"
        // click (not mousedown): mousedown fires before useModal's cleanup returns focus to the opener, so a
        // mousedown-triggered close raced it and focus landed on <body> instead (m1).
        onClick={closeOnBackdrop ? onClose : undefined}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
        className={cn(
          'ndc-overlay relative flex max-h-[min(90dvh,calc(100dvh-2rem))] w-full min-w-0 flex-col [overflow-wrap:anywhere] [&_th]:[overflow-wrap:break-word] rounded-dialog bg-surface-container-lowest shadow-elevated focus:outline-none',
          widths[size],
          className,
        )}
      >
        <div className="flex items-start justify-between gap-4 border-b border-outline-variant px-4 py-3 sm:px-5 sm:py-4">
          <div className="min-w-0">
            <h2
              id={titleId}
              ref={titleRef}
              tabIndex={-1}
              className="text-headline-sm text-on-surface focus:outline-none"
            >
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="mt-1 text-body-sm text-on-surface-variant">
                {description}
              </p>
            )}
          </div>
          <IconButton icon={X} aria-label={closeLabel} onClick={onClose} className="-mr-2 -mt-1" />
        </div>
        <div
          ref={bodyRef}
          tabIndex={bodyScrolls ? 0 : undefined}
          role={bodyScrolls ? 'region' : undefined}
          aria-labelledby={bodyScrolls ? titleId : undefined}
          className="min-h-0 flex-1 overflow-y-auto px-4 py-4 text-body-sm text-on-surface focus-visible:outline-offset-[-3px] sm:px-5"
        >
          {children}
        </div>
        {footer && (
          <div className="flex flex-wrap justify-end gap-3 border-t border-outline-variant px-4 py-3 sm:px-5">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}
