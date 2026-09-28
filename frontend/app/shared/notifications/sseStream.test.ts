import { describe, expect, it, vi } from 'vitest';
import { backoffDelay, SseParser, startNotificationStream, type StreamNotification } from './sseStream';

const encoder = new TextEncoder();

function frame(seq: number, title = `T${seq}`): string {
  const data: StreamNotification = {
    id: `n-${seq}`,
    seq,
    type: 'LISTING_EXPIRED',
    category: 'LISTINGS',
    title,
    message: 'm',
    link: null,
    createdAt: '2026-09-28T00:00:00Z',
  };
  return `id: ${seq}\nevent: notification\ndata: ${JSON.stringify(data)}\n\n`;
}

/** A fake streamed response: yields the chunks, then ends (normal EOF) or fails. */
function response(chunks: string[], end: 'eof' | 'error' = 'eof', status = 200): Response {
  let index = 0;
  return {
    ok: status >= 200 && status < 300,
    status,
    body: {
      getReader: () => ({
        read: async () => {
          if (index < chunks.length) return { done: false, value: encoder.encode(chunks[index++]) };
          if (end === 'error') throw new Error('network');
          return { done: true, value: undefined };
        },
      }),
    },
  } as unknown as Response;
}

/** Runs scheduled reconnects synchronously and records their delays. */
function manualTimers() {
  const queue: Array<() => void> = [];
  const delays: number[] = [];
  return {
    delays,
    setTimer: (fn: () => void, ms: number) => {
      delays.push(ms);
      queue.push(fn);
      return queue.length;
    },
    clearTimer: vi.fn(),
    runNext: () => queue.shift()?.(),
  };
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('SseParser', () => {
  it('parses frames split across chunks, ignores comments and keeps retry hints', () => {
    const parser = new SseParser();
    expect(parser.push(': connected\nretry: 3000\n\n')).toEqual([
      { id: undefined, event: 'message', data: '', retry: 3000 },
    ]);
    const whole = frame(7);
    expect(parser.push(whole.slice(0, 20))).toEqual([]);
    const [parsed] = parser.push(whole.slice(20) + ': hb\n\n');
    expect(parsed.id).toBe('7');
    expect(parsed.event).toBe('notification');
    expect(JSON.parse(parsed.data).seq).toBe(7);
    expect(parser.push('event: ready\ndata: {"replayed":true}\r\n\r\n')[0].event).toBe('ready');
  });
});

describe('backoffDelay', () => {
  it('grows exponentially with full jitter and a cap', () => {
    expect(backoffDelay(0, () => 1)).toBe(1000);
    expect(backoffDelay(3, () => 1)).toBe(8000);
    expect(backoffDelay(10, () => 1)).toBe(30_000);
    expect(backoffDelay(10, () => 0)).toBe(500);
    for (let i = 0; i < 50; i++) {
      const value = backoffDelay(4, Math.random);
      expect(value).toBeGreaterThanOrEqual(500);
      expect(value).toBeLessThanOrEqual(16_000);
    }
  });
});

describe('startNotificationStream', () => {
  it('reconnects after a normal end of stream with Last-Event-ID and never delivers a frame twice', async () => {
    const timers = manualTimers();
    const received: number[] = [];
    const calls: Array<Record<string, string>> = [];
    const responses = [
      response([frame(1), frame(2)]),
      response([frame(2), frame(3)], 'error'),
      response([frame(3), frame(4)]),
    ];
    const fetchImpl = vi.fn(async (_url: string, init: RequestInit) => {
      calls.push({ ...(init.headers as Record<string, string>) });
      return responses.shift() ?? response([]);
    });
    const stream = startNotificationStream({
      url: '/api/v1/notifications/stream',
      fetchImpl,
      onNotification: (n) => received.push(n.seq),
      random: () => 1,
      setTimer: timers.setTimer,
      clearTimer: timers.clearTimer,
    });
    await flush();
    expect(received).toEqual([1, 2]);
    expect(calls[0]['Last-Event-ID']).toBeUndefined();
    timers.runNext(); // reconnect after EOF
    await flush();
    expect(calls[1]['Last-Event-ID']).toBe('2');
    timers.runNext(); // reconnect after the network error
    await flush();
    expect(calls[2]['Last-Event-ID']).toBe('3');
    expect(received).toEqual([1, 2, 3, 4]);
    expect(timers.delays[1]).toBeGreaterThan(timers.delays[0]);
    expect(stream.lastSeq()).toBe(4);
    stream.stop();
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  it('stops on 401, asks for a resync when the server cannot replay, and stop() cancels a pending retry', async () => {
    const timers = manualTimers();
    const statuses: string[] = [];
    const denied = startNotificationStream({
      url: '/x',
      fetchImpl: async () => response([], 'eof', 401),
      onNotification: () => undefined,
      onStatus: (s) => statuses.push(s),
      setTimer: timers.setTimer,
      clearTimer: timers.clearTimer,
    });
    await flush();
    expect(statuses.at(-1)).toBe('stopped');
    expect(timers.delays).toHaveLength(0);
    denied.stop();

    const onResync = vi.fn();
    const stream = startNotificationStream({
      url: '/x',
      fetchImpl: async () => response(['event: resync\ndata: {"reason":"TOO_MANY"}\n\n']),
      onNotification: () => undefined,
      onResync,
      initialLastSeq: 10,
      setTimer: timers.setTimer,
      clearTimer: timers.clearTimer,
    });
    await flush();
    expect(onResync).toHaveBeenCalledTimes(1);
    expect(timers.delays).toHaveLength(1);
    stream.stop();
    expect(timers.clearTimer).toHaveBeenCalled();
  });
});
