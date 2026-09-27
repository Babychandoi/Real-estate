import React from 'react';
import { cn } from './cn';

type ControlSize = 'md' | 'lg';

/** Shared look of text-like controls: 44/48 px, 16 px text (no zoom on iOS), a 4.5:1 border, error border. */
export function fieldClasses({
  size = 'md',
  invalid,
  className,
}: {
  size?: ControlSize;
  invalid?: boolean;
  className?: string;
}) {
  return cn(
    'w-full rounded-input border bg-surface-container-lowest px-3 text-body text-on-surface placeholder:text-outline',
    'transition-colors duration-fast focus-visible:border-primary',
    'disabled:cursor-not-allowed disabled:bg-surface-container disabled:text-on-surface-variant',
    size === 'lg' ? 'min-h-control-lg' : 'min-h-control-md',
    invalid ? 'border-error' : 'border-outline',
    className,
  );
}

export interface TextInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  size?: ControlSize;
  /** Error state; FormField sets aria-invalid for you. */
  invalid?: boolean;
  /** Decorative icon at the start (hidden from assistive tech). */
  leadingIcon?: React.ReactNode;
  /** Element at the end, e.g. an IconButton to show a password. */
  trailing?: React.ReactNode;
}

export const TextInput = React.forwardRef<HTMLInputElement, TextInputProps>(function TextInput(
  { size = 'md', invalid, leadingIcon, trailing, className, ...props },
  ref,
) {
  const isInvalid = invalid || props['aria-invalid'] === true || props['aria-invalid'] === 'true';
  const input = (
    <input
      ref={ref}
      {...props}
      aria-invalid={isInvalid || undefined}
      className={fieldClasses({
        size,
        invalid: isInvalid,
        className: cn(leadingIcon && 'pl-10', trailing && 'pr-12', className),
      })}
    />
  );
  if (!leadingIcon && !trailing) return input;
  return (
    <div className="relative">
      {leadingIcon && (
        <span
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant"
          aria-hidden="true"
        >
          {leadingIcon}
        </span>
      )}
      {input}
      {trailing && <span className="absolute right-0.5 top-1/2 -translate-y-1/2">{trailing}</span>}
    </div>
  );
});

export interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export const TextArea = React.forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  { invalid, className, rows = 4, ...props },
  ref,
) {
  const isInvalid = invalid || props['aria-invalid'] === true || props['aria-invalid'] === 'true';
  return (
    <textarea
      ref={ref}
      rows={rows}
      {...props}
      aria-invalid={isInvalid || undefined}
      className={fieldClasses({ invalid: isInvalid, className: cn('py-2.5 leading-body', className) })}
    />
  );
});
