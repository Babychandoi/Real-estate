import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import type { AnalyticsDashboard, Metric } from '@/entities/analytics/api';
import { formatMetric, ProductAnalyticsPage } from './_admin.analytics';

const measured = (value: number, unit: Metric['unit'] = 'count'): Metric => ({
  status: 'MEASURED',
  value,
  unit,
  reason: null,
  numerator: null,
  denominator: null,
});
const missing = (reason: string, unit: Metric['unit'] = 'percent'): Metric => ({
  status: 'NOT_MEASURED',
  value: null,
  unit,
  reason,
  numerator: null,
  denominator: null,
});

const dashboard: AnalyticsDashboard = {
  window: { from: '2026-09-01', to: '2026-09-28', days: 28, timezone: 'Asia/Ho_Chi_Minh' },
  filters: { device: null, area: null, source: null },
  generatedAt: '2026-09-28T03:00:00Z',
  collection: { ingestionEnabled: false, webEvents: 0, webSessions: 0, scope: 'Chỉ gồm khách đã đồng ý.' },
  freshness: { latestWebEventAt: null, aggregatesComputedAt: null, rawLatency: 'ngay', aggregateLatency: 'mỗi giờ' },
  funnels: [
    {
      key: 'lead',
      title: 'Liên hệ và phản hồi',
      definition: 'Định nghĩa phễu.',
      source: 'database',
      steps: [
        { key: 'leads', label: 'Yêu cầu liên hệ', count: measured(0), fromPrevious: null },
        {
          key: 'responded',
          label: 'Đã phản hồi',
          count: measured(0),
          fromPrevious: missing('Chưa có mẫu để tính tỷ lệ.'),
        },
      ],
    },
  ],
  metrics: [
    {
      key: 'zeroResultRate',
      label: 'Tìm kiếm không có kết quả',
      group: 'search',
      definition: 'Lượt 0 kết quả / lượt tìm kiếm.',
      source: 'web',
      metric: missing('Thu thập sự kiện web đang tắt.'),
    },
    {
      key: 'confirmedAppointments',
      label: 'Lịch hẹn hai bên xác nhận',
      group: 'north-star',
      definition: 'Lịch hẹn xác nhận.',
      source: 'database',
      metric: measured(0),
    },
  ],
  cohorts: { status: 'NOT_MEASURED', reason: 'Thu thập sự kiện web đang tắt.', definition: 'Cohort.', rows: [] },
  webVitals: [],
  webVitalsReason: 'Thu thập sự kiện web đang tắt.',
  breakdowns: {},
  trend: [],
  alerts: [{ severity: 'WARNING', code: 'INGESTION_DISABLED', message: 'Thu thập đang tắt.' }],
};

describe('admin analytics page', () => {
  it('shows "Chưa đo" with its reason, distinct from a measured 0', async () => {
    const load = vi.fn(async () => dashboard);
    render(<ProductAnalyticsPage load={load} />);

    const notMeasured = (await screen.findByRole('heading', { name: 'Tìm kiếm không có kết quả' })).closest('article')!;
    expect(within(notMeasured).getByText('Chưa đo')).toBeInTheDocument();
    expect(within(notMeasured).getByText('Thu thập sự kiện web đang tắt.')).toBeInTheDocument();
    const zero = screen.getByRole('heading', { name: 'Lịch hẹn hai bên xác nhận' }).closest('article')!;
    expect(within(zero).getByText('0')).toBeInTheDocument();
    expect(within(zero).queryByText('Chưa đo')).toBeNull();
    expect(screen.getByRole('alert')).toHaveTextContent('Thu thập đang tắt.');
    expect(load).toHaveBeenCalledWith(expect.objectContaining({ from: expect.any(String), to: expect.any(String) }));
  });

  it('formats units and never turns a missing value into 0', () => {
    expect(formatMetric(missing('x'))).toBe('Chưa đo');
    expect(formatMetric(measured(12.5, 'percent'))).toBe('12,5%');
    expect(formatMetric(measured(0.051, 'score'))).toBe('0,051');
    expect(formatMetric(measured(249500, 'vnd'))).toMatch(/^249\.500/);
  });
});
