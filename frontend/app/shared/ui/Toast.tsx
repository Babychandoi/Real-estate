import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, WifiOff, X, type LucideIcon } from 'lucide-react';
import { cn } from './cn';
import { IconButton } from './IconButton';
import { feedbackToneClasses, type FeedbackAction, type FeedbackKind, type FeedbackTone } from './InlineFeedback';

export interface ToastOptions {
  kind?: FeedbackKind;
  title: string;
  description?: string;
  action?: FeedbackAction;
  /**
   * Auto-dismiss delay in ms; `null` keeps it until dismissed. Default: 6 s for success/info with no `action`,
   * sticky otherwise. A toast with an `action` is never auto-dismissed by default (WCAG 2.2.1): the actionable
   * choice must stay available until the person notices it, not race a timer. Pass an explicit duration of
   * 10000 ms or more to time one out anyway; the timer still pauses while the toast has pointer or focus.
   */
  duration?: number | null;
}

interface ToastItem extends ToastOptions {
  id: string;
}

interface ToastApi {
  show: (options: ToastOptions) => string;
  dismiss: (id: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

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

let sequence = 0;

/**
 * Toast region. Two live regions exist from the start (polite for status, assertive for errors) so screen readers
 * announce toasts reliably. Timers pause while the pointer or focus is on a toast (WCAG 2.2.1).
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const dismiss = useCallback((id: string) => setToasts((current) => current.filter((toast) => toast.id !== id)), []);
  const show = useCallback((options: ToastOptions) => {
    sequence += 1;
    const id = `toast-${sequence}`;
    setToasts((current) => [...current.slice(-3), { ...options, id }]);
    return id;
  }, []);
  const api = useMemo(() => ({ show, dismiss }), [show, dismiss]);

  const polite = toasts.filter((toast) => toneOf[toast.kind ?? 'info'] !== 'error');
  const assertive = toasts.filter((toast) => toneOf[toast.kind ?? 'info'] === 'error');

  return (
    <ToastContext.Provider value={api}>
      {children}
      {typeof document !== 'undefined' &&
        createPortal(
          <div className="pointer-events-none fixed inset-x-0 bottom-0 z-toast flex flex-col items-center gap-2 p-4 sm:bottom-4 sm:left-auto sm:right-4 sm:items-end sm:p-0">
            <div aria-live="assertive" className="flex w-full flex-col items-center gap-2 sm:items-end">
              {assertive.map((toast) => (
                <ToastCard key={toast.id} toast={toast} onDismiss={dismiss} />
              ))}
            </div>
            <div aria-live="polite" className="flex w-full flex-col items-center gap-2 sm:items-end">
              {polite.map((toast) => (
                <ToastCard key={toast.id} toast={toast} onDismiss={dismiss} />
              ))}
            </div>
          </div>,
          document.body,
        )}
    </ToastContext.Provider>
  );
}

function ToastCard({ toast, onDismiss }: { toast: ToastItem; onDismiss: (id: string) => void }) {
  const kind = toast.kind ?? 'info';
  const tone = toneOf[kind];
  const Icon = iconOf[kind];
  const duration =
    toast.duration === undefined
      ? !toast.action && (tone === 'success' || tone === 'info')
        ? 6000
        : null
      : // An explicit duration is still honoured for an actionable toast, but never below the 10 s WCAG 2.2.1
        // floor: a caller cannot accidentally race the action away with a short timeout.
        toast.action && toast.duration != null
        ? Math.max(toast.duration, 10_000)
        : toast.duration;
  const [paused, setPaused] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (duration == null || paused) return undefined;
    timer.current = setTimeout(() => onDismiss(toast.id), duration);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [duration, paused, onDismiss, toast.id]);

  return (
    <div
      className={cn(
        'pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-card border p-3 text-body-sm shadow-elevated',
        feedbackToneClasses[tone],
      )}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{toast.title}</p>
        {toast.description && <p className="mt-0.5">{toast.description}</p>}
        {toast.action && (
          <button
            type="button"
            onClick={() => {
              toast.action?.onClick();
              onDismiss(toast.id);
            }}
            className="mt-2 min-h-control-sm rounded-input font-semibold underline underline-offset-4"
          >
            {toast.action.label}
          </button>
        )}
      </div>
      <IconButton
        icon={X}
        size="sm"
        aria-label="Đóng thông báo"
        onClick={() => onDismiss(toast.id)}
        className="-mr-1 -mt-1 text-current hover:bg-black/5"
      />
    </div>
  );
}

/** `const toast = useToast(); toast.show({ kind: 'success', title: 'Đã lưu tin' })`. Needs <ToastProvider>. */
export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used inside <ToastProvider>');
  return context;
}
