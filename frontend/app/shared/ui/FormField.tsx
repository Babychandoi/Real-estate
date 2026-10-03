import React, { useId } from 'react';
import { AlertCircle, Check, Loader2 } from 'lucide-react';
import { cn } from './cn';

/** Props FormField hands to its control: a stable id plus the hint/error wiring. */
export interface FieldControlProps {
  id: string;
  'aria-describedby'?: string;
  'aria-invalid'?: true;
  required?: boolean;
  disabled?: boolean;
}

export type FieldStatus = 'saving' | 'saved';

interface FormFieldProps {
  label: React.ReactNode;
  /**
   * Guidance (limits, rules) shown under the control, never between label and control: a line there would push this
   * control lower than its neighbours in the same row. Short examples belong in the control's `placeholder`.
   */
  hint?: React.ReactNode;
  /** Error for this field, shown next to it and announced through aria-describedby. */
  error?: React.ReactNode;
  required?: boolean;
  disabled?: boolean;
  /** Autosave feedback for this field. */
  status?: FieldStatus;
  /** Use a fixed id when the control must be addressable (e.g. from an error summary). */
  id?: string;
  className?: string;
  children: (control: FieldControlProps) => React.ReactNode;
}

/**
 * Label, then the control directly under it, then error, hint and status. Everything the control needs to say sits
 * below it, so every control in a row of a form starts at the same y. The label is a real <label for>, hint and error
 * are linked with aria-describedby (error first) and the control gets aria-invalid while there is an error.
 */
export function FormField({ label, hint, error, required, disabled, status, id, className, children }: FormFieldProps) {
  const generated = useId();
  const controlId = id ?? `field${generated.replace(/:/g, '')}`;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const statusId = status ? `${controlId}-status` : undefined;
  const describedBy = [errorId, hintId, statusId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label
        htmlFor={controlId}
        className={cn(
          'text-body-sm font-semibold text-on-surface [overflow-wrap:anywhere]',
          disabled && 'text-on-surface-variant',
        )}
      >
        {label}
        {required && (
          <span className="text-error" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      {children({
        id: controlId,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
        required,
        disabled,
      })}
      {error && (
        // Error text is 14 px (DS-03: errors are never caption-sized) and wraps inside the field.
        <p id={errorId} data-error="" className="flex items-start gap-1.5 text-body-sm font-normal text-error">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span className="min-w-0 [overflow-wrap:anywhere]">{error}</span>
        </p>
      )}
      {hint && (
        <p id={hintId} className="text-label font-normal text-on-surface-variant [overflow-wrap:anywhere]">
          {hint}
        </p>
      )}
      {status && (
        <p
          id={statusId}
          role="status"
          className="flex items-center gap-1.5 text-label font-normal text-on-surface-variant"
        >
          {status === 'saving' ? (
            <>
              <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Đang lưu…
            </>
          ) : (
            <>
              <Check className="h-4 w-4 text-success" aria-hidden="true" /> Đã lưu
            </>
          )}
        </p>
      )}
    </div>
  );
}
