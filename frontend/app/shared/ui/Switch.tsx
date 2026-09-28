import React, { useId } from 'react';
import { cn } from './cn';

interface SwitchProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'onChange' | 'children'> {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  label: React.ReactNode;
  description?: React.ReactNode;
}

/** On/off setting that applies immediately (role="switch"); use Checkbox for choices submitted with a form. */
export function Switch({
  checked,
  onCheckedChange,
  label,
  description,
  disabled,
  className,
  id,
  ...props
}: SwitchProps) {
  const generated = useId();
  const switchId = id ?? `switch${generated.replace(/:/g, '')}`;
  const descriptionId = description ? `${switchId}-description` : undefined;
  return (
    <div className={cn('flex min-h-control-md items-center justify-between gap-4 py-2', className)}>
      <span className="flex flex-col">
        <label
          htmlFor={switchId}
          className={cn('text-body-sm font-medium text-on-surface', disabled && 'text-on-surface-variant')}
        >
          {label}
        </label>
        {description && (
          <span id={descriptionId} className="text-label font-normal text-on-surface-variant">
            {description}
          </span>
        )}
      </span>
      <button
        {...props}
        id={switchId}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-describedby={descriptionId}
        disabled={disabled}
        onClick={() => onCheckedChange(!checked)}
        className="inline-grid min-h-control-md min-w-control-md shrink-0 place-items-center rounded-pill disabled:cursor-not-allowed disabled:opacity-50"
      >
        <span
          className={cn(
            'relative inline-flex h-6 w-11 items-center rounded-pill border-2 transition-colors duration-fast',
            checked ? 'border-primary bg-primary' : 'border-outline bg-surface-container-highest',
          )}
          aria-hidden="true"
        >
          <span
            className={cn(
              'absolute h-4 w-4 rounded-pill shadow transition-transform duration-fast',
              checked ? 'translate-x-5 bg-primary-on' : 'translate-x-0.5 bg-on-surface-variant',
            )}
          />
        </span>
      </button>
    </div>
  );
}
