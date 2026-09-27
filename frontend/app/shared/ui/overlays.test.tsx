import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { Dialog } from './Dialog';
import { Sheet } from './Sheet';
import { Tabs } from './Tabs';

function DialogHarness({ onClose }: { onClose?: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Mở báo cáo</Button>
      <Dialog
        open={open}
        onClose={() => {
          onClose?.();
          setOpen(false);
        }}
        title="Báo cáo tin vi phạm"
        description="Mô tả ngắn"
        footer={<Button onClick={() => setOpen(false)}>Gửi</Button>}
      >
        <label htmlFor="reason">Lý do</label>
        <input id="reason" />
      </Dialog>
    </>
  );
}

describe('Dialog', () => {
  it('is a labelled modal dialog that takes focus and locks page scroll', () => {
    render(<DialogHarness />);
    fireEvent.click(screen.getByRole('button', { name: 'Mở báo cáo' }));

    const dialog = screen.getByRole('dialog', { name: 'Báo cáo tin vi phạm' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleDescription('Mô tả ngắn');
    // First focusable element: the close button in the header.
    expect(screen.getByRole('button', { name: 'Đóng hộp thoại' })).toHaveFocus();
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('keeps Tab and Shift+Tab inside the dialog', () => {
    render(<DialogHarness />);
    fireEvent.click(screen.getByRole('button', { name: 'Mở báo cáo' }));
    const close = screen.getByRole('button', { name: 'Đóng hộp thoại' });
    const send = screen.getByRole('button', { name: 'Gửi' });

    send.focus();
    fireEvent.keyDown(send, { key: 'Tab' });
    expect(close).toHaveFocus();

    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true });
    expect(send).toHaveFocus();
  });

  it('closes on Escape and returns focus to the opener', () => {
    const onClose = vi.fn();
    render(<DialogHarness onClose={onClose} />);
    const opener = screen.getByRole('button', { name: 'Mở báo cáo' });
    opener.focus();
    fireEvent.click(opener);

    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(opener).toHaveFocus();
    expect(document.body.style.overflow).toBe('');
  });

  it('pulls focus back when it escapes to the page behind', () => {
    render(<DialogHarness />);
    const opener = screen.getByRole('button', { name: 'Mở báo cáo' });
    fireEvent.click(opener);
    opener.focus();
    expect(screen.getByRole('dialog').contains(document.activeElement)).toBe(true);
  });
});

describe('Sheet', () => {
  it('behaves as a modal dialog with a close button', () => {
    const onClose = vi.fn();
    render(
      <Sheet open onClose={onClose} title="Bộ lọc">
        <p>Nội dung</p>
      </Sheet>,
    );
    expect(screen.getByRole('dialog', { name: 'Bộ lọc' })).toHaveAttribute('aria-modal', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes only the top-most modal on Escape', () => {
    const outer = vi.fn();
    const inner = vi.fn();
    render(
      <>
        <Sheet open onClose={outer} title="Bộ lọc" />
        <Dialog open onClose={inner} title="Xác nhận" />
      </>,
    );
    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
    expect(inner).toHaveBeenCalledTimes(1);
    expect(outer).not.toHaveBeenCalled();
  });
});

describe('Tabs', () => {
  const items = [
    { id: 'active', label: 'Đang hiển thị', content: <p>Tin đang hiển thị</p> },
    { id: 'pending', label: 'Chờ duyệt', content: <p>Tin chờ duyệt</p> },
    { id: 'archived', label: 'Lưu trữ', disabled: true, content: <p>Lưu trữ</p> },
    { id: 'draft', label: 'Bản nháp', content: <p>Bản nháp</p> },
  ];

  it('moves selection with the arrow keys, skipping disabled tabs, with a roving tab stop', () => {
    render(<Tabs label="Trạng thái tin" items={items} />);
    const active = screen.getByRole('tab', { name: 'Đang hiển thị' });
    expect(active).toHaveAttribute('aria-selected', 'true');
    expect(active).toHaveAttribute('tabindex', '0');
    expect(screen.getByRole('tabpanel', { name: 'Đang hiển thị' })).toHaveTextContent('Tin đang hiển thị');

    fireEvent.keyDown(active, { key: 'ArrowRight' });
    const pending = screen.getByRole('tab', { name: 'Chờ duyệt' });
    expect(pending).toHaveAttribute('aria-selected', 'true');
    expect(pending).toHaveFocus();
    expect(active).toHaveAttribute('tabindex', '-1');

    fireEvent.keyDown(pending, { key: 'ArrowRight' });
    expect(screen.getByRole('tab', { name: 'Bản nháp' })).toHaveAttribute('aria-selected', 'true');

    fireEvent.keyDown(screen.getByRole('tab', { name: 'Bản nháp' }), { key: 'Home' });
    expect(active).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(active, { key: 'ArrowLeft' });
    expect(screen.getByRole('tab', { name: 'Bản nháp' })).toHaveAttribute('aria-selected', 'true');
  });
});
