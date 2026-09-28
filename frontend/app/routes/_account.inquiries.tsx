import { useCallback, useEffect, useState } from 'react';
import {
  Building2,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  MapPin,
  MessageSquareText,
  RefreshCw,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { LeadItem } from '@/entities/lead/model/types';
import { apiClient } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';
import { BecomeOwnerCard } from '@/features/owner-onboarding/BecomeOwner';

type LeadPage = { items: LeadItem[]; totalElements: number; page: number; size: number; totalPages: number };
const STATUS_LABELS: Record<LeadItem['status'], string> = {
  NEW: 'Đã gửi cho người đăng',
  CONTACTED: 'Người đăng đã liên hệ',
  APPOINTED: 'Đã hẹn xem',
  CLOSED: 'Đã hoàn tất',
  SPAM: 'Yêu cầu không hợp lệ',
  WITHDRAWN: 'Bạn đã rút yêu cầu',
};
const formatDate = (value: string) =>
  new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));

export function MyInquiriesPage() {
  const { user } = useAuth();
  const [result, setResult] = useState<LeadPage>({ items: [], totalElements: 0, page: 0, size: 12, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async (page = 0) => {
    setLoading(true);
    setError('');
    try {
      setResult(await apiClient<LeadPage>(`/leads/sent?page=${page}&size=12`));
    } catch {
      setError('Không thể tải các tin bạn đã liên hệ. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load(0);
  }, [load]);

  return (
    <div className="min-h-full bg-white px-4 py-8 md:px-8 lg:py-10">
      <div className="mx-auto max-w-6xl">
        <header className="flex flex-col justify-between gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-end">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-950">Tin đã liên hệ</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
              Xem lại những bất động sản bạn đã gửi yêu cầu hẹn xem hoặc nhận tư vấn.
            </p>
          </div>
          <button
            type="button"
            onClick={() => void load(result.page)}
            disabled={loading}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-300 px-4 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Làm mới
          </button>
        </header>
        {user?.role === 'USER' && (
          <div className="mt-6">
            <BecomeOwnerCard />
          </div>
        )}
        {error && (
          <p role="alert" className="mt-5 rounded-lg bg-rose-50 p-4 text-sm text-rose-800">
            {error}
          </p>
        )}
        {loading ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            {Array.from({ length: 4 }, (_, index) => (
              <div key={index} className="h-52 animate-pulse rounded-xl bg-slate-100" />
            ))}
          </div>
        ) : result.items.length === 0 ? (
          <section className="mt-6 grid min-h-72 place-items-center rounded-xl border border-dashed border-slate-300 p-8 text-center">
            <div>
              <Building2 className="mx-auto h-11 w-11 text-slate-400" />
              <h2 className="mt-4 text-lg font-bold text-slate-950">Bạn chưa liên hệ tin nào</h2>
              <p className="mt-2 text-sm text-slate-600">
                Khi bạn gửi yêu cầu từ trang chi tiết bất động sản, tin đó sẽ xuất hiện tại đây.
              </p>
              <Link
                to="/search"
                className="mt-5 inline-flex min-h-11 items-center rounded-lg bg-slate-950 px-4 text-sm font-bold text-white"
              >
                Tìm bất động sản
              </Link>
            </div>
          </section>
        ) : (
          <section className="mt-6 grid gap-4 sm:grid-cols-2">
            {result.items.map((lead) => (
              <article key={lead.id} className="flex min-w-0 flex-col rounded-xl border border-slate-200 bg-white p-4">
                <div className="flex gap-3">
                  {lead.listingImageUrl ? (
                    <img src={lead.listingImageUrl} alt="" className="h-24 w-32 shrink-0 rounded-lg object-cover" />
                  ) : (
                    <span className="grid h-24 w-32 shrink-0 place-items-center rounded-lg bg-slate-100">
                      <Building2 className="h-6 w-6 text-slate-500" />
                    </span>
                  )}
                  <div className="min-w-0">
                    <span className="inline-flex rounded-md bg-blue-50 px-2 py-1 text-xs font-bold text-blue-800">
                      {lead.requestType === 'VIEWING' ? 'Đã yêu cầu hẹn xem' : 'Đã yêu cầu tư vấn'}
                    </span>
                    <h2 className="mt-2 line-clamp-2 font-bold text-slate-950">{lead.listingTitle}</h2>
                    {lead.listingAddress && (
                      <p className="mt-1 flex items-start gap-1 text-xs text-slate-600">
                        <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                        <span className="line-clamp-2">{lead.listingAddress}</span>
                      </p>
                    )}
                  </div>
                </div>
                <div className="mt-4 border-t border-slate-200 pt-3">
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                    <span className="font-semibold text-slate-700">{STATUS_LABELS[lead.status]}</span>
                    <span className="inline-flex items-center gap-1 text-slate-500">
                      <CalendarDays className="h-3.5 w-3.5" />
                      {formatDate(lead.createdAt)}
                    </span>
                  </div>
                  {lead.note && (
                    <p className="mt-3 flex gap-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                      <MessageSquareText className="mt-0.5 h-4 w-4 shrink-0" />
                      <span className="line-clamp-3">{lead.note}</span>
                    </p>
                  )}
                  <Link
                    to={`/listings/${lead.listingSlug || lead.listingId}`}
                    className="mt-3 inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-lg border border-slate-300 text-sm font-bold text-slate-800 hover:bg-slate-50"
                  >
                    <ExternalLink className="h-4 w-4" />
                    Xem lại tin
                  </Link>
                </div>
              </article>
            ))}
          </section>
        )}
        {result.totalPages > 1 && (
          <nav
            aria-label="Phân trang tin đã liên hệ"
            className="mt-7 flex items-center justify-between border-t border-slate-200 pt-5"
          >
            <p className="text-sm text-slate-600">{result.totalElements} tin đã liên hệ</p>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => void load(result.page - 1)}
                disabled={result.page === 0 || loading}
                aria-label="Trang trước"
                className="grid h-10 w-10 place-items-center rounded-lg border border-slate-300 disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <span className="text-sm font-semibold">
                {result.page + 1}/{result.totalPages}
              </span>
              <button
                type="button"
                onClick={() => void load(result.page + 1)}
                disabled={result.page + 1 >= result.totalPages || loading}
                aria-label="Trang sau"
                className="grid h-10 w-10 place-items-center rounded-lg border border-slate-300 disabled:opacity-40"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </nav>
        )}
      </div>
    </div>
  );
}

export default MyInquiriesPage;
