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
  /** Shown when there is no price; never an invented number. */
  fallback?: string;
  className?: string;
}

/** Price per contract §4 ("3,95 tỷ", "14,5 triệu/tháng"). Rent keeps its period everywhere it is shown. */
export function Money({ price, compact, fallback = 'Chưa có giá', className }: MoneyProps) {
  const text = formatMoney(price, { compact });
  return <span className={cn('tabular-nums', !text && 'text-on-surface-variant', className)}>{text || fallback}</span>;
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
