import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Download, Info, RefreshCw } from 'lucide-react';
import {
  fetchAnalyticsDashboard,
  type AnalyticsDashboard,
  type Breakdown,
  type DashboardQuery,
  type Funnel,
  type Metric,
  type NamedMetric,
} from '@/entities/analytics/api';
import { errorMessage } from '@/shared/api/errors';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { ErrorState } from '@/shared/ui/ErrorState';
import { FormField } from '@/shared/ui/FormField';
import { Select } from '@/shared/ui/Select';
import { LoadingStatus } from '@/shared/ui/Skeleton';
import { TextInput } from '@/shared/ui/TextInput';

const number = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const precise = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 });
const money = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' });

const UNIT_SUFFIX: Record<Metric['unit'], string> = {
  count: '',
  percent: '%',
  minutes: ' phút',
  hours: ' giờ',
  ratio: '',
  vnd: ' ₫',
  ms: ' ms',
  score: '',
};
const SOURCE_LABEL: Record<string, string> = {
  web: 'Sự kiện web',
  database: 'Dữ liệu nghiệp vụ',
  aggregate: 'Bảng tổng hợp',
  server: 'Sự kiện máy chủ',
};
const DEVICE_LABEL: Record<string, string> = {
  mobile: 'Điện thoại',
  tablet: 'Máy tính bảng',
  desktop: 'Máy tính',
  '': 'Không rõ',
  unknown: 'Không rõ',
};
const RATING: Record<string, { label: string; variant: 'success' | 'warning' | 'error' }> = {
  good: { label: 'Tốt', variant: 'success' },
  'needs-improvement': { label: 'Cần cải thiện', variant: 'warning' },
  poor: { label: 'Kém', variant: 'error' },
};
const GROUPS: { key: string; title: string }[] = [
  { key: 'north-star', title: 'Chỉ số trung tâm' },
  { key: 'funnel', title: 'Chuyển đổi' },
  { key: 'search', title: 'Tìm kiếm' },
  { key: 'sla', title: 'Phản hồi liên hệ (SLA)' },
  { key: 'quality', title: 'Chất lượng liên hệ' },
  { key: 'retention', title: 'Quay lại' },
  { key: 'supply', title: 'Nguồn cung' },
  { key: 'broker', title: 'Môi giới và gói dịch vụ' },
];

/** "Chưa đo" is a state, not a number: it never renders as 0. */
export function formatMetric(metric: Metric): string {
  if (metric.status === 'NOT_MEASURED' || metric.value == null) return 'Chưa đo';
  if (metric.unit === 'vnd') return `${money.format(metric.value)}${UNIT_SUFFIX.vnd}`;
  const format = metric.unit === 'score' || metric.unit === 'ratio' ? precise : number;
  return `${format.format(metric.value)}${UNIT_SUFFIX[metric.unit]}`;
}

function vnToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
}

function shiftDay(day: string, delta: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + delta);
  return date.toISOString().slice(0, 10);
}

function MetricValue({ metric, className = '' }: { metric: Metric; className?: string }) {
  const missing = metric.status === 'NOT_MEASURED';
  return (
    <span className={className}>
      <span className={missing ? 'text-on-surface-variant' : 'tabular-nums text-on-surface'}>
        {formatMetric(metric)}
      </span>
      {missing && metric.reason && (
        <span className="mt-1 block text-label text-on-surface-variant">{metric.reason}</span>
      )}
    </span>
  );
}

function MetricTile({ item }: { item: NamedMetric }) {
  const { metric } = item;
  return (
    <article className="rounded-xl border bg-white p-4" style={{ borderColor: 'var(--ndc-border)' }}>
      <h3 className="text-body-sm font-medium text-on-surface-variant">{item.label}</h3>
      <MetricValue metric={metric} className="mt-2 block text-2xl font-bold" />
      {metric.status === 'MEASURED' && metric.denominator != null && (
        <p className="mt-1 text-label tabular-nums text-on-surface-variant">
          {number.format(metric.numerator ?? 0)} / {number.format(metric.denominator)}
        </p>
      )}
      <details className="mt-2 text-label text-on-surface-variant">
        <summary className="flex min-h-11 cursor-pointer items-center">
          Định nghĩa · {SOURCE_LABEL[item.source] ?? item.source}
        </summary>
        <p className="mt-1">{item.definition}</p>
      </details>
    </article>
  );
}

function FunnelCard({ funnel }: { funnel: Funnel }) {
  const first = funnel.steps[0]?.count;
  const base = first?.status === 'MEASURED' && first.value ? first.value : 0;
  return (
    <section
      className="rounded-xl border bg-white p-5"
      style={{ borderColor: 'var(--ndc-border)' }}
      aria-labelledby={`funnel-${funnel.key}`}
    >
      <h2 id={`funnel-${funnel.key}`} className="text-lg font-bold text-on-surface">
        {funnel.title}
      </h2>
      <p className="mt-1 text-label text-on-surface-variant">
        {funnel.definition} Nguồn: {SOURCE_LABEL[funnel.source] ?? funnel.source}.
      </p>
      <ol className="mt-4 grid gap-3">
        {funnel.steps.map((step) => {
          const measured = step.count.status === 'MEASURED' && step.count.value != null;
          const width = measured && base > 0 ? Math.max(2, ((step.count.value ?? 0) / base) * 100) : 0;
          return (
            <li
              key={step.key}
              className="grid gap-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_minmax(0,auto)] sm:items-center"
            >
              <span className="text-body-sm font-medium text-on-surface">{step.label}</span>
              <span className="h-6 overflow-hidden rounded bg-surface-container" aria-hidden="true">
                {measured && (
                  <span
                    className="block h-full rounded bg-primary"
                    style={{ width: `${width}%` }}
                    title={formatMetric(step.count)}
                  />
                )}
              </span>
              <span className="min-w-0 text-body-sm [overflow-wrap:anywhere] sm:text-right">
                <MetricValue metric={step.count} className="font-semibold" />
                {step.fromPrevious && step.fromPrevious.status === 'MEASURED' && (
                  <span className="ml-2 text-label text-on-surface-variant">({formatMetric(step.fromPrevious)})</span>
                )}
              </span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

function BreakdownTable({ title, rows, label }: { title: string; rows: Breakdown[]; label: (key: string) => string }) {
  return (
    <section className="rounded-xl border bg-white p-5" style={{ borderColor: 'var(--ndc-border)' }}>
      <h3 className="font-bold text-on-surface">{title}</h3>
      {rows.length === 0 ? (
        <p className="mt-2 text-body-sm text-on-surface-variant">Chưa có số liệu tổng hợp trong khoảng này.</p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[22rem] text-left text-body-sm">
            <thead className="text-label text-on-surface-variant">
              <tr>
                <th scope="col" className="py-1 pr-2 font-medium">
                  Giá trị
                </th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">
                  Phiên tìm kiếm
                </th>
                <th scope="col" className="py-1 pr-2 text-right font-medium">
                  Lượt xem tin
                </th>
                <th scope="col" className="py-1 text-right font-medium">
                  Mở form liên hệ
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.key} className="border-t" style={{ borderColor: 'var(--ndc-border)' }}>
                  <th scope="row" className="py-1.5 pr-2 font-medium">
                    {label(row.key)}
                  </th>
                  <td className="py-1.5 pr-2 text-right tabular-nums">{number.format(row.searchSessions)}</td>
                  <td className="py-1.5 pr-2 text-right tabular-nums">{number.format(row.detailViews)}</td>
                  <td className="py-1.5 text-right tabular-nums">{number.format(row.leadForms)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function toCsv(data: AnalyticsDashboard): string {
  const rows: string[][] = [['Nhóm', 'Chỉ số', 'Giá trị', 'Trạng thái', 'Lý do', 'Định nghĩa']];
  for (const funnel of data.funnels) {
    for (const step of funnel.steps) {
      rows.push([
        funnel.title,
        step.label,
        step.count.value == null ? '' : String(step.count.value),
        step.count.status,
        step.count.reason ?? '',
        funnel.definition,
      ]);
    }
  }
  for (const item of data.metrics) {
    rows.push([
      item.group,
      item.label,
      item.metric.value == null ? '' : String(item.metric.value),
      item.metric.status,
      item.metric.reason ?? '',
      item.definition,
    ]);
  }
  return rows.map((row) => row.map((value) => `"${value.replaceAll('"', '""')}"`).join(',')).join('\n');
}

export function ProductAnalyticsPage({ load = fetchAnalyticsDashboard }: { load?: typeof fetchAnalyticsDashboard }) {
  const today = vnToday();
  const [query, setQuery] = useState<DashboardQuery>({ from: shiftDay(today, -27), to: today });
  const [draft, setDraft] = useState<DashboardQuery>(query);
  const [data, setData] = useState<AnalyticsDashboard>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await load(query));
    } catch (failure) {
      setError(errorMessage(failure, 'Không thể tải số liệu phân tích. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }, [load, query]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const exportCsv = () => {
    if (!data) return;
    const url = URL.createObjectURL(new Blob([`\uFEFF${toCsv(data)}`], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `phan-tich-${data.window.from}-${data.window.to}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const presets = [7, 28, 90];

  return (
    <section className="pb-2" data-ready={loading && !data ? undefined : 'true'}>
      <div>
        <header className="mb-6 flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-on-surface">Phân tích sản phẩm</h1>
            <p className="mt-2 max-w-2xl text-body-sm text-on-surface-variant">
              Phễu, chỉ số trung tâm, cohort và hiệu năng trang. “Chưa đo” nghĩa là chưa có dữ liệu để tính — khác với
              0.
            </p>
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => void refresh()}
              disabled={loading}
              leftIcon={<RefreshCw className="h-4 w-4" aria-hidden="true" />}
            >
              Tải lại
            </Button>
            <Button
              onClick={exportCsv}
              disabled={!data || loading}
              leftIcon={<Download className="h-4 w-4" aria-hidden="true" />}
            >
              Xuất CSV
            </Button>
          </div>
        </header>

        <form
          className="mb-6 grid gap-3 rounded-xl border bg-white p-4 sm:grid-cols-2 lg:grid-cols-6 lg:items-end"
          style={{ borderColor: 'var(--ndc-border)' }}
          aria-label="Bộ lọc"
          onSubmit={(event) => {
            event.preventDefault();
            setQuery({ ...draft });
          }}
        >
          <FormField label="Từ ngày">
            {(control) => (
              <TextInput
                {...control}
                type="date"
                value={draft.from ?? ''}
                max={draft.to}
                onChange={(event) => setDraft({ ...draft, from: event.target.value })}
              />
            )}
          </FormField>
          <FormField label="Đến ngày">
            {(control) => (
              <TextInput
                {...control}
                type="date"
                value={draft.to ?? ''}
                max={today}
                onChange={(event) => setDraft({ ...draft, to: event.target.value })}
              />
            )}
          </FormField>
          <FormField label="Thiết bị">
            {(control) => (
              <Select
                {...control}
                value={draft.device ?? ''}
                onChange={(event) => setDraft({ ...draft, device: event.target.value || undefined })}
                options={[
                  { value: '', label: 'Tất cả' },
                  { value: 'mobile', label: 'Điện thoại' },
                  { value: 'tablet', label: 'Máy tính bảng' },
                  { value: 'desktop', label: 'Máy tính' },
                ]}
              />
            )}
          </FormField>
          <FormField label="Mã quận/huyện">
            {(control) => (
              <TextInput
                {...control}
                inputMode="numeric"
                value={draft.area ?? ''}
                placeholder="Ví dụ 005"
                onChange={(event) =>
                  setDraft({ ...draft, area: event.target.value.replace(/\D/g, '').slice(0, 5) || undefined })
                }
              />
            )}
          </FormField>
          <FormField label="Nguồn truy cập (utm_source)">
            {(control) => (
              <TextInput
                {...control}
                value={draft.source ?? ''}
                placeholder="google, zalo, (direct)"
                onChange={(event) =>
                  setDraft({ ...draft, source: event.target.value.trim().toLowerCase() || undefined })
                }
              />
            )}
          </FormField>
          <div className="flex flex-wrap gap-2">
            <Button type="submit">Áp dụng</Button>
            {presets.map((days) => (
              <Button
                key={days}
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  const next = { ...draft, from: shiftDay(today, -(days - 1)), to: today };
                  setDraft(next);
                  setQuery(next);
                }}
              >
                {days} ngày
              </Button>
            ))}
          </div>
        </form>

        {loading && !data && <LoadingStatus label="Đang tải số liệu…" />}
        {error && <ErrorState title="Không tải được số liệu" description={error} onRetry={() => void refresh()} />}

        {data && (
          <div className="grid gap-6" aria-busy={loading}>
            <section
              className="rounded-xl border bg-white p-4 text-body-sm text-on-surface-variant"
              style={{ borderColor: 'var(--ndc-border)' }}
            >
              <p>
                <strong className="text-on-surface">
                  {data.window.from} đến {data.window.to}
                </strong>{' '}
                ({data.window.days} ngày, giờ Việt Nam). {data.collection.scope} Sự kiện web trong khoảng:{' '}
                <span className="tabular-nums">{number.format(data.collection.webEvents)}</span> ·{' '}
                {data.collection.ingestionEnabled ? 'đang thu thập' : 'thu thập đang tắt'}.
              </p>
              <p className="mt-1">
                Sự kiện web mới nhất:{' '}
                {data.freshness.latestWebEventAt
                  ? dateTime.format(new Date(data.freshness.latestWebEventAt))
                  : 'chưa có'}{' '}
                · Tổng hợp gần nhất:{' '}
                {data.freshness.aggregatesComputedAt
                  ? dateTime.format(new Date(data.freshness.aggregatesComputedAt))
                  : 'chưa chạy'}
                .
              </p>
              <details className="mt-1">
                <summary className="flex min-h-11 cursor-pointer items-center">Độ trễ số liệu</summary>
                <p className="mt-1">{data.freshness.rawLatency}</p>
                <p>{data.freshness.aggregateLatency}</p>
              </details>
            </section>

            {data.alerts.length > 0 && (
              <section aria-label="Cảnh báo" className="grid gap-2">
                {data.alerts.map((alert) => (
                  <p
                    key={alert.code + alert.message}
                    role={alert.severity === 'WARNING' ? 'alert' : undefined}
                    className={`flex items-start gap-2 rounded-lg border p-3 text-body-sm ${
                      alert.severity === 'WARNING'
                        ? 'border-warning/30 bg-warning-container text-warning-on-container'
                        : 'bg-info-container text-info-on-container'
                    }`}
                  >
                    {alert.severity === 'WARNING' ? (
                      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    ) : (
                      <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    )}
                    <span>
                      <span className="font-semibold">{alert.severity === 'WARNING' ? 'Cảnh báo: ' : 'Lưu ý: '}</span>
                      {alert.message}
                    </span>
                  </p>
                ))}
              </section>
            )}

            {GROUPS.map((group) => {
              const items = data.metrics.filter((item) => item.group === group.key);
              if (!items.length) return null;
              return (
                <section key={group.key} aria-labelledby={`group-${group.key}`}>
                  <h2 id={`group-${group.key}`} className="mb-3 text-lg font-bold text-on-surface">
                    {group.title}
                  </h2>
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                    {items.map((item) => (
                      <MetricTile key={item.key} item={item} />
                    ))}
                  </div>
                </section>
              );
            })}

            <div className="grid gap-4 lg:grid-cols-2">
              {data.funnels.map((funnel) => (
                <FunnelCard key={funnel.key} funnel={funnel} />
              ))}
            </div>

            <section
              className="rounded-xl border bg-white p-5"
              style={{ borderColor: 'var(--ndc-border)' }}
              aria-labelledby="cohorts"
            >
              <h2 id="cohorts" className="text-lg font-bold text-on-surface">
                Cohort theo tuần
              </h2>
              <p className="mt-1 text-label text-on-surface-variant">{data.cohorts.definition}</p>
              {data.cohorts.status === 'NOT_MEASURED' || data.cohorts.rows.length === 0 ? (
                <p className="mt-3 text-body-sm text-on-surface-variant">
                  {data.cohorts.status === 'NOT_MEASURED' ? 'Chưa đo: ' : ''}
                  {data.cohorts.reason}
                </p>
              ) : (
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full min-w-[28rem] text-left text-body-sm">
                    <thead className="text-label text-on-surface-variant">
                      <tr>
                        <th scope="col" className="py-1 pr-2 font-medium">
                          Tuần bắt đầu
                        </th>
                        <th scope="col" className="py-1 pr-2 text-right font-medium">
                          Thiết bị mới
                        </th>
                        {Array.from(
                          { length: Math.max(...data.cohorts.rows.map((row) => row.retentionPercent.length)) },
                          (_, index) => (
                            <th key={index} scope="col" className="py-1 pr-2 text-right font-medium">
                              Tuần {index}
                            </th>
                          ),
                        )}
                      </tr>
                    </thead>
                    <tbody>
                      {data.cohorts.rows.map((row) => (
                        <tr key={row.week} className="border-t" style={{ borderColor: 'var(--ndc-border)' }}>
                          <th scope="row" className="py-1.5 pr-2 font-medium">
                            {row.week}
                          </th>
                          <td className="py-1.5 pr-2 text-right tabular-nums">{number.format(row.devices)}</td>
                          {row.retentionPercent.map((value, index) => (
                            <td key={index} className="py-1.5 pr-2 text-right tabular-nums">
                              {value == null ? '—' : `${number.format(value)}%`}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section
              className="rounded-xl border bg-white p-5"
              style={{ borderColor: 'var(--ndc-border)' }}
              aria-labelledby="vitals"
            >
              <h2 id="vitals" className="text-lg font-bold text-on-surface">
                Hiệu năng thực tế (Core Web Vitals, p75)
              </h2>
              <p className="mt-1 text-label text-on-surface-variant">
                Đo trên trình duyệt của một mẫu phiên đã đồng ý phân tích. Mục tiêu: LCP ≤ 2,5 s, INP ≤ 200 ms, CLS ≤
                0,1.
              </p>
              {data.webVitals.length === 0 ? (
                <p className="mt-3 text-body-sm text-on-surface-variant">
                  {data.webVitalsReason ? `Chưa đo: ${data.webVitalsReason}` : 'Chưa có mẫu đo trong khoảng này.'}
                </p>
              ) : (
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full min-w-[26rem] text-left text-body-sm">
                    <thead className="text-label text-on-surface-variant">
                      <tr>
                        <th scope="col" className="py-1 pr-2 font-medium">
                          Chỉ số
                        </th>
                        <th scope="col" className="py-1 pr-2 font-medium">
                          Thiết bị
                        </th>
                        <th scope="col" className="py-1 pr-2 text-right font-medium">
                          p75
                        </th>
                        <th scope="col" className="py-1 pr-2 text-right font-medium">
                          Số mẫu
                        </th>
                        <th scope="col" className="py-1 font-medium">
                          Đánh giá
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.webVitals.map((vital) => (
                        <tr
                          key={vital.metric + vital.device}
                          className="border-t"
                          style={{ borderColor: 'var(--ndc-border)' }}
                        >
                          <th scope="row" className="py-1.5 pr-2 font-medium">
                            {vital.metric}
                          </th>
                          <td className="py-1.5 pr-2">{DEVICE_LABEL[vital.device] ?? vital.device}</td>
                          <td className="py-1.5 pr-2 text-right tabular-nums">{formatMetric(vital.p75)}</td>
                          <td className="py-1.5 pr-2 text-right tabular-nums">{number.format(vital.samples)}</td>
                          <td className="py-1.5">
                            <Badge variant={RATING[vital.rating].variant}>{RATING[vital.rating].label}</Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section aria-labelledby="breakdowns">
              <h2 id="breakdowns" className="mb-3 text-lg font-bold text-on-surface">
                Theo nguồn, thiết bị, khu vực
              </h2>
              {Object.keys(data.breakdowns).length === 0 ? (
                <p className="text-body-sm text-on-surface-variant">Chưa đo: chưa có sự kiện web trong khoảng này.</p>
              ) : (
                <div className="grid gap-4 lg:grid-cols-3">
                  <BreakdownTable
                    title="Nguồn truy cập"
                    rows={data.breakdowns.source ?? []}
                    label={(key) => (key === '(direct)' ? 'Trực tiếp / không rõ' : key)}
                  />
                  <BreakdownTable
                    title="Thiết bị"
                    rows={data.breakdowns.device ?? []}
                    label={(key) => DEVICE_LABEL[key] ?? key}
                  />
                  <BreakdownTable
                    title="Khu vực (mã quận/huyện)"
                    rows={data.breakdowns.area ?? []}
                    label={(key) => key || 'Không gắn khu vực'}
                  />
                </div>
              )}
            </section>

            {data.trend.length > 0 && (
              <section
                className="rounded-xl border bg-white p-5"
                style={{ borderColor: 'var(--ndc-border)' }}
                aria-labelledby="trend"
              >
                <h2 id="trend" className="text-lg font-bold text-on-surface">
                  Theo ngày
                </h2>
                <div className="mt-3 max-h-96 overflow-auto">
                  <table className="w-full min-w-[26rem] text-left text-body-sm">
                    <thead className="sticky top-0 bg-white text-label text-on-surface-variant">
                      <tr>
                        <th scope="col" className="py-1 pr-2 font-medium">
                          Ngày
                        </th>
                        <th scope="col" className="py-1 pr-2 text-right font-medium">
                          Tìm kiếm
                        </th>
                        <th scope="col" className="py-1 pr-2 text-right font-medium">
                          Xem tin
                        </th>
                        <th scope="col" className="py-1 pr-2 text-right font-medium">
                          Mở form
                        </th>
                        <th scope="col" className="py-1 text-right font-medium">
                          Liên hệ gửi
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.trend.map((day) => (
                        <tr key={day.day} className="border-t" style={{ borderColor: 'var(--ndc-border)' }}>
                          <th scope="row" className="py-1 pr-2 font-medium">
                            {day.day}
                          </th>
                          <td className="py-1 pr-2 text-right tabular-nums">{number.format(day.searches)}</td>
                          <td className="py-1 pr-2 text-right tabular-nums">{number.format(day.detailViews)}</td>
                          <td className="py-1 pr-2 text-right tabular-nums">{number.format(day.leadForms)}</td>
                          <td className="py-1 text-right tabular-nums">{number.format(day.leads)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

export default ProductAnalyticsPage;
