import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '@/shared/auth/AuthContext';
import { LoginModal } from '@/shared/auth/LoginModal';
import { Sheet } from './Sheet';

/**
 * M2: every modal in the app — the kit's own Dialog/Sheet and hand-rolled ones such as the global LoginModal —
 * shares one stack (`useModal`), so opening one while another is open never breaks either: Escape and the Tab
 * trap only ever apply to whichever opened last, and closing it returns focus correctly.
 */
function OpenLoginModalButton() {
  const { setIsLoginModalOpen } = useAuth();
  return (
    <button type="button" onClick={() => setIsLoginModalOpen(true)}>
      Mở đăng nhập
    </button>
  );
}

function Harness({ sheetOpen }: { sheetOpen: boolean }) {
  return (
    <MemoryRouter>
      <AuthProvider>
        <OpenLoginModalButton />
        <Sheet open={sheetOpen} onClose={() => undefined} title="Bộ lọc">
          <p>Nội dung bộ lọc</p>
        </Sheet>
        <LoginModal />
      </AuthProvider>
    </MemoryRouter>
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('shared modal stack (M2)', () => {
  it('a login dialog opened over a Sheet takes focus, and Escape closes only the top-most one', () => {
    render(<Harness sheetOpen />);
    const sheet = screen.getByRole('dialog', { name: 'Bộ lọc' });
    expect(sheet).toBeVisible();

    fireEvent.click(
      // The page behind an open modal is inert/aria-hidden (useModal); the harness opens the login dialog the way the
      // app does (from code, e.g. a 401), so the hidden button is queried with hidden: true.
      screen.getByRole('button', { name: 'Mở đăng nhập', hidden: true }),
    );
    const login = screen.getByRole('dialog', { name: 'Đăng nhập' });
    expect(login).toBeVisible();
    // Focus must land inside the newest (top-most) modal, not be pulled back into the Sheet underneath.
    expect(login.contains(document.activeElement)).toBe(true);

    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Đăng nhập' })).toBeNull();
    expect(screen.getByRole('dialog', { name: 'Bộ lọc' })).toBeVisible();

    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
    // The Sheet's onClose is a no-op in this harness, so it stays open; the point is that the first Escape did
    // not also close it.
    expect(screen.getByRole('dialog', { name: 'Bộ lọc' })).toBeVisible();
  });

  it('Tab stays inside the login dialog while the Sheet is open behind it', () => {
    render(<Harness sheetOpen />);
    fireEvent.click(
      // The page behind an open modal is inert/aria-hidden (useModal); the harness opens the login dialog the way the
      // app does (from code, e.g. a 401), so the hidden button is queried with hidden: true.
      screen.getByRole('button', { name: 'Mở đăng nhập', hidden: true }),
    );
    const login = screen.getByRole('dialog', { name: 'Đăng nhập' });
    const closeButton = screen.getByRole('button', { name: 'Đóng hộp thoại đăng nhập' });

    closeButton.focus();
    fireEvent.keyDown(closeButton, { key: 'Tab' });
    // Focus must not escape into the Sheet: it should still be somewhere inside the login dialog.
    expect(login.contains(document.activeElement)).toBe(true);
  });

  it('a Sheet opened after the login dialog becomes the top of the stack', () => {
    const { rerender } = render(<Harness sheetOpen={false} />);
    fireEvent.click(
      // The page behind an open modal is inert/aria-hidden (useModal); the harness opens the login dialog the way the
      // app does (from code, e.g. a 401), so the hidden button is queried with hidden: true.
      screen.getByRole('button', { name: 'Mở đăng nhập', hidden: true }),
    );
    expect(screen.getByRole('dialog', { name: 'Đăng nhập' })).toBeVisible();

    rerender(<Harness sheetOpen />);
    const sheet = screen.getByRole('dialog', { name: 'Bộ lọc' });
    expect(sheet.contains(document.activeElement)).toBe(true);

    // The login dialog below is now hidden from assistive tech and the pointer while the Sheet is on top.
    const lower = screen.getByRole('dialog', { name: 'Đăng nhập', hidden: true });
    expect(lower.closest('[inert]')).not.toBeNull();

    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
    // Sheet's onClose is a no-op here, so only the fact that the login dialog was untouched matters.
    expect(lower).toBeVisible();
  });
});
