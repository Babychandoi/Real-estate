import { act, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider, useToast } from './Toast';

function ShowButton(props: Parameters<ReturnType<typeof useToast>['show']>[0]) {
  const toast = useToast();
  return (
    <button type="button" onClick={() => toast.show(props)}>
      show
    </button>
  );
}

describe('Toast timing (WCAG 2.2.1, m11)', () => {
  it('auto-dismisses a plain success/info toast after 6 s', async () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <ShowButton kind="success" title="Đã lưu tin" />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'show' }).click());
    expect(screen.getByText('Đã lưu tin')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(screen.queryByText('Đã lưu tin')).toBeNull();
    vi.useRealTimers();
  });

  it('never auto-dismisses a toast that has an action by default', async () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <ShowButton kind="success" title="Đã xóa tin" action={{ label: 'Hoàn tác', onClick: () => undefined }} />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'show' }).click());
    expect(screen.getByText('Đã xóa tin')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(screen.getByText('Đã xóa tin')).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('floors an explicit duration at 10 s when the toast has an action', async () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <ShowButton kind="info" title="Đã gửi" action={{ label: 'Xem', onClick: () => undefined }} duration={2000} />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'show' }).click());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(screen.getByText('Đã gửi')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(screen.queryByText('Đã gửi')).toBeNull();
    vi.useRealTimers();
  });
});
