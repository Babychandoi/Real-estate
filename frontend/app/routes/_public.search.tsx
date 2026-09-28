import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Columns2, LayoutList, Map as MapIcon, SearchX, SlidersHorizontal } from 'lucide-react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingSummaryV2, MapPoint } from '@/entities/listing/model/v2';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import {
  activeFilterCount,
  DEFAULT_FILTERS,
  effectiveSort,
  parseSearchParams,
  serializeFilters,
  SORT_LABELS,
  validateFilters,
  withoutParams,
  withPurpose,
  type SearchFilters,
  type SearchSort,
  type SearchView,
} from '@/features/search/filterSchema';
import { useListingSearch } from '@/features/search/useListingSearch';
import { PriceTypeChips } from '@/features/search/ui/PriceTypeChips';
import { SearchBox } from '@/features/search/ui/SearchBox';
import { Button } from '@/shared/ui/Button';
import { Chip, ChipGroup } from '@/shared/ui/Chip';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { LoadMore } from '@/shared/ui/Pagination';
import { Select } from '@/shared/ui/Select';
import { Sheet } from '@/shared/ui/Sheet';
import { Skeleton } from '@/shared/ui/Skeleton';
import { cn } from '@/shared/ui/cn';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';

/** MapLibre (≈ 470 kB) is fetched only when the map is shown (F15.1): list mode never downloads it. */
const SearchMap = lazy(() => import('@/features/search/ui/SearchMap'));
/** The full filter form is only needed once the sheet opens. */
const FilterFields = lazy(() => import('@/features/search/ui/FilterFields').then((m) => ({ default: m.FilterFields })));

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

const NOTICE_TEXT: Record<string, string> = {
  SEARCH_ENGINE_UNAVAILABLE:
    'Công cụ tìm kiếm đang bảo trì; kết quả được lấy trực tiếp từ cơ sở dữ liệu nên có thể chậm hơn.',
  RELEVANCE_APPROXIMATE: 'Tạm thời sắp xếp theo tin mới nhất thay cho mức độ phù hợp với từ khóa.',
};

/** Old links (/search?keyword=…&propertyType=…) keep working: they are rewritten to the v2 parameter names. */
function migrateLegacyParams(params: URLSearchParams): URLSearchParams | null {
  const legacy = { keyword: 'q', propertyType: 'type' } as const;
  if (!Object.keys(legacy).some((key) => params.has(key))) return null;
  const next = new URLSearchParams(params);
  for (const [from, to] of Object.entries(legacy)) {
    const value = next.get(from);
    next.delete(from);
    if (value && !next.has(to)) next.set(to, value);
  }
  return next;
}

export function SearchAndMapPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { filters, errors: urlErrors } = useMemo(() => parseSearchParams(searchParams), [searchParams]);
  const { state, loadMore, retry, saveScroll } = useListingSearch(filters, location.key);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [draft, setDraft] = useState<SearchFilters>(filters);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [selectedPoint, setSelectedPoint] = useState<MapPoint | null>(null);
  const [pointListing, setPointListing] = useState<ListingSummaryV2 | null>(null);

  useDocumentMeta({
    title: `${filters.purpose === 'RENT' ? 'Nhà đất cho thuê' : 'Nhà đất bán'} | Tìm kiếm | Nhà Đất Chuẩn`,
    description:
      'Tìm nhà đất bán và cho thuê tại Hà Nội theo khu vực, giá, diện tích, số phòng ngủ và mức độ xác minh.',
  });

  useEffect(() => {
    const migrated = migrateLegacyParams(searchParams);
    if (migrated) setSearchParams(migrated, { replace: true });
  }, [searchParams, setSearchParams]);

  /** Intentional changes add a history entry (back/forward walks through them); map moves replace it (F03.4). */
  const apply = useCallback(
    (next: SearchFilters, mode: 'push' | 'replace' = 'push') => {
      setSearchParams(serializeFilters(next), { replace: mode === 'replace' });
    },
    [setSearchParams],
  );

  // Restore the scroll position when coming back from a detail page; remember it when leaving.
  useLayoutEffect(() => {
    if (state.restored && state.scrollY > 0) window.scrollTo(0, state.scrollY);
    // Only on mount: the snapshot belongs to this history entry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useLayoutEffect(() => () => saveScroll(window.scrollY), [saveScroll]);

  const openFilters = () => {
    setDraft(filters);
    setFiltersOpen(true);
  };
  const draftErrors = validateFilters(draft);
  const view = filters.view;
  const showMap = view !== 'list';
  const sort = effectiveSort(filters);

  const selectPoint = (point: MapPoint) => {
    setSelectedPoint(point);
    const known = state.items.find((item) => item.id === point.id) ?? null;
    setPointListing(known);
    if (known && view === 'split') {
      document
        .querySelector(`[data-listing-id="${point.id}"]`)
        ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      return;
    }
    if (!known) {
      listingV2Api
        .detail(point.id)
        .then(setPointListing)
        .catch(() => setPointListing(null));
    }
  };

  const resultsHeading =
    state.status === 'ready' && state.total
      ? `${state.total.relation === 'gte' ? 'Hơn ' : ''}${state.total.value.toLocaleString('vi-VN')} tin ${
          filters.purpose === 'RENT' ? 'cho thuê' : 'đang bán'
        }`
      : filters.purpose === 'RENT'
        ? 'Nhà đất cho thuê'
        : 'Nhà đất đang bán';

  const results = (
    <section aria-labelledby="search-results-heading" className="flex min-w-0 flex-col gap-4">
      <h2 id="search-results-heading" className="text-headline-sm text-on-surface" aria-live="polite">
        {resultsHeading}
      </h2>
      {state.restarted && (
        <InlineFeedback kind="info" title="Danh sách đã được tải lại từ đầu">
          Hệ thống tìm kiếm vừa chuyển chế độ nên thứ tự trang cũ không còn dùng được.
        </InlineFeedback>
      )}
      {state.notices
        .filter((notice) => NOTICE_TEXT[notice])
        .map((notice) => (
          <InlineFeedback key={notice} kind="warning" title={NOTICE_TEXT[notice]} />
        ))}
      {state.status === 'error' && state.error ? (
        state.error.errors.length ? (
          <InlineFeedback kind="error" title={state.error.message}>
            <ul className="list-disc pl-5">
              {state.error.errors.map((error) => (
                <li key={`${error.param}-${error.message}`}>{error.message}</li>
              ))}
            </ul>
          </InlineFeedback>
        ) : (
          <ErrorState description={state.error.message} onRetry={retry} headingLevel={3} />
        )
      ) : state.status === 'loading' && state.items.length === 0 ? (
        <div
          className={cn('grid gap-4', view === 'split' ? 'sm:grid-cols-2' : 'sm:grid-cols-2 xl:grid-cols-3')}
          role="status"
          aria-label="Đang tải kết quả"
        >
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-80 rounded-card" />
          ))}
        </div>
      ) : state.items.length === 0 ? (
        <EmptyState
          icon={SearchX}
          title="Chưa có tin phù hợp"
          description={
            state.suggestions.length
              ? 'Thử nới một điều kiện dưới đây (số tin là số đang có với điều kiện đã nới):'
              : 'Thử bỏ bớt bộ lọc hoặc tìm ở khu vực khác.'
          }
          actions={
            state.suggestions.length ? (
              state.suggestions.map((suggestion) => (
                <Button
                  key={suggestion.type}
                  variant="outline"
                  onClick={() => apply(withoutParams(filters, suggestion.drop))}
                >
                  {SUGGESTION_LABELS[suggestion.type] ?? 'Nới điều kiện'} (
                  {suggestion.total.relation === 'gte' ? 'hơn ' : ''}
                  {suggestion.total.value.toLocaleString('vi-VN')} tin)
                </Button>
              ))
            ) : (
              <Button variant="outline" onClick={() => apply({ ...DEFAULT_FILTERS, purpose: filters.purpose })}>
                Xóa bộ lọc
              </Button>
            )
          }
        />
      ) : (
        <>
          <ul
            className={cn(
              'grid grid-cols-1 gap-4',
              view === 'split' ? 'sm:grid-cols-2' : 'sm:grid-cols-2 xl:grid-cols-3',
            )}
          >
            {state.items.map((listing, index) => (
              <li key={listing.id} className="min-w-0">
                <ListingCard
                  listing={listing}
                  priority={index < 2}
                  highlighted={showMap && (hoveredId === listing.id || selectedPoint?.id === listing.id)}
                  onHoverChange={showMap ? setHoveredId : undefined}
                  linkState={{ fromSearch: `${location.pathname}${location.search}` }}
                />
              </li>
            ))}
          </ul>
          <LoadMore
            loadedCount={state.items.length}
            hasNext={state.hasNext}
            loading={state.loadingMore}
            onLoadMore={loadMore}
            total={state.total}
            noun="tin"
            error={state.loadMoreError}
          />
        </>
      )}
    </section>
  );

  const map = showMap && (
    <Suspense
      fallback={
        <div
          className="grid h-full min-h-80 place-items-center bg-surface-container text-body-sm text-on-surface-variant"
          role="status"
        >
          Đang tải bản đồ…
        </div>
      }
    >
      <SearchMap
        filters={filters}
        selectedId={hoveredId ?? selectedPoint?.id ?? null}
        onViewportChange={(bbox) => {
          const next = { ...filters, bbox };
          delete next.place;
          apply(next, 'replace');
        }}
        onSelectPoint={selectPoint}
      />
    </Suspense>
  );

  const setView = (next: SearchView) => apply({ ...filters, view: next }, 'replace');

  return (
    <div
      className="mx-auto flex max-w-[1440px] flex-col gap-4 px-4 py-4 md:px-6"
      data-ready={state.status === 'loading' ? 'false' : 'true'}
    >
      <header className="flex flex-col gap-3">
        <h1 className="sr-only">Tìm kiếm nhà đất</h1>
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start">
          <ChipGroup label="Nhu cầu" className="shrink-0 flex-nowrap">
            {(['SALE', 'RENT'] as const).map((purpose) => (
              <Chip
                key={purpose}
                selected={filters.purpose === purpose}
                onClick={() => apply(withPurpose(filters, purpose))}
              >
                {purpose === 'SALE' ? 'Mua' : 'Thuê'}
              </Chip>
            ))}
          </ChipGroup>
          <SearchBox
            keyword={filters.q ?? ''}
            place={filters.place}
            onKeyword={(q) => {
              const next = { ...filters };
              if (q) next.q = q;
              else {
                delete next.q;
                if (next.sort === 'RELEVANCE') delete next.sort;
              }
              apply(next);
            }}
            onPlace={({ label, bbox }) => apply({ ...filters, bbox, place: label })}
            onClearPlace={() => apply(withoutParams(filters, ['bbox']))}
          />
        </div>
        <div className="hidden lg:block">
          <PriceTypeChips filters={filters} onChange={(next) => apply(next)} size="sm" />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            onClick={openFilters}
            leftIcon={<SlidersHorizontal className="h-4 w-4" />}
            aria-haspopup="dialog"
          >
            Bộ lọc{activeFilterCount(filters) ? ` (${activeFilterCount(filters)})` : ''}
          </Button>
          <div className="flex items-center gap-2 text-body-sm text-on-surface-variant">
            <span className="sr-only sm:not-sr-only" aria-hidden="true">
              Sắp xếp
            </span>
            <Select
              aria-label="Sắp xếp kết quả"
              value={sort}
              onChange={(event) => {
                const next = { ...filters, sort: event.target.value as SearchSort };
                apply(next);
              }}
              options={(Object.keys(SORT_LABELS) as SearchSort[])
                .filter((value) => value !== 'RELEVANCE' || filters.q)
                .map((value) => ({ value, label: SORT_LABELS[value] }))}
            />
          </div>
          <div role="group" aria-label="Chế độ hiển thị kết quả" className="ml-auto flex gap-1">
            <Chip selected={view === 'list'} onClick={() => setView('list')} icon={LayoutList}>
              Danh sách
            </Chip>
            <Chip
              selected={view === 'split'}
              onClick={() => setView('split')}
              icon={Columns2}
              className="hidden lg:inline-flex"
            >
              Chia đôi
            </Chip>
            <Chip selected={view === 'map'} onClick={() => setView('map')} icon={MapIcon}>
              Bản đồ
            </Chip>
          </div>
        </div>
        {urlErrors.length > 0 && (
          <InlineFeedback kind="warning" title="Một số điều kiện trong đường dẫn không hợp lệ nên đã được bỏ qua">
            {urlErrors.map((error) => error.message).join(' ')}
          </InlineFeedback>
        )}
      </header>

      {view === 'map' ? (
        <div className="flex flex-col gap-4">
          <div className="relative h-[70dvh] overflow-hidden rounded-card border border-outline-variant">{map}</div>
          <div className="lg:hidden">
            <Button variant="outline" onClick={() => setView('list')} leftIcon={<LayoutList className="h-4 w-4" />}>
              Xem danh sách ({state.items.length})
            </Button>
          </div>
          <div className="hidden lg:block">{results}</div>
        </div>
      ) : view === 'split' ? (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,11fr)_minmax(0,9fr)]">
          <div className="lg:order-1">{results}</div>
          <div className="order-first lg:order-2">
            <div className="relative h-[50dvh] overflow-hidden rounded-card border border-outline-variant lg:sticky lg:top-20 lg:h-[calc(100dvh-7rem)]">
              {map}
            </div>
          </div>
        </div>
      ) : (
        results
      )}

      <Sheet
        open={filtersOpen}
        onClose={() => setFiltersOpen(false)}
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
                setFiltersOpen(false);
                apply(draft);
              }}
            >
              Xem kết quả
            </Button>
          </div>
        }
      >
        <Suspense fallback={<Skeleton className="h-96 rounded-card" />}>
          <FilterFields key={draft.purpose} draft={draft} onChange={setDraft} />
        </Suspense>
      </Sheet>

      <Sheet
        open={Boolean(selectedPoint) && view === 'map'}
        onClose={() => setSelectedPoint(null)}
        title="Tin trên bản đồ"
      >
        {pointListing ? (
          <ListingCard listing={pointListing} headingLevel="h2" />
        ) : (
          <Skeleton className="h-72 rounded-card" />
        )}
        <Button
          variant="ghost"
          className="mt-3 w-full"
          onClick={() => selectedPoint && navigate(`/listings/${selectedPoint.slug}`)}
        >
          Mở trang chi tiết
        </Button>
      </Sheet>
    </div>
  );
}
