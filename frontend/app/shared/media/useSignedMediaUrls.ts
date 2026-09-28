import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '@/shared/api/client';

/**
 * Images of drafts, pending edits and hidden listings are not served publicly (S1, F14.4): their owner and staff see
 * them through short-lived signed URLs issued by `POST /api/v1/media/signed-urls`. The cache is per tab and a URL is
 * refreshed one minute before it expires.
 */
const OWN_MEDIA = /^\/api\/v1\/public\/media\/[0-9a-fA-F-]{36}(?:__w\d{2,4})?\.(?:jpg|png|webp|avif)$/;
const BATCH = 50;
const MARGIN_MS = 60_000;

interface Entry {
  /** Signed URL, or null when the server did not sign it (not the caller's, or public anyway): use the plain URL. */
  signed: string | null;
  validUntil: number;
}

const cache = new Map<string, Entry>();

export const isOwnMedia = (url: string | null | undefined): url is string => Boolean(url && OWN_MEDIA.test(url));

function fresh(url: string, now: number): Entry | undefined {
  const entry = cache.get(url);
  return entry && entry.validUntil > now ? entry : undefined;
}

/** Signs every own-media URL that is not cached (batches of 50); failures fall back to the plain URL briefly. */
export async function loadSignedMediaUrls(urls: readonly string[]): Promise<void> {
  const now = Date.now();
  const missing = [...new Set(urls.filter((url) => isOwnMedia(url) && !fresh(url, now)))];
  for (let i = 0; i < missing.length; i += BATCH) {
    const chunk = missing.slice(i, i + BATCH);
    try {
      const result = await apiClient<{ urls: Record<string, string>; expiresAt: string | null }>('/media/signed-urls', {
        method: 'POST',
        body: JSON.stringify({ urls: chunk }),
      });
      const expires = result.expiresAt ? Date.parse(result.expiresAt) - MARGIN_MS : now + 5 * 60_000;
      for (const url of chunk) {
        const signed = result.urls?.[url];
        cache.set(url, { signed: signed ?? null, validUntil: signed ? expires : now + 5 * 60_000 });
      }
    } catch {
      for (const url of chunk) cache.set(url, { signed: null, validUntil: now + 30_000 });
    }
  }
}

/** Stores a signed URL the server already returned (e.g. `previewUrl` of an upload). */
export function rememberSignedMediaUrl(
  url: string,
  signed: string | null | undefined,
  expiresAt: string | null | undefined,
) {
  if (!isOwnMedia(url) || !signed || !expiresAt) return;
  cache.set(url, { signed, validUntil: Date.parse(expiresAt) - MARGIN_MS });
}

/** Test helper. */
export function resetSignedMediaCache() {
  cache.clear();
}

/**
 * Returns `display(url)`: the signed URL for the caller's own (or, for staff, any) uploaded image, the plain URL for
 * external/public images, and `undefined` while an own-media URL is still being signed (render a placeholder, so no
 * request hits the public endpoint and 404s).
 */
export function useSignedMediaUrls(
  urls: readonly (string | null | undefined)[],
): (url: string | null | undefined) => string | undefined {
  const [version, setVersion] = useState(0);
  const key = useMemo(() => [...new Set(urls.filter(isOwnMedia))].sort().join('\n'), [urls]);

  useEffect(() => {
    if (!key) return undefined;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const list = key.split('\n');
    const refresh = () => {
      void loadSignedMediaUrls(list).then(() => {
        if (cancelled) return;
        setVersion((value) => value + 1);
        const now = Date.now();
        const next = Math.min(...list.map((url) => cache.get(url)?.validUntil ?? now + 60_000));
        timer = setTimeout(refresh, Math.max(5_000, next - now));
      });
    };
    refresh();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [key]);

  return useCallback(
    (url: string | null | undefined) => {
      if (!url) return undefined;
      if (!isOwnMedia(url)) return url;
      const entry = cache.get(url);
      if (!entry) return undefined;
      return entry.signed ?? url;
    },
    // `version` re-creates the resolver after each load so consumers re-render with the signed URLs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [version],
  );
}
