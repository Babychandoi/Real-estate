import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import {
  ANONYMOUS_ID_KEY,
  CONSENT_STORAGE_KEY,
  getAnalyticsConsent,
  getStoredAnalyticsConsent,
  SESSION_KEY,
  setAnalyticsConsent,
} from './consent';
import { createAnalyticsClient, type AnalyticsClient, type EventBatch } from './track';

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

  it('without consent sends consent "denied" and no identifiers or UTM', async () => {
    makeClient();
    client.track('listing_detail_viewed', detail, { listingId: LISTING });
    await vi.runAllTimersAsync();

    expect(sent).toHaveLength(1);
    expect(sent[0].consent).toBe('denied');
    const [event] = sent[0].events;
    expect(event).toMatchObject({
      name: 'listing_detail_viewed',
      v: 1,
      eventId: 'id-1',
      occurredAt: '2026-09-01T03:00:00.000Z',
      anonymousId: null,
      sessionId: null,
      listingId: LISTING,
      page: '/listings/can-ho-2pn',
      properties: detail,
    });
    expect(event.utm).toBeUndefined();
    expect(localStorage.getItem(ANONYMOUS_ID_KEY)).toBeNull();
  });

  it('with consent adds a stable anonymous id, a session id and the landing UTM (not other query params)', async () => {
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
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

  it('never mixes consent states in one request', async () => {
    makeClient();
    client.track('compare_opened', { listingIds: [LISTING] });
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
    client.track('compare_opened', { listingIds: [LISTING] });
    await vi.runAllTimersAsync();
    expect(sent.map((batch) => batch.consent)).toEqual(['denied', 'granted']);
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
    expect(JSON.parse(text)).toMatchObject({ consent: 'denied', events: [{ name: 'compare_opened' }] });
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

  it('strips identifiers from waiting events when consent is withdrawn', async () => {
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
    makeClient();
    client.track('listing_detail_viewed', detail, { listingId: LISTING });
    setAnalyticsConsent('denied');
    await vi.runAllTimersAsync();
    expect(sent[0].consent).toBe('denied');
    expect(sent[0].events[0]).toMatchObject({ anonymousId: null, sessionId: null });
    expect(sent[0].events[0].utm).toBeUndefined();
  });
});

describe('consent helpers', () => {
  beforeEach(() => localStorage.clear());

  it('treats a missing choice as denied but reports it as undecided', () => {
    expect(getStoredAnalyticsConsent()).toBeNull();
    expect(getAnalyticsConsent()).toBe('denied');
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
