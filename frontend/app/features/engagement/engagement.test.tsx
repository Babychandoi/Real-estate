import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_FILTERS } from '@/features/search/filterSchema';

const apiClient = vi.fn();
vi.mock('@/shared/api/client', () => ({ apiClient: (...args: unknown[]) => apiClient(...args) }));

const auth = { user: null as null | { id: string }, setIsLoginModalOpen: vi.fn() };
vi.mock('@/shared/auth/AuthContext', () => ({ useAuth: () => auth }));
const toast = { show: vi.fn(), dismiss: vi.fn() };
vi.mock('@/shared/ui/Toast', () => ({ useToast: () => toast }));

const { savedListingsStore } = await import('./savedListingsStore');
const { FavoriteButton } = await import('./FavoriteButton');
const { savedFilterParams } = await import('./ui/SaveSearchDialog');

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('saved listings store and favourite button', () => {
  beforeEach(() => {
    apiClient.mockReset();
    auth.user = null;
    auth.setIsLoginModalOpen.mockReset();
    toast.show.mockReset();
    savedListingsStore.ensure(null);
  });

  it('asks a signed-out visitor to sign in instead of saving', () => {
    render(<FavoriteButton listingId="l-1" title="Căn hộ A" />);
    const button = screen.getByRole('button', { name: 'Lưu tin: Căn hộ A' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(button);
    expect(auth.setIsLoginModalOpen).toHaveBeenCalledWith(true);
    expect(apiClient).not.toHaveBeenCalled();
  });

  it('loads the ids once, toggles optimistically and rolls back when the server refuses', async () => {
    auth.user = { id: 'u-1' };
    apiClient.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === '/me/saved-listings/ids') return { ids: ['l-2'] };
      if (init?.method === 'PUT') throw { problem: { detail: 'Bạn đã lưu tối đa 500 tin.' } };
      return undefined;
    });
    render(
      <>
        <FavoriteButton listingId="l-1" title="A" />
        <FavoriteButton listingId="l-2" title="B" />
      </>,
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Bỏ lưu tin: B' })).toHaveAttribute('aria-pressed', 'true'),
    );
    expect(apiClient.mock.calls.filter(([path]) => path === '/me/saved-listings/ids')).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: 'Lưu tin: A' }));
    await flush();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Lưu tin: A' })).toHaveAttribute('aria-pressed', 'false'),
    );
    expect(toast.show).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'error', description: 'Bạn đã lưu tối đa 500 tin.' }),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Bỏ lưu tin: B' }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Lưu tin: B' })).toHaveAttribute('aria-pressed', 'false'),
    );
    expect(apiClient).toHaveBeenCalledWith('/me/saved-listings/l-2', { method: 'DELETE' });
  });
});

describe('saved search filter', () => {
  it('keeps the shareable filter parameters and drops the UI-only view', () => {
    const params = savedFilterParams({
      ...DEFAULT_FILTERS,
      purpose: 'RENT',
      types: ['HOUSE', 'APARTMENT'],
      districts: ['005'],
      view: 'map',
    });
    expect(params.purpose).toBe('RENT');
    expect(params.district).toBe('005');
    expect(params.type?.split(',').sort()).toEqual(['APARTMENT', 'HOUSE']);
    expect(params).not.toHaveProperty('view');
    expect(params).not.toHaveProperty('cursor');
  });
});
