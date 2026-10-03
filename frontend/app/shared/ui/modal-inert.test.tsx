import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Dialog } from './Dialog';
import { Sheet } from './Sheet';
import { ToastProvider } from './Toast';

// Review of PR #25 (major 2): with a modal open, the page behind it is inert and aria-hidden, so neither Tab, the
// pointer nor a screen reader's virtual cursor can reach it; nested modals hide the lower one; closing restores
// exactly what was there before (including an element that was already aria-hidden).

function Page({ dialog, sheet }: { dialog: boolean; sheet: boolean }) {
  return (
    <ToastProvider>
      <header data-testid="header">
        <a href="/khu-vuc">Khu vực</a>
      </header>
      <div data-testid="decor" aria-hidden="true" />
      <Dialog open={dialog} onClose={vi.fn()} title="Đăng nhập">
        <button type="button">Đăng nhập</button>
      </Dialog>
      <Sheet open={sheet} onClose={vi.fn()} title="Bộ lọc">
        <button type="button">Áp dụng</button>
      </Sheet>
    </ToastProvider>
  );
}

const layerOf = (name: string) => screen.getByRole('dialog', { name }).closest('[role="presentation"]')!;

describe('modal background is inert (useModal)', () => {
  it('hides the page behind one modal and restores it on close', () => {
    const { container, rerender } = render(<Page dialog sheet={false} />);
    // The app's own container (where the page lives) is a sibling of the portalled dialog layer.
    expect(container).toHaveAttribute('inert');
    expect(container).toHaveAttribute('aria-hidden', 'true');
    expect(layerOf('Đăng nhập')).not.toHaveAttribute('inert');
    // The toast live region stays reachable for announcements.
    expect(document.querySelector('[data-modal-keep]')).not.toHaveAttribute('inert');
    // In the accessibility tree only the dialog's link-free content remains.
    expect(screen.queryByRole('link', { name: 'Khu vực' })).toBeNull();

    rerender(<Page dialog={false} sheet={false} />);
    expect(container).not.toHaveAttribute('inert');
    expect(container).not.toHaveAttribute('aria-hidden');
    expect(screen.getByRole('link', { name: 'Khu vực' })).toBeInTheDocument();
  });

  it('a nested modal hides the lower one; closing it gives the lower one back, still hiding the page', () => {
    const { container, rerender } = render(<Page dialog sheet={false} />);
    rerender(<Page dialog sheet />);
    expect(layerOf('Bộ lọc')).not.toHaveAttribute('inert');
    expect(
      document.querySelector('[role="dialog"][aria-labelledby]')!.closest('[role="presentation"]'),
    ).toHaveAttribute('inert');
    rerender(<Page dialog sheet={false} />);
    expect(layerOf('Đăng nhập')).not.toHaveAttribute('inert');
    expect(container).toHaveAttribute('inert');
    rerender(<Page dialog={false} sheet={false} />);
    expect(container).not.toHaveAttribute('inert');
  });

  it('keeps an element that was aria-hidden before as it was', () => {
    const { rerender } = render(<Page dialog sheet={false} />);
    rerender(<Page dialog={false} sheet={false} />);
    expect(screen.getByTestId('decor')).toHaveAttribute('aria-hidden', 'true');
  });
});
