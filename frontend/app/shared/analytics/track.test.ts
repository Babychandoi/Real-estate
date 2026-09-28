import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { MAX_LISTING_IDS } from './catalog';
import {
  ANONYMOUS_ID_KEY,
  CONSENT_POLICY_VERSION,
  CONSENT_STORAGE_KEY,
  CONSENT_VERSION_KEY,
  getAnalyticsConsent,
  getStoredAnalyticsConsent,
  SESSION_KEY,
  UTM_KEY,
  setAnalyticsConsent,
} from './consent';
import {
  captureLandingContext,
  createAnalyticsClient,
  resetLandingContextForTests,
  type AnalyticsClient,
  type EventBatch,
} from './track';

const LISTING = '5b1d6f0e-2f55-4c1e-9d0a-2a6f5d7e8c01';
const OTHER = '6c2e7f1f-3a66-4d2f-8e1b-3b7a6e8f9d02';
const HASH = '0123456789abcdef0123456789abcdef';
const detail = { purpose: 'SALE', propertyType: 'APARTMENT', district: '005' } as const;

let ids = 0;
let sent: EventBatch[];
let send: Mock<(body: string) => Promise<Response>>;
let beacon: Mock<(url: string, body: Blob) => boolean>;
let client: AnalyticsClient;

function makeClient(overrides: Parameters<typeof createAnalyticsClient>[0] = {}) {
  client = createAnalyticsClient({
    send,
    beacon,
    randomId: () => `id-${++ids}`,
    now: () => new Date('2026-09-01T03:00:00Z'),
    ...overrides,
  });
  return client;
}

beforeEach(() => {
  vi.useFakeTimers();
  localStorage.clear();
  sessionStorage.clear();
  ids = 0;
  sent = [];
  send = vi.fn(async (body: string) => {
    sent.push(JSON.parse(body) as EventBatch);
    return new Response(null, { status: 202 });
  });
  beacon = vi.fn((_url: string, _body: Blob) => true);
  window.history.replaceState(null, '', '/listings/can-ho-2pn?utm_source=zalo&utm_campaign=thu-9&q=bi-mat');
  resetLandingContextForTests();
  // Most tests exercise delivery for a visitor who agreed; the consent tests below clear it first.
  setAnalyticsConsent('granted');
});

afterEach(() => {
  client?.dispose();
  vi.useRealTimers();
});

describe('track()', () => {
  it('ignores names outside the web catalog, including server-only events', async () => {
    makeClient();
    // @ts-expect-error — not a web event
    client.track('lead_submitted', { leadId: 'x' });
    // @ts-expect-error — unknown name
    client.track('page_scrolled', {});
    await vi.runAllTimersAsync();
    expect(client.pending()).toBe(0);
    expect(send).not.toHaveBeenCalled();
  });

  it('sends only catalogued properties, caps id lists at 48 and drops nulls the server would refuse', async () => {
    makeClient();
    const listingIds = Array.from({ length: 60 }, () => LISTING);
    client.track('search_results_viewed', {
      filterHash: HASH,
      listingIds,
      offset: 0,
      // @ts-expect-error — extra keys never leave the page
      email: 'khach@example.invalid',
    });
    client.track('search_performed', { filterHash: HASH, purpose: 'RENT', resultCount: null, zeroResult: true });
    // @ts-expect-error — district cannot be null for the server; the client drops it instead of losing the batch
    client.track('listing_detail_viewed', { purpose: 'SALE', district: null }, { listingId: LISTING });
    await vi.runAllTimersAsync();

    const [viewed, performed, detailEvent] = sent[0].events;
    expect(viewed.properties).toEqual({ filterHash: HASH, listingIds: listingIds.slice(0, 48), offset: 0 });
    expect(performed.properties).toEqual({ filterHash: HASH, purpose: 'RENT', resultCount: null, zeroResult: true });
    expect(detailEvent.properties).toEqual({ purpose: 'SALE' });
  });

  it('requires a listing id (a UUID) for listing events', async () => {
    makeClient();
    // @ts-expect-error — listing events need { listingId }
    client.track('listing_detail_viewed', detail);
    client.track('lead_form_opened', { requestType: 'VIEWING' }, { listingId: 'not-a-uuid' });
    client.track('lead_form_opened', { requestType: 'VIEWING' }, { listingId: OTHER });
    await vi.runAllTimersAsync();
    expect(sent).toHaveLength(1);
    expect(sent[0].events.map((event) => [event.name, event.listingId])).toEqual([['lead_form_opened', OTHER]]);
  });

  it('before consent (undecided, denied or given for an older policy) nothing is queued, sent or stored', async () => {
    for (const setup of [
      () => localStorage.clear(),
      () => setAnalyticsConsent('denied'),
      () => {
        localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
        localStorage.setItem(CONSENT_VERSION_KEY, '2020-01-01');
      },
    ]) {
      localStorage.clear();
      sessionStorage.clear();
      setup();
      makeClient();
      client.track('listing_detail_viewed', detail, { listingId: LISTING });
      expect(client.pending()).toBe(0);
      window.dispatchEvent(new Event('pagehide'));
      await vi.runAllTimersAsync();
      client.dispose();
    }
    expect(send).not.toHaveBeenCalled();
    expect(beacon).not.toHaveBeenCalled();
    expect(localStorage.getItem(ANONYMOUS_ID_KEY)).toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
    expect(sessionStorage.getItem(UTM_KEY)).toBeNull();
  });

  it('with consent sends the event envelope the server expects', async () => {
    makeClient();
    client.track('listing_detail_viewed', detail, { listingId: LISTING });
    await vi.runAllTimersAsync();
    expect(sent[0].consent).toBe('granted');
    expect(sent[0].events[0]).toMatchObject({
      name: 'listing_detail_viewed',
      v: 1,
      occurredAt: '2026-09-01T03:00:00.000Z',
      listingId: LISTING,
      page: '/listings/can-ho-2pn',
      properties: detail,
    });
  });

  it('with consent adds a stable anonymous id, a session id and the landing UTM (not other query params)', async () => {
    makeClient();
    client.track('listing_detail_viewed', detail, { listingId: LISTING });
    client.track('compare_opened', { listingIds: [LISTING, OTHER] });
    await vi.runAllTimersAsync();

    expect(sent[0].consent).toBe('granted');
    const [first, second] = sent[0].events;
    expect(first.anonymousId).toBeTruthy();
    expect(second.anonymousId).toBe(first.anonymousId);
    expect(second.sessionId).toBe(first.sessionId);
    expect(first.utm).toEqual({ source: 'zalo', campaign: 'thu-9' });
    expect(JSON.stringify(sent)).not.toContain('bi-mat');
  });

  it('batches: one request after the interval, immediately at 20 events, at most 50 per request', async () => {
    makeClient();
    client.track('kyc_required_shown', { context: 'lead_form' });
    client.track('kyc_required_shown', { context: 'lead_form' });
    expect(send).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(5000);
    expect(sent.map((batch) => batch.events.length)).toEqual([2]);

    for (let index = 0; index < 20; index += 1) client.track('kyc_required_shown', { context: 'lead_form' });
    await vi.advanceTimersByTimeAsync(0);
    expect(sent.map((batch) => batch.events.length)).toEqual([2, 20]);

    client.dispose();
    const big = makeClient({ batchSize: 1000 });
    for (let index = 0; index < 120; index += 1) big.track('kyc_required_shown', { context: 'lead_form' });
    await vi.runAllTimersAsync();
    expect(sent.slice(2).map((batch) => batch.events.length)).toEqual([50, 50, 20]);
  });

  it('an event tracked before consent is not sent once consent is given later', async () => {
    localStorage.clear();
    makeClient();
    client.track('compare_opened', { listingIds: [LISTING] });
    setAnalyticsConsent('granted');
    client.track('compare_opened', { listingIds: [OTHER] });
    await vi.runAllTimersAsync();
    expect(sent).toHaveLength(1);
    expect(sent[0].events.map((event) => event.properties.listingIds)).toEqual([[OTHER]]);
  });

  it('flushes everything with sendBeacon (text/plain) on pagehide', async () => {
    makeClient();
    client.track('compare_opened', { listingIds: [LISTING] });
    window.dispatchEvent(new Event('pagehide'));

    expect(beacon).toHaveBeenCalledTimes(1);
    const [url, blob] = beacon.mock.calls[0] as [string, Blob];
    expect(url).toBe('/api/v1/events');
    expect(blob.type).toBe('text/plain;charset=utf-8');
    vi.useRealTimers();
    const text = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(blob);
    });
    expect(JSON.parse(text)).toMatchObject({ consent: 'granted', events: [{ name: 'compare_opened' }] });
    expect(client.pending()).toBe(0);
    expect(send).not.toHaveBeenCalled();
  });

  it('drops batches the server rejects as invalid and retries network or server errors', async () => {
    send.mockResolvedValueOnce(new Response(null, { status: 400 }));
    makeClient();
    client.track('kyc_required_shown', { context: 'lead_form' });
    await vi.runAllTimersAsync();
    expect(send).toHaveBeenCalledTimes(1);
    expect(client.pending()).toBe(0);

    send.mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(new Response(null, { status: 503 }));
    client.track('kyc_required_shown', { context: 'map' });
    await vi.runAllTimersAsync();
    // offline → 503 → accepted on the third attempt
    expect(send).toHaveBeenCalledTimes(4);
    expect(sent.at(-1)?.events[0].properties).toEqual({ context: 'map' });
  });

  it('drops waiting events and identifiers when consent is withdrawn', async () => {
    makeClient();
    client.track('listing_detail_viewed', detail, { listingId: LISTING });
    expect(client.pending()).toBe(1);
    setAnalyticsConsent('denied');
    expect(client.pending()).toBe(0);
    await vi.runAllTimersAsync();
    expect(send).not.toHaveBeenCalled();
    expect(localStorage.getItem(ANONYMOUS_ID_KEY)).toBeNull();
  });
});

describe('property validation matches the server (m2)', () => {
  it('drops an event whose required property fails the server pattern, without losing the rest of the batch', async () => {
    const warn = vi.fn();
    makeClient({ warn });
    client.track('search_performed', { filterHash: 'not-32-hex-chars', purpose: 'SALE', resultCount: null });
    client.track('kyc_required_shown', { context: 'lead_form' });
    await vi.runAllTimersAsync();

    expect(sent).toHaveLength(1);
    expect(sent[0].events.map((event) => event.name)).toEqual(['kyc_required_shown']);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('search_performed'), expect.anything());
  });

  it('drops only an invalid optional property, keeping the rest of the event', async () => {
    makeClient();
    client.track(
      'listing_detail_viewed',
      // @ts-expect-error — propertyType outside the server's enum
      { purpose: 'SALE', propertyType: 'CASTLE', district: 'not-digits' },
      { listingId: LISTING },
    );
    await vi.runAllTimersAsync();
    expect(sent[0].events[0].properties).toEqual({ purpose: 'SALE' });
  });

  it('rejects an event missing a required property (filterHash never supplied)', async () => {
    makeClient();
    client.track('compare_opened', { listingIds: [LISTING] });
    // @ts-expect-error — filterHash omitted entirely
    client.track('search_results_viewed', { listingIds: [LISTING] });
    await vi.runAllTimersAsync();
    // compare_opened has no required filterHash, so it is unaffected; search_results_viewed is missing one.
    expect(sent[0].events.map((event) => event.name)).toEqual(['compare_opened']);
  });

  it('sanitises the page path, dropping it when it does not match the server pattern', async () => {
    window.history.replaceState(null, '', `/${'a'.repeat(250)}`);
    makeClient();
    client.track('kyc_required_shown', { context: 'lead_form' });
    await vi.runAllTimersAsync();
    expect(sent[0].events[0].page).toBeUndefined();
  });

  it('keeps a page path that matches the server pattern', async () => {
    makeClient();
    client.track('kyc_required_shown', { context: 'lead_form' });
    await vi.runAllTimersAsync();
    expect(sent[0].events[0].page).toBe('/listings/can-ho-2pn');
  });
});

describe('batching stays under the byte cap (m3)', () => {
  it('splits a batch that would exceed the byte cap even though it is under 50 events and 20 events', async () => {
    makeClient({ maxBatchBytes: 8000 });
    const bigIds = Array.from({ length: MAX_LISTING_IDS }, () => LISTING);
    // 10 events × ~1.9 KB each ≈ 19 KB, so an 8 KB cap must split them into several requests.
    for (let index = 0; index < 10; index += 1) client.track('compare_opened', { listingIds: bigIds });
    await vi.runAllTimersAsync();
    expect(sent.length).toBeGreaterThan(1);
    for (const batch of sent) {
      expect(new Blob([JSON.stringify(batch)]).size).toBeLessThanOrEqual(8000);
    }
    expect(sent.flatMap((batch) => batch.events)).toHaveLength(10);
  });

  it('always sends at least one event even if it alone exceeds the cap', async () => {
    makeClient({ maxBatchBytes: 10 });
    client.track('compare_opened', { listingIds: [LISTING] });
    await vi.advanceTimersByTimeAsync(5000);
    expect(sent).toHaveLength(1);
    expect(sent[0].events).toHaveLength(1);
  });
});

describe('landing UTM is captured once, early (m4)', () => {
  it('before consent the landing UTM stays in memory only; consent persists it for the session', () => {
    localStorage.clear();
    window.history.replaceState(null, '', '/?utm_source=zalo&utm_campaign=thu-9');
    captureLandingContext();
    expect(sessionStorage.getItem(UTM_KEY)).toBeNull();
    makeClient();
    setAnalyticsConsent('granted');
    expect(JSON.parse(sessionStorage.getItem(UTM_KEY) ?? '{}')).toEqual({ source: 'zalo', campaign: 'thu-9' });
  });

  it('captureLandingContext persists the UTM once and later URL changes do not overwrite it', () => {
    window.history.replaceState(null, '', '/?utm_source=zalo&utm_campaign=thu-9');
    captureLandingContext();
    expect(JSON.parse(sessionStorage.getItem(UTM_KEY) ?? '{}')).toEqual({ source: 'zalo', campaign: 'thu-9' });

    window.history.replaceState(null, '', '/search?purpose=SALE');
    captureLandingContext();
    expect(JSON.parse(sessionStorage.getItem(UTM_KEY) ?? '{}')).toEqual({ source: 'zalo', campaign: 'thu-9' });
  });

  it('a client created after the URL was rewritten still uses the UTM captured at app start', async () => {
    window.history.replaceState(null, '', '/?utm_source=zalo&utm_campaign=thu-9');
    captureLandingContext();
    // A route effect (e.g. search filters) rewrites the URL, dropping the campaign params — after landing.
    window.history.replaceState(null, '', '/search?purpose=SALE');

    makeClient();
    client.track('compare_opened', { listingIds: [LISTING] });
    await vi.runAllTimersAsync();
    expect(sent[0].events[0].utm).toEqual({ source: 'zalo', campaign: 'thu-9' });
  });
});

describe('track() never throws (m5)', () => {
  it('treats non-object properties as empty and unknown names as a no-op', () => {
    makeClient();
    const call = (name: string, properties: unknown, context?: unknown) =>
      (client.track as (n: string, p: unknown, c?: unknown) => void)(name, properties, context);
    expect(() => call('kyc_required_shown', null)).not.toThrow();
    expect(() => call('kyc_required_shown', undefined)).not.toThrow();
    expect(() => call('kyc_required_shown', 'not an object')).not.toThrow();
    expect(() => call('kyc_required_shown', 42)).not.toThrow();
    expect(() => call('does_not_exist', {})).not.toThrow();
    expect(() => call('listing_detail_viewed', {}, { listingId: { not: 'a string' } })).not.toThrow();
    expect(client.pending()).toBe(0);
  });

  it('the module-level track() also never throws', async () => {
    const { track } = await import('./track');
    expect(() => (track as (n: string, p: unknown) => void)('kyc_required_shown', null)).not.toThrow();
  });
});

describe('consent helpers', () => {
  beforeEach(() => localStorage.clear());

  it('treats a missing choice as denied but reports it as undecided', () => {
    expect(getStoredAnalyticsConsent()).toBeNull();
    expect(getAnalyticsConsent()).toBe('denied');
  });

  it('asks again when the choice was made for another policy version', () => {
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
    expect(getStoredAnalyticsConsent()).toBeNull();
    localStorage.setItem(CONSENT_VERSION_KEY, '2020-01-01');
    expect(getAnalyticsConsent()).toBe('denied');
    setAnalyticsConsent('granted');
    expect(localStorage.getItem(CONSENT_VERSION_KEY)).toBe(CONSENT_POLICY_VERSION);
    expect(getStoredAnalyticsConsent()).toBe('granted');
  });

  it('stores the choice under bds.consent.analytics and clears identifiers on denial', () => {
    setAnalyticsConsent('granted');
    expect(localStorage.getItem('bds.consent.analytics')).toBe('granted');
    localStorage.setItem(ANONYMOUS_ID_KEY, 'anon');
    sessionStorage.setItem(SESSION_KEY, '{"id":"s","lastSeen":1}');

    setAnalyticsConsent('denied');
    expect(getAnalyticsConsent()).toBe('denied');
    expect(localStorage.getItem(ANONYMOUS_ID_KEY)).toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });
});
