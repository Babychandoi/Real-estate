import React from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from './cn';
import { fieldClasses } from './TextInput';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  options?: readonly SelectOption[];
  /** First, empty option (e.g. "Tất cả loại hình"). */
  placeholder?: string;
  size?: 'md' | 'lg';
  invalid?: boolean;
}

/** Native <select> (keyboard and screen-reader behaviour for free) in the design-system look. */
export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { options, placeholder, size = 'md', invalid, className, children, ...props },
  ref,
) {
  const isInvalid = invalid || props['aria-invalid'] === true || props['aria-invalid'] === 'true';
  return (
    <div className="relative">
      <select
        ref={ref}
        {...props}
        aria-invalid={isInvalid || undefined}
        className={fieldClasses({ size, invalid: isInvalid, className: cn('appearance-none pr-10', className) })}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options?.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
        {children}
      </select>
      <ChevronDown
        className="pointer-events-none absolute right-3 top-1/2 h-5 w-5 -translate-y-1/2 text-on-surface-variant"
        aria-hidden="true"
      />
    </div>
  );
});
