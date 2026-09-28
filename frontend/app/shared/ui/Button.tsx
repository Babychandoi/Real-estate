import React from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { cn } from './cn';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
/** sm = 36 px (dense desktop toolbars), md = 44 px (default touch target), lg = 48 px (primary touch actions). */
export type ButtonSize = 'sm' | 'md' | 'lg';

const base =
  'inline-flex items-center justify-center rounded-input font-medium transition-colors duration-fast ease-standard ' +
  'focus-visible:outline focus:ring-2 focus:ring-primary/20 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none ' +
  'aria-disabled:opacity-50 motion-reduce:active:scale-100';

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-primary-on hover:bg-primary-container shadow-sm shadow-primary/20',
  secondary: 'bg-secondary text-secondary-on hover:bg-secondary-container hover:text-secondary-on-container',
  outline: 'border border-outline-variant bg-transparent text-on-surface hover:bg-surface-container',
  ghost: 'bg-transparent text-on-surface hover:bg-surface-container-high',
  danger: 'bg-error text-error-on hover:bg-error-on-container',
};

const sizes: Record<ButtonSize, string> = {
  sm: 'min-h-control-sm text-xs px-3 gap-1.5',
  md: 'min-h-control-md text-sm px-4 gap-2',
  lg: 'min-h-control-lg text-base px-5 gap-2.5',
};

/** Class names of a button look, for elements that cannot use <Button> (e.g. a download <a>). */
export function buttonClasses({
  variant = 'primary',
  size = 'md',
  className,
}: { variant?: ButtonVariant; size?: ButtonSize; className?: string } = {}): string {
  return cn(base, variants[variant], sizes[size], className);
}

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner, disables the button and sets aria-busy; keep the label so the action stays clear. */
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  leftIcon,
  rightIcon,
  className,
  disabled,
  // Defaults to "button": inside a <form>, a bare <button> submits it. Every kit-internal use (Pagination,
  // LoadMore, InlineFeedback's action, ErrorState's retry, Toast, Dialog/Sheet…) relies on this default; pass
  // type="submit" explicitly for the one button in a form that should submit it.
  type = 'button',
  ...props
}) => (
  <button
    type={type}
    className={buttonClasses({ variant, size, className })}
    disabled={disabled || isLoading}
    aria-busy={isLoading || undefined}
    {...props}
  >
    {isLoading ? (
      <Loader2 className="h-4 w-4 shrink-0 motion-safe:animate-spin" aria-hidden="true" />
    ) : (
      leftIcon && (
        <span className="flex-shrink-0" aria-hidden="true">
          {leftIcon}
        </span>
      )
    )}
    {children}
    {!isLoading && rightIcon && (
      <span className="flex-shrink-0" aria-hidden="true">
        {rightIcon}
      </span>
    )}
  </button>
);

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant;
  size?: ButtonSize;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

/** A router link that looks like a button: use it instead of wrapping <Button> in <Link> (no nested controls). */
export const ButtonLink: React.FC<ButtonLinkProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  leftIcon,
  rightIcon,
  className,
  ...props
}) => (
  <Link
    className={buttonClasses({ variant, size, className: typeof className === 'string' ? className : undefined })}
    {...props}
  >
    {leftIcon && (
      <span className="flex-shrink-0" aria-hidden="true">
        {leftIcon}
      </span>
    )}
    {children}
    {rightIcon && (
      <span className="flex-shrink-0" aria-hidden="true">
        {rightIcon}
      </span>
    )}
  </Link>
);
