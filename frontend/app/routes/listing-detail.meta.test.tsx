import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { ToastProvider } from '@/shared/ui/Toast';
import { ListingDetailPage } from './_public.listings.$listingId';

const listing = {
  id: '5b1d6f0e-2f55-4c1e-9d0a-2a6f5d7e8c01',
  slug: 'can-ho-2pn-cau-giay-2601',
  title: 'Căn hộ 2PN Cầu Giấy view thoáng',
  purpose: 'SALE',
  propertyType: 'APARTMENT',
  price: { amount: 3_950_000_000, currency: 'VND', period: null },
  unitPrice: { amount: 56_428_571, per: 'M2' },
  areaM2: 70,
  bedrooms: 2,
  bathrooms: 2,
  location: {
    districtCode: '005',
    districtName: 'Cầu Giấy',
    addressSummary: 'Dịch Vọng, Cầu Giấy, Hà Nội',
    precision: 'APPROXIMATE',
  },
  image: null,
  imageCount: 0,
  trust: { identity: { status: 'VERIFIED' }, listing: { status: 'CHECKED' }, ownership: { status: 'NOT_SUBMITTED' } },
  freshness: { publishedAt: '2026-09-01T03:00:00Z', updatedAt: '2026-09-01T03:00:00Z' },
  seller: { id: 'owner-1', name: 'Môi giới Demo', role: 'BROKER' },
  project: null,
  priceChange: null,
  description: 'Căn hộ hai phòng ngủ.',
  images: [],
  facts: { bedrooms: 2, bathrooms: 2 },
  rentTerms: null,
  legal: null,
  furnishing: null,
  revisionNumber: 1,
};

function mockApi({ found }: { found: boolean }) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (found && url.endsWith(`/api/v2/listings/${listing.slug}`)) return Response.json(listing);
      if (found && url.includes(`/api/v2/listings/${listing.id}/price-history`)) {
        return Response.json({ listingId: listing.id, purpose: 'SALE', points: [] });
      }
      if (found && url.includes(`/api/v2/listings/${listing.id}/similar`)) return Response.json([]);
      return Response.json(
        { title: 'Not found', status: 404, detail: 'Không tìm thấy', code: 'LISTING_NOT_FOUND' },
        { status: 404 },
      );
    }),
  );
}

function renderDetail(slug: string) {
  return render(
    <MemoryRouter initialEntries={[`/listings/${slug}`]}>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path="/listings/:listingId" element={<ListingDetailPage />} />
            <Route path="/search" element={<h1>Trang tìm kiếm</h1>} />
            <Route path="/" element={<h1>Trang chủ</h1>} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const canonical = () => document.head.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? null;
const robots = () => document.head.querySelector('meta[name="robots"]')?.getAttribute('content');
const jsonLdCount = () => document.head.querySelectorAll('script[type="application/ld+json"]').length;

describe('listing detail metadata (F16.2)', () => {
  beforeEach(() => {
    document.head.innerHTML = '<meta name="robots" content="index,follow,max-image-preview:large">';
    document.title = 'Nhà Đất Chuẩn';
    sessionStorage.clear();
    vi.stubGlobal('scrollTo', vi.fn());
  });

  it('sets canonical, title and JSON-LD for the listing and removes them when the visitor leaves', async () => {
    mockApi({ found: true });
    renderDetail(listing.slug);

    expect(await screen.findByRole('heading', { level: 1, name: listing.title })).toBeInTheDocument();
    // Metadata is applied in an effect right after the page renders.
    await waitFor(() => expect(document.title).toBe(`${listing.title} | Nhà Đất Chuẩn`));
    expect(canonical()).toBe(`http://localhost:3000/listings/${listing.slug}`);
    expect(jsonLdCount()).toBe(1);

    fireEvent.click(screen.getByRole('link', { name: /Quay lại danh sách/ }));
    expect(await screen.findByRole('heading', { name: 'Trang tìm kiếm' })).toBeInTheDocument();

    await waitFor(() => expect(canonical()).toBeNull());
    expect(jsonLdCount()).toBe(0);
    expect(robots()).toBe('index,follow,max-image-preview:large');
    expect(document.title).toBe('Nhà Đất Chuẩn');
  });

  it('marks a missing listing noindex only while it is shown', async () => {
    mockApi({ found: false });
    renderDetail('tin-khong-ton-tai');

    expect(await screen.findByText('Không tìm thấy bất động sản')).toBeInTheDocument();
    await waitFor(() => expect(robots()).toBe('noindex'));
    expect(canonical()).toBeNull();

    fireEvent.click(screen.getByRole('link', { name: 'Quay lại trang chủ' }));
    expect(await screen.findByRole('heading', { name: 'Trang chủ' })).toBeInTheDocument();
    await waitFor(() => expect(robots()).toBe('index,follow,max-image-preview:large'));
  });
});
