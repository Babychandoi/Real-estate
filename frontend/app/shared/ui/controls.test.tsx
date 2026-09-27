import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { Search } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { Checkbox } from './Checkbox';
import { Chip } from './Chip';
import { cn } from './cn';
import { FormField } from './FormField';
import { IconButton } from './IconButton';
import { RadioGroup } from './Radio';
import { Switch } from './Switch';
import { TextInput } from './TextInput';

describe('FormField', () => {
  it('labels the control and links hint and error through aria-describedby', () => {
    render(
      <FormField label="Diện tích (m²)" hint="Diện tích sử dụng" error="Diện tích phải lớn hơn 0." required>
        {(field) => <TextInput {...field} defaultValue="0" />}
      </FormField>,
    );
    const input = screen.getByRole('textbox', { name: /Diện tích \(m²\)/ });
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Diện tích phải lớn hơn 0. Diện tích sử dụng');
  });

  it('announces autosave status for the field', () => {
    render(
      <FormField label="Địa chỉ" status="saving">
        {(field) => <TextInput {...field} />}
      </FormField>,
    );
    expect(screen.getByRole('status')).toHaveTextContent('Đang lưu…');
    expect(screen.getByRole('textbox', { name: 'Địa chỉ' })).toHaveAccessibleDescription('Đang lưu…');
  });
});

describe('buttons and toggles', () => {
  it('marks a loading button busy and disabled', () => {
    render(<Button isLoading>Gửi yêu cầu</Button>);
    const button = screen.getByRole('button', { name: 'Gửi yêu cầu' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });

  it('gives icon-only buttons their aria-label as accessible name', () => {
    render(<IconButton icon={Search} aria-label="Tìm kiếm" />);
    expect(screen.getByRole('button', { name: 'Tìm kiếm' })).toHaveAttribute('type', 'button');
  });

  it('exposes chip selection as aria-pressed', () => {
    function Harness() {
      const [on, setOn] = useState(false);
      return (
        <Chip selected={on} onClick={() => setOn(!on)}>
          Căn hộ
        </Chip>
      );
    }
    render(<Harness />);
    const chip = screen.getByRole('button', { name: 'Căn hộ' });
    expect(chip).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(chip);
    expect(chip).toHaveAttribute('aria-pressed', 'true');
  });

  it('toggles a switch from the switch and from its label', () => {
    const onChange = vi.fn();
    render(<Switch label="Hiển thị công khai" checked={false} onCheckedChange={onChange} />);
    const control = screen.getByRole('switch', { name: 'Hiển thị công khai' });
    expect(control).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(control);
    fireEvent.click(screen.getByText('Hiển thị công khai'));
    expect(onChange).toHaveBeenNthCalledWith(1, true);
    expect(onChange).toHaveBeenNthCalledWith(2, true);
  });

  it('supports indeterminate checkboxes and grouped radios with an error', () => {
    const onChange = vi.fn();
    render(
      <>
        <Checkbox label="Chọn một phần" indeterminate description="2/5 dòng" />
        <RadioGroup
          legend="Nhu cầu"
          name="purpose"
          value={null}
          onChange={onChange}
          error="Chọn nhu cầu."
          options={[
            { value: 'SALE', label: 'Mua bán' },
            { value: 'RENT', label: 'Cho thuê' },
          ]}
        />
      </>,
    );
    const checkbox = screen.getByRole('checkbox', { name: 'Chọn một phần' }) as HTMLInputElement;
    expect(checkbox.indeterminate).toBe(true);
    expect(checkbox).toHaveAccessibleDescription('2/5 dòng');

    const group = screen.getByRole('group', { name: 'Nhu cầu' });
    expect(group).toHaveAccessibleDescription('Chọn nhu cầu.');
    fireEvent.click(screen.getByRole('radio', { name: 'Cho thuê' }));
    expect(onChange).toHaveBeenCalledWith('RENT');
  });
});

describe('cn', () => {
  it('keeps token font sizes next to token colours and resolves real conflicts', () => {
    expect(cn('text-body text-on-surface')).toBe('text-body text-on-surface');
    expect(cn('text-label', 'text-body-sm')).toBe('text-body-sm');
    expect(cn('min-h-control-sm', 'min-h-11')).toBe('min-h-11');
    expect(cn('rounded-card', 'rounded-panel')).toBe('rounded-panel');
    expect(cn('shadow-card', 'shadow-elevated')).toBe('shadow-elevated');
  });
});
