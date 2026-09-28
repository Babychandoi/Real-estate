import React, { useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from './cn';
import { IconButton } from './IconButton';
import { useModal } from './internal/useModal';

const widths = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl' } as const;

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
 * Modal dialog: role="dialog" + aria-modal, labelled by its title, focus trapped inside, Escape and the close
 * button close it, the page does not scroll behind it and focus returns to the opener.
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
  const generated = useId().replace(/:/g, '');
  const titleId = `dialog${generated}-title`;
  const descriptionId = description ? `dialog${generated}-description` : undefined;
  useModal({ open, onClose, panelRef, initialFocusRef });

  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-overlay flex items-center justify-center p-4" role="presentation">
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
          'relative flex max-h-[calc(100dvh-2rem)] w-full flex-col rounded-dialog bg-surface-container-lowest shadow-elevated focus:outline-none',
          widths[size],
          className,
        )}
      >
        <div className="flex items-start justify-between gap-4 border-b border-outline-variant px-5 py-4">
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
          <IconButton icon={X} aria-label={closeLabel} onClick={onClose} className="-mr-2 -mt-1" />
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4 text-body-sm text-on-surface">{children}</div>
        {footer && (
          <div className="flex flex-wrap justify-end gap-3 border-t border-outline-variant px-5 py-3">{footer}</div>
        )}
      </div>
    </div>,
    document.body,
  );
}
