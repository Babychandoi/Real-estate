import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingDetailV2 } from '@/entities/listing/model/v2';
import { PropertyComparePage } from './_public.compare';

vi.mock('@/entities/listing/api/listingV2Api', () => ({ listingV2Api: { detail: vi.fn(), search: vi.fn() } }));
vi.mock('@/shared/analytics/track', () => ({ track: vi.fn() }));

function listing(id: string, title: string): ListingDetailV2 {
  return {
    id,
    slug: id,
    title,
    purpose: 'SALE',
    propertyType: 'APARTMENT',
    price: { amount: 2_000_000_000, currency: 'VND', period: null },
    unitPrice: null,
    areaM2: 60,
    location: { addressSummary: 'Hà Nội', precision: 'APPROXIMATE' },
    image: null,
    imageCount: 0,
    images: [],
    facts: {},
    rentTerms: null,
    legal: null,
    furnishing: null,
    revisionNumber: 1,
    trust: {
      identity: { status: 'NOT_SUBMITTED', checkedAt: null, expiresAt: null },
      listing: { status: 'CHECKED', checkedAt: null },
      ownership: { status: 'NOT_SUBMITTED', checkedAt: null, expiresAt: null, documentType: null },
    },
    freshness: { publishedAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z' },
    seller: { id: 'seller', role: 'OWNER' },
    project: null,
    priceChange: null,
  };
}

it('retries only the failed compare column, announces loading, and preserves the ready listing', async () => {
  let resolve!: (value: ListingDetailV2) => void;
  vi.mocked(listingV2Api.detail)
    .mockResolvedValueOnce(listing('first', 'Căn hộ đã tải'))
    .mockRejectedValueOnce(new Error('offline'))
    .mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
  render(
    <MemoryRouter initialEntries={['/compare?ids=first,second']}>
      <PropertyComparePage />
    </MemoryRouter>,
  );
  expect(await screen.findByRole('alert')).toHaveTextContent('Không làm mới được 1 tin (lỗi kết nối).');
  expect(screen.getByRole('link', { name: 'Căn hộ đã tải' })).toBeInTheDocument();
  expect(screen.getByText('Không tải được tin này.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  await waitFor(() => expect(listingV2Api.detail).toHaveBeenCalledTimes(3));
  expect(vi.mocked(listingV2Api.detail).mock.calls.map(([id]) => id)).toEqual(['first', 'second', 'second']);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.getByRole('status')).toContainElement(screen.getByLabelText('Đang tải'));
  expect(screen.getByRole('link', { name: 'Căn hộ đã tải' })).toBeInTheDocument();
  await act(async () => resolve(listing('second', 'Căn hộ sau khi thử lại')));
  expect(screen.getByRole('link', { name: 'Căn hộ sau khi thử lại' })).toBeInTheDocument();
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument();
});
