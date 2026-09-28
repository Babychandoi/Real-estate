import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Building2, Check, MapPin, Plus, Scale, Search, ShieldCheck, Trophy, X } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingApi } from '@/entities/listing/api/listingApi';
import {
  calculateUnitPrice,
  formatPriceVnd,
  formatPropertyType,
  type Listing,
  type ListingDetail,
} from '@/entities/listing/model/types';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { listingPath } from '@/entities/listing/model/seo';
import { useModal } from '@/shared/ui/useModal';
import { MAX_COMPARE, compareStore, useCompareItems, type CompareItem } from '@/features/compare/compareStore';

type Detail = ListingDetail & { publishedAt?: string };
type Row = {
  label: string;
  hint?: string;
  display: (item: Detail) => string;
  /** Numeric value used to pick the best cell; `better` says whether lower or higher wins. */
  metric?: (item: Detail) => number | null | undefined;
  better?: 'low' | 'high';
};

const unitPrice = (item: Detail) => (item.areaM2 > 0 ? item.priceVnd / item.areaM2 : null);
const withUnit = (value: number | null | undefined, unit: string) => (value != null ? `${value} ${unit}` : '—');

const ROWS: Row[] = [
  { label: 'Giá', display: (item) => formatPriceVnd(item.priceVnd), metric: (item) => item.priceVnd, better: 'low' },
  { label: 'Diện tích', display: (item) => `${item.areaM2} m²`, metric: (item) => item.areaM2, better: 'high' },
  {
    label: 'Đơn giá / m²',
    hint: 'Giá chia diện tích — dùng để so sánh giá trị thực giữa các tin',
    display: (item) => calculateUnitPrice(item.priceVnd, item.areaM2) || '—',
    metric: unitPrice,
    better: 'low',
  },
  { label: 'Loại hình', display: (item) => formatPropertyType(item.propertyType) },
  { label: 'Khu vực', display: (item) => item.addressSummary },
  {
    label: 'Phòng ngủ',
    display: (item) => withUnit(item.bedrooms, 'phòng'),
    metric: (item) => item.bedrooms,
    better: 'high',
  },
  {
    label: 'Phòng tắm / WC',
    display: (item) => withUnit(item.bathrooms, 'phòng'),
    metric: (item) => item.bathrooms,
    better: 'high',
  },
  { label: 'Số tầng', display: (item) => withUnit(item.floors, 'tầng') },
  {
    label: 'Mặt tiền',
    display: (item) => withUnit(item.frontageM, 'm'),
    metric: (item) => item.frontageM,
    better: 'high',
  },
  {
    label: 'Đường vào',
    display: (item) => withUnit(item.roadWidthM, 'm'),
    metric: (item) => item.roadWidthM,
    better: 'high',
  },
  { label: 'Hướng nhà', display: (item) => item.direction || '—' },
  { label: 'Pháp lý', display: (item) => item.legalStatus || '—' },
  { label: 'Người đăng', display: (item) => (item.isVerified ? 'Đã xác thực eKYC' : 'Chưa xác thực') },
];

function bestIds(row: Row, items: Detail[]): Set<string> {
  if (!row.metric || !row.better || items.length < 2) return new Set();
  const values = items
    .map((item) => ({ id: item.id, value: row.metric!(item) }))
    .filter((entry): entry is { id: string; value: number } => entry.value != null && Number.isFinite(entry.value));
  if (values.length < 2) return new Set();
  const target =
    row.better === 'low'
      ? Math.min(...values.map((entry) => entry.value))
      : Math.max(...values.map((entry) => entry.value));
  if (values.every((entry) => entry.value === target)) return new Set();
  return new Set(values.filter((entry) => entry.value === target).map((entry) => entry.id));
}

const rowDiffers = (row: Row, items: Detail[]) =>
  items.length > 1 && items.some((item) => row.display(item) !== row.display(items[0]));

const toCompareItem = (item: Listing | Detail): CompareItem => ({
  id: item.id,
  slug: item.slug,
  title: item.title,
  purpose: item.purpose,
  priceVnd: item.priceVnd,
  areaM2: item.areaM2,
  addressSummary: item.addressSummary,
  primaryImageUrl: 'primaryImageUrl' in item ? item.primaryImageUrl : (item.imageUrls?.[0] ?? ''),
});

function ListingPicker({
  purpose,
  excludeIds,
  onPick,
  onClose,
}: {
  purpose?: 'SALE' | 'RENT';
  excludeIds: string[];
  onPick: (listing: Listing) => void;
  onClose: () => void;
}) {
  const [activePurpose, setActivePurpose] = useState<'SALE' | 'RENT'>(purpose ?? 'SALE');
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const panelRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  // Shared modal stack (M2): previously this picker only closed on Escape, had no Tab trap, no scroll lock and
  // no focus return; now it joins the same stack as every other kit modal.
  useModal({ open: true, onClose, panelRef, initialFocusRef: searchRef });

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(keyword.trim()), 350);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    listingApi
      .searchListings({ purpose: activePurpose, keyword: query || undefined, sortBy: 'LATEST', page: 0, size: 30 })
      .then((data) => {
        if (active) setResults(data);
      })
      .catch(() => {
        if (active) setError('Không tải được danh sách tin. Vui lòng thử lại.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [activePurpose, query]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/60 p-0 sm:items-center sm:p-4"
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={panelRef}
        tabIndex={-1}
        className="flex max-h-[88vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:rounded-2xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="picker-title"
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 p-5">
          <div>
            <h2 id="picker-title" className="text-lg font-bold text-slate-900">
              Chọn tin để so sánh
            </h2>
            <p className="mt-0.5 text-sm text-slate-500">
              {purpose
                ? `Chỉ hiển thị tin ${purpose === 'SALE' ? 'đang bán' : 'cho thuê'} để so sánh cùng loại nhu cầu.`
                : 'Chọn nhu cầu, sau đó chọn tin đầu tiên.'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="grid h-10 w-10 shrink-0 place-items-center rounded-full hover:bg-slate-100"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="space-y-3 border-b border-slate-100 p-5">
          {!purpose && (
            <div
              className="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-0.5 text-sm"
              role="group"
              aria-label="Nhu cầu"
            >
              {(['SALE', 'RENT'] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setActivePurpose(value)}
                  aria-pressed={activePurpose === value}
                  className={`min-h-9 rounded-md px-4 font-semibold ${activePurpose === value ? 'bg-primary text-white' : 'text-slate-600'}`}
                >
                  {value === 'SALE' ? 'Cần bán' : 'Cho thuê'}
                </button>
              ))}
            </div>
          )}
          <label className="flex min-h-11 items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 focus-within:ring-2 focus-within:ring-primary/20">
            <Search className="h-4 w-4 text-slate-400" aria-hidden="true" />
            <input
              ref={searchRef}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Lọc theo tiêu đề, khu vực, dự án…"
              aria-label="Lọc tin đăng"
              className="w-full bg-transparent text-sm focus:outline-none"
            />
          </label>
        </div>
        <div className="flex-1 overflow-y-auto p-3">
          {loading && <p className="p-6 text-center text-sm text-slate-500">Đang tải tin đăng…</p>}
          {!loading && error && (
            <p role="alert" className="p-6 text-center text-sm text-rose-700">
              {error}
            </p>
          )}
          {!loading && !error && results.length === 0 && (
            <p className="p-6 text-center text-sm text-slate-500">Không có tin phù hợp.</p>
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
                      className="flex w-full items-center gap-3 rounded-xl p-2 text-left transition hover:bg-slate-50 disabled:cursor-default disabled:opacity-60"
                    >
                      <div className="h-14 w-20 shrink-0 overflow-hidden rounded-lg bg-slate-100">
                        {listing.primaryImageUrl ? (
                          <img
                            src={listing.primaryImageUrl}
                            alt=""
                            loading="lazy"
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          <Building2 className="m-auto mt-4 h-6 w-6 text-slate-400" aria-hidden="true" />
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="line-clamp-1 text-sm font-bold text-slate-900">{listing.title}</p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          {formatPropertyType(listing.propertyType)} · {listing.areaM2} m² · {listing.addressSummary}
                        </p>
                        <p className="mt-0.5 text-sm font-bold text-emerald-800">{formatPriceVnd(listing.priceVnd)}</p>
                      </div>
                      <span
                        className={`inline-flex min-h-9 shrink-0 items-center gap-1 rounded-lg px-3 text-xs font-bold ${added ? 'bg-slate-100 text-slate-500' : 'bg-primary/10 text-primary'}`}
                      >
                        {added ? (
                          <>
                            <Check className="h-4 w-4" /> Đã chọn
                          </>
                        ) : (
                          <>
                            <Plus className="h-4 w-4" /> Chọn
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

export const PropertyComparePage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const stored = useCompareItems();
  const [details, setDetails] = useState<Record<string, Detail>>({});
  const [failedIds, setFailedIds] = useState<string[]>([]);
  const [diffOnly, setDiffOnly] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);

  // A shared link (?ids=) wins over the locally stored selection, then both stay in sync.
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
    const storedIds = stored.map((item) => item.id).join(',');
    if (selectedIds.join(',') !== (searchParams.get('ids') ?? '')) {
      setSearchParams(selectedIds.length ? { ids: selectedIds.join(',') } : {}, { replace: true });
    }
    if (urlIds.length && urlIds.join(',') !== storedIds) {
      const known = urlIds.map(
        (id) => stored.find((item) => item.id === id) ?? (details[id] ? toCompareItem(details[id]) : null),
      );
      if (known.every(Boolean)) compareStore.replaceAll(known as CompareItem[]);
    }
    // Keyed on the joined id list (a new array every render); URL and store are synced from it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey, stored, details]);

  useEffect(() => {
    const missing = selectedIds.filter((id) => !details[id] && !failedIds.includes(id));
    if (!missing.length) return;
    let active = true;
    Promise.allSettled(missing.map((id) => listingApi.getListingDetail(id) as Promise<Detail>)).then((results) => {
      if (!active) return;
      const loaded: Record<string, Detail> = {};
      const failed: string[] = [];
      results.forEach((result, index) => {
        if (result.status === 'fulfilled' && result.value?.id) loaded[missing[index]] = result.value;
        else failed.push(missing[index]);
      });
      setDetails((current) => ({ ...current, ...loaded }));
      if (failed.length) setFailedIds((current) => [...current, ...failed]);
    });
    return () => {
      active = false;
    };
    // Fetch only when the selection changes; details/failedIds are what this effect fills in.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey]);

  const selected = selectedIds.map((id) => details[id]).filter((item): item is Detail => Boolean(item));
  const loading = selectedIds.some((id) => !details[id] && !failedIds.includes(id));
  const purpose = selected[0]?.purpose ?? stored[0]?.purpose;
  const unavailable = selectedIds.filter((id) => failedIds.includes(id));

  const setSelection = (items: CompareItem[]) => {
    compareStore.replaceAll(items);
    setSearchParams(items.length ? { ids: items.map((item) => item.id).join(',') } : {}, { replace: true });
  };
  const currentItems = () =>
    selectedIds
      .map((id) => (details[id] ? toCompareItem(details[id]) : stored.find((item) => item.id === id)))
      .filter((item): item is CompareItem => Boolean(item));

  const removeListing = (id: string) => setSelection(currentItems().filter((item) => item.id !== id));
  const addListing = (listing: Listing) => {
    const next = [...currentItems(), toCompareItem(listing)];
    setSelection(next);
    if (next.length >= MAX_COMPARE) setPickerOpen(false);
  };

  const rows = ROWS.filter((row) => !diffOnly || rowDiffers(row, selected));
  const emptySlots = Math.max(0, MAX_COMPARE - selectedIds.length + unavailable.length);
  const columns = `minmax(150px,200px) repeat(${MAX_COMPARE}, minmax(220px, 1fr))`;

  return (
    <div className="min-h-screen bg-slate-50 py-8" data-ready={loading ? 'false' : 'true'}>
      <div className="container mx-auto max-w-[1360px] px-4">
        <Link
          to="/search"
          className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900"
        >
          <ArrowLeft className="h-4 w-4" /> Quay lại tìm kiếm
        </Link>

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start">
            <div className="max-w-3xl">
              <h1 className="flex items-center gap-2 text-2xl font-bold tracking-tight text-slate-900 md:text-3xl">
                <Scale className="h-7 w-7 text-primary" aria-hidden="true" /> So sánh tin đăng
              </h1>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">
                Đặt tối đa {MAX_COMPARE} tin <strong>cùng nhu cầu</strong> (cùng bán hoặc cùng cho thuê) cạnh nhau để so
                giá, diện tích, <strong>đơn giá/m²</strong>, số phòng, mặt tiền, pháp lý. Ô có nhãn{' '}
                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-bold text-emerald-800">
                  <Trophy className="h-3 w-3" />
                  Tốt nhất
                </span>{' '}
                là giá trị có lợi nhất cho người mua/thuê ở hàng đó.
              </p>
              <p className="mt-2 text-xs text-slate-500">
                Thêm tin bằng nút <strong>“+ So sánh”</strong> trên thẻ tin ở trang tìm kiếm, trang chi tiết, hoặc chọn
                ngay tại đây.
              </p>
            </div>
            {selectedIds.length > 0 && (
              <div className="flex flex-wrap items-center gap-2">
                <label className="flex min-h-11 cursor-pointer items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs font-semibold text-slate-700">
                  <input type="checkbox" checked={diffOnly} onChange={(event) => setDiffOnly(event.target.checked)} />
                  Chỉ xem điểm khác biệt
                </label>
                <Button variant="outline" size="sm" className="min-h-11" onClick={() => setSelection([])}>
                  <X className="h-4 w-4" /> Xóa tất cả
                </Button>
              </div>
            )}
          </div>
        </section>

        {selectedIds.length === 0 && (
          <section className="rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm md:p-12">
            <Scale className="mx-auto mb-3 h-12 w-12 text-primary/60" aria-hidden="true" />
            <h2 className="text-xl font-bold text-slate-900">Bạn chưa chọn tin nào để so sánh</h2>
            <ol className="mx-auto mt-5 grid max-w-3xl gap-3 text-left text-sm text-slate-600 md:grid-cols-3">
              {[
                'Tìm tin phù hợp ở trang Tìm kiếm & Bản đồ.',
                'Bấm “+ So sánh” trên 2–3 tin cùng nhu cầu bán hoặc thuê.',
                'Bấm “So sánh ngay” ở thanh cuối màn hình để xem bảng đối chiếu.',
              ].map((step, index) => (
                <li key={step} className="flex gap-3 rounded-xl bg-slate-50 p-4">
                  <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-primary text-xs font-bold text-white">
                    {index + 1}
                  </span>
                  {step}
                </li>
              ))}
            </ol>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Button onClick={() => setPickerOpen(true)} leftIcon={<Plus className="h-4 w-4" />} className="min-h-11">
                Chọn tin ngay tại đây
              </Button>
              <ButtonLink to="/search" variant="outline" leftIcon={<Search className="h-4 w-4" />} className="min-h-11">
                Mở trang tìm kiếm
              </ButtonLink>
            </div>
          </section>
        )}

        {unavailable.length > 0 && (
          <div
            role="alert"
            className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
          >
            {unavailable.length} tin đã chọn không còn hiển thị công khai nên được bỏ khỏi bảng.
            <button
              type="button"
              className="font-bold underline underline-offset-4"
              onClick={() => setSelection(currentItems().filter((item) => !unavailable.includes(item.id)))}
            >
              Bỏ khỏi danh sách
            </button>
          </div>
        )}

        {selectedIds.length > 0 && (
          <section className="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="min-w-[860px]">
              <div className="grid border-b border-slate-200 bg-slate-50" style={{ gridTemplateColumns: columns }}>
                <div className="sticky left-0 z-10 bg-slate-50 p-5">
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Tiêu chí</p>
                  <p className="mt-1 font-bold text-slate-900">
                    {selected.length} tin · {purpose === 'RENT' ? 'Cho thuê' : 'Bán'}
                  </p>
                  {selected.length < 2 && !loading && (
                    <p className="mt-2 text-xs font-medium text-amber-700">Thêm ít nhất 1 tin nữa để so sánh.</p>
                  )}
                </div>
                {selectedIds
                  .filter((id) => !failedIds.includes(id))
                  .map((id) => {
                    const item = details[id];
                    if (!item)
                      return (
                        <div key={id} className="border-l border-slate-200 bg-white p-5">
                          <div className="aspect-video animate-pulse rounded-xl bg-slate-100" />
                          <div className="mt-3 h-5 w-1/2 animate-pulse rounded bg-slate-100" />
                        </div>
                      );
                    return (
                      <article key={id} className="relative border-l border-slate-200 bg-white p-5">
                        <button
                          type="button"
                          onClick={() => removeListing(id)}
                          aria-label={`Bỏ ${item.title} khỏi so sánh`}
                          className="absolute right-7 top-7 z-10 grid h-9 w-9 place-items-center rounded-full bg-white/90 text-slate-600 shadow hover:text-rose-700"
                        >
                          <X className="h-4 w-4" />
                        </button>
                        <Link
                          to={listingPath(item)}
                          className="mb-3 block aspect-video overflow-hidden rounded-xl bg-slate-100"
                        >
                          {item.imageUrls?.[0] ? (
                            <img src={item.imageUrls[0]} alt={item.title} className="h-full w-full object-cover" />
                          ) : (
                            <div className="flex h-full items-center justify-center">
                              <Building2 className="h-10 w-10 text-slate-400" />
                            </div>
                          )}
                        </Link>
                        {item.isVerified && (
                          <span className="mb-2 inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-1 text-xs font-semibold text-emerald-800">
                            <ShieldCheck className="h-3.5 w-3.5" /> Đã xác thực người đăng
                          </span>
                        )}
                        <p className="text-xl font-black text-emerald-800">{formatPriceVnd(item.priceVnd)}</p>
                        <Link
                          to={listingPath(item)}
                          className="mt-1 line-clamp-2 block text-sm font-bold text-slate-900 hover:text-emerald-700"
                        >
                          {item.title}
                        </Link>
                        <p className="mt-2 flex items-start gap-1 text-xs text-slate-500">
                          <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {item.addressSummary}
                        </p>
                      </article>
                    );
                  })}
                {Array.from({ length: emptySlots }, (_, index) => (
                  <div key={`slot-${index}`} className="border-l border-slate-200 bg-white p-5">
                    <button
                      type="button"
                      onClick={() => setPickerOpen(true)}
                      className="flex h-full min-h-[220px] w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-300 text-sm font-semibold text-slate-500 transition hover:border-primary hover:bg-primary/5 hover:text-primary"
                    >
                      <Plus className="h-7 w-7" aria-hidden="true" />
                      Thêm tin để so sánh
                      <span className="text-xs font-normal">
                        {purpose ? `Tin ${purpose === 'SALE' ? 'bán' : 'cho thuê'}` : 'Bán hoặc cho thuê'}
                      </span>
                    </button>
                  </div>
                ))}
              </div>

              <div className="divide-y divide-slate-100 text-sm">
                {rows.length === 0 && (
                  <p className="p-6 text-center text-slate-500">Các tin đang giống nhau ở mọi tiêu chí.</p>
                )}
                {rows.map((row) => {
                  const winners = bestIds(row, selected);
                  return (
                    <div key={row.label} className="grid" style={{ gridTemplateColumns: columns }}>
                      <div className="sticky left-0 z-10 bg-slate-50 p-4">
                        <p className="font-semibold text-slate-700">{row.label}</p>
                        {row.hint && <p className="mt-0.5 text-xs text-slate-500">{row.hint}</p>}
                      </div>
                      {selected.map((item) => (
                        <div
                          key={item.id}
                          className={`flex items-center gap-2 border-l border-slate-100 p-4 ${winners.has(item.id) ? 'bg-emerald-50/70 font-semibold text-emerald-900' : 'text-slate-800'}`}
                        >
                          <span>{row.display(item)}</span>
                          {winners.has(item.id) && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-bold text-emerald-800">
                              <Trophy className="h-3 w-3" aria-hidden="true" />
                              Tốt nhất
                            </span>
                          )}
                        </div>
                      ))}
                      {Array.from({ length: MAX_COMPARE - selected.length }, (_, index) => (
                        <div key={index} className="border-l border-slate-100" />
                      ))}
                    </div>
                  );
                })}
              </div>
            </div>
          </section>
        )}

        <p className="mt-5 text-sm text-slate-500">
          Thông tin do người đăng cung cấp và nền tảng kiểm duyệt trước khi công khai. “Tốt nhất” chỉ phản ánh con số,
          không thay thế việc tự kiểm tra hiện trạng và giấy tờ trước khi quyết định.
        </p>
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
