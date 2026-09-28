import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Gallery } from './Gallery';

const images = Array.from({ length: 20 }, (_, index) => ({
  url: `https://img.test/${index + 1}.jpg`,
  srcset: [
    { url: `https://img.test/${index + 1}__w320.webp`, width: 320 },
    { url: `https://img.test/${index + 1}__w1600.webp`, width: 1600 },
  ],
}));

describe('Gallery (F14.1, F14.3)', () => {
  it('shows all 20 photos in the lightbox with counter, arrow keys, alt text and focus return', () => {
    render(<Gallery images={images} title="Căn hộ thử" />);
    const hero = screen.getByRole('img', { name: 'Ảnh 1/20 của Căn hộ thử' });
    expect(hero).toHaveAttribute('fetchpriority', 'high');
    expect(hero.getAttribute('srcset')).toContain('__w320.webp 320w');
    expect(hero).toHaveAttribute('sizes');

    const open = screen.getByRole('button', { name: 'Xem tất cả 20 ảnh' });
    open.focus();
    fireEvent.click(open);
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('Ảnh 1/20');
    expect(screen.getAllByRole('button', { name: /^Xem ảnh \d+\/20$/ })).toHaveLength(20);

    fireEvent.keyDown(window, { key: 'ArrowRight' });
    expect(dialog).toHaveTextContent('Ảnh 2/20');
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    expect(dialog).toHaveTextContent('Ảnh 20/20');
    expect(screen.getByRole('img', { name: 'Ảnh 20/20 của Căn hộ thử' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Xem ảnh 5/20' }));
    expect(dialog).toHaveTextContent('Ảnh 5/20');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(open).toHaveFocus();
  });

  it('says so when a listing has no photo', () => {
    render(<Gallery images={[]} title="Đất trống" />);
    expect(screen.getByRole('img', { name: 'Đất trống: chưa có ảnh' })).toBeInTheDocument();
  });
});
