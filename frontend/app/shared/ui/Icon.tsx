import type { LucideIcon } from 'lucide-react';
import { cn } from './cn';

const sizes = { sm: 'h-icon-sm w-icon-sm', md: 'h-icon-md w-icon-md', lg: 'h-icon-lg w-icon-lg' } as const;

interface IconProps {
  icon: LucideIcon;
  /** 16 / 20 / 24 px — the only icon sizes of the design system. */
  size?: keyof typeof sizes;
  /** Give a label only when the icon carries meaning on its own; otherwise it is hidden from assistive tech. */
  label?: string;
  className?: string;
}

/** Lucide icon with the design-system sizes (icons are never emoji). */
export function Icon({ icon: LucideComponent, size = 'sm', label, className }: IconProps) {
  return label ? (
    <LucideComponent className={cn(sizes[size], 'shrink-0', className)} role="img" aria-label={label} />
  ) : (
    <LucideComponent className={cn(sizes[size], 'shrink-0', className)} aria-hidden="true" />
  );
}
