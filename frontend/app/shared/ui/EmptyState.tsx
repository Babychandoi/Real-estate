import React from 'react';
import { Inbox, type LucideIcon } from 'lucide-react';
import { cn } from './cn';

interface EmptyStateProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  icon?: LucideIcon;
  /** Next step(s), e.g. a ButtonLink "Xóa bộ lọc". */
  actions?: React.ReactNode;
  headingLevel?: 2 | 3 | 4;
  className?: string;
}

/** Nothing to show yet — says why and what to do next, never a blank area. */
export function EmptyState({
  title,
  description,
  icon: Icon = Inbox,
  actions,
  headingLevel = 3,
  className,
}: EmptyStateProps) {
  const Heading = `h${headingLevel}` as const;
  return (
    <div
      className={cn(
        'flex flex-col items-center gap-3 rounded-card border border-dashed border-outline-variant bg-surface-container-lowest px-6 py-10 text-center',
        className,
      )}
    >
      <span className="grid h-12 w-12 place-items-center rounded-pill bg-surface-container text-primary">
        <Icon className="h-6 w-6" aria-hidden="true" />
      </span>
      <Heading className="text-headline-sm text-on-surface">{title}</Heading>
      {description && <p className="max-w-prose text-body-sm text-on-surface-variant">{description}</p>}
      {actions && <div className="mt-1 flex flex-wrap justify-center gap-3">{actions}</div>}
    </div>
  );
}
