import React, { useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from './cn';
import { IconButton } from './IconButton';
import { useModal } from './internal/useModal';

export interface SheetProps {
  open: boolean;
  onClose: () => void;
  title: React.ReactNode;
  description?: React.ReactNode;
  children?: React.ReactNode;
  /** Sticky actions (e.g. "Xóa lọc" / "Xem 120 kết quả"); stays above the home indicator. */
  footer?: React.ReactNode;
  initialFocusRef?: React.RefObject<HTMLElement | null>;
  closeLabel?: string;
  className?: string;
}

/**
 * Bottom sheet on phones, right-hand panel from the `sm` breakpoint (filters, marker details). Same modal
 * behaviour as Dialog: focus trap, Escape, scroll lock, focus return. The drag handle is decorative; closing
 * never depends on a swipe gesture.
 */
export function Sheet({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  initialFocusRef,
  closeLabel = 'Đóng',
  className,
}: SheetProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const generated = useId().replace(/:/g, '');
  const titleId = `sheet${generated}-title`;
  const descriptionId = description ? `sheet${generated}-description` : undefined;
  useModal({ open, onClose, panelRef, initialFocusRef });

  if (!open) return null;
  return createPortal(
    <div
      className="fixed inset-0 z-overlay flex items-end justify-center sm:items-stretch sm:justify-end"
      role="presentation"
    >
      {/* click (not mousedown): see Dialog.tsx for why mousedown races the focus-return on close (m1). */}
      <div className="absolute inset-0 bg-inverse-surface/60" aria-hidden="true" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
        className={cn(
          'ndc-overlay relative flex max-h-[88dvh] w-full min-w-0 flex-col [overflow-wrap:anywhere] [&_th]:[overflow-wrap:break-word] rounded-t-panel bg-surface-container-lowest shadow-elevated focus:outline-none',
          'sm:h-full sm:max-h-none sm:max-w-md sm:rounded-none sm:rounded-l-panel',
          className,
        )}
      >
        <span
          className="mx-auto mt-2 h-1.5 w-10 shrink-0 rounded-pill bg-outline-variant sm:hidden"
          aria-hidden="true"
        />
        <div className="flex items-start justify-between gap-4 border-b border-outline-variant px-4 py-3 sm:px-5 sm:py-4">
          <div className="min-w-0">
            <h2 id={titleId} className="text-headline-sm text-on-surface">
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="mt-1 text-body-sm text-on-surface-variant">
                {description}
              </p>
            )}
          </div>
          <IconButton icon={X} aria-label={closeLabel} onClick={onClose} className="-mr-2" />
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 text-body-sm text-on-surface sm:px-5">{children}</div>
        {footer && (
          <div className="flex flex-wrap gap-3 border-t border-outline-variant px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:px-5">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}
