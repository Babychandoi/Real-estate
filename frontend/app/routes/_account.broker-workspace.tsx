import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatListingStatus } from '@/entities/listing/model/types';
import { apiClient } from '@/shared/api/client';

type Data = {
  listingStats: { listings: number; active: number; pending: number };
  leadStats: { leads: number; new_leads: number; avg_wait_minutes: number };
  sla: { firstResponseMinutes: number; reminderEnabled: boolean; dailyDigestEnabled: boolean };
  listings: Array<{ id: string; status: string; created_at: string; updated_at: string }>;
};

export function BrokerWorkspacePage() {
  const [data, setData] = useState<Data>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await apiClient<Data>('/broker/workspace')); }
    catch { setError('Không thể tải không gian môi giới. Vui lòng thử lại.'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const save = async () => {
    if (!data) return;
    setMessage(''); setError('');
    try {
      setData(await apiClient<Data>('/broker/workspace/sla', { method: 'PUT', body: JSON.stringify(data.sla) }));
      setMessage('Đã lưu mục tiêu phản hồi và lịch nhắc.');
    } catch { setError('Không thể lưu cấu hình phản hồi. Vui lòng thử lại.'); }
  };

  if (loading) return <main className="p-10" role="status">Đang tải dữ liệu không gian môi giới…</main>;
  if (error || !data) return <main className="mx-auto max-w-lg px-4 py-16 text-center"><p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-800">{error || 'Không có dữ liệu để hiển thị.'}</p><button type="button" onClick={() => void load()} className="mt-4 min-h-11 rounded-xl bg-primary px-5 font-bold text-white">Tải lại</button></main>;

  const cards = [['Tin đăng', data.listingStats.listings], ['Đang hiển thị', data.listingStats.active], ['Chờ duyệt', data.listingStats.pending], ['Lead mới', data.leadStats.new_leads]];
  return <main className="max-w-6xl mx-auto px-4 py-10 space-y-8">
    <header className="flex flex-col md:flex-row md:items-end justify-between gap-4"><div><p className="text-emerald-700 text-sm font-bold">KHÔNG GIAN MÔI GIỚI</p><h1 className="text-3xl font-extrabold">Hiệu suất từ dữ liệu thật</h1><p className="text-slate-600 mt-2">Nền tảng giúp kiểm duyệt và kết nối; các bên tự liên hệ, không đặt cọc hay mua bán trên web.</p></div><Link to="/billing" className="min-h-11 px-5 rounded-xl bg-emerald-700 text-white font-bold inline-flex items-center">Quản lý gói đăng tin</Link></header>
    {message && <p role="status" className="rounded-xl bg-emerald-50 text-emerald-900 p-3">{message}</p>}
    <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">{cards.map(([name, value]) => <article key={String(name)} className="rounded-2xl border bg-white p-5"><p className="text-sm text-slate-500">{name}</p><p className="text-3xl font-extrabold mt-1">{value}</p></article>)}</section>
    <section className="grid lg:grid-cols-3 gap-6"><article className="lg:col-span-2 rounded-2xl border bg-white p-6"><h2 className="text-xl font-bold mb-4">Kho tin gần đây</h2>{data.listings.length === 0 ? <p className="text-slate-500">Bạn chưa có tin đăng nào.</p> : <div className="divide-y">{data.listings.map(item => <div key={item.id} className="py-3 flex justify-between gap-3"><Link className="text-sm font-semibold text-emerald-800" to={`/listings/${item.id}`}>Tin {item.id.slice(0, 8)}</Link><span className="font-bold text-sm">{formatListingStatus(item.status)}</span></div>)}</div>}</article><article className="rounded-2xl bg-slate-950 text-white p-6"><h2 className="text-xl font-bold">Mục tiêu phản hồi lead</h2><p className="text-slate-200 text-sm mt-2">Hiện có {data.leadStats.leads} lead, thời gian chờ trung bình {data.leadStats.avg_wait_minutes} phút.</p><label className="block mt-5 text-sm">Thời gian phản hồi mục tiêu (phút)<input type="number" min={5} max={1440} value={data.sla.firstResponseMinutes} onChange={event => setData({ ...data, sla: { ...data.sla, firstResponseMinutes: +event.target.value } })} className="mt-1 w-full p-3 rounded-lg bg-slate-800 border border-slate-700" /></label>{(['reminderEnabled', 'dailyDigestEnabled'] as const).map(key => <label key={key} className="flex gap-2 mt-4"><input type="checkbox" checked={data.sla[key]} onChange={event => setData({ ...data, sla: { ...data.sla, [key]: event.target.checked } })} />{key === 'reminderEnabled' ? 'Nhắc lead quá hạn phản hồi' : 'Gửi tổng hợp hằng ngày'}</label>)}<button type="button" onClick={() => void save()} className="mt-6 w-full min-h-11 rounded-xl bg-emerald-500 text-emerald-950 font-extrabold">Lưu cấu hình</button></article></section>
  </main>;
}

export default BrokerWorkspacePage;
