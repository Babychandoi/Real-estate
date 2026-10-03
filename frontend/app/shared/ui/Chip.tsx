import React from 'react';
import { Check, type LucideIcon } from 'lucide-react';
import { cn } from './cn';

interface ChipProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'aria-pressed'> {
  /** Toggle state, exposed as aria-pressed (never colour alone: a check mark is shown too). */
  selected: boolean;
  icon?: LucideIcon;
  /** md = 44 px (touch), sm = 36 px (dense desktop filter bars; 44 px on a touch screen). Text is 14 px at both. */
  size?: 'sm' | 'md';
}

/** Toggle chip for quick filters (e.g. property type). Group chips in a ChipGroup. */
export const Chip = React.forwardRef<HTMLButtonElement, ChipProps>(function Chip(
  { selected, icon: Icon, size = 'md', className, children, type = 'button', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-pressed={selected}
      className={cn(
        'inline-flex min-w-11 max-w-full shrink-0 items-center justify-center gap-1.5 break-words rounded-pill border px-3 font-semibold transition-colors duration-fast',
        'disabled:pointer-events-none disabled:opacity-50',
        size === 'md' ? 'min-h-control-md text-body-sm' : 'min-h-control-sm text-body-sm',
        selected
          ? 'border-primary bg-primary/10 text-primary'
          : 'border-outline bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
        className,
      )}
      {...props}
    >
      {selected ? (
        <Check className="h-4 w-4 shrink-0" aria-hidden="true" />
      ) : (
        Icon && <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      )}
      {children}
    </button>
  );
});

/** Labelled group of chips (role="group"). */
export function ChipGroup({
  label,
  children,
  className,
}: {
  label: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div role="group" aria-label={label} className={cn('flex flex-wrap items-center gap-2', className)}>
      {children}
    </div>
  );
}
