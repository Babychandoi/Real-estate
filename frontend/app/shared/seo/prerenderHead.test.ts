import { beforeEach, describe, expect, it, vi } from 'vitest';
import { resetPrerenderedHead, resetPrerenderedHeadOnNavigation } from './prerenderHead';
import { applyDocumentMeta } from './useDocumentMeta';

/** The head as the prerender layer (backend SpaShell) sends it for a listing page. */
function prerenderedHead() {
  document.head.innerHTML = `
    <title data-prerender data-default="Nhà Đất Chuẩn">Căn hộ A | Nhà Đất Chuẩn</title>
    <meta name="description" content="Căn hộ A cần bán" data-prerender data-default="Mặc định">
    <meta name="robots" content="index,follow" data-prerender data-default="index,follow,max-image-preview:large">
    <meta property="og:type" content="product" data-prerender data-default="website">
    <meta property="og:url" content="https://example.test/listings/a" data-prerender>
    <link rel="canonical" href="https://example.test/listings/a" data-prerender>
    <script type="application/ld+json" data-prerender>{"@type":"Product","name":"A"}</script>`;
}

const head = () => ({
  title: document.head.querySelector('title')?.textContent,
  description: document.head.querySelector('meta[name="description"]')?.getAttribute('content'),
  robots: document.head.querySelector('meta[name="robots"]')?.getAttribute('content'),
  ogType: document.head.querySelector('meta[property="og:type"]')?.getAttribute('content'),
  ogUrl: document.head.querySelector('meta[property="og:url"]')?.getAttribute('content') ?? null,
  canonical: document.head.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? null,
  jsonLd: document.head.querySelectorAll('script[type="application/ld+json"]').length,
  marked: document.head.querySelectorAll('[data-prerender]').length,
});

describe('resetPrerenderedHead', () => {
  beforeEach(prerenderedHead);

  it('restores the shell defaults and removes the tags the prerender layer added', () => {
    resetPrerenderedHead();
    expect(head()).toEqual({
      title: 'Nhà Đất Chuẩn',
      description: 'Mặc định',
      robots: 'index,follow,max-image-preview:large',
      ogType: 'website',
      ogUrl: null,
      canonical: null,
      jsonLd: 0,
      marked: 0,
    });
  });
});

describe('useDocumentMeta on a prerendered page', () => {
  beforeEach(prerenderedHead);

  it('takes over the prerendered tags and undoes them to the shell defaults, never to the landing page', () => {
    const undo = applyDocumentMeta({
      title: 'Căn hộ A | Nhà Đất Chuẩn',
      canonical: 'https://example.test/listings/a',
      jsonLd: { '@type': 'Product', name: 'A' },
    });
    // one canonical, one JSON-LD (the prerendered copy is replaced, not duplicated)
    expect(document.head.querySelectorAll('link[rel="canonical"]')).toHaveLength(1);
    expect(head().jsonLd).toBe(1);

    undo();
    expect(document.title).toBe('Nhà Đất Chuẩn');
    expect(head().canonical).toBeNull();
    expect(head().jsonLd).toBe(0);
  });
});

describe('resetPrerenderedHeadOnNavigation', () => {
  beforeEach(prerenderedHead);

  it('keeps the landing metadata until the first navigation to another URL, then resets once', () => {
    let listener: ((state: { location: { pathname: string; search: string } }) => void) | null = null;
    const unsubscribe = vi.fn();
    const router = {
      subscribe: vi.fn((next: typeof listener) => {
        listener = next;
        return unsubscribe;
      }),
    };
    window.history.replaceState(null, '', '/listings/a');
    resetPrerenderedHeadOnNavigation(router);

    listener!({ location: { pathname: '/listings/a', search: '' } });
    expect(head().canonical).toBe('https://example.test/listings/a');

    listener!({ location: { pathname: '/search', search: '?purpose=SALE' } });
    expect(head().canonical).toBeNull();
    expect(head().title).toBe('Nhà Đất Chuẩn');
  });

  it('does nothing on a page that was not prerendered', () => {
    document.head.innerHTML = '<title>Nhà Đất Chuẩn</title>';
    const router = { subscribe: vi.fn(() => () => undefined) };
    resetPrerenderedHeadOnNavigation(router);
    expect(router.subscribe).not.toHaveBeenCalled();
  });
});
