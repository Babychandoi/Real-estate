/**
 * `track(name, properties)` — consent-aware, batched web analytics (contract §5/§12).
 *
 * - Only catalogued web events are sent (unknown or server-only names are a no-op); only catalogued properties
 *   leave the page, and each one is validated against the same patterns the backend enforces (m2). Listing events
 *   need `{ listingId }` (enforced by the types too). An event the server would reject for a required property is
 *   dropped here (with a console.warn) instead of losing the rest of the batch; an invalid *optional* property is
 *   dropped on its own and the event is still sent.
 * - Consent is taken when the event happens. Without "granted" the event carries no anonymous id, session id or
 *   UTM and the batch is sent with `consent: "denied"` (the server stores it without identifiers).
 * - Events are batched (flush at 20 waiting events, after 5 s, or at ~60 KB of serialised JSON, whichever comes
 *   first — well under the server's 64 KB body limit and sendBeacon/keepalive limits), flushed with
 *   `fetch(keepalive)` when the tab is hidden and with `navigator.sendBeacon` on `pagehide`.
 * - The user id is never in the body: the server reads it from the bearer token (fetch path only).
 * - `track()` never throws: bad input (null properties, an unknown event name, a non-UUID listingId) is a no-op,
 *   optionally logged with `console.warn` (m5).
 */
import { apiFetch, apiUrl } from '@/shared/api/client';
import {
  isWebEventName,
  sanitizeCatalogProperties,
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
/** Same shape the server's `EventIngestionService.PAGE_PATH` accepts (path only, no query string or fragment). */
const PAGE_PATH = /^\/[A-Za-z0-9/_.~%:@!$&'()*+,;=-]{0,199}$/;
/** Comfortably under the server's 64 KB body cap and the sendBeacon/keepalive limits (m3). */
const MAX_BATCH_BYTES = 60_000;

export interface AnalyticsClientOptions {
  /** Endpoint path or URL; default `/events` under the API base (`/api/v1/events`). */
  endpoint?: string;
  /** Flush as soon as this many events wait (default 20). */
  batchSize?: number;
  /** Flush waiting events after this delay (default 5000 ms). */
  flushIntervalMs?: number;
  /** Events per request; the ingestion API accepts at most 50. */
  maxBatch?: number;
  /** Serialised bytes per request; the ingestion API and sendBeacon/keepalive accept at most 64 KB (default 60 000). */
  maxBatchBytes?: number;
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
  /** Logs dropped/invalid events; default `console.warn`, silenced in tests unless overridden. */
  warn?: (message: string, detail?: unknown) => void;
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

/**
 * Captures the landing UTM parameters into sessionStorage, once per session (m4). Must run before any client-side
 * URL rewrite (e.g. a search page replacing the query string, a listing page redirecting to its canonical slug)
 * can drop them. Safe to call more than once: a no-op once something is stored, even an empty capture.
 */
export function captureLandingContext(): void {
  if (typeof window === 'undefined') return;
  if (readStorage(() => sessionStorage, UTM_KEY) != null) return;
  const utm = parseUtm(window.location.search);
  if (utm) writeStorage(() => sessionStorage, UTM_KEY, JSON.stringify(utm));
}

function currentDevice(): Device | undefined {
  if (typeof window === 'undefined') return undefined;
  const width = window.innerWidth;
  return width < 768 ? 'mobile' : width < 1024 ? 'tablet' : 'desktop';
}

/** The current path, sanitised to what the server's page-path pattern accepts; `undefined` otherwise (m2). */
function currentPagePath(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  const path = window.location.pathname;
  return PAGE_PATH.test(path) ? path : undefined;
}

export function createAnalyticsClient(options: AnalyticsClientOptions = {}) {
  const endpoint = options.endpoint ?? '/events';
  const batchSize = options.batchSize ?? 20;
  const flushIntervalMs = options.flushIntervalMs ?? 5000;
  const maxBatch = Math.min(options.maxBatch ?? 50, 50);
  const maxBatchBytes = options.maxBatchBytes ?? MAX_BATCH_BYTES;
  const maxQueue = options.maxQueue ?? 200;
  const maxAttempts = options.maxAttempts ?? 3;
  const consentOf = options.consent ?? getAnalyticsConsent;
  const now = options.now ?? (() => new Date());
  const randomId = options.randomId ?? defaultRandomId;
  const warn = options.warn ?? ((message: string, detail?: unknown) => console.warn(`[analytics] ${message}`, detail));
  const send =
    options.send ??
    ((body: string) => apiFetch(endpoint, { method: 'POST', body, keepalive: true, credentials: 'same-origin' }));
  const beacon =
    options.beacon ??
    ((url: string, body: Blob) =>
      typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function'
        ? navigator.sendBeacon(url, body)
        : false);
  // The landing UTM should already be in sessionStorage (captureLandingContext ran at app start); this call is a
  // safety net for callers that create a client without that having happened yet (e.g. tests).
  captureLandingContext();

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
    if (!stored) return undefined;
    try {
      return JSON.parse(stored) as Record<string, string>;
    } catch {
      return undefined;
    }
  }

  function schedule(delay: number) {
    if (timer != null) return;
    timer = setTimeout(() => {
      timer = null;
      void flush();
    }, delay);
  }

  /** Byte size of an event once serialised inside a batch (its JSON plus one comma). */
  function eventSize(event: WebEvent): number {
    return new Blob([JSON.stringify(event)]).size + 1;
  }

  /** Takes up to `maxBatch` same-consent events from the front of the queue, capped at `maxBatchBytes`. */
  function takeBatch(): Pending[] {
    if (!queue.length) return [];
    const consent = queue[0].consent;
    const batch: Pending[] = [];
    const rest: Pending[] = [];
    let bytes = 2; // "[" + "]" of the events array
    for (const item of queue) {
      if (batch.length >= maxBatch || item.consent !== consent) {
        rest.push(item);
        continue;
      }
      const size = eventSize(item.event);
      if (batch.length > 0 && bytes + size > maxBatchBytes) {
        rest.push(item);
        continue;
      }
      batch.push(item);
      bytes += size;
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
    // Never throws (m5): bad input from a caller must not break the page it instruments.
    try {
      if (!isWebEventName(name)) {
        warn(`unknown event "${String(name)}" ignored`);
        return;
      }
      const context: TrackContext = rest[0] ?? {};
      if (context.listingId !== undefined && !UUID.test(context.listingId)) {
        warn(`"${name}": listingId is not a UUID, event dropped`, context.listingId);
        return;
      }
      if (WEB_EVENT_CATALOG[name].requiresListing && !context.listingId) {
        warn(`"${name}" requires a listingId, event dropped`);
        return;
      }
      const sanitizedProperties = sanitizeCatalogProperties(name, properties);
      if (sanitizedProperties == null) {
        warn(`"${name}": a required property is missing or invalid, event dropped`, properties);
        return;
      }
      const consent = consentOf();
      const granted = consent === 'granted';
      const event: WebEvent = {
        eventId: randomId(),
        name,
        v: WEB_EVENT_CATALOG[name].version,
        occurredAt: now().toISOString(),
        anonymousId: granted ? anonymousId() : null,
        sessionId: granted ? sessionId() : null,
        properties: sanitizedProperties,
        page: currentPagePath(),
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
    } catch (error) {
      warn(`"${String(name)}": tracking failed unexpectedly, event dropped`, error);
    }
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

// Captured once, as early as this module is first imported (main.tsx imports it before rendering, specifically
// for this), so a later client-side URL rewrite (search filters, canonical slug redirects…) can never drop the
// landing campaign parameters before they are read (m4).
captureLandingContext();

/** Records a catalogued web event; see the module comment for consent, batching, validation and delivery. */
export function track<N extends WebEventName>(
  name: N,
  properties: WebEventProperties[N],
  ...context: TrackContextArg<N>
): void {
  if (analyticsDisabled || typeof window === 'undefined') return;
  try {
    defaultClient ??= createAnalyticsClient();
    defaultClient.track(name, properties, ...context);
  } catch {
    // track() must never throw into the caller (m5); createAnalyticsClient itself does not throw in practice, but
    // this guards the public entry point unconditionally.
  }
}
