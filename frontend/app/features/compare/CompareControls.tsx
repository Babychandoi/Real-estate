import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Building2, Check, Plus, Scale, X } from 'lucide-react';
import { formatMoney } from '@/shared/format/money';
import { MAX_COMPARE, compareResultMessage, compareStore, useCompareItems, type CompareItem } from './compareStore';

export function compareHref(items: CompareItem[]) {
  return items.length ? `/compare?ids=${items.map((item) => item.id).join(',')}` : '/compare';
}

/** Toggle a listing in the compare list; `variant="overlay"` sits on top of a card image. */
export function CompareToggleButton({
  listing,
  variant = 'overlay',
}: {
  listing: CompareItem;
  variant?: 'overlay' | 'inline';
}) {
  const items = useCompareItems();
  const selected = items.some((item) => item.id === listing.id);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!message) return;
    const timer = window.setTimeout(() => setMessage(null), 3500);
    return () => window.clearTimeout(timer);
  }, [message]);

  const toggle = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setMessage(compareResultMessage(compareStore.toggle(listing)));
  };

  const base =
    variant === 'overlay'
      ? 'min-h-11 rounded-lg px-3 text-sm shadow-md backdrop-blur-sm'
      : 'min-h-11 w-full justify-center rounded-lg px-4 text-sm';
  const tone = selected
    ? 'bg-primary text-white border-primary'
    : variant === 'overlay'
      ? 'bg-white/95 text-primary border-white hover:bg-white'
      : 'bg-white text-primary border-primary/30 hover:bg-primary/10';

  return (
    <span className={variant === 'overlay' ? 'relative inline-flex' : 'relative flex w-full'}>
      <button
        type="button"
        onClick={toggle}
        aria-pressed={selected}
        aria-label={
          selected ? `Bỏ ${listing.title} khỏi danh sách so sánh` : `Thêm ${listing.title} vào danh sách so sánh`
        }
        className={`inline-flex items-center gap-1.5 border font-bold transition ${base} ${tone}`}
      >
        {selected ? <Check className="h-4 w-4" aria-hidden="true" /> : <Plus className="h-4 w-4" aria-hidden="true" />}
        {selected ? 'Đã chọn so sánh' : 'So sánh'}
      </button>
      {message && (
        <span
          role="status"
          className="absolute right-0 top-full z-20 mt-2 w-64 rounded-lg bg-slate-900 px-3 py-2 text-xs font-medium leading-snug text-white shadow-lg"
        >
          {message}
        </span>
      )}
    </span>
  );
}

/** Floating tray listing the selected items; hidden on the compare page itself. */
export function CompareTray() {
  const items = useCompareItems();
  const location = useLocation();
  if (!items.length || location.pathname.startsWith('/compare') || location.pathname.startsWith('/admin')) return null;

  const ready = items.length >= 2;
  return (
    <>
      <div aria-hidden="true" className="h-40 md:h-20" />
      <aside
        aria-label="Danh sách so sánh"
        className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 shadow-[0_-8px_24px_rgba(15,39,66,0.12)] backdrop-blur"
      >
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-3 sm:px-6 lg:px-8 md:flex-row md:items-center">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
            <Scale className="h-5 w-5 text-primary" aria-hidden="true" />
            So sánh {items.length}/{MAX_COMPARE} tin
            <span className="font-medium text-slate-500">· {items[0].purpose === 'SALE' ? 'Bán' : 'Cho thuê'}</span>
          </div>
          <ul className="flex flex-1 gap-2 overflow-x-auto">
            {items.map((item) => (
              <li
                key={item.id}
                className="flex min-w-[210px] max-w-[260px] items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-1.5 pr-2"
              >
                <div className="h-10 w-12 shrink-0 overflow-hidden rounded-lg bg-slate-200">
                  {item.imageUrl ? (
                    <img src={item.imageUrl} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <Building2 className="m-auto mt-2.5 h-5 w-5 text-slate-400" aria-hidden="true" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-bold text-slate-900">{item.title}</p>
                  <p data-price="" className="text-sm font-semibold text-emerald-800">
                    {formatMoney(item.price) || 'Chưa có giá'}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => compareStore.remove(item.id)}
                  aria-label={`Bỏ ${item.title} khỏi so sánh`}
                  className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-slate-500 hover:bg-white hover:text-rose-700"
                >
                  <X className="h-4 w-4" />
                </button>
              </li>
            ))}
            {Array.from({ length: MAX_COMPARE - items.length }, (_, index) => (
              <li
                key={`empty-${index}`}
                className="hidden min-w-[160px] items-center justify-center rounded-xl border border-dashed border-slate-300 px-3 text-xs text-slate-500 md:flex"
              >
                Chọn thêm tin
              </li>
            ))}
          </ul>
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => compareStore.clear()}
              className="min-h-11 rounded-lg px-3 text-sm font-semibold text-slate-600 hover:bg-slate-100"
            >
              Xóa hết
            </button>
            <Link
              to={compareHref(items)}
              aria-disabled={!ready}
              onClick={(event) => {
                if (!ready) event.preventDefault();
              }}
              className={`inline-flex min-h-11 items-center gap-2 rounded-lg px-5 text-sm font-bold text-white ${ready ? 'bg-primary hover:bg-primary/90' : 'cursor-not-allowed bg-slate-400'}`}
            >
              <Scale className="h-4 w-4" aria-hidden="true" />
              {ready ? 'So sánh ngay' : 'Chọn ít nhất 2 tin'}
            </Link>
          </div>
        </div>
      </aside>
    </>
  );
}
