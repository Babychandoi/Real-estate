/**
 * Real-user monitoring of Core Web Vitals (F15.3, DS-15): LCP, INP, CLS and TTFB from the `web-vitals` library are sent
 * as `web_vital` events through `track()`. Only for visitors who granted analytics consent, and only for a sample of
 * sessions (`VITE_RUM_SAMPLE_RATE`, default 0.25). `web-vitals` is loaded on demand, so the shell does not carry it.
 * Each metric is reported once per page load (its final value), tagged with the route pattern
 * (`/listings/:listingId`), never the concrete path.
 */
import type { MetricType, onCLS, onINP, onLCP, onTTFB } from 'web-vitals';
import { getAnalyticsConsent, onAnalyticsConsentChange } from './consent';
import { track } from './track';

export const RUM_SAMPLE_KEY = 'bds.rum.sampled';
const DEFAULT_RATE = 0.25;
const ROUTE = /^\/[A-Za-z0-9/:_.-]{0,99}$/;

interface VitalsModule {
  onLCP: typeof onLCP;
  onINP: typeof onINP;
  onCLS: typeof onCLS;
  onTTFB: typeof onTTFB;
}

export interface RumOptions {
  /** Sample rate 0..1 (default from VITE_RUM_SAMPLE_RATE). */
  rate?: number;
  random?: () => number;
  load?: () => Promise<VitalsModule>;
  report?: (
    metric: 'LCP' | 'INP' | 'CLS' | 'TTFB',
    value: number,
    rating: MetricType['rating'],
    route?: string,
  ) => void;
  /** Route pattern of the page being measured, e.g. from the router's current matches. */
  route?: () => string | undefined;
}

export function configuredSampleRate(raw: unknown = import.meta.env.VITE_RUM_SAMPLE_RATE): number {
  if (raw === undefined || raw === null || raw === '') return DEFAULT_RATE;
  const value = Number(raw);
  return Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : DEFAULT_RATE;
}

/** Sampling is decided once per browser session so a sampled session reports every page it loads. */
export function isSessionSampled(rate: number, random: () => number = Math.random): boolean {
  try {
    const stored = sessionStorage.getItem(RUM_SAMPLE_KEY);
    if (stored === '1' || stored === '0') return stored === '1';
    const sampled = random() < rate;
    sessionStorage.setItem(RUM_SAMPLE_KEY, sampled ? '1' : '0');
    return sampled;
  } catch {
    return false;
  }
}

/** Joins the matched route paths into one pattern the event catalog accepts ("*" becomes ":splat"). */
export function routePattern(matches: ReadonlyArray<{ route: { path?: string } }>): string | undefined {
  const parts = matches
    .map((match) => match.route.path ?? '')
    .filter(Boolean)
    .map((path) => path.replace(/\*/g, ':splat'));
  const joined = `/${parts.join('/')}`.replace(/\/{2,}/g, '/');
  const pattern = joined.length > 1 ? joined.replace(/\/$/, '') : joined;
  return ROUTE.test(pattern) ? pattern : undefined;
}

let started = false;

/** Starts RUM once per page load if consent is (or later becomes) granted and the session is sampled. */
export function startRumWhenConsented(options: RumOptions = {}): () => void {
  const tryStart = () => {
    if (started || getAnalyticsConsent() !== 'granted') return;
    if (!isSessionSampled(options.rate ?? configuredSampleRate(), options.random)) return;
    started = true;
    const landingRoute = options.route?.();
    const report =
      options.report ??
      ((metric, value, rating, route) => track('web_vital', { metric, value, rating, ...(route ? { route } : {}) }));
    const load = options.load ?? (() => import('web-vitals'));
    void load()
      .then((vitals) => {
        const send = (metric: MetricType) => {
          // A visitor may withdraw consent after the page loaded: late metrics are then not sent.
          if (getAnalyticsConsent() !== 'granted') return;
          report(metric.name as 'LCP' | 'INP' | 'CLS' | 'TTFB', Math.max(0, metric.value), metric.rating, landingRoute);
        };
        vitals.onLCP(send);
        vitals.onINP(send);
        vitals.onCLS(send);
        vitals.onTTFB(send);
      })
      .catch(() => {
        started = false;
      });
  };
  tryStart();
  return onAnalyticsConsentChange((consent) => {
    if (consent === 'granted') tryStart();
  });
}

/** Test hook. */
export function resetRumForTests(): void {
  started = false;
}
