import { vi } from 'vitest';

export interface RecordedCall {
  method: string;
  path: string;
  body: unknown;
}

interface Answer {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}
type Handler = (call: RecordedCall) => Answer | undefined;

/** Stubs fetch with a route table keyed by "METHOD /path" (path after /api/v1); records every call. */
export function stubApi(routes: Record<string, Handler | Answer>): RecordedCall[] {
  const calls: RecordedCall[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), 'http://localhost');
      const path = url.pathname.replace(/^\/api\/v1/, '');
      const method = (init?.method ?? 'GET').toUpperCase();
      const call = { method, path, body: init?.body ? JSON.parse(String(init.body)) : undefined };
      calls.push(call);
      const route = routes[`${method} ${path}`];
      const answer = typeof route === 'function' ? route(call) : route;
      if (!answer) return new Response('', { status: 200 });
      const status = answer.status ?? 200;
      return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), {
        status,
        headers: { 'Content-Type': 'application/json', ...(answer.headers ?? {}) },
      });
    }),
  );
  return calls;
}

export const problem = (status: number, code: string, detail = 'Lỗi') => ({
  status,
  body: { title: 'Lỗi', status, detail, code },
});
