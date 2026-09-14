import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarCheck, CheckCircle2, Clock3, Phone, RefreshCw, Search, ShieldCheck, Users, X } from 'lucide-react';
import { apiClient } from '@/shared/api/client';

type LeadStatus = 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM';
type Lead = {
  id: string;
  listingId: string;
  fullName: string;
  maskedPhone: string;
  note?: string;
  status: LeadStatus;
  createdAt: string;
};
type LeadFilter = 'ALL' | LeadStatus;

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Mới nhận', CONTACTED: 'Đã liên hệ', APPOINTED: 'Đã hẹn xem', CLOSED: 'Đã hoàn tất', SPAM: 'Không hợp lệ',
};

const STATUS_STYLES: Record<LeadStatus, string> = {
  NEW: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONTACTED: 'bg-sky-50 text-sky-800 ring-sky-200',
  APPOINTED: 'bg-violet-50 text-violet-800 ring-violet-200',
  CLOSED: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  SPAM: 'bg-rose-50 text-rose-800 ring-rose-200',
};

const formatDateTime = (value: string) => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
const initials = (name: string) => name.trim().split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase() || 'K';

export function MyLeadsPage() {
  const [leads, setLeads] = useState<Lead[]>([]);
  const [phones, setPhones] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<LeadFilter>('ALL');
  const [query, setQuery] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setLeads(await apiClient<Lead[]>('/leads?size=100')); }
    catch { setError('Không thể tải danh sách khách quan tâm. Vui lòng thử lại.'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const reveal = async (leadId: string) => {
    setBusyId(leadId); setError('');
    try {
      const result = await apiClient<{ phone: string }>(`/leads/${leadId}/contact`);
      setPhones((current) => ({ ...current, [leadId]: result.phone }));
    } catch { setError('Không thể xem số liên hệ. Hãy kiểm tra quyền truy cập và thử lại.'); }
    finally { setBusyId(''); }
  };

  const updateStatus = async (leadId: string, status: LeadStatus) => {
    setBusyId(leadId); setError('');
    try {
      const updated = await apiClient<Lead>(`/leads/${leadId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
      setLeads((current) => current.map((lead) => lead.id === leadId ? updated : lead));
    } catch { setError('Không thể cập nhật trạng thái chăm sóc. Vui lòng thử lại.'); }
    finally { setBusyId(''); }
  };

  const counts = useMemo(() => leads.reduce<Record<LeadFilter, number>>((result, lead) => ({ ...result, [lead.status]: result[lead.status] + 1, ALL: result.ALL + 1 }), { ALL: 0, NEW: 0, CONTACTED: 0, APPOINTED: 0, CLOSED: 0, SPAM: 0 }), [leads]);
  const visibleLeads = useMemo(() => leads.filter((lead) => {
    const matchesStatus = filter === 'ALL' || lead.status === filter;
    const normalizedQuery = query.trim().toLocaleLowerCase('vi-VN');
    return matchesStatus && (!normalizedQuery || `${lead.fullName} ${lead.maskedPhone} ${lead.note ?? ''}`.toLocaleLowerCase('vi-VN').includes(normalizedQuery));
  }), [filter, leads, query]);

  const filters: { value: LeadFilter; label: string }[] = [
    { value: 'ALL', label: 'Tất cả' }, { value: 'NEW', label: 'Mới nhận' }, { value: 'CONTACTED', label: 'Đang liên hệ' }, { value: 'APPOINTED', label: 'Đã hẹn xem' }, { value: 'CLOSED', label: 'Hoàn tất' },
  ];

  return <main className="min-h-full bg-surface px-4 py-8 md:px-8 lg:py-10">
    <div className="mx-auto max-w-7xl">
      <header className="border-b border-outline-variant/50 pb-7">
        <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
          <div className="max-w-3xl">
            <div className="mb-3 inline-flex items-center gap-2 rounded-md bg-secondary/10 px-3 py-1.5 text-xs font-bold tracking-wide text-secondary"><ShieldCheck className="h-4 w-4" />CRM MÔI GIỚI</div>
            <h1 className="text-3xl font-bold tracking-tight text-primary md:text-4xl">Khách quan tâm & hộp thư Lead</h1>
            <p className="mt-3 text-sm leading-6 text-on-surface-variant md:text-base">Theo dõi yêu cầu từ các tài khoản đã xác thực eKYC, liên hệ an toàn và cập nhật tiến độ chăm sóc tại một nơi.</p>
          </div>
          <button type="button" onClick={() => void load()} disabled={loading} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-surface-container-low px-4 text-sm font-semibold text-on-surface transition-colors hover:bg-surface-container disabled:opacity-60 focus-visible:ring-2 focus-visible:ring-primary"><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Làm mới dữ liệu</button>
        </div>
      </header>

      {error && <div role="alert" className="mt-6 flex items-start gap-3 rounded-xl bg-error-container px-4 py-3 text-on-error-container"><X className="mt-0.5 h-4 w-4 shrink-0" /><p className="text-sm font-medium">{error}</p></div>}

      <section aria-label="Tổng quan lead" className="grid gap-px overflow-hidden rounded-xl bg-outline-variant/50 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
        <div className="bg-surface-container-lowest p-5"><p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Tổng lead</p><p className="mt-2 text-3xl font-bold tracking-tight text-primary">{counts.ALL}</p><p className="mt-2 text-xs text-on-surface-variant">Đã nhận trong danh sách của bạn</p></div>
        <div className="bg-surface-container-lowest p-5"><p className="text-xs font-bold uppercase tracking-wider text-amber-800">Cần phản hồi</p><p className="mt-2 text-3xl font-bold tracking-tight text-amber-800">{counts.NEW}</p><p className="mt-2 flex items-center gap-1 text-xs text-on-surface-variant"><Clock3 className="h-3.5 w-3.5" />Yêu cầu mới chưa xử lý</p></div>
        <div className="bg-surface-container-lowest p-5"><p className="text-xs font-bold uppercase tracking-wider text-primary">Đã hẹn xem</p><p className="mt-2 text-3xl font-bold tracking-tight text-primary">{counts.APPOINTED}</p><p className="mt-2 flex items-center gap-1 text-xs text-on-surface-variant"><CalendarCheck className="h-3.5 w-3.5" />Đang chờ buổi hẹn</p></div>
        <div className="bg-surface-container-lowest p-5"><p className="text-xs font-bold uppercase tracking-wider text-emerald-800">Đã hoàn tất</p><p className="mt-2 text-3xl font-bold tracking-tight text-emerald-800">{counts.CLOSED}</p><p className="mt-2 flex items-center gap-1 text-xs text-on-surface-variant"><CheckCircle2 className="h-3.5 w-3.5" />Đã đóng quy trình chăm sóc</p></div>
      </section>

      <section className="mt-8">
        <div className="flex flex-col gap-4 border-b border-outline-variant/50 pb-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex gap-2 overflow-x-auto pb-1" aria-label="Lọc lead theo trạng thái">
            {filters.map((item) => <button key={item.value} type="button" onClick={() => setFilter(item.value)} className={`min-h-10 shrink-0 rounded-full px-4 text-sm font-semibold transition-colors focus-visible:ring-2 focus-visible:ring-primary ${filter === item.value ? 'bg-primary text-on-primary shadow-sm' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container hover:text-on-surface'}`}>{item.label}<span className={`ml-2 text-xs ${filter === item.value ? 'text-on-primary/80' : 'text-on-surface-variant'}`}>{counts[item.value]}</span></button>)}
          </div>
          <label className="relative block w-full lg:w-80"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input value={query} onChange={(event) => setQuery(event.target.value)} className="min-h-11 w-full rounded-lg border border-outline-variant/60 bg-surface-container-lowest py-2 pl-10 pr-4 text-sm text-on-surface outline-none transition-shadow placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary" placeholder="Tìm tên, số điện thoại, ghi chú" /></label>
        </div>

        <div className="mt-5 flex items-center justify-between gap-4"><p className="text-sm font-semibold text-on-surface"><span className="mr-2 inline-block h-2 w-2 rounded-full bg-secondary" />{visibleLeads.length} khách quan tâm</p><p className="hidden text-xs text-on-surface-variant sm:block">Thông tin liên hệ chỉ hiện khi bạn mở theo quyền được cấp.</p></div>

        {loading ? <div className="mt-5 grid gap-4">{Array.from({ length: 3 }, (_, index) => <div key={index} className="h-48 animate-pulse rounded-xl bg-surface-container-low" />)}</div> : visibleLeads.length === 0 ? <section className="mt-5 grid min-h-72 place-items-center rounded-xl border border-dashed border-outline-variant bg-surface-container-low p-8 text-center"><div><Users className="mx-auto h-11 w-11 text-on-surface-variant" /><h2 className="mt-4 text-lg font-bold text-on-surface">Không tìm thấy khách quan tâm</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-on-surface-variant">Thử thay đổi bộ lọc hoặc từ khóa. Lead mới từ các tin đăng của bạn sẽ xuất hiện ở đây.</p>{(filter !== 'ALL' || query) && <button type="button" onClick={() => { setFilter('ALL'); setQuery(''); }} className="mt-5 min-h-11 rounded-lg bg-primary px-4 text-sm font-bold text-on-primary">Xóa bộ lọc</button>}</div></section> : <div className="mt-5 grid gap-4">
          {visibleLeads.map((lead) => <article key={lead.id} className="rounded-xl bg-surface-container-lowest p-5 shadow-sm ring-1 ring-outline-variant/50 transition-shadow hover:shadow-md md:p-6">
            <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ring-1 ${STATUS_STYLES[lead.status]}`}>{STATUS_LABELS[lead.status]}</span><span className="flex items-center gap-1 text-xs text-on-surface-variant"><Clock3 className="h-3.5 w-3.5" />Gửi lúc {formatDateTime(lead.createdAt)}</span></div>
                <div className="mt-5 flex items-start gap-3"><div className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-primary-fixed text-sm font-bold text-on-primary-fixed">{initials(lead.fullName)}</div><div className="min-w-0"><h2 className="truncate text-lg font-bold text-primary">{lead.fullName}</h2><p className="mt-1 text-sm text-on-surface-variant">Khách quan tâm qua tin đăng của bạn</p></div></div>
                {lead.note && <div className="mt-5 rounded-lg bg-surface-container-low px-4 py-3"><p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Nội dung khách để lại</p><p className="mt-1.5 break-words text-sm leading-6 text-on-surface">{lead.note}</p></div>}
              </div>
              <div className="grid gap-2 sm:grid-cols-2 lg:w-56 lg:grid-cols-1">
                <button type="button" disabled={busyId === lead.id} onClick={() => void reveal(lead.id)} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-secondary px-4 text-sm font-bold text-on-secondary transition-colors hover:bg-on-secondary-container disabled:opacity-60 focus-visible:ring-2 focus-visible:ring-primary"><Phone className="h-4 w-4" />{phones[lead.id] || lead.maskedPhone}</button>
                <label className="sr-only" htmlFor={`lead-status-${lead.id}`}>Trạng thái chăm sóc {lead.fullName}</label><select id={`lead-status-${lead.id}`} disabled={busyId === lead.id} value={lead.status} onChange={(event) => void updateStatus(lead.id, event.target.value as LeadStatus)} className="min-h-11 rounded-lg border border-outline-variant/60 bg-surface-container-low px-3 text-sm font-semibold text-on-surface outline-none focus:ring-2 focus:ring-primary disabled:opacity-60">{Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
              </div>
            </div>
          </article>)}
        </div>}
      </section>
    </div>
  </main>;
}

export default MyLeadsPage;
