/**
 * Notification stream client (audit F12.2). `EventSource` cannot send the bearer token, so the stream is read with
 * `fetch` and parsed here:
 * - frames `id: <seq>` / `event: notification` / `data: {…}`, comments (`: hb` heartbeats) and `retry:` hints;
 * - reconnects after an error **and** after a normal end of stream (server timeout, deploy, proxy), with exponential
 *   backoff and full jitter (1 s → 30 s), reset once a stream stays up;
 * - sends `Last-Event-ID` with the highest seq seen so the server replays what was missed;
 * - drops frames it has already delivered (the server's replay may overlap), so the UI never updates twice;
 * - stops for good on 401/403 (signed out) and on `stop()`.
 */

export interface StreamNotification {
  id: string;
  seq: number;
  type: string;
  category: string;
  title: string;
  message: string;
  link: string | null;
  createdAt: string;
}

export interface SseFrame {
  id?: string;
  event: string;
  data: string;
  retry?: number;
}

/** Incremental parser: feed chunks, get complete frames. */
export class SseParser {
  private buffer = '';

  push(chunk: string): SseFrame[] {
    this.buffer += chunk.replace(/\r\n?/g, '\n');
    const frames: SseFrame[] = [];
    let end = this.buffer.indexOf('\n\n');
    while (end >= 0) {
      const block = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const frame = parseBlock(block);
      if (frame) frames.push(frame);
      end = this.buffer.indexOf('\n\n');
    }
    return frames;
  }
}

function parseBlock(block: string): SseFrame | null {
  let id: string | undefined;
  let event = 'message';
  let retry: number | undefined;
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'id') id = value;
    else if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
    else if (field === 'retry' && /^\d+$/.test(value)) retry = Number(value);
  }
  if (!data.length && retry === undefined && event === 'message') return null;
  return { id, event, data: data.join('\n'), retry };
}

/** Full-jitter exponential backoff: random in [0, min(cap, base·2^attempt)], never below `floor`. */
export function backoffDelay(
  attempt: number,
  random: () => number = Math.random,
  base = 1000,
  cap = 30_000,
  floor = 500,
) {
  const ceiling = Math.min(cap, base * 2 ** Math.max(0, attempt));
  return Math.max(floor, Math.round(random() * ceiling));
}

export type StreamStatus = 'connecting' | 'open' | 'retrying' | 'stopped';

export interface NotificationStreamOptions {
  url: string;
  /** Performs the request (adds the bearer token); defaults to `fetch`. */
  fetchImpl?: (url: string, init: RequestInit) => Promise<Response>;
  onNotification: (notification: StreamNotification) => void;
  /** The server could not replay everything (`event: resync`): reload the list over REST. */
  onResync?: () => void;
  onStatus?: (status: StreamStatus) => void;
  /** Seq the client already has (e.g. from the list it loaded); used as the first `Last-Event-ID`. */
  initialLastSeq?: number | null;
  random?: () => number;
  setTimer?: (fn: () => void, ms: number) => unknown;
  clearTimer?: (handle: unknown) => void;
  /** A stream that stayed open this long resets the backoff. */
  stableAfterMs?: number;
  /** How many delivered ids are remembered for dedupe. */
  dedupeWindow?: number;
}

export interface NotificationStream {
  stop: () => void;
  lastSeq: () => number | null;
}

export function startNotificationStream(options: NotificationStreamOptions): NotificationStream {
  const fetchImpl = options.fetchImpl ?? ((url, init) => fetch(url, init));
  const random = options.random ?? Math.random;
  const setTimer = options.setTimer ?? ((fn, ms) => setTimeout(fn, ms));
  const clearTimer = options.clearTimer ?? ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>));
  const stableAfter = options.stableAfterMs ?? 60_000;
  const window = options.dedupeWindow ?? 500;
  const seen = new Set<string>();
  const seenOrder: string[] = [];
  let lastSeq: number | null = options.initialLastSeq ?? null;
  let attempt = 0;
  let stopped = false;
  let controller: AbortController | null = null;
  let timer: unknown = null;
  let serverRetry: number | null = null;

  const status = (value: StreamStatus) => options.onStatus?.(value);

  const remember = (id: string) => {
    seen.add(id);
    seenOrder.push(id);
    if (seenOrder.length > window) seen.delete(seenOrder.shift() as string);
  };

  const handle = (frame: SseFrame) => {
    if (frame.retry !== undefined) serverRetry = frame.retry;
    if (frame.event === 'resync') {
      options.onResync?.();
      return;
    }
    if (frame.event !== 'notification') return;
    let payload: StreamNotification;
    try {
      payload = JSON.parse(frame.data) as StreamNotification;
    } catch {
      return;
    }
    const key = payload.id ?? frame.id;
    if (!key || seen.has(key)) return;
    remember(key);
    const seq = Number(frame.id ?? payload.seq);
    if (Number.isFinite(seq) && (lastSeq === null || seq > lastSeq)) lastSeq = seq;
    options.onNotification(payload);
  };

  const schedule = () => {
    if (stopped) return;
    status('retrying');
    const delay = Math.max(backoffDelay(attempt, random), serverRetry ?? 0);
    attempt += 1;
    timer = setTimer(() => {
      timer = null;
      void connect();
    }, delay);
  };

  const connect = async () => {
    if (stopped) return;
    status('connecting');
    controller = new AbortController();
    const headers: Record<string, string> = { Accept: 'text/event-stream' };
    if (lastSeq !== null) headers['Last-Event-ID'] = String(lastSeq);
    const openedAt = Date.now();
    try {
      const response = await fetchImpl(options.url, { headers, signal: controller.signal, cache: 'no-store' });
      if (response.status === 401 || response.status === 403) {
        stopped = true;
        status('stopped');
        return;
      }
      if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
      status('open');
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parser = new SseParser();
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        parser.push(decoder.decode(value, { stream: true })).forEach(handle);
        if (Date.now() - openedAt >= stableAfter) attempt = 0;
      }
    } catch {
      if (stopped) return;
    }
    if (Date.now() - openedAt >= stableAfter) attempt = 0;
    // Normal end of stream or an error: reconnect either way (F12.2).
    schedule();
  };

  void connect();
  return {
    stop: () => {
      stopped = true;
      if (timer !== null) clearTimer(timer);
      controller?.abort();
      status('stopped');
    },
    lastSeq: () => lastSeq,
  };
}
