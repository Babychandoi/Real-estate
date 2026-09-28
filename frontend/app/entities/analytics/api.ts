import { apiClient } from '@/shared/api/client';

/** `NOT_MEASURED` (value null) is never the same as a measured 0. */
export interface Metric {
  status: 'MEASURED' | 'NOT_MEASURED';
  value: number | null;
  unit: 'count' | 'percent' | 'minutes' | 'hours' | 'ratio' | 'vnd' | 'ms' | 'score';
  reason: string | null;
  numerator: number | null;
  denominator: number | null;
}

export interface FunnelStep {
  key: string;
  label: string;
  count: Metric;
  fromPrevious: Metric | null;
}

export interface Funnel {
  key: string;
  title: string;
  definition: string;
  source: string;
  steps: FunnelStep[];
}

export interface NamedMetric {
  key: string;
  label: string;
  group: string;
  definition: string;
  source: string;
  metric: Metric;
}

export interface Breakdown {
  key: string;
  searchSessions: number;
  detailViews: number;
  leadForms: number;
}

export interface TrendDay {
  day: string;
  searches: number;
  detailViews: number;
  leadForms: number;
  leads: number;
}

export interface VitalRow {
  metric: 'LCP' | 'INP' | 'CLS' | 'TTFB';
  device: string;
  p75: Metric;
  samples: number;
  goodAtMost: number;
  poorAbove: number;
  rating: 'good' | 'needs-improvement' | 'poor';
}

export interface AnalyticsDashboard {
  window: { from: string; to: string; days: number; timezone: string };
  filters: { device: string | null; area: string | null; source: string | null };
  generatedAt: string;
  collection: { ingestionEnabled: boolean; webEvents: number; webSessions: number; scope: string };
  freshness: {
    latestWebEventAt: string | null;
    aggregatesComputedAt: string | null;
    rawLatency: string;
    aggregateLatency: string;
  };
  funnels: Funnel[];
  metrics: NamedMetric[];
  cohorts: {
    status: Metric['status'];
    reason: string | null;
    definition: string;
    rows: { week: string; devices: number; retentionPercent: (number | null)[] }[];
  };
  webVitals: VitalRow[];
  webVitalsReason: string | null;
  breakdowns: Partial<Record<'source' | 'device' | 'area', Breakdown[]>>;
  trend: TrendDay[];
  alerts: { severity: 'WARNING' | 'INFO'; code: string; message: string }[];
}

export interface DashboardQuery {
  from?: string;
  to?: string;
  device?: string;
  area?: string;
  source?: string;
}

export function fetchAnalyticsDashboard(query: DashboardQuery): Promise<AnalyticsDashboard> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) if (value) params.set(key, value);
  const search = params.toString();
  return apiClient<AnalyticsDashboard>(`/analytics/dashboard${search ? `?${search}` : ''}`);
}
