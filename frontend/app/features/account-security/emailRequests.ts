import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '@/shared/api/client';

/** Seconds the UI waits before the same e-mail request can be sent again (the server also limits it per address). */
export const RESEND_COOLDOWN_SECONDS = 60;

export type EmailRequestResult = { ok: true } | { ok: false; retryAfterSeconds?: number };

/**
 * POSTs an e-mail-only request (forgot password, resend verification). The server answers 202 whatever the address
 * is, so the UI never learns — or shows — whether an account exists. 429 carries Retry-After.
 */
export async function sendEmailRequest(path: string, email: string): Promise<EmailRequestResult> {
  try {
    const response = await apiFetch(path, { method: 'POST', body: JSON.stringify({ email }) });
    if (response.ok) return { ok: true };
    if (response.status === 429) {
      const header = Number(response.headers.get('Retry-After'));
      return { ok: false, retryAfterSeconds: Number.isFinite(header) && header > 0 ? header : RESEND_COOLDOWN_SECONDS };
    }
    return { ok: false };
  } catch {
    return { ok: false };
  }
}

/** Countdown in whole seconds; `start(n)` restarts it. */
export function useCooldown(): [number, (seconds: number) => void] {
  const [until, setUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (until <= now) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [until, now]);
  const start = useCallback((seconds: number) => {
    const current = Date.now();
    setNow(current);
    setUntil(current + seconds * 1000);
  }, []);
  return [Math.max(0, Math.ceil((until - now) / 1000)), start];
}

export function formatWait(seconds: number): string {
  if (seconds < 60) return `${seconds} giây`;
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} phút`;
}
