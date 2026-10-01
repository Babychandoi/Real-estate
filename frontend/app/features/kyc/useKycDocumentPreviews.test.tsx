import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '@/shared/api/client';
import { useKycDocumentPreviews } from './useKycDocumentPreviews';

vi.mock('@/shared/api/client', () => ({ apiFetch: vi.fn() }));
afterEach(() => vi.useRealTimers());

function urls() {
  const create = vi.fn(() => 'blob:private');
  const revoke = vi.fn();
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL = create;
      static revokeObjectURL = revoke;
    },
  );
  return { create, revoke };
}
const documents = { front: '/private/front' };

describe('private KYC previews', () => {
  it('removes and revokes displayed images at grant expiry', async () => {
    const { revoke } = urls();
    vi.mocked(apiFetch).mockResolvedValue(new Response(new Blob(['image'])));
    const access = { token: 'test', expiresAt: new Date(Date.now() + 60000).toISOString() };
    const { result } = renderHook(() => useKycDocumentPreviews(access, documents));
    await waitFor(() => expect(result.current.previews.front).toBe('blob:private'));
    vi.useFakeTimers();
    vi.setSystemTime(Date.parse(access.expiresAt) + 1);
    act(() => window.dispatchEvent(new Event('focus')));
    expect(result.current.expired).toBe(true);
    expect(result.current.previews).toEqual({});
    expect(revoke).toHaveBeenCalledWith('blob:private');
  });

  it('does not create URLs for a response arriving after unmount', async () => {
    const { create } = urls();
    let resolve!: (response: Response) => void;
    vi.mocked(apiFetch).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const access = { token: 'late', expiresAt: new Date(Date.now() + 60000).toISOString() };
    const { unmount } = renderHook(() => useKycDocumentPreviews(access, documents));
    unmount();
    await act(async () => {
      resolve(new Response(new Blob(['late'])));
    });
    expect(create).not.toHaveBeenCalled();
  });

  it('refuses expired grants without fetching private images', () => {
    vi.mocked(apiFetch).mockClear();
    const access = { token: 'expired', expiresAt: '2020-01-01T00:00:00Z' };
    const { result } = renderHook(() => useKycDocumentPreviews(access, documents));
    expect(result.current.expired).toBe(true);
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it('exposes load errors so the page can offer reauthentication', async () => {
    urls();
    vi.mocked(apiFetch).mockRejectedValue(new Error('offline'));
    const access = { token: 'failure', expiresAt: new Date(Date.now() + 60000).toISOString() };
    const { result } = renderHook(() => useKycDocumentPreviews(access, documents));
    await waitFor(() => expect(result.current.error).not.toBe(''));
    expect(result.current.previews).toEqual({});
  });
});
