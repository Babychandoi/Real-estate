import React, { useId } from 'react';
import { AlertCircle } from 'lucide-react';
import { cn } from './cn';

export interface RadioProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: React.ReactNode;
  description?: React.ReactNode;
}

/** Native radio with its label (44 px row). Group radios with RadioGroup or a <fieldset>. */
export const Radio = React.forwardRef<HTMLInputElement, RadioProps>(function Radio(
  { label, description, className, id, disabled, ...props },
  ref,
) {
  const generated = useId();
  const inputId = id ?? `radio${generated.replace(/:/g, '')}`;
  const descriptionId = description ? `${inputId}-description` : undefined;
  return (
    <div className={cn('flex min-h-control-md items-start gap-3 py-2.5', className)}>
      <input
        ref={ref}
        id={inputId}
        type="radio"
        disabled={disabled}
        aria-describedby={descriptionId}
        className="mt-0.5 h-5 w-5 shrink-0 cursor-pointer accent-primary disabled:cursor-not-allowed"
        {...props}
      />
      <span className="flex flex-col">
        <label
          htmlFor={inputId}
          className={cn(
            'cursor-pointer text-body-sm font-medium text-on-surface',
            disabled && 'cursor-not-allowed text-on-surface-variant',
          )}
        >
          {label}
        </label>
        {description && (
          <span id={descriptionId} className="text-label font-normal text-on-surface-variant">
            {description}
          </span>
        )}
      </span>
    </div>
  );
});

export interface RadioGroupOption<Value extends string> {
  value: Value;
  label: React.ReactNode;
  description?: React.ReactNode;
  disabled?: boolean;
}

interface RadioGroupProps<Value extends string> {
  legend: React.ReactNode;
  name: string;
  value: Value | null;
  onChange: (value: Value) => void;
  options: ReadonlyArray<RadioGroupOption<Value>>;
  hint?: React.ReactNode;
  error?: React.ReactNode;
  required?: boolean;
  className?: string;
}

/** <fieldset> + <legend> so the question is read with every option; arrow keys move between options natively. */
export function RadioGroup<Value extends string>({
  legend,
  name,
  value,
  onChange,
  options,
  hint,
  error,
  required,
  className,
}: RadioGroupProps<Value>) {
  const generated = useId();
  const base = `radiogroup${generated.replace(/:/g, '')}`;
  const hintId = hint ? `${base}-hint` : undefined;
  const errorId = error ? `${base}-error` : undefined;
  return (
    <fieldset
      className={cn('flex flex-col', className)}
      aria-describedby={[errorId, hintId].filter(Boolean).join(' ') || undefined}
      aria-invalid={error ? true : undefined}
    >
      <legend className="text-body-sm font-semibold text-on-surface">
        {legend}
        {required && (
          <span className="text-error" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </legend>
      {hint && (
        <p id={hintId} className="mt-1 text-label font-normal text-on-surface-variant">
          {hint}
        </p>
      )}
      <div className="mt-1">
        {options.map((option) => (
          <Radio
            key={option.value}
            name={name}
            value={option.value}
            label={option.label}
            description={option.description}
            disabled={option.disabled}
            checked={value === option.value}
            required={required}
            onChange={() => onChange(option.value)}
          />
        ))}
      </div>
      {error && (
        <p id={errorId} className="mt-1 flex items-start gap-1.5 text-label font-normal text-error">
          <AlertCircle className="mt-px h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      )}
    </fieldset>
  );
}
