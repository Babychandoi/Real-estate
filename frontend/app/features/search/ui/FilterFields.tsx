import { useState } from 'react';
import { PriceTypeChips } from './PriceTypeChips';
import { FURNISHING_LABELS, LEGAL_LABELS, propertyTypeLabel } from '@/entities/listing/model/v2';
import { Chip, ChipGroup } from '@/shared/ui/Chip';
import { FormField } from '@/shared/ui/FormField';
import { RadioGroup } from '@/shared/ui/Radio';
import { TextInput } from '@/shared/ui/TextInput';
import {
  DISTRICTS,
  FURNISHINGS,
  LEGAL_CODES,
  PROPERTY_TYPES,
  validateFilters,
  type SearchFilters,
  type VerifiedScope,
} from '../filterSchema';

function toggle<T>(values: readonly T[], value: T): T[] {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value];
}

/** Price input unit per purpose: RENT in triệu/tháng, SALE in tỷ. */
export function priceUnit(purpose: SearchFilters['purpose']) {
  return purpose === 'RENT'
    ? { factor: 1_000_000, label: 'triệu đồng/tháng' }
    : { factor: 1_000_000_000, label: 'tỷ đồng' };
}

const decimalInput = new Intl.NumberFormat('en-US', { maximumFractionDigits: 3, useGrouping: false });

function toUnitText(value: number | undefined, factor: number) {
  return value == null ? '' : decimalInput.format(value / factor).replace('.', ',');
}

function fromUnitText(text: string, factor: number): number | undefined | null {
  const trimmed = text.trim().replace(',', '.');
  if (!trimmed) return undefined;
  if (!/^\d+(\.\d{1,3})?$/.test(trimmed)) return null;
  return Math.round(Number(trimmed) * factor);
}

/**
 * Every server-side filter (F03.2) with labels and units that follow the purpose (F04.2). Used in the filter sheet;
 * the caller applies the draft.
 */
export function FilterFields({ draft, onChange }: { draft: SearchFilters; onChange: (next: SearchFilters) => void }) {
  const unit = priceUnit(draft.purpose);
  const [priceText, setPriceText] = useState(() => ({
    min: toUnitText(draft.priceMin, unit.factor),
    max: toUnitText(draft.priceMax, unit.factor),
    purpose: draft.purpose,
  }));
  // Re-seed the text inputs when the purpose (unit) or a preset changed the bounds from outside.
  const seeded =
    priceText.purpose === draft.purpose &&
    fromUnitText(priceText.min, unit.factor) === draft.priceMin &&
    fromUnitText(priceText.max, unit.factor) === draft.priceMax;
  const shown = seeded
    ? priceText
    : {
        min: toUnitText(draft.priceMin, unit.factor),
        max: toUnitText(draft.priceMax, unit.factor),
        purpose: draft.purpose,
      };
  const [priceError, setPriceError] = useState<string | null>(null);
  const crossErrors = validateFilters(draft);
  const errorFor = (param: string) => crossErrors.find((error) => error.param === param)?.message;

  const setPrice = (which: 'min' | 'max', text: string) => {
    const nextText = { ...shown, [which]: text };
    setPriceText(nextText);
    const parsed = fromUnitText(text, unit.factor);
    if (parsed === null) {
      setPriceError(`Nhập số ${unit.label}, ví dụ ${draft.purpose === 'RENT' ? '12,5' : '3,2'}.`);
      return;
    }
    setPriceError(null);
    const next = { ...draft };
    if (which === 'min') {
      if (parsed === undefined) delete next.priceMin;
      else next.priceMin = parsed;
    } else if (parsed === undefined) delete next.priceMax;
    else next.priceMax = parsed;
    onChange(next);
  };

  const setArea = (which: 'areaMin' | 'areaMax', text: string) => {
    const next = { ...draft };
    const value = Number(text.replace(',', '.'));
    if (!text.trim() || !Number.isFinite(value) || value <= 0) delete next[which];
    else next[which] = Math.round(value * 100) / 100;
    onChange(next);
  };

  return (
    <div className="flex flex-col gap-6">
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body-sm font-semibold text-on-surface">Loại hình</legend>
        <ChipGroup label="Loại hình">
          {PROPERTY_TYPES.map((type) => (
            <Chip
              key={type}
              selected={draft.types.includes(type)}
              onClick={() => onChange({ ...draft, types: toggle(draft.types, type).sort() })}
            >
              {propertyTypeLabel(type)}
            </Chip>
          ))}
        </ChipGroup>
      </fieldset>

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-2 text-body-sm font-semibold text-on-surface">
          {draft.purpose === 'RENT' ? 'Giá thuê mỗi tháng' : 'Giá bán'}
        </legend>
        <PriceTypeChips filters={draft} onChange={onChange} />
        <div className="grid grid-cols-2 gap-3">
          <FormField label={`Từ (${unit.label})`} error={priceError ?? undefined}>
            {(control) => (
              <TextInput
                {...control}
                inputMode="decimal"
                value={shown.min}
                onChange={(event) => setPrice('min', event.target.value)}
              />
            )}
          </FormField>
          <FormField label={`Đến (${unit.label})`} error={errorFor('priceMax')}>
            {(control) => (
              <TextInput
                {...control}
                inputMode="decimal"
                value={shown.max}
                onChange={(event) => setPrice('max', event.target.value)}
              />
            )}
          </FormField>
        </div>
      </fieldset>

      <fieldset className="grid grid-cols-2 gap-3">
        <legend className="mb-2 text-body-sm font-semibold text-on-surface">Diện tích (m²)</legend>
        <FormField label="Từ">
          {(control) => (
            <TextInput
              {...control}
              inputMode="decimal"
              defaultValue={draft.areaMin ?? ''}
              onChange={(event) => setArea('areaMin', event.target.value)}
            />
          )}
        </FormField>
        <FormField label="Đến" error={errorFor('areaMax')}>
          {(control) => (
            <TextInput
              {...control}
              inputMode="decimal"
              defaultValue={draft.areaMax ?? ''}
              onChange={(event) => setArea('areaMax', event.target.value)}
            />
          )}
        </FormField>
      </fieldset>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body-sm font-semibold text-on-surface">Số phòng ngủ tối thiểu</legend>
        <ChipGroup label="Số phòng ngủ tối thiểu">
          {[1, 2, 3, 4, 5].map((beds) => (
            <Chip
              key={beds}
              selected={draft.bedsMin === beds}
              onClick={() => {
                const next = { ...draft };
                if (draft.bedsMin === beds) delete next.bedsMin;
                else next.bedsMin = beds;
                onChange(next);
              }}
            >
              {beds}+
            </Chip>
          ))}
        </ChipGroup>
      </fieldset>

      <RadioGroup<'ALL' | VerifiedScope>
        name="verified"
        legend="Xác minh"
        value={draft.verified ?? 'ALL'}
        onChange={(value) => {
          const next = { ...draft };
          if (value === 'ALL') delete next.verified;
          else next.verified = value;
          onChange(next);
        }}
        options={[
          { value: 'ALL', label: 'Tất cả tin' },
          {
            value: 'IDENTITY',
            label: 'Người đăng đã xác minh danh tính',
            description: 'Không bảo đảm quyền sở hữu hay pháp lý giao dịch.',
          },
          {
            value: 'OWNERSHIP',
            label: 'Tin đã đối chiếu giấy tờ chủ sở hữu',
            description: 'Đối chiếu tại thời điểm kiểm tra; không thay thế thẩm định pháp lý.',
          },
        ]}
      />

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body-sm font-semibold text-on-surface">Pháp lý</legend>
        <ChipGroup label="Pháp lý">
          {LEGAL_CODES.map((code) => (
            <Chip
              key={code}
              selected={draft.legal.includes(code)}
              onClick={() => onChange({ ...draft, legal: toggle(draft.legal, code).sort() })}
            >
              {LEGAL_LABELS[code]}
            </Chip>
          ))}
        </ChipGroup>
      </fieldset>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body-sm font-semibold text-on-surface">Nội thất</legend>
        <ChipGroup label="Nội thất">
          {FURNISHINGS.map((value) => (
            <Chip
              key={value}
              selected={draft.furnishing.includes(value)}
              onClick={() => onChange({ ...draft, furnishing: toggle(draft.furnishing, value).sort() })}
            >
              {FURNISHING_LABELS[value]}
            </Chip>
          ))}
        </ChipGroup>
      </fieldset>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-body-sm font-semibold text-on-surface">Khu vực (Hà Nội)</legend>
        <p className="text-label font-normal text-on-surface-variant">
          Theo tên quận/huyện trước 07/2025 mà tin đăng đang dùng.
        </p>
        <ChipGroup label="Khu vực">
          {DISTRICTS.map((district) => (
            <Chip
              key={district.code}
              size="sm"
              selected={draft.districts.includes(district.code)}
              onClick={() => onChange({ ...draft, districts: toggle(draft.districts, district.code).sort() })}
            >
              {district.name}
            </Chip>
          ))}
        </ChipGroup>
      </fieldset>
    </div>
  );
}
