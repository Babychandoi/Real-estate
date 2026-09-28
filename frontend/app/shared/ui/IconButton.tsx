import React from 'react';
import type { LucideIcon } from 'lucide-react';
import { cn } from './cn';

type IconButtonVariant = 'ghost' | 'outline' | 'primary' | 'danger';
type IconButtonSize = 'sm' | 'md' | 'lg';

interface IconButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label' | 'children'> {
  /** Required: an icon-only control has no visible text, so this is its whole accessible name. */
  'aria-label': string;
  icon: LucideIcon;
  variant?: IconButtonVariant;
  /** sm = 36 px, md = 44 px (default touch target), lg = 48 px. */
  size?: IconButtonSize;
}

const variants: Record<IconButtonVariant, string> = {
  ghost: 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
  outline: 'border border-outline text-on-surface hover:bg-surface-container',
  primary: 'bg-primary text-primary-on hover:bg-primary-container',
  danger: 'text-error hover:bg-error-container',
};

const sizes: Record<IconButtonSize, { box: string; icon: string }> = {
  sm: { box: 'h-control-sm w-control-sm', icon: 'h-4 w-4' },
  md: { box: 'h-control-md w-control-md', icon: 'h-5 w-5' },
  lg: { box: 'h-control-lg w-control-lg', icon: 'h-6 w-6' },
};

export const IconButton = React.forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { icon: Icon, variant = 'ghost', size = 'md', className, type = 'button', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        'inline-grid shrink-0 place-items-center rounded-input transition-colors duration-fast disabled:pointer-events-none disabled:opacity-50',
        variants[variant],
        sizes[size].box,
        className,
      )}
      {...props}
    >
      <Icon className={sizes[size].icon} aria-hidden="true" />
    </button>
  );
});
