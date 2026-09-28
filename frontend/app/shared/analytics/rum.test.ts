import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MetricType } from 'web-vitals';
import { setAnalyticsConsent } from './consent';
import {
  configuredSampleRate,
  isSessionSampled,
  resetRumForTests,
  routePattern,
  RUM_SAMPLE_KEY,
  startRumWhenConsented,
} from './rum';

type Callback = (metric: MetricType) => void;

function fakeVitals() {
  const callbacks: Callback[] = [];
  const register = (callback: Callback) => callbacks.push(callback);
  return {
    callbacks,
    module: { onLCP: register, onINP: register, onCLS: register, onTTFB: register } as never,
  };
}

let stop: (() => void) | undefined;
beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  resetRumForTests();
});
afterEach(() => stop?.());

describe('RUM (web vitals)', () => {
  it('loads nothing before consent and starts once consent is granted', async () => {
    const vitals = fakeVitals();
    const load = vi.fn(async () => vitals.module);
    const report = vi.fn();
    stop = startRumWhenConsented({ rate: 1, load, report, route: () => '/listings/:listingId' });
    expect(load).not.toHaveBeenCalled();

    setAnalyticsConsent('granted');
    await vi.waitFor(() => expect(vitals.callbacks).toHaveLength(4));
    vitals.callbacks[0]({ name: 'LCP', value: 1234.5, rating: 'good' } as MetricType);
    expect(report).toHaveBeenCalledWith('LCP', 1234.5, 'good', '/listings/:listingId');

    setAnalyticsConsent('granted');
    expect(load).toHaveBeenCalledTimes(1);
    setAnalyticsConsent('denied');
    vitals.callbacks[2]({ name: 'CLS', value: 0.02, rating: 'good' } as MetricType);
    expect(report).toHaveBeenCalledTimes(1);
  });

  it('samples per session and never loads for an unsampled session', () => {
    setAnalyticsConsent('granted');
    const load = vi.fn();
    stop = startRumWhenConsented({ rate: 0.25, random: () => 0.9, load });
    expect(load).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(RUM_SAMPLE_KEY)).toBe('0');
    expect(isSessionSampled(1, () => 0)).toBe(false);
    sessionStorage.clear();
    expect(isSessionSampled(0.25, () => 0.1)).toBe(true);
  });

  it('reads the configured rate within 0..1', () => {
    expect(configuredSampleRate(undefined)).toBe(0.25);
    expect(configuredSampleRate('0.5')).toBe(0.5);
    expect(configuredSampleRate('7')).toBe(1);
    expect(configuredSampleRate('abc')).toBe(0.25);
  });

  it('reports the route pattern, never the concrete path', () => {
    expect(routePattern([{ route: { path: '/' } }, { route: { path: 'listings/:listingId' } }])).toBe(
      '/listings/:listingId',
    );
    expect(
      routePattern([{ route: {} }, { route: { path: '/2026/nhadatchuan/admin' } }, { route: { path: 'analytics' } }]),
    ).toBe('/2026/nhadatchuan/admin/analytics');
    expect(routePattern([{ route: { path: '/admin/*' } }])).toBe('/admin/:splat');
    expect(routePattern([])).toBe('/');
  });
});
