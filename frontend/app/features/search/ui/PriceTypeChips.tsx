import { Chip, ChipGroup } from '@/shared/ui/Chip';
import { pricePresets, presetMatches, type SearchFilters } from '../filterSchema';

/** Purpose/type-aware price bands (F04.2); selecting one again clears the price range. */
export function PriceTypeChips({
  filters,
  onChange,
  size = 'md',
}: {
  filters: SearchFilters;
  onChange: (next: SearchFilters) => void;
  size?: 'sm' | 'md';
}) {
  return (
    <ChipGroup label={filters.purpose === 'RENT' ? 'Giá thuê mỗi tháng' : 'Khoảng giá'}>
      {pricePresets(filters.purpose, filters.types).map((preset) => {
        const selected = presetMatches(preset, filters);
        return (
          <Chip
            key={preset.label}
            size={size}
            selected={selected}
            onClick={() => {
              const next = { ...filters };
              if (selected) {
                delete next.priceMin;
                delete next.priceMax;
              } else {
                next.priceMin = preset.min;
                next.priceMax = preset.max;
                if (preset.min == null) delete next.priceMin;
                if (preset.max == null) delete next.priceMax;
              }
              onChange(next);
            }}
          >
            {preset.label}
          </Chip>
        );
      })}
    </ChipGroup>
  );
}
