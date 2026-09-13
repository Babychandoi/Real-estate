import { useCallback, useEffect, useState } from 'react';
import { Phone, RefreshCw, Users } from 'lucide-react';
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

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Mới', CONTACTED: 'Đã liên hệ', APPOINTED: 'Đã hẹn xem', CLOSED: 'Đã đóng', SPAM: 'Không hợp lệ',
};

export function MyLeadsPage() {
  const [leads, setLeads] = useState<Lead[]>([]);
  const [phones, setPhones] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');

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
      const updated = await apiClient<Lead>(`/leads/${leadId}/status`, {
        method: 'PATCH', body: JSON.stringify({ status }),
      });
      setLeads((current) => current.map((lead) => lead.id === leadId ? updated : lead));
    } catch { setError('Không thể cập nhật trạng thái chăm sóc. Vui lòng thử lại.'); }
    finally { setBusyId(''); }
  };

  return <main className="mx-auto max-w-6xl space-y-6 px-4 py-8 md:px-8">
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div><h1 className="text-3xl font-extrabold">Khách quan tâm tin đăng</h1><p className="mt-2 text-sm text-slate-600">Chỉ bạn và nhân sự kiểm duyệt được xem thông tin khách đã đồng ý chia sẻ.</p></div>
      <button type="button" onClick={() => void load()} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border px-4 font-semibold"><RefreshCw className="h-4 w-4" />Tải lại</button>
    </header>
    {error && <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-800">{error}</p>}
    {loading ? <p role="status" className="rounded-xl bg-slate-50 p-6">Đang tải khách quan tâm…</p> : leads.length === 0 ? <section className="grid min-h-56 place-items-center rounded-2xl border border-dashed bg-white p-8 text-center"><div><Users className="mx-auto h-10 w-10 text-slate-400" /><h2 className="mt-3 text-lg font-bold">Chưa có khách quan tâm</h2><p className="mt-1 text-sm text-slate-600">Yêu cầu mới từ các tin của bạn sẽ xuất hiện tại đây.</p></div></section> : <section className="grid gap-4">
      {leads.map((lead) => <article key={lead.id} className="rounded-2xl border bg-white p-5">
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 className="break-words text-lg font-bold">{lead.fullName}</h2><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-800">{STATUS_LABELS[lead.status]}</span></div><p className="mt-1 text-sm text-slate-600">Gửi lúc {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(lead.createdAt))}</p>{lead.note && <p className="mt-3 break-words rounded-xl bg-slate-50 p-3 text-sm">{lead.note}</p>}</div>
          <div className="flex min-w-56 flex-col gap-2"><button type="button" disabled={busyId === lead.id} onClick={() => void reveal(lead.id)} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-primary px-4 font-bold text-white disabled:opacity-60"><Phone className="h-4 w-4" />{phones[lead.id] || lead.maskedPhone}</button><select aria-label={`Trạng thái chăm sóc ${lead.fullName}`} disabled={busyId === lead.id} value={lead.status} onChange={(event) => void updateStatus(lead.id, event.target.value as LeadStatus)} className="min-h-11 rounded-xl border bg-white px-3 text-sm font-semibold disabled:opacity-60">{Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div></div>
      </article>)}
    </section>}
  </main>;
}

export default MyLeadsPage;
