import {
  formatMoney,
  formatUnitPrice,
  type FormatMoneyOptions,
  type Money as MoneyValue,
  type UnitPrice,
} from '@/shared/format/money';
import { cn } from './cn';

interface MoneyProps extends FormatMoneyOptions {
  price: MoneyValue | null | undefined;
  /** Shown when there is no price at all (missing/invalid amount). */
  fallback?: string;
  /**
   * Shown when a price object is present but its amount is zero or negative (m9): rendering "0 ₫" would read as a
   * real, quoted price, so a zero/negative amount is treated the same as "no usable price" but with wording that
   * fits how a free-text asking price is usually missing on this market — "thoả thuận" (negotiable) — rather than
   * "Chưa có giá" (no price yet), which is reserved for when the field itself is absent.
   */
  zeroFallback?: string;
  className?: string;
}

/** Price per contract §4 ("3,95 tỷ", "14,5 triệu/tháng"). Rent keeps its period everywhere it is shown. */
export function Money({
  price,
  compact,
  fallback = 'Chưa có giá',
  zeroFallback = 'Thỏa thuận',
  className,
}: MoneyProps) {
  const isNonPositive = Boolean(price) && Number.isFinite(price!.amount) && price!.amount <= 0;
  const text = isNonPositive ? '' : formatMoney(price, { compact });
  const shown = text || (isNonPositive ? zeroFallback : fallback);
  return <span className={cn('tabular-nums', !text && 'text-on-surface-variant', className)}>{shown}</span>;
}

/** "~48,2 triệu/m²" for SALE listings; renders nothing when there is no unit price (RENT). */
export function UnitPriceText({
  unitPrice,
  className,
}: {
  unitPrice: UnitPrice | null | undefined;
  className?: string;
}) {
  const text = formatUnitPrice(unitPrice);
  return text ? <span className={cn('tabular-nums', className)}>{text}</span> : null;
}
