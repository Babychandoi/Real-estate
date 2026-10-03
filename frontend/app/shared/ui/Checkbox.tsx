import React, { useEffect, useId, useRef } from 'react';
import { cn } from './cn';

export interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: React.ReactNode;
  description?: React.ReactNode;
  /** "Some selected" state, e.g. a select-all box for part of a page. */
  indeterminate?: boolean;
}

/** Native checkbox with its label: the row is 44 px tall for spacing, but only the input and the label text (via
 * `htmlFor`) are clickable — the description and the row's own padding are not (NIT). */
export const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { label, description, indeterminate = false, className, id, disabled, ...props },
  forwardedRef,
) {
  const generated = useId();
  const inputId = id ?? `checkbox${generated.replace(/:/g, '')}`;
  const descriptionId = description ? `${inputId}-description` : undefined;
  const innerRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (innerRef.current) innerRef.current.indeterminate = indeterminate;
  }, [indeterminate]);

  const setRef = (element: HTMLInputElement | null) => {
    innerRef.current = element;
    if (typeof forwardedRef === 'function') forwardedRef(element);
    else if (forwardedRef) forwardedRef.current = element;
  };

  return (
    <div className={cn('flex min-h-control-md items-start gap-3 py-2.5', className)}>
      <input
        ref={setRef}
        id={inputId}
        type="checkbox"
        disabled={disabled}
        aria-describedby={descriptionId}
        className="mt-0.5 h-5 w-5 shrink-0 cursor-pointer rounded accent-primary disabled:cursor-not-allowed"
        {...props}
      />
      <span className="flex flex-col">
        <label
          htmlFor={inputId}
          className={cn(
            // The label is the touch target: 11 px of padding above and below, cancelled by the margin so the row
            // keeps its layout, make it 44 px tall with its 22 px line (DS-03).
            '-my-[11px] cursor-pointer py-[11px] text-body-sm font-medium text-on-surface',
            disabled && 'cursor-not-allowed text-on-surface-variant',
          )}
        >
          {label}
        </label>
        {description && (
          <span id={descriptionId} className="text-label font-normal text-on-surface-variant">
            {description}
          </span>
        )}
      </span>
    </div>
  );
});
