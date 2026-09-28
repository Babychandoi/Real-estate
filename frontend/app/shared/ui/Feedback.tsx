import type { ReactNode } from 'react';
import { Building2, RefreshCw } from 'lucide-react';
import { ui } from '@/i18n/vi/ui';
export function StatePanel({ title, description, error = false, onRetry, action }: { title?: string; description?: string; error?: boolean; onRetry?: () => void; action?: ReactNode }) {
  return <div className="ndc-state" role={error ? 'alert' : 'status'}><span className="ndc-state-icon"><Building2 className="h-6 w-6" aria-hidden="true" /></span><h2>{title || (error ? ui.errorTitle : ui.empty)}</h2>{(description || error) && <p>{description || ui.errorBody}</p>}{onRetry && <button type="button" className="ndc-primary-link" onClick={onRetry}><RefreshCw className="h-4 w-4" aria-hidden="true" />{ui.retry}</button>}{action}</div>;
}
export function ListingSkeleton({ count = 6 }: { count?: number }) { return <div className="ndc-listing-grid" role="status" aria-label={ui.loading}>{Array.from({ length: count }, (_, index) => <div key={index} className="ndc-skeleton-card animate-pulse" aria-hidden="true"><div className="aspect-[4/3] bg-surface-container" /><div className="space-y-3 p-5"><div className="h-5 w-2/3 rounded bg-surface-container" /><div className="h-4 rounded bg-surface-container" /><div className="h-4 w-1/2 rounded bg-surface-container" /></div></div>)}</div>; }
