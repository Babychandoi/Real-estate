/**
 * Money formatting for listing prices (contract §4 in docs/audit-2026-09-27/02_CONTRACTS.md).
 *
 * - vi-VN number format: decimal comma, dot grouping ("3,95 tỷ", "1.200.000 ₫").
 * - SALE prices have no period; RENT prices carry `period: 'MONTH'` and render with "/tháng".
 * - Unit price is a SALE-only field ("~48,2 triệu/m²").
 * Never compare or sort RENT against SALE amounts: callers keep one purpose at a time.
 */

export type PricePeriod = 'MONTH' | 'YEAR';

export interface Money {
  amount: number;
  currency: string;
  /** `null`/absent for SALE, `'MONTH'` for RENT. */
  period?: PricePeriod | null;
}

export interface UnitPrice {
  amount: number;
  per: 'M2';
}

export interface RentTerms {
  monthlyServiceFee?: number | null;
  deposit?: number | null;
}

export interface FormatMoneyOptions {
  /** Abbreviate with "tỷ"/"triệu" (default). `false` prints every digit ("3.950.000.000 ₫"). */
  compact?: boolean;
}

const LOCALE = 'vi-VN';
const BILLION = 1_000_000_000;
const MILLION = 1_000_000;

const PERIOD_SUFFIX: Record<PricePeriod, string> = { MONTH: '/tháng', YEAR: '/năm' };

const decimalFormatters = new Map<number, Intl.NumberFormat>();
function decimal(value: number, maxFractionDigits: number): string {
  let formatter = decimalFormatters.get(maxFractionDigits);
  if (!formatter) {
    formatter = new Intl.NumberFormat(LOCALE, { minimumFractionDigits: 0, maximumFractionDigits: maxFractionDigits });
    decimalFormatters.set(maxFractionDigits, formatter);
  }
  return formatter.format(value);
}

function devWarn(message: string, detail?: unknown): void {
  if (typeof console !== 'undefined') console.warn(`[money] ${message}`, detail);
}

/** Never throws (m9): an invalid ISO 4217 code makes `Intl.NumberFormat` throw a `RangeError`; falls back to VND. */
function currency(amount: number, code: string): string {
  try {
    return new Intl.NumberFormat(LOCALE, { style: 'currency', currency: code, maximumFractionDigits: 0 }).format(
      amount,
    );
  } catch (error) {
    devWarn(`"${code}" is not a valid currency code, falling back to VND`, error);
    if (code === 'VND') return `${decimal(amount, 0)} ₫`; // avoid infinite recursion if VND itself ever fails
    try {
      return new Intl.NumberFormat(LOCALE, { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(
        amount,
      );
    } catch {
      return `${decimal(amount, 0)} ₫`;
    }
  }
}

/**
 * Compact VND amount: ≥ 1 tỷ → up to 2 decimals in tỷ; ≥ 1 triệu → up to 1 decimal in triệu; below that every
 * digit. The amount is rounded to the nearest đồng *before* picking a branch (not only within it), so e.g.
 * 999 999,6 (a fraction can appear via `unitPriceFromArea`) is treated as the 1 000 000 it will display as and
 * renders "1 triệu", not "1.000.000 ₫" (m9). Rounding within a branch is done on integers (hundredths of a tỷ,
 * tenths of a triệu) so there is no floating-point drift, and a value that rounds up to 1.000 triệu is shown as
 * "1 tỷ".
 */
export function formatVndCompact(amount: number): string {
  if (!Number.isFinite(amount)) return '';
  const sign = amount < 0 ? '-' : '';
  const value = Math.round(Math.abs(amount));
  if (value < MILLION) return sign + currency(value, 'VND');

  const tenthsOfMillion = Math.round(value / 100_000);
  if (value < BILLION && tenthsOfMillion < 10_000) return `${sign}${decimal(tenthsOfMillion / 10, 1)} triệu`;

  const hundredthsOfBillion = Math.round(value / 10_000_000);
  return `${sign}${decimal(hundredthsOfBillion / 100, 2)} tỷ`;
}

export function periodSuffix(period?: PricePeriod | null): string {
  return period ? (PERIOD_SUFFIX[period] ?? '') : '';
}

/** "3,95 tỷ" · "850 triệu" · "14,5 triệu/tháng"; empty string when there is no price. */
export function formatMoney(price: Money | null | undefined, options: FormatMoneyOptions = {}): string {
  if (!price || !Number.isFinite(price.amount)) return '';
  const { compact = true } = options;
  const code = (price.currency || 'VND').toUpperCase();
  const text = code === 'VND' && compact ? formatVndCompact(price.amount) : currency(price.amount, code);
  return text + periodSuffix(price.period);
}

/** "~48,2 triệu/m²"; empty string when absent (RENT listings have no unit price). */
export function formatUnitPrice(unitPrice: UnitPrice | null | undefined): string {
  if (!unitPrice || !Number.isFinite(unitPrice.amount) || unitPrice.amount <= 0) return '';
  return `~${formatVndCompact(unitPrice.amount)}/m²`;
}

/** Service fee per month and deposit of a rental, formatted; `null` for the parts that were not provided. */
export function formatRentTerms(terms: RentTerms | null | undefined): {
  monthlyServiceFee: string | null;
  deposit: string | null;
} {
  const fee = terms?.monthlyServiceFee;
  const deposit = terms?.deposit;
  return {
    monthlyServiceFee: fee != null ? formatMoney({ amount: fee, currency: 'VND', period: 'MONTH' }) : null,
    deposit: deposit != null ? formatMoney({ amount: deposit, currency: 'VND' }) : null,
  };
}

/** Adapter for v1 responses that only carry `priceVnd` + `purpose`: RENT prices are per month. */
export function moneyFromLegacy(priceVnd: number, purpose: 'SALE' | 'RENT'): Money {
  return { amount: priceVnd, currency: 'VND', period: purpose === 'RENT' ? 'MONTH' : null };
}

/** Unit price derived from area for v1 responses; SALE only, as in the v2 contract. */
export function unitPriceFromArea(priceVnd: number, areaM2: number, purpose: 'SALE' | 'RENT'): UnitPrice | null {
  if (purpose !== 'SALE' || !(areaM2 > 0) || !Number.isFinite(priceVnd)) return null;
  return { amount: priceVnd / areaM2, per: 'M2' };
}
