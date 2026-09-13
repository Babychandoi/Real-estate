import { useEffect, useState } from 'react';
import { BarChart3, Download, RefreshCw } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { Button } from '@/shared/ui/Button';

interface Step { step:number; name:string; count:number; percentOfPrevious:number }
interface Funnel { impressions:number; detailViews:number; totalLeads:number; totalContacted:number; totalClosed:number; conversionRate:number; steps:Step[] }

export function ProductAnalyticsPage() {
  const [data,setData]=useState<Funnel>(); const [error,setError]=useState(''); const [loading,setLoading]=useState(true);
  const load=()=>{setLoading(true);setError('');apiClient<Funnel>('/analytics/funnel').then(setData).catch(()=>setError('Không thể tải số liệu phân tích từ máy chủ.')).finally(()=>setLoading(false))};
  useEffect(load,[]);
  const exportCsv=()=>{if(!data)return;const rows=[['Bước','Tên','Số lượng','Tỷ lệ'],...data.steps.map(x=>[String(x.step),x.name,String(x.count),String(x.percentOfPrevious)])];const url=URL.createObjectURL(new Blob(['\uFEFF'+rows.map(r=>r.map(v=>`"${v}"`).join(',')).join('\n')],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='bao-cao-phan-tich.csv';a.click();URL.revokeObjectURL(url)};
  return <main className="min-h-screen bg-slate-50 py-8"><div className="container mx-auto max-w-6xl px-4">
    <header className="mb-6 flex flex-col justify-between gap-4 md:flex-row md:items-end"><div><h1 className="text-3xl font-bold text-slate-900">Phân tích chuyển đổi</h1><p className="mt-1 text-sm text-slate-600">Số liệu được tổng hợp trực tiếp từ hoạt động đã ghi nhận trong hệ thống.</p></div><div className="flex gap-2"><Button variant="outline" onClick={load}><RefreshCw className="mr-1 h-4 w-4"/>Tải lại</Button><Button onClick={exportCsv} disabled={!data}><Download className="mr-1 h-4 w-4"/>Xuất CSV</Button></div></header>
    {loading&&<section className="rounded-2xl border bg-white p-10 text-center" role="status">Đang tải số liệu…</section>}
    {!loading&&error&&<section className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-800" role="alert">{error}</section>}
    {!loading&&data&&<><section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{[['Lead',data.totalLeads],['Đã liên hệ',data.totalContacted],['Đã chốt',data.totalClosed],['Tỷ lệ chốt',`${data.conversionRate}%`]].map(([label,value])=><article key={label} className="rounded-2xl bg-white p-5 shadow-sm"><p className="text-sm text-slate-500">{label}</p><p className="mt-1 text-3xl font-bold text-slate-900">{value}</p></article>)}</section><section className="mt-6 overflow-hidden rounded-2xl border bg-white"><div className="flex items-center gap-2 border-b p-5"><BarChart3 className="h-5 w-5 text-emerald-700"/><h2 className="text-lg font-bold">Phễu dữ liệu</h2></div>{data.steps.length===0?<p className="p-8 text-center text-slate-500">Chưa có dữ liệu chuyển đổi.</p>:<div className="divide-y">{data.steps.map(x=><div key={x.step} className="grid gap-2 p-4 sm:grid-cols-[1fr_auto_auto]"><span className="font-medium">{x.name}</span><span className="tabular-nums">{x.count.toLocaleString('vi-VN')}</span><span className="w-24 text-right tabular-nums text-slate-500">{x.percentOfPrevious}%</span></div>)}</div>}</section></>}
  </div></main>;
}
export default ProductAnalyticsPage;
