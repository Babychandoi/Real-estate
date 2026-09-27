import React from 'react';
import { AlertCircle, RotateCw, type LucideIcon } from 'lucide-react';
import { Button } from './Button';
import { cn } from './cn';

interface ErrorStateProps {
  title?: React.ReactNode;
  description?: React.ReactNode;
  /** Shows "Thử lại" when the failure is retryable. */
  onRetry?: () => void;
  retrying?: boolean;
  retryLabel?: string;
  icon?: LucideIcon;
  headingLevel?: 2 | 3 | 4;
  className?: string;
}

/** A load failed: plain-language reason plus a retry, announced with role="alert". */
export function ErrorState({
  title = 'Không tải được dữ liệu',
  description = 'Kết nối có thể đang gián đoạn. Vui lòng thử lại.',
  onRetry,
  retrying = false,
  retryLabel = 'Thử lại',
  icon: Icon = AlertCircle,
  headingLevel = 3,
  className,
}: ErrorStateProps) {
  const Heading = `h${headingLevel}` as const;
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center gap-3 rounded-card border border-error/30 bg-error-container/40 px-6 py-10 text-center',
        className,
      )}
    >
      <span className="grid h-12 w-12 place-items-center rounded-pill bg-error-container text-error">
        <Icon className="h-6 w-6" aria-hidden="true" />
      </span>
      <Heading className="text-headline-sm text-on-surface">{title}</Heading>
      {description && <p className="max-w-prose text-body-sm text-on-surface-variant">{description}</p>}
      {onRetry && (
        <Button variant="outline" onClick={onRetry} isLoading={retrying} leftIcon={<RotateCw className="h-4 w-4" />}>
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
