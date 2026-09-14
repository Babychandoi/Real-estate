import { useCallback, useEffect, useState } from 'react';
import { Check, ChevronLeft, ChevronRight, Clock3, Ellipsis, Phone, RefreshCw, Search, ShieldCheck, Users, X } from 'lucide-react';
import { apiClient } from '@/shared/api/client';

type LeadStatus = 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM';
type Lead = { id: string; listingId: string; fullName: string; maskedPhone: string; note?: string; consentPolicy: boolean; status: LeadStatus; createdAt: string };
type LeadFilter = 'ALL' | LeadStatus;
type LeadPage = { items: Lead[]; totalElements: number; page: number; size: number; totalPages: number; statusCounts: Partial<Record<LeadStatus, number>> };

const STATUS_LABELS: Record<LeadStatus, string> = { NEW: 'Mới nhận', CONTACTED: 'Đã liên hệ', APPOINTED: 'Đã hẹn xem', CLOSED: 'Đã hoàn tất', SPAM: 'Không hợp lệ' };
const formatDateTime = (value: string) => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
const initials = (name: string) => name.trim().split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase() || 'K';

export function MyLeadsPage() {
  const [result, setResult] = useState<LeadPage>({ items: [], totalElements: 0, page: 0, size: 20, totalPages: 0, statusCounts: {} });
  const [phones, setPhones] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<LeadFilter>('ALL');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [statusMenuId, setStatusMenuId] = useState('');

  useEffect(() => { const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 300); return () => window.clearTimeout(timer); }, [query]);

  const load = useCallback(async (page = 0) => {
    setLoading(true); setError('');
    const params = new URLSearchParams({ page: String(page), size: '20' });
    if (filter !== 'ALL') params.set('status', filter);
    if (debouncedQuery) params.set('q', debouncedQuery);
    try { setResult(await apiClient<LeadPage>(`/leads/search?${params}`)); }
    catch { setError('Không thể tải danh sách khách quan tâm. Vui lòng thử lại.'); }
    finally { setLoading(false); }
  }, [debouncedQuery, filter]);

  useEffect(() => { void load(0); }, [load]);

  const reveal = async (leadId: string) => {
    setBusyId(leadId); setError('');
    try { const data = await apiClient<{ phone: string }>(`/leads/${leadId}/contact`); setPhones((current) => ({ ...current, [leadId]: data.phone })); }
    catch { setError('Không thể xem số liên hệ. Hãy kiểm tra quyền truy cập và thử lại.'); }
    finally { setBusyId(''); }
  };

  const updateStatus = async (leadId: string, status: LeadStatus) => {
    setBusyId(leadId); setStatusMenuId(''); setError('');
    try {
      await apiClient<Lead>(`/leads/${leadId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
      await load(result.page);
    } catch { setError('Không thể cập nhật trạng thái chăm sóc. Vui lòng thử lại.'); }
    finally { setBusyId(''); }
  };

  const countFor = (status?: LeadStatus) => status ? result.statusCounts[status] ?? 0 : Object.values(result.statusCounts).reduce((total, count) => total + (count ?? 0), 0);
  const filters: { value: LeadFilter; label: string }[] = [{ value: 'ALL', label: 'Tất cả' }, { value: 'NEW', label: 'Mới nhận' }, { value: 'CONTACTED', label: 'Đã liên hệ' }, { value: 'APPOINTED', label: 'Đã hẹn xem' }, { value: 'CLOSED', label: 'Hoàn tất' }];
  const pageStart = result.totalElements === 0 ? 0 : result.page * result.size + 1;
  const pageEnd = Math.min((result.page + 1) * result.size, result.totalElements);

  return <main className="min-h-full bg-white px-4 py-8 md:px-8 lg:py-10">
    <div className="mx-auto max-w-6xl">
      <header className="border-b border-slate-200 pb-7">
        <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
          <div className="max-w-3xl"><div className="mb-3 inline-flex items-center gap-2 text-xs font-bold tracking-wide text-slate-600"><ShieldCheck className="h-4 w-4" />QUẢN LÝ KHÁCH QUAN TÂM</div><h1 className="text-3xl font-bold tracking-tight text-slate-950 md:text-4xl">Hộp thư khách quan tâm</h1><p className="mt-3 text-sm leading-6 text-slate-600 md:text-base">Quản lý các yêu cầu liên hệ của người dùng đã xác thực eKYC.</p></div>
          <button type="button" onClick={() => void load(result.page)} disabled={loading} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Làm mới</button>
        </div>
      </header>

      {error && <div role="alert" className="mt-6 flex items-start gap-3 rounded-lg border border-rose-200 bg-white px-4 py-3 text-rose-800"><X className="mt-0.5 h-4 w-4 shrink-0" /><p className="text-sm font-medium">{error}</p></div>}

      <section className="mt-7 border-y border-slate-200 py-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex gap-2 overflow-x-auto pb-1" aria-label="Lọc lead theo trạng thái">{filters.map((item) => <button key={item.value} type="button" onClick={() => setFilter(item.value)} className={`min-h-10 shrink-0 rounded-lg border px-4 text-sm font-semibold ${filter === item.value ? 'border-slate-950 bg-slate-950 text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'}`}>{item.label} <span className="ml-1 tabular-nums">{countFor(item.value === 'ALL' ? undefined : item.value)}</span></button>)}</div>
          <label className="relative block w-full lg:w-80"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" /><input value={query} onChange={(event) => setQuery(event.target.value)} className="min-h-11 w-full rounded-lg border border-slate-300 bg-white py-2 pl-10 pr-4 text-sm text-slate-950 outline-none placeholder:text-slate-400 focus:border-slate-950" placeholder="Tìm tên hoặc nội dung liên hệ" /></label>
        </div>
      </section>

      <div className="mt-5 flex items-center justify-between gap-4"><p className="text-sm font-semibold text-slate-900"><span className="mr-2 inline-block h-2 w-2 rounded-full bg-slate-900" />{countFor()} khách quan tâm</p><p className="hidden text-xs text-slate-500 sm:block">Số điện thoại chỉ hiện sau khi bạn bấm “Xem số liên hệ”.</p></div>

      {loading ? <div className="mt-5 grid gap-4">{Array.from({ length: 3 }, (_, index) => <div key={index} className="h-44 animate-pulse rounded-xl border border-slate-200 bg-white" />)}</div> : result.items.length === 0 ? <section className="mt-5 grid min-h-72 place-items-center rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center"><div><Users className="mx-auto h-11 w-11 text-slate-400" /><h2 className="mt-4 text-lg font-bold text-slate-950">Không tìm thấy khách quan tâm</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-600">Thử thay đổi bộ lọc hoặc từ khóa. Lead mới sẽ hiển thị tại đây.</p>{(filter !== 'ALL' || query) && <button type="button" onClick={() => { setFilter('ALL'); setQuery(''); }} className="mt-5 min-h-11 rounded-lg border border-slate-950 px-4 text-sm font-bold text-slate-950">Xóa bộ lọc</button>}</div></section> : <div className="mt-5 grid gap-3">
        {result.items.map((lead) => <article key={lead.id} className="rounded-xl border border-slate-200 bg-white p-5 md:p-6">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><span className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-bold text-slate-700">{STATUS_LABELS[lead.status]}</span><span className="flex items-center gap-1 text-xs text-slate-500"><Clock3 className="h-3.5 w-3.5" />Gửi lúc {formatDateTime(lead.createdAt)}</span></div><div className="mt-5 flex items-start gap-3"><div className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-slate-100 text-sm font-bold text-slate-700">{initials(lead.fullName)}</div><div className="min-w-0"><h2 className="truncate text-lg font-bold text-slate-950">{lead.fullName}</h2><p className="mt-1 text-sm text-slate-600">Khách quan tâm qua tin đăng của bạn</p></div></div>{lead.note && <div className="mt-5 rounded-lg bg-slate-50 px-4 py-3"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Nội dung khách để lại</p><p className="mt-1.5 break-words text-sm leading-6 text-slate-800">{lead.note}</p></div>}</div>
            <div className="grid gap-2 sm:grid-cols-2 lg:w-52 lg:grid-cols-1">{phones[lead.id] ? <a href={`tel:${phones[lead.id]}`} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-950 bg-white px-4 text-sm font-bold text-slate-950"><Phone className="h-4 w-4" />{phones[lead.id]}</a> : <button type="button" disabled={busyId === lead.id || !lead.consentPolicy} onClick={() => void reveal(lead.id)} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-950 bg-white px-4 text-sm font-bold text-slate-950 hover:bg-slate-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"><Phone className="h-4 w-4" />{lead.consentPolicy ? 'Xem số liên hệ' : 'Chưa có đồng ý liên hệ'}</button>}
              <div className="relative"><button type="button" aria-label={`Đổi trạng thái ${lead.fullName}`} aria-expanded={statusMenuId === lead.id} onClick={() => setStatusMenuId((current) => current === lead.id ? '' : lead.id)} disabled={busyId === lead.id} className="inline-flex min-h-11 w-full items-center justify-between rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"><span>{STATUS_LABELS[lead.status]}</span><Ellipsis className="h-5 w-5" /></button>{statusMenuId === lead.id && <div className="absolute right-0 z-20 mt-1 w-52 rounded-lg border border-slate-200 bg-white p-1 shadow-lg">{(Object.entries(STATUS_LABELS) as [LeadStatus, string][]).map(([value, label]) => <button key={value} type="button" onClick={() => void updateStatus(lead.id, value)} className="flex min-h-10 w-full items-center justify-between rounded-md px-3 text-left text-sm text-slate-700 hover:bg-slate-50"><span>{label}</span>{lead.status === value && <Check className="h-4 w-4" />}</button>)}</div>}</div>
            </div></div>
        </article>)}
      </div>}

      {result.totalPages > 1 && <nav aria-label="Phân trang khách quan tâm" className="mt-7 flex flex-col gap-3 border-t border-slate-200 pt-5 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-slate-600">Hiển thị {pageStart}–{pageEnd} trên {result.totalElements} khách</p><div className="flex items-center gap-2"><button type="button" onClick={() => void load(result.page - 1)} disabled={result.page === 0 || loading} className="inline-flex min-h-10 items-center gap-1 rounded-lg border border-slate-300 px-3 text-sm font-semibold text-slate-700 disabled:opacity-40"><ChevronLeft className="h-4 w-4" />Trước</button><span className="px-2 text-sm font-semibold text-slate-700">{result.page + 1}/{result.totalPages}</span><button type="button" onClick={() => void load(result.page + 1)} disabled={result.page + 1 >= result.totalPages || loading} className="inline-flex min-h-10 items-center gap-1 rounded-lg border border-slate-300 px-3 text-sm font-semibold text-slate-700 disabled:opacity-40">Sau<ChevronRight className="h-4 w-4" /></button></div></nav>}
    </div>
  </main>;
}

export default MyLeadsPage;
