/**
 * `track(name, properties)` — consent-aware, batched web analytics (contract §5/§12).
 *
 * - Only catalogued web events are sent (unknown or server-only names are a no-op); only catalogued properties
 *   leave the page. Listing events need `{ listingId }` (enforced by the types); an event the server would reject
 *   is dropped here, because one invalid event makes the server refuse the whole batch.
 * - Consent is taken when the event happens. Without "granted" the event carries no anonymous id, session id or
 *   UTM and the batch is sent with `consent: "denied"` (the server stores it without identifiers).
 * - Events are batched (flush at 20 waiting events or after 5 s, at most 50 per request), flushed with
 *   `fetch(keepalive)` when the tab is hidden and with `navigator.sendBeacon` on `pagehide`.
 * - The user id is never in the body: the server reads it from the bearer token (fetch path only).
 */
import { apiFetch, apiUrl } from '@/shared/api/client';
import {
  isWebEventName,
  pickCatalogProperties,
  WEB_EVENT_CATALOG,
  type ListingEventName,
  type WebEventName,
  type WebEventProperties,
} from './catalog';
import {
  ANONYMOUS_ID_KEY,
  getAnalyticsConsent,
  onAnalyticsConsentChange,
  SESSION_KEY,
  UTM_KEY,
  type AnalyticsConsent,
} from './consent';

export type Device = 'mobile' | 'tablet' | 'desktop';

export interface WebEvent {
  eventId: string;
  name: WebEventName;
  v: number;
  occurredAt: string;
  anonymousId: string | null;
  sessionId: string | null;
  listingId?: string;
  properties: Record<string, unknown>;
  page?: string;
  utm?: Record<string, string>;
  device?: Device;
}

export interface EventBatch {
  consent: AnalyticsConsent;
  events: WebEvent[];
}

export interface TrackContext {
  /** Listing the event is about (envelope field, not a property). */
  listingId?: string;
}

/** `listing_detail_viewed` and `lead_form_opened` must say which listing they are about. */
export type TrackContextArg<N extends WebEventName> = N extends ListingEventName
  ? [context: TrackContext & { listingId: string }]
  : [context?: TrackContext];

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface AnalyticsClientOptions {
  /** Endpoint path or URL; default `/events` under the API base (`/api/v1/events`). */
  endpoint?: string;
  /** Flush as soon as this many events wait (default 20). */
  batchSize?: number;
  /** Flush waiting events after this delay (default 5000 ms). */
  flushIntervalMs?: number;
  /** Events per request; the ingestion API accepts at most 50. */
  maxBatch?: number;
  /** Oldest events are dropped beyond this many waiting events (default 200). */
  maxQueue?: number;
  /** Delivery attempts per event before it is dropped (default 3). */
  maxAttempts?: number;
  /** Install visibilitychange/pagehide listeners (default true). */
  lifecycle?: boolean;
  send?: (body: string) => Promise<Response>;
  beacon?: (url: string, body: Blob) => boolean;
  consent?: () => AnalyticsConsent;
  now?: () => Date;
  randomId?: () => string;
}

interface Pending {
  consent: AnalyticsConsent;
  event: WebEvent;
  attempts: number;
}

const SESSION_IDLE_MS = 30 * 60 * 1000;
const UTM_PARAMS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content'] as const;

function readStorage(storage: () => Storage, key: string): string | null {
  try {
    return storage().getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(storage: () => Storage, key: string, value: string): void {
  try {
    storage().setItem(key, value);
  } catch {
    // Identifiers are best effort; without storage each event simply gets fresh ids.
  }
}

function defaultRandomId(): string {
  const cryptoApi: Crypto | undefined = typeof crypto === 'undefined' ? undefined : crypto;
  // randomUUID exists only in secure contexts (HTTPS, localhost).
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID();
  // RFC 4122 v4 from getRandomValues otherwise.
  const bytes = new Uint8Array(16);
  if (cryptoApi) cryptoApi.getRandomValues(bytes);
  else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function parseUtm(search: string): Record<string, string> | undefined {
  const params = new URLSearchParams(search);
  const utm: Record<string, string> = {};
  for (const key of UTM_PARAMS) {
    const value = params.get(key);
    if (value) utm[key.slice(4)] = value.slice(0, 100);
  }
  return Object.keys(utm).length ? utm : undefined;
}

function currentDevice(): Device | undefined {
  if (typeof window === 'undefined') return undefined;
  const width = window.innerWidth;
  return width < 768 ? 'mobile' : width < 1024 ? 'tablet' : 'desktop';
}

export function createAnalyticsClient(options: AnalyticsClientOptions = {}) {
  const endpoint = options.endpoint ?? '/events';
  const batchSize = options.batchSize ?? 20;
  const flushIntervalMs = options.flushIntervalMs ?? 5000;
  const maxBatch = Math.min(options.maxBatch ?? 50, 50);
  const maxQueue = options.maxQueue ?? 200;
  const maxAttempts = options.maxAttempts ?? 3;
  const consentOf = options.consent ?? getAnalyticsConsent;
  const now = options.now ?? (() => new Date());
  const randomId = options.randomId ?? defaultRandomId;
  const send =
    options.send ??
    ((body: string) => apiFetch(endpoint, { method: 'POST', body, keepalive: true, credentials: 'same-origin' }));
  const beacon =
    options.beacon ??
    ((url: string, body: Blob) =>
      typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function'
        ? navigator.sendBeacon(url, body)
        : false);
  const landingUtm = typeof window === 'undefined' ? undefined : parseUtm(window.location.search);

  let queue: Pending[] = [];
  let timer: ReturnType<typeof setTimeout> | null = null;
  let sending = false;

  function anonymousId(): string {
    const existing = readStorage(() => localStorage, ANONYMOUS_ID_KEY);
    if (existing) return existing;
    const created = randomId();
    writeStorage(() => localStorage, ANONYMOUS_ID_KEY, created);
    return created;
  }

  function sessionId(): string {
    const at = now().getTime();
    const stored = readStorage(() => sessionStorage, SESSION_KEY);
    let id: string | null = null;
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as { id?: string; lastSeen?: number };
        if (parsed.id && typeof parsed.lastSeen === 'number' && at - parsed.lastSeen < SESSION_IDLE_MS) id = parsed.id;
      } catch {
        id = null;
      }
    }
    id ??= randomId();
    writeStorage(() => sessionStorage, SESSION_KEY, JSON.stringify({ id, lastSeen: at }));
    return id;
  }

  function utm(): Record<string, string> | undefined {
    const stored = readStorage(() => sessionStorage, UTM_KEY);
    if (stored) {
      try {
        return JSON.parse(stored) as Record<string, string>;
      } catch {
        return undefined;
      }
    }
    if (landingUtm) writeStorage(() => sessionStorage, UTM_KEY, JSON.stringify(landingUtm));
    return landingUtm;
  }

  function schedule(delay: number) {
    if (timer != null) return;
    timer = setTimeout(() => {
      timer = null;
      void flush();
    }, delay);
  }

  function takeBatch(): Pending[] {
    if (!queue.length) return [];
    const consent = queue[0].consent;
    const batch: Pending[] = [];
    const rest: Pending[] = [];
    for (const item of queue) {
      if (batch.length < maxBatch && item.consent === consent) batch.push(item);
      else rest.push(item);
    }
    queue = rest;
    return batch;
  }

  const body = (batch: Pending[]) =>
    JSON.stringify({ consent: batch[0].consent, events: batch.map((item) => item.event) } satisfies EventBatch);

  function requeue(batch: Pending[]) {
    const retry = batch
      .map((item) => ({ ...item, attempts: item.attempts + 1 }))
      .filter((item) => item.attempts < maxAttempts);
    queue = [...retry, ...queue].slice(-maxQueue);
    if (retry.length) schedule(flushIntervalMs * 2 ** retry[0].attempts);
  }

  async function flush(): Promise<void> {
    if (sending) return;
    if (timer != null) {
      clearTimeout(timer);
      timer = null;
    }
    const batch = takeBatch();
    if (!batch.length) return;
    sending = true;
    try {
      const response = await send(body(batch));
      // Invalid payloads (400/413/422…) would fail again: drop them. Retry throttling and server errors.
      if (!response.ok && (response.status === 408 || response.status === 429 || response.status >= 500)) {
        requeue(batch);
      }
    } catch {
      requeue(batch);
    } finally {
      sending = false;
    }
    if (queue.length && timer == null) schedule(0);
  }

  /** Last chance on pagehide: beacons cannot carry the bearer token but survive the page unloading. */
  function flushWithBeacon(): void {
    if (timer != null) {
      clearTimeout(timer);
      timer = null;
    }
    const url = apiUrl(endpoint);
    let batch = takeBatch();
    while (batch.length) {
      const payload = body(batch);
      // text/plain is CORS-safelisted, so every browser accepts the beacon; the server parses it as JSON.
      if (!beacon(url, new Blob([payload], { type: 'text/plain;charset=UTF-8' })))
        void send(payload).catch(() => undefined);
      batch = takeBatch();
    }
  }

  function track<N extends WebEventName>(name: N, properties: WebEventProperties[N], ...rest: TrackContextArg<N>) {
    if (!isWebEventName(name)) return;
    const context: TrackContext = rest[0] ?? {};
    if (context.listingId !== undefined && !UUID.test(context.listingId)) return;
    if (WEB_EVENT_CATALOG[name].requiresListing && !context.listingId) return;
    const consent = consentOf();
    const granted = consent === 'granted';
    const event: WebEvent = {
      eventId: randomId(),
      name,
      v: WEB_EVENT_CATALOG[name].version,
      occurredAt: now().toISOString(),
      anonymousId: granted ? anonymousId() : null,
      sessionId: granted ? sessionId() : null,
      properties: pickCatalogProperties(name, properties),
      page: typeof window === 'undefined' ? undefined : window.location.pathname,
      device: currentDevice(),
    };
    if (context.listingId) event.listingId = context.listingId;
    if (granted) {
      const campaign = utm();
      if (campaign) event.utm = campaign;
    }
    queue.push({ consent, event, attempts: 0 });
    if (queue.length > maxQueue) queue = queue.slice(-maxQueue);
    if (queue.length >= batchSize) void flush();
    else schedule(flushIntervalMs);
  }

  const onVisibility = () => {
    if (document.visibilityState === 'hidden') void flush();
  };
  const onPageHide = () => flushWithBeacon();
  // Withdrawn consent also strips identifiers from events that were not sent yet.
  const unsubscribeConsent = onAnalyticsConsentChange((consent) => {
    if (consent !== 'denied') return;
    queue = queue.map((item) => {
      const event = { ...item.event, anonymousId: null, sessionId: null };
      delete event.utm;
      return { ...item, consent: 'denied', event };
    });
  });
  const lifecycle = options.lifecycle !== false && typeof window !== 'undefined';
  if (lifecycle) {
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('pagehide', onPageHide);
  }

  return {
    track,
    flush,
    flushWithBeacon,
    pending: () => queue.length,
    dispose() {
      if (timer != null) clearTimeout(timer);
      timer = null;
      unsubscribeConsent();
      if (lifecycle) {
        document.removeEventListener('visibilitychange', onVisibility);
        window.removeEventListener('pagehide', onPageHide);
      }
    },
  };
}

export type AnalyticsClient = ReturnType<typeof createAnalyticsClient>;

let defaultClient: AnalyticsClient | null = null;
const analyticsDisabled = import.meta.env.MODE === 'test' || import.meta.env.VITE_ANALYTICS_DISABLED === 'true';

/** Records a catalogued web event; see the module comment for consent, batching and delivery. */
export function track<N extends WebEventName>(
  name: N,
  properties: WebEventProperties[N],
  ...context: TrackContextArg<N>
): void {
  if (analyticsDisabled || typeof window === 'undefined') return;
  defaultClient ??= createAnalyticsClient();
  defaultClient.track(name, properties, ...context);
}
