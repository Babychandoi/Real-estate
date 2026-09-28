import React from 'react';
import { cn } from './cn';

export type BadgeVariant =
  | 'neutral'
  | 'primary'
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  /** Legacy alias of `success` (evidence-backed verification). */
  | 'verified'
  | 'vip';

const variants: Record<BadgeVariant, string> = {
  neutral: 'bg-surface-container-high text-on-surface-variant',
  primary: 'bg-primary/10 text-primary',
  success: 'bg-success-container text-success-on-container border border-success/20',
  warning: 'bg-warning-container text-warning-on-container border border-warning/20',
  error: 'bg-error-container text-error-on-container',
  info: 'bg-info-container text-info-on-container',
  verified: 'bg-emerald-50 text-emerald-800 border border-secondary/20',
  vip: 'bg-amber-50 text-amber-900 border border-tertiary/20',
};

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  /** Decorative icon (hidden from assistive tech); the text must carry the meaning on its own. */
  icon?: React.ReactNode;
}

/** Short status label, 12 px minimum. Never colour alone: the text says what the status is. */
export const Badge: React.FC<BadgeProps> = ({ children, variant = 'neutral', icon, className, ...props }) => (
  <span
    className={cn(
      'inline-flex items-center gap-1 rounded-pill px-2.5 py-0.5 text-xs font-semibold',
      variants[variant],
      className,
    )}
    {...props}
  >
    {icon && (
      <span className="flex-shrink-0 text-xs" aria-hidden="true">
        {icon}
      </span>
    )}
    {children}
  </span>
);
