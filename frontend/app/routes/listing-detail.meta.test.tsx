import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { ListingDetailPage } from './_public.listings.$listingId';

const listing = {
  id: '5b1d6f0e-2f55-4c1e-9d0a-2a6f5d7e8c01',
  slug: 'can-ho-2pn-cau-giay-2601',
  title: 'Căn hộ 2PN Cầu Giấy view thoáng',
  purpose: 'SALE',
  propertyType: 'APARTMENT',
  priceVnd: 3_950_000_000,
  areaM2: 70,
  addressSummary: 'Dịch Vọng, Cầu Giấy, Hà Nội',
  description: 'Căn hộ hai phòng ngủ.',
  isVerified: false,
  ownerId: 'owner-1',
  status: 'ACTIVE',
  revisionNumber: 1,
  revisionStatus: 'APPROVED',
  imageUrls: [],
  createdAt: '2026-09-01T03:00:00Z',
  updatedAt: '2026-09-01T03:00:00Z',
};

function mockApi({ found }: { found: boolean }) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (found && url.endsWith(`/listings/by-slug/${listing.slug}`)) return Response.json(listing);
      if (found && url.endsWith('/public/profiles/owner-1')) {
        return Response.json({
          displayName: 'Môi giới Demo',
          identityVerified: true,
          activeListingCount: 3,
          memberSince: '2026-01-01T00:00:00Z',
        });
      }
      return Response.json({ title: 'Not found', status: 404, detail: 'Không tìm thấy' }, { status: 404 });
    }),
  );
}

function renderDetail(slug: string) {
  return render(
    <MemoryRouter initialEntries={[`/listings/${slug}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/listings/:listingId" element={<ListingDetailPage />} />
          <Route path="/search" element={<h1>Trang tìm kiếm</h1>} />
          <Route path="/" element={<h1>Trang chủ</h1>} />
        </Routes>
      </AuthProvider>
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
