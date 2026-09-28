import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { applyDocumentMeta, serializeJsonLd, useDocumentMeta, type DocumentMeta } from './useDocumentMeta';

// The defaults index.html ships with.
function resetHead() {
  document.head.innerHTML = `
    <meta name="description" content="Mặc định">
    <meta name="robots" content="index,follow,max-image-preview:large">
    <meta property="og:type" content="website">`;
  document.title = 'Nhà Đất Chuẩn';
}

const head = () => ({
  title: document.title,
  description: document.head.querySelector('meta[name="description"]')?.getAttribute('content'),
  robots: document.head.querySelector('meta[name="robots"]')?.getAttribute('content'),
  canonical: document.head.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? null,
  ogType: document.head.querySelector('meta[property="og:type"]')?.getAttribute('content'),
  ogTitle: document.head.querySelector('meta[property="og:title"]')?.getAttribute('content') ?? null,
  jsonLd: Array.from(document.head.querySelectorAll('script[type="application/ld+json"]')).map((script) =>
    JSON.parse(script.textContent ?? 'null'),
  ),
});

const detailMeta: DocumentMeta = {
  title: 'Căn hộ 2PN | Nhà Đất Chuẩn',
  description: 'Căn hộ cần bán tại Cầu Giấy',
  canonical: '/listings/can-ho-2pn',
  robots: 'noindex',
  og: { title: 'Căn hộ 2PN', type: 'product', url: '/listings/can-ho-2pn' },
  jsonLd: { '@type': 'Product', name: 'Căn hộ 2PN' },
};

describe('applyDocumentMeta', () => {
  beforeEach(resetHead);

  it('sets every field and restores the index.html defaults afterwards', () => {
    const before = head();
    const undo = applyDocumentMeta(detailMeta);

    expect(head()).toEqual({
      title: 'Căn hộ 2PN | Nhà Đất Chuẩn',
      description: 'Căn hộ cần bán tại Cầu Giấy',
      robots: 'noindex',
      canonical: 'http://localhost:3000/listings/can-ho-2pn',
      ogType: 'product',
      ogTitle: 'Căn hộ 2PN',
      jsonLd: [{ '@type': 'Product', name: 'Căn hộ 2PN' }],
    });

    undo();
    expect(head()).toEqual(before);
    expect(document.head.querySelectorAll('[data-document-meta]')).toHaveLength(0);
  });

  it('leaves tags it was not asked to change alone', () => {
    const undo = applyDocumentMeta({ title: 'Chỉ tiêu đề' });
    expect(head().robots).toBe('index,follow,max-image-preview:large');
    expect(head().canonical).toBeNull();
    undo();
    expect(document.title).toBe('Nhà Đất Chuẩn');
  });

  it('cannot close the JSON-LD script from inside a value', () => {
    expect(serializeJsonLd({ name: '</script><script>alert(1)</script>' })).not.toContain('</script>');
  });
});

describe('useDocumentMeta', () => {
  beforeEach(resetHead);

  it('replaces instead of stacking metadata when the route data changes', () => {
    const { rerender, unmount } = renderHook(({ meta }) => useDocumentMeta(meta), {
      initialProps: { meta: detailMeta as DocumentMeta | null },
    });
    rerender({
      meta: { ...detailMeta, canonical: '/listings/nha-pho-4-tang', jsonLd: { '@type': 'Product', name: 'Nhà phố' } },
    });

    expect(head().canonical).toBe('http://localhost:3000/listings/nha-pho-4-tang');
    expect(head().jsonLd).toEqual([{ '@type': 'Product', name: 'Nhà phố' }]);
    expect(document.head.querySelectorAll('link[rel="canonical"]')).toHaveLength(1);

    unmount();
    expect(head().canonical).toBeNull();
    expect(head().jsonLd).toEqual([]);
    expect(head().robots).toBe('index,follow,max-image-preview:large');
  });

  it('keeps the defaults while data is loading (null)', () => {
    renderHook(() => useDocumentMeta(null));
    expect(head().title).toBe('Nhà Đất Chuẩn');
    expect(head().canonical).toBeNull();
  });
});
