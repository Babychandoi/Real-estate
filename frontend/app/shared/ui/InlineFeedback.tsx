import React from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, WifiOff, type LucideIcon } from 'lucide-react';
import { Button } from './Button';
import { cn } from './cn';

export type FeedbackTone = 'success' | 'error' | 'warning' | 'info';

/** DS-08 feedback states: success, retryable error, conflict, offline (plus plain info/warning). */
export type FeedbackKind = FeedbackTone | 'conflict' | 'offline';

const toneOf: Record<FeedbackKind, FeedbackTone> = {
  success: 'success',
  error: 'error',
  warning: 'warning',
  info: 'info',
  conflict: 'warning',
  offline: 'warning',
};

const iconOf: Record<FeedbackKind, LucideIcon> = {
  success: CheckCircle2,
  error: AlertCircle,
  warning: AlertTriangle,
  info: Info,
  conflict: AlertTriangle,
  offline: WifiOff,
};

export const feedbackToneClasses: Record<FeedbackTone, string> = {
  success: 'border-success/30 bg-success-container text-success-on-container',
  error: 'border-error/30 bg-error-container text-error-on-container',
  warning: 'border-warning/30 bg-warning-container text-warning-on-container',
  info: 'border-info/30 bg-info-container text-info-on-container',
};

export interface FeedbackAction {
  label: string;
  onClick: () => void;
  isLoading?: boolean;
}

interface InlineFeedbackProps {
  kind?: FeedbackKind;
  title: React.ReactNode;
  children?: React.ReactNode;
  action?: FeedbackAction;
  className?: string;
}

/**
 * Message next to the content it is about. Errors use role="alert", everything else role="status". Report
 * success only after the backend confirmed it; field errors belong to FormField, not here.
 */
export function InlineFeedback({ kind = 'info', title, children, action, className }: InlineFeedbackProps) {
  const tone = toneOf[kind];
  const Icon = iconOf[kind];
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'flex flex-wrap items-start gap-3 rounded-card border p-3 text-body-sm',
        feedbackToneClasses[tone],
        className,
      )}
    >
      <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        {children && <div className="mt-0.5">{children}</div>}
      </div>
      {action && (
        <Button
          size="sm"
          variant="outline"
          onClick={action.onClick}
          isLoading={action.isLoading}
          className="bg-surface-container-lowest"
        >
          {action.label}
        </Button>
      )}
    </div>
  );
}
