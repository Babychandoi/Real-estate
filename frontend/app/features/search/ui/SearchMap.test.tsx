import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { MapResponseV2 } from '@/entities/listing/model/v2';
import { DEFAULT_FILTERS } from '../filterSchema';
import SearchMap from './SearchMap';
vi.mock('@/entities/listing/api/listingV2Api', () => ({ listingV2Api: { map: vi.fn() } }));
vi.mock('maplibre-gl', () => ({
  setWorkerUrl: vi.fn(),
  NavigationControl: class {},
  Map: class {
    addControl() {}
    on(event: string, callback: () => void) {
      if (event === 'load') callback();
    }
    getBounds() {
      return { getWest: () => 105.8, getSouth: () => 21, getEast: () => 105.9, getNorth: () => 21.1 };
    }
    getZoom() {
      return 12;
    }
    remove() {}
  },
}));
const emptyMap = (total: number): MapResponseV2 => ({
  mode: 'points',
  points: [],
  clusters: [],
  total: { value: total, relation: 'eq' },
  engine: 'database',
  dataAsOf: '',
});
const props = { selectedId: null, onViewportChange: vi.fn(), onSelectPoint: vi.fn() };
it('rejects the late map response from the previous filter set', async () => {
  let resolveOld!: (value: MapResponseV2) => void;
  vi.mocked(listingV2Api.map)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
    )
    .mockResolvedValueOnce(emptyMap(7));
  const { rerender } = render(<SearchMap {...props} filters={DEFAULT_FILTERS} />);
  await waitFor(() => expect(listingV2Api.map).toHaveBeenCalledTimes(1));
  rerender(<SearchMap {...props} filters={{ ...DEFAULT_FILTERS, purpose: 'RENT' }} />);
  await screen.findByText('7 tin trong vùng này');
  await act(async () => resolveOld(emptyMap(99)));
  expect(screen.queryByText('99 tin trong vùng này')).not.toBeInTheDocument();
});
it('offers retry when the map data request fails', async () => {
  vi.mocked(listingV2Api.map).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(emptyMap(3));
  render(<SearchMap {...props} filters={DEFAULT_FILTERS} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử tải lại bản đồ' }));
  await screen.findByText('3 tin trong vùng này');
});
