import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ClampedText, ExpandableText } from './ClampedText';

const LONG = `${'khongkhoangtrang'.repeat(20)} và một câu tiếng Việt dài để kiểm tra việc cắt dòng`;

describe('ClampedText', () => {
  it('clamps visually but keeps the whole text in the DOM and in the tooltip', () => {
    render(<ClampedText text={LONG} lines={2} />);
    const node = screen.getByText(LONG);
    expect(node).toHaveClass('line-clamp-2');
    expect(node).toHaveAttribute('title', LONG);
  });
});

describe('ExpandableText', () => {
  it('shows a short text as it is, without a toggle', () => {
    render(<ExpandableText text="Ngắn gọn" />);
    expect(screen.getByText('Ngắn gọn')).not.toHaveClass('line-clamp-4');
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('clamps a long text and expands it on request', () => {
    render(<ExpandableText text={LONG} threshold={40} />);
    expect(screen.getByText(LONG)).toHaveClass('line-clamp-4');
    const toggle = screen.getByRole('button', { name: 'Xem thêm' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);
    expect(screen.getByText(LONG)).not.toHaveClass('line-clamp-4');
    expect(screen.getByRole('button', { name: 'Thu gọn' })).toHaveAttribute('aria-expanded', 'true');
  });
});
