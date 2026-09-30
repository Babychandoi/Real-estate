import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingSummaryV2, SearchResponseV2 } from '@/entities/listing/model/v2';
import { DEFAULT_FILTERS } from './filterSchema';
import type * as FilterSchema from './filterSchema';
import { useListingSearch } from './useListingSearch';

vi.mock('@/entities/listing/api/listingV2Api', () => ({
  listingV2Api: { search: vi.fn() },
}));
vi.mock('@/shared/analytics/track', () => ({ track: vi.fn() }));
vi.mock('./filterSchema', async (importOriginal) => ({
  ...(await importOriginal<typeof FilterSchema>()),
  filterHash: vi.fn().mockResolvedValue('test-filter-hash'),
}));

function page(ids: string[], cursor: string | null = null): SearchResponseV2 {
  return {
    items: ids.map((id) => ({ id }) as ListingSummaryV2),
    pageInfo: { hasNext: cursor !== null, nextCursor: cursor, size: 24 },
    total: { value: ids.length, relation: 'eq' },
    queryVersion: 'v2',
    dataAsOf: '2026-09-29T00:00:00Z',
    engine: 'search',
    degraded: false,
    notices: [],
    suggestions: [],
  };
}

describe('search cursor lifecycle', () => {
  it('does not append a late sale page after switching to rent', async () => {
    let resolveMore!: (value: SearchResponseV2) => void;
    let moreSignal: AbortSignal | undefined;
    vi.mocked(listingV2Api.search).mockImplementation((params, signal) => {
      if (params.has('cursor')) {
        moreSignal = signal;
        return new Promise((resolve) => {
          resolveMore = resolve;
        });
      }
      return Promise.resolve(params.get('purpose') === 'RENT' ? page(['rent']) : page(['sale'], 'next'));
    });
    const { result, rerender } = renderHook(
      ({ purpose }: { purpose: 'SALE' | 'RENT' }) =>
        useListingSearch({ ...DEFAULT_FILTERS, purpose }, 'filter-race-test'),
      { initialProps: { purpose: 'SALE' as 'SALE' | 'RENT' } },
    );
    await waitFor(() => expect(result.current.state.status).toBe('ready'));
    let loading!: Promise<void>;
    act(() => {
      loading = result.current.loadMore();
    });
    rerender({ purpose: 'RENT' });
    await waitFor(() => expect(result.current.state.items.map((item) => item.id)).toEqual(['rent']));
    expect(moreSignal?.aborted).toBe(true);
    await act(async () => {
      resolveMore(page(['stale-sale']));
      await loading;
    });
    expect(result.current.state.items.map((item) => item.id)).toEqual(['rent']);
    expect(result.current.state.loadingMore).toBe(false);
  });

  it('deduplicates both existing cards and duplicates inside the next page', async () => {
    vi.mocked(listingV2Api.search).mockImplementation((params) =>
      Promise.resolve(params.has('cursor') ? page(['a', 'b', 'b']) : page(['a'], 'next')),
    );
    const { result } = renderHook(() => useListingSearch(DEFAULT_FILTERS, 'dedup-test'));
    await waitFor(() => expect(result.current.state.status).toBe('ready'));
    await act(async () => {
      await result.current.loadMore();
    });
    expect(result.current.state.items.map((item) => item.id)).toEqual(['a', 'b']);
    expect(result.current.state.hasNext).toBe(false);
  });

  it('aborts load-more when a restored history entry is unmounted', async () => {
    vi.mocked(listingV2Api.search).mockResolvedValue(page(['a'], 'next'));
    const first = renderHook(() => useListingSearch(DEFAULT_FILTERS, 'restore-cleanup-test'));
    await waitFor(() => expect(first.result.current.state.status).toBe('ready'));
    first.unmount();
    let signal: AbortSignal | undefined;
    vi.mocked(listingV2Api.search).mockImplementation((_params, nextSignal) => {
      signal = nextSignal;
      return new Promise(() => {});
    });
    const restored = renderHook(() => useListingSearch(DEFAULT_FILTERS, 'restore-cleanup-test'));
    expect(restored.result.current.state.restored).toBe(true);
    act(() => {
      void restored.result.current.loadMore();
    });
    restored.unmount();
    expect(signal?.aborted).toBe(true);
  });
  it('never caches old sale results under a new rent history entry', async () => {
    vi.mocked(listingV2Api.search).mockImplementation((params) =>
      params.get('purpose') === 'RENT' ? new Promise(() => {}) : Promise.resolve(page(['sale'])),
    );
    const first = renderHook(
      ({ purpose }: { purpose: 'SALE' | 'RENT' }) =>
        useListingSearch({ ...DEFAULT_FILTERS, purpose }, 'snapshot-filter-test'),
      { initialProps: { purpose: 'SALE' as 'SALE' | 'RENT' } },
    );
    await waitFor(() => expect(first.result.current.state.status).toBe('ready'));
    first.rerender({ purpose: 'RENT' });
    first.unmount();
    const next = renderHook(() => useListingSearch({ ...DEFAULT_FILTERS, purpose: 'RENT' }, 'snapshot-filter-test'));
    expect(next.result.current.state.restored).toBe(false);
    expect(next.result.current.state.items).toEqual([]);
  });
});
