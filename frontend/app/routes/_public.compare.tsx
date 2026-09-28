import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Building2, Check, MapPin, Plus, Scale, Search, Trophy, X } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import { listingPath } from '@/entities/listing/model/seo';
import {
  FURNISHING_LABELS,
  formatArea,
  formatDate,
  propertyTypeLabel,
  type ListingDetailV2,
  type ListingSummaryV2,
} from '@/entities/listing/model/v2';
import {
  MAX_COMPARE,
  compareItemFromSummary,
  compareStore,
  useCompareItems,
  type CompareItem,
} from '@/features/compare/compareStore';
import { DEFAULT_FILTERS, toApiParams } from '@/features/search/filterSchema';
import { track } from '@/shared/analytics/track';
import { formatMoney, formatRentTerms, formatUnitPrice } from '@/shared/format/money';
import { ApiProblemException } from '@/shared/types/problem-details';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { trustLabel } from '@/shared/ui/TrustBadge';
import { useModal } from '@/shared/ui/useModal';

type Detail = ListingDetailV2;
type Slot =
  | { id: string; kind: 'loading' }
  | { id: string; kind: 'ready'; item: Detail }
  | { id: string; kind: 'gone'; title?: string }
  | { id: string; kind: 'error' };

type Row = {
  label: string;
  hint?: string;
  display: (item: Detail) => string;
  metric?: (item: Detail) => number | null | undefined;
  better?: 'low' | 'high';
  purpose?: 'SALE' | 'RENT';
};

const withUnit = (value: number | null | undefined, unit: string) => (value != null ? `${value} ${unit}` : '—');

/** Every price keeps its period ("/tháng" for rent, F04.3); rows that only make sense for one purpose say so. */
const ROWS: Row[] = [
  {
    label: 'Giá',
    display: (item) => formatMoney(item.price) || '—',
    metric: (item) => item.price.amount,
    better: 'low',
  },
  {
    label: 'Đơn giá / m²',
    hint: 'Giá bán chia diện tích',
    display: (item) => formatUnitPrice(item.unitPrice) || '—',
    metric: (item) => item.unitPrice?.amount,
    better: 'low',
    purpose: 'SALE',
  },
  {
    label: 'Phí dịch vụ',
    display: (item) => formatRentTerms(item.rentTerms).monthlyServiceFee ?? '—',
    metric: (item) => item.rentTerms?.monthlyServiceFee,
    better: 'low',
    purpose: 'RENT',
  },
  { label: 'Đặt cọc', display: (item) => formatRentTerms(item.rentTerms).deposit ?? '—', purpose: 'RENT' },
  { label: 'Diện tích', display: (item) => formatArea(item.areaM2), metric: (item) => item.areaM2, better: 'high' },
  { label: 'Loại hình', display: (item) => propertyTypeLabel(item.propertyType) },
  { label: 'Khu vực', display: (item) => item.location.districtName || item.location.addressSummary || '—' },
  {
    label: 'Phòng ngủ',
    display: (item) => withUnit(item.facts.bedrooms, 'phòng'),
    metric: (item) => item.facts.bedrooms,
    better: 'high',
  },
  {
    label: 'Phòng tắm / WC',
    display: (item) => withUnit(item.facts.bathrooms, 'phòng'),
    metric: (item) => item.facts.bathrooms,
    better: 'high',
  },
  { label: 'Số tầng', display: (item) => withUnit(item.facts.floors, 'tầng') },
  {
    label: 'Mặt tiền',
    display: (item) => withUnit(item.facts.frontageM, 'm'),
    metric: (item) => item.facts.frontageM,
    better: 'high',
  },
  {
    label: 'Đường vào',
    display: (item) => withUnit(item.facts.roadWidthM, 'm'),
    metric: (item) => item.facts.roadWidthM,
    better: 'high',
  },
  { label: 'Hướng nhà', display: (item) => item.facts.direction || '—' },
  { label: 'Pháp lý', display: (item) => item.legal?.label || item.facts.legalStatusText || '—' },
  { label: 'Nội thất', display: (item) => (item.furnishing ? FURNISHING_LABELS[item.furnishing] : '—') },
  { label: 'Danh tính người đăng', display: (item) => trustLabel('identity', item.trust.identity.status) },
  { label: 'Giấy tờ chủ sở hữu', display: (item) => trustLabel('ownership', item.trust.ownership.status) },
  { label: 'Cập nhật', display: (item) => formatDate(item.freshness.updatedAt) || '—' },
];

function bestIds(row: Row, items: Detail[]): Set<string> {
  if (!row.metric || !row.better || items.length < 2) return new Set();
  const values = items
    .map((item) => ({ id: item.id, value: row.metric!(item) }))
    .filter((entry): entry is { id: string; value: number } => entry.value != null && Number.isFinite(entry.value));
  if (values.length < 2) return new Set();
  const target =
    row.better === 'low' ? Math.min(...values.map((e) => e.value)) : Math.max(...values.map((e) => e.value));
  if (values.every((entry) => entry.value === target)) return new Set();
  return new Set(values.filter((entry) => entry.value === target).map((entry) => entry.id));
}

const rowDiffers = (row: Row, items: Detail[]) =>
  items.length > 1 && items.some((item) => row.display(item) !== row.display(items[0]));

function ListingPicker({
  purpose,
  excludeIds,
  onPick,
  onClose,
}: {
  purpose?: 'SALE' | 'RENT';
  excludeIds: string[];
  onPick: (listing: ListingSummaryV2) => void;
  onClose: () => void;
}) {
  const [activePurpose, setActivePurpose] = useState<'SALE' | 'RENT'>(purpose ?? 'SALE');
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ListingSummaryV2[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const panelRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  useModal({ open: true, onClose, panelRef, initialFocusRef: searchRef });

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(keyword.trim()), 350);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setError('');
    listingV2Api
      .search(
        toApiParams(
          { ...DEFAULT_FILTERS, purpose: activePurpose, q: query || undefined, sort: 'NEWEST' },
          { size: 30 },
        ),
        abort.signal,
      )
      .then((data) => setResults(data.items))
      .catch(() => !abort.signal.aborted && setError('Không tải được danh sách tin. Vui lòng thử lại.'))
      .finally(() => !abort.signal.aborted && setLoading(false));
    return () => abort.abort();
  }, [activePurpose, query]);

  return (
    <div
      className="fixed inset-0 z-overlay flex items-end justify-center bg-inverse-surface/60 sm:items-center sm:p-4"
      role="presentation"
    >
      <div
        ref={panelRef}
        tabIndex={-1}
        className="flex max-h-[88vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-panel bg-surface-container-lowest shadow-elevated sm:rounded-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="picker-title"
      >
        <div className="flex items-start justify-between gap-3 border-b border-outline-variant p-5">
          <div>
            <h2 id="picker-title" className="text-headline-sm text-on-surface">
              Chọn tin để so sánh
            </h2>
            <p className="mt-0.5 text-body-sm text-on-surface-variant">
              {purpose
                ? `Chỉ tin ${purpose === 'SALE' ? 'đang bán' : 'cho thuê'}: không so giá thuê với giá bán.`
                : 'Chọn nhu cầu rồi chọn tin.'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="grid h-11 w-11 shrink-0 place-items-center rounded-pill hover:bg-surface-container"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
        <div className="space-y-3 border-b border-outline-variant p-5">
          {!purpose && (
            <div role="group" aria-label="Nhu cầu" className="flex gap-2">
              {(['SALE', 'RENT'] as const).map((value) => (
                <Button
                  key={value}
                  size="sm"
                  variant={activePurpose === value ? 'primary' : 'outline'}
                  aria-pressed={activePurpose === value}
                  onClick={() => setActivePurpose(value)}
                >
                  {value === 'SALE' ? 'Cần bán' : 'Cho thuê'}
                </Button>
              ))}
            </div>
          )}
          <label className="flex min-h-11 items-center gap-2 rounded-input border border-outline px-3">
            <Search className="h-4 w-4 text-on-surface-variant" aria-hidden="true" />
            <input
              ref={searchRef}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Lọc theo tiêu đề, khu vực, dự án…"
              aria-label="Lọc tin đăng"
              className="w-full bg-transparent text-body-sm focus:outline-none"
            />
          </label>
        </div>
        <div className="flex-1 overflow-y-auto p-3">
          {loading && <p className="p-6 text-center text-body-sm text-on-surface-variant">Đang tải tin đăng…</p>}
          {!loading && error && (
            <p role="alert" className="p-6 text-center text-body-sm text-error">
              {error}
            </p>
          )}
          {!loading && !error && results.length === 0 && (
            <p className="p-6 text-center text-body-sm text-on-surface-variant">Không có tin phù hợp.</p>
          )}
          {!loading && !error && (
            <ul className="space-y-1.5">
              {results.map((listing) => {
                const added = excludeIds.includes(listing.id);
                return (
                  <li key={listing.id}>
                    <button
                      type="button"
                      disabled={added}
                      onClick={() => onPick(listing)}
                      className="flex w-full items-center gap-3 rounded-card p-2 text-left hover:bg-surface-container disabled:opacity-60"
                    >
                      <div className="h-14 w-20 shrink-0 overflow-hidden rounded-input bg-surface-container">
                        {listing.image ? (
                          <img src={listing.image.url} alt="" loading="lazy" className="h-full w-full object-cover" />
                        ) : (
                          <Building2 className="m-auto mt-4 h-6 w-6 text-outline" aria-hidden="true" />
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="line-clamp-1 text-body-sm font-bold text-on-surface">{listing.title}</p>
                        <p className="mt-0.5 text-label font-normal text-on-surface-variant">
                          {propertyTypeLabel(listing.propertyType)} · {formatArea(listing.areaM2)} ·{' '}
                          {listing.location.addressSummary}
                        </p>
                        <p className="mt-0.5 text-body-sm font-bold text-primary">{formatMoney(listing.price)}</p>
                      </div>
                      <span className="inline-flex min-h-9 shrink-0 items-center gap-1 rounded-input px-3 text-label font-bold text-primary">
                        {added ? (
                          <>
                            <Check className="h-4 w-4" aria-hidden="true" /> Đã chọn
                          </>
                        ) : (
                          <>
                            <Plus className="h-4 w-4" aria-hidden="true" /> Chọn
                          </>
                        )}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Compare up to three listings of one purpose (UI-05) on the current public v2 detail: a listing that is no longer
 * public becomes an inactive column, a failed refresh can be retried, "differences only" hides equal rows.
 */
export const PropertyComparePage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const stored = useCompareItems();
  const [slots, setSlots] = useState<Record<string, Slot>>({});
  const [diffOnly, setDiffOnly] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [reload, setReload] = useState(0);

  const urlIds = useMemo(
    () =>
      (searchParams.get('ids') ?? '')
        .split(',')
        .map((id) => id.trim())
        .filter(Boolean)
        .slice(0, MAX_COMPARE),
    [searchParams],
  );
  const selectedIds = urlIds.length ? urlIds : stored.map((item) => item.id);
  const selectedKey = selectedIds.join(',');

  useEffect(() => {
    if (selectedKey !== (searchParams.get('ids') ?? '')) {
      setSearchParams(selectedKey ? { ids: selectedKey } : {}, { replace: true });
    }
  }, [selectedKey, searchParams, setSearchParams]);

  useEffect(() => {
    const missing = selectedIds.filter((id) => !slots[id] || slots[id].kind === 'error');
    if (!missing.length) return;
    let active = true;
    setSlots((current) => ({
      ...current,
      ...Object.fromEntries(missing.map((id) => [id, { id, kind: 'loading' } as Slot])),
    }));
    Promise.allSettled(missing.map((id) => listingV2Api.detail(id))).then((results) => {
      if (!active) return;
      const next: Record<string, Slot> = {};
      results.forEach((result, index) => {
        const id = missing[index];
        if (result.status === 'fulfilled') next[id] = { id, kind: 'ready', item: result.value };
        else {
          const problem = result.reason instanceof ApiProblemException ? result.reason.problem : null;
          if (problem?.status === 410 || problem?.status === 404) {
            next[id] = { id, kind: 'gone', title: (problem as unknown as { title?: string }).title };
          } else next[id] = { id, kind: 'error' };
        }
      });
      setSlots((current) => ({ ...current, ...next }));
      const ready = Object.values(next)
        .filter((slot) => slot.kind === 'ready')
        .map((slot) => slot.id);
      if (ready.length) track('compare_opened', { listingIds: ready });
    });
    return () => {
      active = false;
    };
    // keyed on the selection and explicit retries; slots are what this effect fills in
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey, reload]);

  const current = selectedIds.map((id) => slots[id] ?? ({ id, kind: 'loading' } as Slot));
  const ready = current.flatMap((slot) => (slot.kind === 'ready' ? [slot.item] : []));
  const loading = current.some((slot) => slot.kind === 'loading');
  const purpose = ready[0]?.purpose ?? stored[0]?.purpose;
  const failed = current.filter((slot) => slot.kind === 'error');

  const setSelection = (items: CompareItem[]) => {
    compareStore.replaceAll(items);
    setSearchParams(items.length ? { ids: items.map((item) => item.id).join(',') } : {}, { replace: true });
  };
  const currentItems = () =>
    selectedIds
      .map((id) => {
        const slot = slots[id];
        return slot?.kind === 'ready'
          ? compareItemFromSummary(slot.item)
          : (stored.find((item) => item.id === id) ?? {
              id,
              slug: id,
              title: 'Tin đã chọn',
              purpose: purpose ?? 'SALE',
            });
      })
      .filter(Boolean) as CompareItem[];
  const removeListing = (id: string) => setSelection(currentItems().filter((item) => item.id !== id));
  const addListing = (listing: ListingSummaryV2) => {
    const next = [...currentItems(), compareItemFromSummary(listing)];
    setSelection(next);
    if (next.length >= MAX_COMPARE) setPickerOpen(false);
  };

  const rows = ROWS.filter((row) => (!row.purpose || row.purpose === purpose) && (!diffOnly || rowDiffers(row, ready)));
  const emptySlots = Math.max(0, MAX_COMPARE - selectedIds.length);
  const columns = `minmax(150px,200px) repeat(${MAX_COMPARE}, minmax(220px, 1fr))`;

  return (
    <div className="min-h-screen bg-surface py-8" data-ready={loading ? 'false' : 'true'}>
      <div className="mx-auto max-w-[1360px] px-4">
        <Link
          to="/search"
          className="mb-4 inline-flex min-h-11 items-center gap-2 text-body-sm font-medium text-on-surface-variant hover:text-on-surface"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Quay lại tìm kiếm
        </Link>
        <section className="mb-6 flex flex-col justify-between gap-4 rounded-card border border-outline-variant bg-surface-container-lowest p-6 md:flex-row md:items-start">
          <div className="max-w-3xl">
            <h1 className="flex items-center gap-2 text-headline-md text-on-surface">
              <Scale className="h-7 w-7 text-primary" aria-hidden="true" /> So sánh tin đăng
            </h1>
            <p className="mt-2 text-body-sm text-on-surface-variant">
              Tối đa {MAX_COMPARE} tin cùng nhu cầu (cùng bán hoặc cùng cho thuê). “Tốt nhất” chỉ phản ánh con số ở hàng
              đó.
            </p>
          </div>
          {selectedIds.length > 0 && (
            <div className="flex flex-wrap items-center gap-3">
              <Checkbox
                label="Chỉ xem điểm khác biệt"
                checked={diffOnly}
                onChange={(event) => setDiffOnly(event.target.checked)}
              />
              <Button variant="outline" size="sm" onClick={() => setSelection([])} leftIcon={<X className="h-4 w-4" />}>
                Xóa tất cả
              </Button>
            </div>
          )}
        </section>

        {selectedIds.length === 0 && (
          <section className="rounded-card border border-outline-variant bg-surface-container-lowest p-8 text-center">
            <h2 className="text-headline-sm text-on-surface">Bạn chưa chọn tin nào để so sánh</h2>
            <p className="mt-2 text-body-sm text-on-surface-variant">
              Bấm “So sánh” trên 2–3 tin cùng nhu cầu ở trang tìm kiếm, hoặc chọn ngay tại đây.
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Button onClick={() => setPickerOpen(true)} leftIcon={<Plus className="h-4 w-4" />}>
                Chọn tin ngay tại đây
              </Button>
              <ButtonLink to="/search" variant="outline" leftIcon={<Search className="h-4 w-4" />}>
                Mở trang tìm kiếm
              </ButtonLink>
            </div>
          </section>
        )}

        {failed.length > 0 && (
          <InlineFeedback
            kind="error"
            title={`Không làm mới được ${failed.length} tin (lỗi kết nối).`}
            action={{ label: 'Thử lại', onClick: () => setReload((value) => value + 1) }}
            className="mb-4"
          />
        )}

        {selectedIds.length > 0 && (
          <section className="overflow-x-auto rounded-card border border-outline-variant bg-surface-container-lowest">
            <div className="min-w-[860px]">
              <div
                className="grid border-b border-outline-variant bg-surface-container"
                style={{ gridTemplateColumns: columns }}
              >
                <div className="sticky left-0 z-10 bg-surface-container p-5">
                  <p className="text-label text-on-surface-variant">Tiêu chí</p>
                  <p className="mt-1 font-bold text-on-surface">
                    {ready.length} tin · {purpose === 'RENT' ? 'Cho thuê (giá theo tháng)' : 'Bán'}
                  </p>
                  {ready.length < 2 && !loading && (
                    <p className="mt-2 text-label text-warning">Thêm ít nhất 1 tin nữa để so sánh.</p>
                  )}
                </div>
                {current.map((slot) => {
                  if (slot.kind === 'ready') {
                    const item = slot.item;
                    return (
                      <article
                        key={slot.id}
                        className="relative border-l border-outline-variant bg-surface-container-lowest p-5"
                      >
                        <button
                          type="button"
                          onClick={() => removeListing(slot.id)}
                          aria-label={`Bỏ ${item.title} khỏi so sánh`}
                          className="absolute right-7 top-7 z-10 grid h-11 w-11 place-items-center rounded-pill bg-surface-container-lowest/90 shadow-card hover:text-error"
                        >
                          <X className="h-4 w-4" aria-hidden="true" />
                        </button>
                        <div className="mb-3 aspect-video overflow-hidden rounded-card bg-surface-container">
                          {item.images[0] ? (
                            <img
                              src={item.images[0].url}
                              alt={`Ảnh đại diện: ${item.title}`}
                              className="h-full w-full object-cover"
                            />
                          ) : (
                            <div className="flex h-full items-center justify-center">
                              <Building2 className="h-10 w-10 text-outline" aria-hidden="true" />
                            </div>
                          )}
                        </div>
                        <p className="text-headline-sm text-primary">{formatMoney(item.price)}</p>
                        <Link
                          to={listingPath(item)}
                          className="mt-1 line-clamp-2 block text-body-sm font-bold text-on-surface hover:text-primary"
                        >
                          {item.title}
                        </Link>
                        <p className="mt-2 flex items-start gap-1 text-label font-normal text-on-surface-variant">
                          <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />{' '}
                          {item.location.addressSummary}
                        </p>
                      </article>
                    );
                  }
                  if (slot.kind === 'gone') {
                    return (
                      <article
                        key={slot.id}
                        data-inactive="true"
                        className="border-l border-outline-variant bg-surface-container p-5 text-on-surface-variant"
                      >
                        <p className="font-bold text-on-surface">Tin không còn hiển thị</p>
                        {slot.title && <p className="mt-1 line-clamp-2 text-body-sm">{slot.title}</p>}
                        <p className="mt-1 text-label font-normal">
                          Tin đã được ẩn, hết hạn hoặc bị gỡ nên không được đưa vào bảng.
                        </p>
                        <Button variant="outline" size="sm" className="mt-3" onClick={() => removeListing(slot.id)}>
                          Bỏ khỏi so sánh
                        </Button>
                      </article>
                    );
                  }
                  return (
                    <div
                      key={slot.id}
                      className="border-l border-outline-variant p-5"
                      role={slot.kind === 'loading' ? 'status' : undefined}
                    >
                      {slot.kind === 'loading' ? (
                        <div
                          className="aspect-video animate-pulse rounded-card bg-surface-container"
                          aria-label="Đang tải"
                        />
                      ) : (
                        <p className="text-body-sm text-error">Không tải được tin này.</p>
                      )}
                    </div>
                  );
                })}
                {Array.from({ length: emptySlots }, (_, index) => (
                  <div key={`slot-${index}`} className="border-l border-outline-variant p-5">
                    <button
                      type="button"
                      onClick={() => setPickerOpen(true)}
                      className="flex h-full min-h-[220px] w-full flex-col items-center justify-center gap-2 rounded-card border-2 border-dashed border-outline text-body-sm font-semibold text-on-surface-variant hover:border-primary hover:text-primary"
                    >
                      <Plus className="h-7 w-7" aria-hidden="true" />
                      Thêm tin để so sánh
                    </button>
                  </div>
                ))}
              </div>
              <div className="divide-y divide-outline-variant/60 text-body-sm">
                {rows.length === 0 && (
                  <p className="p-6 text-center text-on-surface-variant">Các tin đang giống nhau ở mọi tiêu chí.</p>
                )}
                {rows.map((row) => {
                  const winners = bestIds(row, ready);
                  return (
                    <div key={row.label} className="grid" style={{ gridTemplateColumns: columns }}>
                      <div className="sticky left-0 z-10 bg-surface-container p-4">
                        <p className="font-semibold text-on-surface">{row.label}</p>
                        {row.hint && (
                          <p className="mt-0.5 text-label font-normal text-on-surface-variant">{row.hint}</p>
                        )}
                      </div>
                      {current.map((slot) => (
                        <div
                          key={slot.id}
                          className={`flex items-center gap-2 border-l border-outline-variant/60 p-4 ${slot.kind === 'ready' && winners.has(slot.id) ? 'bg-success-container font-semibold text-success-on-container' : 'text-on-surface'}`}
                        >
                          {slot.kind === 'ready' ? (
                            <>
                              <span>{row.display(slot.item)}</span>
                              {winners.has(slot.id) && (
                                <span className="inline-flex items-center gap-1 text-label">
                                  <Trophy className="h-3 w-3" aria-hidden="true" /> Tốt nhất
                                </span>
                              )}
                            </>
                          ) : (
                            <span className="text-on-surface-variant">—</span>
                          )}
                        </div>
                      ))}
                      {Array.from({ length: emptySlots }, (_, index) => (
                        <div key={index} className="border-l border-outline-variant/60" />
                      ))}
                    </div>
                  );
                })}
              </div>
            </div>
          </section>
        )}
      </div>
      {pickerOpen && (
        <ListingPicker
          purpose={purpose}
          excludeIds={selectedIds}
          onPick={addListing}
          onClose={() => setPickerOpen(false)}
        />
      )}
    </div>
  );
};

export default PropertyComparePage;
