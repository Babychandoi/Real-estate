import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { Dialog } from './Dialog';
import { Sheet } from './Sheet';
import { Tabs } from './Tabs';
import { focusableWithin } from './internal/useModal';

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
    // Default initial focus goes to the title, not the close button (NIT): the least meaningful thing to land on.
    expect(screen.getByRole('heading', { name: 'Báo cáo tin vi phạm' })).toHaveFocus();
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

  it('wraps past hidden descendants and roving controls excluded from the tab order', () => {
    render(
      <Dialog open onClose={() => undefined} title="Trang công khai">
        <input aria-label="Nguồn thông tin" />
        <button tabIndex={0}>Tab đang chọn</button>
        <button tabIndex={-1}>Tab khác</button>
        <div style={{ display: 'none' }}>
          <div>
            <button>Thao tác trong phần ẩn</button>
          </div>
        </div>
        <fieldset disabled>
          <button>Thao tác bị vô hiệu</button>
        </fieldset>
      </Dialog>,
    );
    const dialog = screen.getByRole('dialog');
    const close = screen.getByRole('button', { name: 'Đóng hộp thoại' });
    const selected = screen.getByRole('button', { name: 'Tab đang chọn' });
    expect(focusableWithin(dialog)).toEqual([close, screen.getByRole('textbox'), selected]);

    selected.focus();
    fireEvent.keyDown(selected, { key: 'Tab' });
    expect(close).toHaveFocus();
    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true });
    expect(selected).toHaveFocus();
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

  it('is centred with a title bar and footer outside the scrolling body, wide sizes for diff views', () => {
    render(
      <Dialog open onClose={() => undefined} title="Đối chiếu bằng chứng" size="xl" footer={<Button>Xác nhận</Button>}>
        <p>Nội dung dài</p>
      </Dialog>,
    );
    const dialog = screen.getByRole('dialog', { name: 'Đối chiếu bằng chứng' });
    expect(dialog.className).toContain('max-w-4xl');
    expect(dialog.className).toContain('max-h-[min(90dvh,calc(100dvh-2rem))]');
    // The portal root centres the panel (never a right-hand edge) and keeps a margin to the viewport.
    expect(dialog.parentElement!.className).toContain('items-center justify-center');
    const body = screen.getByText('Nội dung dài').parentElement!;
    expect(body.className).toContain('overflow-y-auto');
    expect(dialog.lastElementChild).toContainElement(screen.getByRole('button', { name: 'Xác nhận' }));
    expect(dialog.lastElementChild).not.toBe(body);
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
