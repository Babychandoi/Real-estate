import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, BarChart3, Download, RefreshCw } from 'lucide-react';
import { fetchFunnelAnalytics } from '@/entities/lead/api/leadApi';
import type { FunnelAnalytics } from '@/entities/lead/model/types';
import { Button } from '@/shared/ui/Button';

const formatNumber = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
const formatPercent = (value: number) =>
  `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}%`;

export function ProductAnalyticsPage() {
  const [data, setData] = useState<FunnelAnalytics>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await fetchFunnelAnalytics());
    } catch {
      setError('Không thể tải số liệu phân tích từ máy chủ. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const maxCount = useMemo(() => Math.max(1, ...(data?.steps.map((step) => step.count) ?? [])), [data]);

  const exportCsv = () => {
    if (!data) return;
    const rows = [
      ['Bước', 'Tên', 'Số lượng', 'Tỷ lệ so với bước trước'],
      ...data.steps.map((step) => [String(step.stepIndex), step.stepName, String(step.count), String(step.percentage)]),
    ];
    const content = rows.map((row) => row.map((value) => `"${value.replaceAll('"', '""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'bao-cao-phan-tich.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const metrics = data
    ? [
        { label: 'Lead đã nhận', value: formatNumber(data.leadsSubmitted), note: 'Tổng yêu cầu liên hệ' },
        { label: 'Đã liên hệ', value: formatNumber(data.contactedCount), note: 'Đã xử lý hoặc hẹn xem' },
        { label: 'Đã chốt', value: formatNumber(data.dealsClosed), note: 'Lead hoàn tất giao dịch' },
        { label: 'Tỷ lệ chốt', value: formatPercent(data.conversionRatePercent), note: 'Tính trên tổng lead' },
      ]
    : [];

  return (
    <main className="min-h-screen bg-slate-50 py-8">
      <div className="container mx-auto max-w-6xl px-4">
        <header className="mb-7 flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-950">Phân tích chuyển đổi</h1>
            <p className="mt-2 max-w-2xl text-sm text-slate-600">
              Số liệu tổng hợp trực tiếp từ hoạt động đã ghi nhận trong hệ thống.
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => void load()} disabled={loading}>
              <RefreshCw className={`mr-1 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              Tải lại
            </Button>
            <Button onClick={exportCsv} disabled={!data || loading}>
              <Download className="mr-1 h-4 w-4" />
              Xuất CSV
            </Button>
          </div>
        </header>

        {loading && (
          <section
            className="rounded-xl border border-slate-200 bg-white p-10 text-center text-slate-600"
            role="status"
          >
            Đang tải số liệu…
          </section>
        )}

        {!loading && error && (
          <section
            className="flex flex-col items-start gap-4 rounded-xl border border-rose-200 bg-rose-50 p-6 text-rose-800"
            role="alert"
          >
            <div className="flex gap-3">
              <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
              <span>{error}</span>
            </div>
            <Button variant="outline" onClick={() => void load()}>
              Thử lại
            </Button>
          </section>
        )}

        {!loading && data && (
          <>
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Tổng quan chuyển đổi">
              {metrics.map((metric) => (
                <article key={metric.label} className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-sm font-medium text-slate-600">{metric.label}</p>
                  <p className="mt-2 text-3xl font-bold tabular-nums text-slate-950">{metric.value}</p>
                  <p className="mt-2 text-xs text-slate-500">{metric.note}</p>
                </article>
              ))}
            </section>

            <section className="mt-6 overflow-hidden rounded-xl border border-slate-200 bg-white">
              <div className="border-b border-slate-200 p-5">
                <div className="flex items-center gap-2">
                  <BarChart3 className="h-5 w-5 text-emerald-700" />
                  <h2 className="text-lg font-bold text-slate-950">Phễu chuyển đổi</h2>
                </div>
                <p className="mt-1 text-sm text-slate-500">Chiều dài thanh thể hiện quy mô tương đối giữa các bước.</p>
              </div>

              {data.steps.length === 0 ? (
                <div className="p-10 text-center">
                  <p className="font-medium text-slate-700">Chưa có dữ liệu chuyển đổi</p>
                  <p className="mt-1 text-sm text-slate-500">Biểu đồ sẽ xuất hiện khi hệ thống ghi nhận hoạt động.</p>
                </div>
              ) : (
                <div className="space-y-5 p-5 sm:p-6" role="img" aria-label="Biểu đồ phễu chuyển đổi">
                  {data.steps.map((step) => {
                    const isUntracked = step.stepName.toLocaleLowerCase('vi-VN').includes('chưa thu thập');
                    const width = step.count === 0 ? 0 : Math.max(6, (step.count / maxCount) * 100);
                    return (
                      <div
                        key={step.stepIndex}
                        className="grid min-w-0 gap-2 sm:grid-cols-[minmax(190px,0.8fr)_minmax(240px,2fr)_110px] sm:items-center"
                      >
                        <div className="min-w-0">
                          <p className="font-medium text-slate-800">
                            {step.stepName.replace(/\s*\(chưa thu thập\)\s*/i, '')}
                          </p>
                          {isUntracked && <p className="text-xs text-amber-700">Chưa bật thu thập dữ liệu</p>}
                        </div>
                        <div className="h-8 overflow-hidden rounded-md bg-slate-100" aria-hidden="true">
                          <div
                            className="flex h-full items-center justify-end rounded-md bg-emerald-600 px-2 text-xs font-bold tabular-nums text-white transition-[width] duration-500"
                            style={{ width: `${width}%` }}
                          >
                            {step.count > 0 && formatNumber(step.count)}
                          </div>
                        </div>
                        <div className="flex justify-between gap-3 text-sm tabular-nums sm:block sm:text-right">
                          <span className="font-semibold text-slate-900 sm:hidden">{formatNumber(step.count)}</span>
                          <span className="text-slate-500">
                            {step.stepIndex === 1 || isUntracked ? '—' : formatPercent(step.percentage)}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </main>
  );
}

export default ProductAnalyticsPage;
