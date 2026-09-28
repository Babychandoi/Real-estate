import { describe, expect, it } from 'vitest';
import { listingDocumentMeta } from './seo';
import type { ListingDetailV2 } from './v2';

const base: ListingDetailV2 = {
  id: '11111111-1111-4111-8111-111111111111',
  slug: 'can-ho-2pn-cau-giay',
  title: 'Căn hộ 2PN Cầu Giấy',
  purpose: 'SALE',
  propertyType: 'APARTMENT',
  price: { amount: 3_950_000_000, currency: 'VND', period: null },
  unitPrice: { amount: 56_428_571, per: 'M2' },
  areaM2: 70,
  location: {
    districtCode: '005',
    districtName: 'Cầu Giấy',
    addressSummary: 'Dịch Vọng, Cầu Giấy',
    precision: 'APPROXIMATE',
  },
  image: { url: '/media/a.jpg', srcset: [] },
  imageCount: 1,
  trust: {
    identity: { status: 'VERIFIED' },
    listing: { status: 'CHECKED' },
    ownership: { status: 'NOT_SUBMITTED' },
  },
  freshness: { publishedAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  seller: { id: 'owner-1', name: 'Người bán', role: 'BROKER' },
  project: null,
  priceChange: null,
  images: [{ url: '/media/a.jpg', srcset: [] }],
  facts: {},
  rentTerms: null,
  legal: null,
  furnishing: null,
  revisionNumber: 1,
};

const rentListing = (amount: number): ListingDetailV2 => ({
  ...base,
  purpose: 'RENT',
  price: { amount, currency: 'VND', period: 'MONTH' },
  unitPrice: null,
});

describe('listingDocumentMeta (m13)', () => {
  it('keeps the "/tháng" period for a RENT listing in the description and OG description', () => {
    const meta = listingDocumentMeta(rentListing(14_500_000), 'https://example.com');
    expect(meta.description).toContain('14,5 triệu/tháng');
    expect(meta.og?.description).toContain('14,5 triệu/tháng');
  });

  it('does not add a period for a SALE listing', () => {
    const meta = listingDocumentMeta(base, 'https://example.com');
    expect(meta.description).toContain('3,95 tỷ');
    expect(meta.description).not.toContain('/tháng');
  });

  it('adds a monthly priceSpecification to JSON-LD only for RENT', () => {
    const rent = listingDocumentMeta(rentListing(14_500_000), 'https://example.com');
    const rentOffers = (rent.jsonLd as { offers: Record<string, unknown> }).offers;
    expect(rentOffers.priceSpecification).toMatchObject({ unitCode: 'MON' });

    const sale = listingDocumentMeta(base, 'https://example.com');
    const saleOffers = (sale.jsonLd as { offers: Record<string, unknown> }).offers;
    expect(saleOffers.priceSpecification).toBeUndefined();
  });

  it('resolves og:image and JSON-LD image to absolute URLs', () => {
    const meta = listingDocumentMeta(base, 'https://example.com');
    expect(meta.og?.image).toBe('https://example.com/media/a.jpg');
    expect((meta.jsonLd as { image: string[] }).image).toEqual(['https://example.com/media/a.jpg']);
  });

  it('leaves an already-absolute image URL untouched', () => {
    const meta = listingDocumentMeta(
      { ...base, images: [{ url: 'https://cdn.example.com/a.jpg' }] },
      'https://example.com',
    );
    expect(meta.og?.image).toBe('https://cdn.example.com/a.jpg');
  });
});
