import { useState } from 'react';
import { SearchX } from 'lucide-react';
import type { ListingSummaryV2, MapPoint } from '@/entities/listing/model/v2';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Sheet } from '@/shared/ui/Sheet';
import { Skeleton } from '@/shared/ui/Skeleton';
import { DEFAULT_FILTERS, validateFilters, withoutParams, type SearchFilters } from '../filterSchema';
import type { SearchState } from '../useListingSearch';
import { FilterFields } from './FilterFields';

/*
 * Secondary panels of /search, loaded on demand (F15.2 bundle budget): the filter sheet, the zero-result state with
 * its suggestions, the error state and the map point sheet. The list view of a successful search never needs them.
 */

const SUGGESTION_LABELS: Record<string, string> = {
  REMOVE_KEYWORD: 'Bỏ từ khóa',
  REMOVE_PRICE: 'Bỏ khoảng giá',
  REMOVE_AREA: 'Bỏ khoảng diện tích',
  REMOVE_BEDROOMS: 'Bỏ số phòng ngủ',
  REMOVE_VERIFIED: 'Bỏ điều kiện xác minh',
  REMOVE_ATTRIBUTES: 'Bỏ pháp lý và nội thất',
  WIDEN_AREA: 'Tìm ở mọi khu vực',
  REMOVE_TYPE: 'Mọi loại hình',
};

export function FilterSheet({
  filters,
  onClose,
  onApply,
}: {
  filters: SearchFilters;
  onClose: () => void;
  onApply: (next: SearchFilters) => void;
}) {
  const [draft, setDraft] = useState<SearchFilters>(filters);
  const draftErrors = validateFilters(draft);
  return (
    <Sheet
      open
      onClose={onClose}
      title="Bộ lọc"
      description={filters.purpose === 'RENT' ? 'Giá thuê tính theo tháng.' : 'Giá bán tính theo tổng giá.'}
      footer={
        <div className="flex w-full gap-3">
          <Button
            variant="ghost"
            onClick={() =>
              setDraft({
                ...DEFAULT_FILTERS,
                purpose: draft.purpose,
                q: draft.q,
                bbox: draft.bbox,
                place: draft.place,
                view: draft.view,
              })
            }
          >
            Xóa lọc
          </Button>
          <Button
            className="flex-1"
            disabled={draftErrors.length > 0}
            onClick={() => {
              onClose();
              onApply(draft);
            }}
          >
            Xem kết quả
          </Button>
        </div>
      }
    >
      <FilterFields key={draft.purpose} draft={draft} onChange={setDraft} />
    </Sheet>
  );
}

export function NoResults({
  filters,
  suggestions,
  onApply,
}: {
  filters: SearchFilters;
  suggestions: SearchState['suggestions'];
  onApply: (next: SearchFilters) => void;
}) {
  return (
    <EmptyState
      icon={SearchX}
      title="Chưa có tin phù hợp"
      description={
        suggestions.length
          ? 'Thử nới một điều kiện dưới đây (số tin là số đang có với điều kiện đã nới):'
          : 'Thử bỏ bớt bộ lọc hoặc tìm ở khu vực khác.'
      }
      actions={
        suggestions.length ? (
          suggestions.map((suggestion) => (
            <Button key={suggestion.type} variant="outline" onClick={() => onApply(withoutParams(filters, suggestion.drop))}>
              {SUGGESTION_LABELS[suggestion.type] ?? 'Nới điều kiện'} (
              {suggestion.total.relation === 'gte' ? 'hơn ' : ''}
              {suggestion.total.value.toLocaleString('vi-VN')} tin)
            </Button>
          ))
        ) : (
          <Button variant="outline" onClick={() => onApply({ ...DEFAULT_FILTERS, purpose: filters.purpose })}>
            Xóa bộ lọc
          </Button>
        )
      }
    />
  );
}

export function SearchError({
  error,
  onRetry,
}: {
  error: NonNullable<SearchState['error']>;
  onRetry: () => void;
}) {
  return error.errors.length ? (
    <InlineFeedback kind="error" title={error.message}>
      <ul className="list-disc pl-5">
        {error.errors.map((item) => (
          <li key={`${item.param}-${item.message}`}>{item.message}</li>
        ))}
      </ul>
    </InlineFeedback>
  ) : (
    <ErrorState description={error.message} onRetry={onRetry} headingLevel={3} />
  );
}

export function MapPointSheet({
  point,
  listing,
  onClose,
  onOpen,
}: {
  point: MapPoint;
  listing: ListingSummaryV2 | null;
  onClose: () => void;
  onOpen: (point: MapPoint) => void;
}) {
  return (
    <Sheet open onClose={onClose} title="Tin trên bản đồ">
      {listing ? <ListingCard listing={listing} headingLevel="h2" /> : <Skeleton className="h-72 rounded-card" />}
      <Button variant="ghost" className="mt-3 w-full" onClick={() => onOpen(point)}>
        Mở trang chi tiết
      </Button>
    </Sheet>
  );
}
