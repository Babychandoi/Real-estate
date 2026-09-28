import type { ReactNode } from 'react';
import { Building2, RefreshCw } from 'lucide-react';
import { ui } from '@/i18n/vi/ui';
export function StatePanel({
  title,
  description,
  error = false,
  onRetry,
  action,
  headingLevel = 2,
}: {
  title?: string;
  description?: string;
  error?: boolean;
  onRetry?: () => void;
  action?: ReactNode;
  /** 1 when the state replaces the whole page (not found, page failed to load): the page still has its h1. */
  headingLevel?: 1 | 2;
}) {
  const Heading = headingLevel === 1 ? 'h1' : 'h2';
  return (
    <div className="ndc-state" role={error ? 'alert' : 'status'}>
      <span className="ndc-state-icon">
        <Building2 className="h-6 w-6" aria-hidden="true" />
      </span>
      <Heading>{title || (error ? ui.errorTitle : ui.empty)}</Heading>
      {(description || error) && <p>{description || ui.errorBody}</p>}
      {onRetry && (
        <button type="button" className="ndc-primary-link" onClick={onRetry}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          {ui.retry}
        </button>
      )}
      {action}
    </div>
  );
}
export function ListingSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="ndc-listing-grid" role="status" aria-label={ui.loading}>
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="ndc-skeleton-card animate-pulse" aria-hidden="true">
          <div className="aspect-[4/3] bg-surface-container" />
          <div className="space-y-3 p-5">
            <div className="h-5 w-2/3 rounded bg-surface-container" />
            <div className="h-4 rounded bg-surface-container" />
            <div className="h-4 w-1/2 rounded bg-surface-container" />
          </div>
        </div>
      ))}
    </div>
  );
}
