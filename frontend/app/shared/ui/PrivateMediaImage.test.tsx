import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { apiFetch } from '@/shared/api/client';
import { PrivateMediaImage } from './PrivateMediaImage';
vi.mock('@/shared/api/client', () => ({ apiFetch: vi.fn() }));
afterEach(() => vi.useRealTimers());

it('revokes and hides staff evidence when its grant expires', async () => {
  const revoke = vi.fn();
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL = vi.fn(() => 'blob:evidence');
      static revokeObjectURL = revoke;
    },
  );
  vi.mocked(apiFetch).mockResolvedValue(new Response(new Blob(['image'])));
  const expires = Date.now() + 60000;
  render(
    <PrivateMediaImage src="/private" alt="Evidence" accessToken="grant" expiresAt={new Date(expires).toISOString()} />,
  );
  await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:evidence'));
  vi.useFakeTimers();
  vi.setSystemTime(expires + 1);
  act(() => window.dispatchEvent(new Event('focus')));
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.getByText('Quyền xem ảnh đã hết hạn')).toBeInTheDocument();
  expect(revoke).toHaveBeenCalledWith('blob:evidence');
});

it('ignores a response that arrives after the evidence dialog closes', async () => {
  const create = vi.fn();
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL = create;
      static revokeObjectURL = vi.fn();
    },
  );
  let resolve!: (response: Response) => void;
  vi.mocked(apiFetch).mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const { unmount } = render(<PrivateMediaImage src="/private" alt="Evidence" />);
  unmount();
  await act(async () => {
    resolve(new Response(new Blob(['image'])));
  });
  expect(create).not.toHaveBeenCalled();
});
