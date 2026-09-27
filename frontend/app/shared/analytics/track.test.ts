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

const detail = { purpose: 'SALE', propertyType: 'APARTMENT', district: 'Cầu Giấy' } as const;

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

  it('sends only catalogued properties and caps id lists at 48', async () => {
    makeClient();
    const listingIds = Array.from({ length: 60 }, (_, index) => `l${index}`);
    client.track('search_results_viewed', {
      filterHash: 'abc',
      listingIds,
      offset: 0,
      // @ts-expect-error — extra keys never leave the page
      email: 'khach@example.invalid',
    });
    await vi.runAllTimersAsync();
    const [event] = sent[0].events;
    expect(event.properties).toEqual({ filterHash: 'abc', listingIds: listingIds.slice(0, 48), offset: 0 });
  });

  it('without consent sends consent "denied" and no identifiers or UTM', async () => {
    makeClient();
    client.track('listing_detail_viewed', detail, { listingId: 'listing-1' });
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
      listingId: 'listing-1',
      page: '/listings/can-ho-2pn',
      properties: detail,
    });
    expect(event.utm).toBeUndefined();
    expect(localStorage.getItem(ANONYMOUS_ID_KEY)).toBeNull();
  });

  it('with consent adds a stable anonymous id, a session id and the landing UTM (not other query params)', async () => {
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
    makeClient();
    client.track('listing_detail_viewed', detail);
    client.track('compare_opened', { listingIds: ['a', 'b'] });
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
    client.track('lead_form_opened', { requestType: 'VIEWING' });
    client.track('lead_form_opened', { requestType: 'CONSULTATION' });
    expect(send).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(5000);
    expect(sent.map((batch) => batch.events.length)).toEqual([2]);

    for (let index = 0; index < 20; index += 1) client.track('kyc_required_shown', { context: 'lead' });
    await vi.advanceTimersByTimeAsync(0);
    expect(sent.map((batch) => batch.events.length)).toEqual([2, 20]);

    client.dispose();
    const big = makeClient({ batchSize: 1000 });
    for (let index = 0; index < 120; index += 1) big.track('kyc_required_shown', { context: 'lead' });
    await vi.runAllTimersAsync();
    expect(sent.slice(2).map((batch) => batch.events.length)).toEqual([50, 50, 20]);
  });

  it('never mixes consent states in one request', async () => {
    makeClient();
    client.track('lead_form_opened', { requestType: 'VIEWING' });
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
    client.track('lead_form_opened', { requestType: 'VIEWING' });
    await vi.runAllTimersAsync();
    expect(sent.map((batch) => batch.consent)).toEqual(['denied', 'granted']);
  });

  it('flushes everything with sendBeacon on pagehide', () => {
    makeClient();
    client.track('compare_opened', { listingIds: ['a'] });
    window.dispatchEvent(new Event('pagehide'));

    expect(beacon).toHaveBeenCalledTimes(1);
    const [url, blob] = beacon.mock.calls[0] as [string, Blob];
    expect(url).toBe('/api/v1/events');
    expect(blob.type).toBe('application/json');
    expect(client.pending()).toBe(0);
    expect(send).not.toHaveBeenCalled();
  });

  it('drops batches the server rejects as invalid and retries network or server errors', async () => {
    send.mockResolvedValueOnce(new Response(null, { status: 400 }));
    makeClient();
    client.track('lead_form_opened', { requestType: 'VIEWING' });
    await vi.runAllTimersAsync();
    expect(send).toHaveBeenCalledTimes(1);
    expect(client.pending()).toBe(0);

    send.mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(new Response(null, { status: 503 }));
    client.track('lead_form_opened', { requestType: 'CONSULTATION' });
    await vi.runAllTimersAsync();
    // offline → 503 → accepted on the third attempt
    expect(send).toHaveBeenCalledTimes(4);
    expect(sent.at(-1)?.events[0].properties).toEqual({ requestType: 'CONSULTATION' });
  });

  it('strips identifiers from waiting events when consent is withdrawn', async () => {
    localStorage.setItem(CONSENT_STORAGE_KEY, 'granted');
    makeClient();
    client.track('listing_detail_viewed', detail);
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
