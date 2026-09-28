import { describe, expect, it } from 'vitest';
import { calculateUnitPrice, formatPriceVnd } from './types';

describe('legacy listing price helpers', () => {
  it('use the vi-VN decimal comma of the shared money formatter', () => {
    expect(formatPriceVnd(3_950_000_000)).toBe('3,95 tỷ');
    expect(formatPriceVnd(1_500_000_000)).toBe('1,5 tỷ');
    expect(formatPriceVnd(14_500_000)).toBe('14,5 triệu');
    // Previously rendered as "1000.0 triệu".
    expect(formatPriceVnd(999_999_999)).toBe('1 tỷ');
  });

  it('keep the short "tr/m²" unit price used on cards', () => {
    expect(calculateUnitPrice(4_817_000_000, 100)).toBe('~48,2 tr/m²');
    expect(calculateUnitPrice(4_800_000_000, 100)).toBe('~48 tr/m²');
    expect(calculateUnitPrice(500_000_000, 1000)).toBe('');
    expect(calculateUnitPrice(1_000_000_000, 0)).toBe('');
  });
});
