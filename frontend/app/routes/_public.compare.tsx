import React, { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Building2, MapPin, RefreshCw, ShieldCheck, X } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingApi } from '@/entities/listing/api/listingApi';
import {
  calculateUnitPrice,
  formatPriceVnd,
  formatPropertyType,
  type Listing,
} from '@/entities/listing/model/types';
import { Button } from '@/shared/ui/Button';

function fieldDiffers(items: Listing[], value: (item: Listing) => unknown) {
  if (items.length < 2) return false;
  return items.some((item) => value(item) !== value(items[0]));
}

export const PropertyComparePage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [listings, setListings] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [diffOnly, setDiffOnly] = useState(false);

  const requestedIds = useMemo(
    () => (searchParams.get('ids') ?? '').split(',').map((id) => id.trim()).filter(Boolean).slice(0, 3),
    [searchParams],
  );

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    listingApi.searchListings({ page: 0, size: 30, sortBy: 'LATEST' })
      .then((result) => {
        if (!active) return;
        setListings(result);
      })
      .catch(() => {
        if (!active) return;
        setError('Không thể tải dữ liệu tin đăng. Vui lòng thử lại.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => { active = false; };
  }, []);

  const selected = useMemo(() => {
    if (requestedIds.length) {
      const requested = requestedIds
        .map((id) => listings.find((listing) => listing.id === id))
        .filter((listing): listing is Listing => Boolean(listing));
      const purpose = requested[0]?.purpose;
      return requested.filter((listing) => listing.purpose === purpose).slice(0, 3);
    }

    const saleListings = listings.filter((listing) => listing.purpose === 'SALE');
    const source = saleListings.length >= 2 ? saleListings : listings;
    const purpose = source[0]?.purpose;
    return source.filter((listing) => listing.purpose === purpose).slice(0, 3);
  }, [listings, requestedIds]);

  const removeListing = (id: string) => {
    const nextIds = selected.filter((listing) => listing.id !== id).map((listing) => listing.id);
    setSearchParams(nextIds.length ? { ids: nextIds.join(',') } : {});
  };

  const rows = [
    { label: 'Mục đích', value: (item: Listing) => item.purpose === 'SALE' ? 'Bán' : 'Cho thuê' },
    { label: 'Loại hình', value: (item: Listing) => formatPropertyType(item.propertyType) },
    { label: 'Giá', value: (item: Listing) => formatPriceVnd(item.priceVnd) },
    { label: 'Diện tích', value: (item: Listing) => `${item.areaM2} m²` },
    { label: 'Đơn giá tham khảo', value: (item: Listing) => calculateUnitPrice(item.priceVnd, item.areaM2) || 'Chưa đủ dữ liệu' },
    { label: 'Khu vực', value: (item: Listing) => item.addressSummary },
    { label: 'Xác thực người đăng', value: (item: Listing) => item.isVerified ? 'Đã xác thực' : 'Chưa xác thực' },
  ];

  return (
    <main className="min-h-screen bg-slate-50 py-8">
      <div className="container mx-auto max-w-[1360px] px-4">
        <Link to="/search" className="mb-4 inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900">
          <ArrowLeft className="h-4 w-4" /> Quay lại tìm kiếm
        </Link>

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-900 md:text-3xl">So sánh tin đăng</h1>
              <p className="mt-1 text-sm text-slate-500">
                Dữ liệu dưới đây được tải trực tiếp từ các tin đang công khai. Chỉ so sánh các tin cùng mục đích bán hoặc cho thuê.
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <label className="flex cursor-pointer items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700">
                <input type="checkbox" checked={diffOnly} onChange={(event) => setDiffOnly(event.target.checked)} />
                Chỉ xem điểm khác biệt
              </label>
              <Link to="/search">
                <Button variant="outline" size="sm"><RefreshCw className="mr-1 h-4 w-4" /> Chọn lại</Button>
              </Link>
            </div>
          </div>
        </section>

        {loading && <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center text-slate-600">Đang tải tin đăng…</div>}
        {!loading && error && <div role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-800">{error}</div>}
        {!loading && !error && selected.length < 2 && (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <Building2 className="mx-auto mb-3 h-10 w-10 text-slate-400" />
            <h2 className="text-lg font-bold text-slate-900">Chưa đủ tin để so sánh</h2>
            <p className="mt-1 text-sm text-slate-500">Cần ít nhất hai tin công khai cùng mục đích.</p>
            <Link to="/search" className="mt-4 inline-block"><Button>Tìm tin đăng</Button></Link>
          </div>
        )}

        {!loading && !error && selected.length >= 2 && (
          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="grid border-b border-slate-200 bg-slate-50 md:grid-cols-4">
              <div className="p-5 md:col-span-1">
                <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Tiêu chí</p>
                <p className="mt-1 font-bold text-slate-900">{selected.length} tin đang so sánh</p>
              </div>
              {selected.map((item) => (
                <article key={item.id} className="relative border-t border-slate-200 bg-white p-5 md:border-l md:border-t-0">
                  <button type="button" onClick={() => removeListing(item.id)} aria-label={`Bỏ ${item.title} khỏi so sánh`} className="absolute right-7 top-7 z-10 rounded-full bg-white/90 p-1.5 text-slate-600 shadow hover:text-rose-700">
                    <X className="h-4 w-4" />
                  </button>
                  <div className="mb-3 aspect-video overflow-hidden rounded-xl bg-slate-100">
                    {item.primaryImageUrl ? (
                      <img src={item.primaryImageUrl} alt={item.title} className="h-full w-full object-cover" />
                    ) : (
                      <div className="flex h-full items-center justify-center"><Building2 className="h-10 w-10 text-slate-400" /></div>
                    )}
                  </div>
                  <div className="mb-2 flex items-center gap-2 text-xs text-slate-600">
                    {item.isVerified && <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-1 font-semibold text-emerald-800"><ShieldCheck className="h-3.5 w-3.5" /> Đã xác thực người đăng</span>}
                  </div>
                  <p className="text-xl font-black text-emerald-800">{formatPriceVnd(item.priceVnd)}</p>
                  <Link to={`/listings/${item.id}`} className="mt-1 line-clamp-2 block text-sm font-bold text-slate-900 hover:text-emerald-700">{item.title}</Link>
                  <p className="mt-2 flex items-start gap-1 text-xs text-slate-500"><MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {item.addressSummary}</p>
                </article>
              ))}
            </div>

            <div className="divide-y divide-slate-100 text-sm">
              {rows.filter((row) => !diffOnly || fieldDiffers(selected, row.value)).map((row) => (
                <div key={row.label} className="grid md:grid-cols-4">
                  <div className="bg-slate-50 p-4 font-semibold text-slate-600">{row.label}</div>
                  {selected.map((item) => <div key={item.id} className="border-t border-slate-100 p-4 text-slate-800 md:border-l md:border-t-0">{row.value(item)}</div>)}
                </div>
              ))}
            </div>
          </section>
        )}

        <p className="mt-5 text-sm text-slate-500">
          Thông tin do người đăng cung cấp và nền tảng kiểm duyệt trước khi công khai. Người dùng cần liên hệ trực tiếp và tự kiểm tra hiện trạng, giấy tờ trước khi quyết định.
        </p>
      </div>
    </main>
  );
};

export default PropertyComparePage;
