import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';
import { cn } from './cn';
import { InlineFeedback } from './InlineFeedback';

/** Page numbers to show around the current page, with `null` for a gap ("…"). Pages are 1-based. */
export function pageWindow(page: number, pageCount: number, radius = 1): Array<number | null> {
  const pages = new Set<number>([1, pageCount]);
  for (let offset = -radius; offset <= radius; offset += 1) pages.add(page + offset);
  const sorted = [...pages].filter((value) => value >= 1 && value <= pageCount).sort((a, b) => a - b);
  const result: Array<number | null> = [];
  sorted.forEach((value, index) => {
    if (index > 0 && value - sorted[index - 1] > 1) result.push(null);
    result.push(value);
  });
  return result;
}

interface PaginationProps {
  /** Current page, 1-based. */
  page: number;
  /** Known number of pages; without it only previous/next are shown (cursor or estimated totals). */
  pageCount?: number;
  hasNext?: boolean;
  onPageChange: (page: number) => void;
  label?: string;
  disabled?: boolean;
  className?: string;
}

/** Page-based navigation for server-paged lists (admin queues, tables). */
export function Pagination({
  page,
  pageCount,
  hasNext,
  onPageChange,
  label = 'Phân trang',
  disabled = false,
  className,
}: PaginationProps) {
  const canNext = pageCount != null ? page < pageCount : Boolean(hasNext);
  return (
    <nav aria-label={label} className={cn('flex flex-wrap items-center justify-center gap-1', className)}>
      <Button
        variant="ghost"
        onClick={() => onPageChange(page - 1)}
        disabled={disabled || page <= 1}
        leftIcon={<ChevronLeft className="h-4 w-4" />}
      >
        Trước
      </Button>
      {pageCount != null ? (
        <ul className="flex items-center gap-1">
          {pageWindow(page, pageCount).map((value, index) =>
            value == null ? (
              <li key={`gap-${index}`} className="px-1 text-on-surface-variant" aria-hidden="true">
                …
              </li>
            ) : (
              <li key={value}>
                <button
                  type="button"
                  onClick={() => onPageChange(value)}
                  disabled={disabled}
                  aria-current={value === page ? 'page' : undefined}
                  aria-label={`Trang ${value}`}
                  className={cn(
                    'min-h-control-md min-w-control-md rounded-input px-2 text-body-sm font-semibold transition-colors',
                    value === page ? 'bg-primary text-primary-on' : 'text-on-surface hover:bg-surface-container',
                  )}
                >
                  {value}
                </button>
              </li>
            ),
          )}
        </ul>
      ) : (
        <span className="px-3 text-body-sm text-on-surface-variant">Trang {page}</span>
      )}
      <Button
        variant="ghost"
        onClick={() => onPageChange(page + 1)}
        disabled={disabled || !canNext}
        rightIcon={<ChevronRight className="h-4 w-4" />}
      >
        Sau
      </Button>
    </nav>
  );
}

export interface ResultTotal {
  value: number;
  /** "eq" = exact count, "gte" = at least this many (search engines cap counts). */
  relation: 'eq' | 'gte';
}

const numberFormat = new Intl.NumberFormat('vi-VN');

/** "Đang hiển thị 24 trên 1.200 tin" / "… trên hơn 10.000 tin" / "Đã hiển thị tất cả 12 tin". */
export function loadedSummary(loaded: number, hasNext: boolean, total: ResultTotal | null | undefined, noun: string) {
  if (!hasNext) return `Đã hiển thị tất cả ${numberFormat.format(loaded)} ${noun}`;
  if (!total) return `Đang hiển thị ${numberFormat.format(loaded)} ${noun}`;
  const count = numberFormat.format(total.value);
  return `Đang hiển thị ${numberFormat.format(loaded)} trên ${total.relation === 'gte' ? `hơn ${count}` : count} ${noun}`;
}

interface LoadMoreProps {
  loadedCount: number;
  hasNext: boolean;
  loading: boolean;
  onLoadMore: () => void;
  total?: ResultTotal | null;
  /** What is being counted ("tin", "khách"). */
  noun?: string;
  label?: string;
  /** Last attempt failed; shows a retry that calls onLoadMore again. */
  error?: string | null;
  className?: string;
}

/**
 * Cursor pagination ("Xem thêm"): the summary never uses the page length as the total, estimates say "hơn", and
 * the count is announced politely after each load.
 */
export function LoadMore({
  loadedCount,
  hasNext,
  loading,
  onLoadMore,
  total,
  noun = 'kết quả',
  label = 'Xem thêm',
  error,
  className,
}: LoadMoreProps) {
  return (
    <div className={cn('flex flex-col items-center gap-3', className)}>
      <p role="status" className="text-body-sm text-on-surface-variant">
        {loadedSummary(loadedCount, hasNext, total, noun)}
      </p>
      {error ? (
        <InlineFeedback
          kind="error"
          title={error}
          action={{ label: 'Thử lại', onClick: onLoadMore, isLoading: loading }}
        />
      ) : (
        hasNext && (
          <Button variant="outline" size="lg" onClick={onLoadMore} isLoading={loading} className="min-w-48">
            {loading ? 'Đang tải…' : label}
          </Button>
        )
      )}
    </div>
  );
}
