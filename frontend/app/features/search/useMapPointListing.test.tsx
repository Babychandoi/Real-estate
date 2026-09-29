import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingDetailV2, ListingSummaryV2 } from '@/entities/listing/model/v2';
import { useMapPointListing } from './useMapPointListing';

vi.mock('@/entities/listing/api/listingV2Api', () => ({
  listingV2Api: { detail: vi.fn() },
}));

describe('map point selection', () => {
  it('ignores late responses after selection changes and aborts on close', async () => {
    const pending = new Map<string, (value: ListingDetailV2) => void>();
    const signals = new Map<string, AbortSignal>();
    vi.mocked(listingV2Api.detail).mockImplementation((id, signal) => {
      signals.set(id, signal!);
      return new Promise((resolve) => pending.set(id, resolve));
    });
    const items: ListingSummaryV2[] = [];
    const { result, rerender } = renderHook(({ id }: { id: string | null }) => useMapPointListing(id, items), {
      initialProps: { id: 'a' as string | null },
    });
    rerender({ id: 'b' });
    expect(signals.get('a')?.aborted).toBe(true);
    await act(async () => pending.get('b')!({ id: 'b' } as ListingDetailV2));
    await waitFor(() => expect(result.current?.id).toBe('b'));
    // Simulate a transport that resolves despite cancellation.
    await act(async () => pending.get('a')!({ id: 'a' } as ListingDetailV2));
    expect(result.current?.id).toBe('b');
    rerender({ id: 'c' });
    expect(result.current).toBeNull();
    rerender({ id: null });
    expect(signals.get('c')?.aborted).toBe(true);
    await act(async () => pending.get('c')!({ id: 'c' } as ListingDetailV2));
    expect(result.current).toBeNull();
  });

  it('uses an already loaded card without another detail request', () => {
    vi.mocked(listingV2Api.detail).mockClear();
    const item = { id: 'known' } as ListingSummaryV2;
    const { result } = renderHook(() => useMapPointListing('known', [item]));
    expect(result.current).toBe(item);
    expect(listingV2Api.detail).not.toHaveBeenCalled();
  });
});
