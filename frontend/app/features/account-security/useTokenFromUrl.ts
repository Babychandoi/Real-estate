import { useEffect, useState } from 'react';

/**
 * Reads `?token=` once and removes it from the address bar right away, so the one-time link does not stay in the
 * history, in screenshots or in a later Referer. The token lives only in component state afterwards.
 */
export function useTokenFromUrl(): string {
  // Pure initializer (StrictMode may call it twice); the address bar is cleaned in the effect.
  const [token] = useState(() => new URL(window.location.href).searchParams.get('token') ?? '');
  useEffect(() => {
    const url = new URL(window.location.href);
    if (!url.searchParams.has('token')) return;
    url.searchParams.delete('token');
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
  }, []);
  return token;
}
