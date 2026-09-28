import { describe, expect, it } from 'vitest';
import { listingDocumentMeta } from './seo';
import type { ListingDetail } from './types';

const base: ListingDetail = {
  id: '11111111-1111-4111-8111-111111111111',
  ownerId: 'owner-1',
  slug: 'can-ho-2pn-quan-1',
  title: 'Căn hộ 2PN Quận 1',
  purpose: 'SALE',
  propertyType: 'APARTMENT',
  priceVnd: 3_950_000_000,
  areaM2: 70,
  isVerified: true,
  addressSummary: 'Quận 1, TP.HCM',
  status: 'PUBLISHED',
  revisionNumber: 1,
  revisionStatus: 'PUBLISHED',
  imageUrls: ['/media/a.jpg'],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('listingDocumentMeta (m13)', () => {
  it('keeps the "/tháng" period for a RENT listing in the description and OG description', () => {
    const meta = listingDocumentMeta({ ...base, purpose: 'RENT', priceVnd: 14_500_000 }, 'https://example.com');
    expect(meta.description).toContain('14,5 triệu/tháng');
    expect(meta.og?.description).toContain('14,5 triệu/tháng');
  });

  it('does not add a period for a SALE listing', () => {
    const meta = listingDocumentMeta(base, 'https://example.com');
    expect(meta.description).toContain('3,95 tỷ');
    expect(meta.description).not.toContain('/tháng');
  });

  it('adds a monthly priceSpecification to JSON-LD only for RENT', () => {
    const rent = listingDocumentMeta({ ...base, purpose: 'RENT', priceVnd: 14_500_000 }, 'https://example.com');
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
    const meta = listingDocumentMeta({ ...base, imageUrls: ['https://cdn.example.com/a.jpg'] }, 'https://example.com');
    expect(meta.og?.image).toBe('https://cdn.example.com/a.jpg');
  });
});
