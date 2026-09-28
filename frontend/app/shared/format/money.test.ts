import { describe, expect, it } from 'vitest';
import {
  formatMoney,
  formatRentTerms,
  formatUnitPrice,
  formatVndCompact,
  moneyFromLegacy,
  periodSuffix,
  unitPriceFromArea,
} from './money';

const NBSP = ' ';
const sale = (amount: number) => ({ amount, currency: 'VND', period: null });
const rent = (amount: number) => ({ amount, currency: 'VND', period: 'MONTH' as const });

describe('formatMoney (contract §4 examples)', () => {
  it('formats SALE prices in tỷ and triệu with a decimal comma', () => {
    expect(formatMoney(sale(3_950_000_000))).toBe('3,95 tỷ');
    expect(formatMoney(sale(850_000_000))).toBe('850 triệu');
  });

  it('adds the monthly period to RENT prices', () => {
    expect(formatMoney(rent(14_500_000))).toBe('14,5 triệu/tháng');
    expect(formatMoney({ amount: 120_000_000, currency: 'VND', period: 'YEAR' })).toBe('120 triệu/năm');
  });

  it('prints every digit when compact is false, keeping the period', () => {
    expect(formatMoney(sale(3_950_000_000), { compact: false })).toBe(`3.950.000.000${NBSP}₫`);
    expect(formatMoney(rent(14_500_000), { compact: false })).toBe(`14.500.000${NBSP}₫/tháng`);
  });

  it('returns an empty string when there is no usable price', () => {
    expect(formatMoney(null)).toBe('');
    expect(formatMoney(undefined)).toBe('');
    expect(formatMoney(sale(Number.NaN))).toBe('');
    expect(formatMoney(sale(Number.POSITIVE_INFINITY))).toBe('');
  });

  it('falls back to Intl currency formatting for other currencies', () => {
    expect(formatMoney({ amount: 1500, currency: 'usd', period: null })).toBe(`1.500${NBSP}US$`);
  });
});

describe('formatVndCompact boundaries', () => {
  it.each([
    [0, `0${NBSP}₫`],
    [999_999, `999.999${NBSP}₫`],
    [1_000_000, '1 triệu'],
    [1_050_000, '1,1 triệu'],
    [1_040_000, '1 triệu'],
    [14_550_000, '14,6 triệu'],
    [999_940_000, '999,9 triệu'],
    // Rounds up to 1.000 triệu → shown in the next unit instead of "1.000 triệu".
    [999_950_000, '1 tỷ'],
    [999_999_999, '1 tỷ'],
    [1_000_000_000, '1 tỷ'],
    [1_004_999_999, '1 tỷ'],
    [1_005_000_000, '1,01 tỷ'],
    [1_250_000_000, '1,25 tỷ'],
    [12_345_000_000, '12,35 tỷ'],
    [125_000_000_000, '125 tỷ'],
    [1_234_500_000_000, '1.234,5 tỷ'],
  ])('%d -> %s', (amount, expected) => {
    expect(formatVndCompact(amount)).toBe(expected);
  });

  it('keeps the sign of negative amounts (price changes)', () => {
    expect(formatVndCompact(-250_000_000)).toBe('-250 triệu');
  });
});

describe('formatUnitPrice', () => {
  it('formats the SALE unit price per m²', () => {
    expect(formatUnitPrice({ amount: 48_170_000, per: 'M2' })).toBe('~48,2 triệu/m²');
    expect(formatUnitPrice({ amount: 1_250_000_000, per: 'M2' })).toBe('~1,25 tỷ/m²');
    expect(formatUnitPrice({ amount: 850_000, per: 'M2' })).toBe(`~850.000${NBSP}₫/m²`);
  });

  it('is empty for missing or non-positive unit prices', () => {
    expect(formatUnitPrice(null)).toBe('');
    expect(formatUnitPrice({ amount: 0, per: 'M2' })).toBe('');
  });
});

describe('rent terms and legacy adapters', () => {
  it('formats service fee per month and deposit separately', () => {
    expect(formatRentTerms({ monthlyServiceFee: 1_200_000, deposit: 29_000_000 })).toEqual({
      monthlyServiceFee: '1,2 triệu/tháng',
      deposit: '29 triệu',
    });
    expect(formatRentTerms({ monthlyServiceFee: null })).toEqual({ monthlyServiceFee: null, deposit: null });
    expect(formatRentTerms(null)).toEqual({ monthlyServiceFee: null, deposit: null });
  });

  it('maps v1 priceVnd + purpose to the money contract', () => {
    expect(moneyFromLegacy(14_500_000, 'RENT')).toEqual({ amount: 14_500_000, currency: 'VND', period: 'MONTH' });
    expect(moneyFromLegacy(3_950_000_000, 'SALE')).toEqual({ amount: 3_950_000_000, currency: 'VND', period: null });
  });

  it('derives a unit price for SALE only', () => {
    expect(unitPriceFromArea(4_817_000_000, 100, 'SALE')).toEqual({ amount: 48_170_000, per: 'M2' });
    expect(unitPriceFromArea(14_500_000, 70, 'RENT')).toBeNull();
    expect(unitPriceFromArea(4_817_000_000, 0, 'SALE')).toBeNull();
  });

  it('has no suffix without a period', () => {
    expect(periodSuffix(null)).toBe('');
    expect(periodSuffix('MONTH')).toBe('/tháng');
  });
});
