import { fireEvent, render, screen, within } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { DataTable, type DataTableSort, type DataTableStatus } from './DataTable';
import { InlineFeedback } from './InlineFeedback';
import { Money, UnitPriceText } from './Money';
import { loadedSummary, pageWindow, Pagination } from './Pagination';
import { imageFromUrl, ResponsiveImage, srcSetOf } from './ResponsiveImage';
import { TrustBadge, TrustPanel, trustLabel } from './TrustBadge';

describe('TrustBadge (contract §6)', () => {
  it('says what was verified, never a bare "Đã xác thực"', () => {
    expect(trustLabel('identity', 'VERIFIED')).toBe('Đã xác minh danh tính người đăng');
    expect(trustLabel('listing', 'CHECKED')).toBe('Nội dung tin đã qua kiểm duyệt');
    expect(trustLabel('ownership', 'VERIFIED')).toBe('Đã đối chiếu giấy tờ chủ sở hữu');
    for (const status of ['VERIFIED', 'PENDING', 'REJECTED', 'EXPIRED', 'NOT_SUBMITTED'] as const) {
      expect(trustLabel('identity', status)).not.toBe('Đã xác thực');
    }
  });

  it('does not reveal a rejected check to visitors', () => {
    expect(trustLabel('identity', 'REJECTED')).toBe(trustLabel('identity', 'NOT_SUBMITTED'));
  });

  it('falls back to the same kind\'s "not submitted" copy for an unrecognised status (m8)', () => {
    // @ts-expect-error — simulating a status value this build does not know about yet
    render(<TrustBadge kind="identity" status="SOMETHING_NEW" />);
    expect(screen.getByText('Chưa xác minh danh tính người đăng')).toBeInTheDocument();
    expect(screen.queryByText('Nội dung tin chưa qua kiểm duyệt')).toBeNull();

    // @ts-expect-error — same for ownership
    render(<TrustBadge kind="ownership" status="SOMETHING_NEW" />);
    expect(screen.getByText('Chưa đối chiếu giấy tờ chủ sở hữu')).toBeInTheDocument();
  });

  it('never shows a check date for a rejected status (m8)', () => {
    render(
      <TrustBadge
        kind="ownership"
        status="REJECTED"
        checkedAt="2026-08-12T02:00:00Z"
        expiresAt="2028-08-12T02:00:00Z"
        detailed
      />,
    );
    const note = screen.getByText(/Đối chiếu tại thời điểm kiểm tra/);
    expect(note).not.toHaveTextContent('Kiểm tra ngày');
    expect(note).not.toHaveTextContent('Hiệu lực đến');
  });

  it('shows the scope note and dates in detailed mode', () => {
    render(
      <TrustBadge
        kind="identity"
        status="VERIFIED"
        checkedAt="2026-08-12T02:00:00Z"
        expiresAt="2028-08-12T02:00:00Z"
        detailed
      />,
    );
    expect(screen.getByText(/Không bảo đảm quyền sở hữu hay pháp lý giao dịch/)).toHaveTextContent(
      'Kiểm tra ngày 12/08/2026. Hiệu lực đến 12/08/2028.',
    );
  });

  it('lists the three checks separately in the panel', () => {
    render(
      <TrustPanel
        trust={{
          identity: { status: 'VERIFIED' },
          listing: { status: 'NOT_CHECKED' },
          ownership: { status: 'REVOKED' },
        }}
      />,
    );
    const panel = screen.getByRole('region', { name: 'Mức độ xác minh' });
    expect(
      within(panel)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      expect.stringContaining('Đã xác minh danh tính người đăng'),
      expect.stringContaining('Nội dung tin chưa qua kiểm duyệt'),
      expect.stringContaining('Kết quả đối chiếu giấy tờ đã bị thu hồi'),
    ]);
  });
});

describe('Money', () => {
  it('renders the contract formats and a neutral fallback', () => {
    render(
      <>
        <Money price={{ amount: 14_500_000, currency: 'VND', period: 'MONTH' }} />
        <Money price={null} />
        <UnitPriceText unitPrice={{ amount: 48_170_000, per: 'M2' }} />
        <UnitPriceText unitPrice={null} />
      </>,
    );
    expect(screen.getByText('14,5 triệu/tháng')).toBeInTheDocument();
    expect(screen.getByText('Chưa có giá')).toBeInTheDocument();
    expect(screen.getByText('~48,2 triệu/m²')).toBeInTheDocument();
  });

  it('never prints "0 ₫" for a zero or negative amount, showing a negotiable fallback instead (m9)', () => {
    render(
      <>
        <Money price={{ amount: 0, currency: 'VND', period: null }} />
        <Money price={{ amount: -1, currency: 'VND', period: null }} zeroFallback="Liên hệ" />
      </>,
    );
    expect(screen.getByText('Thỏa thuận')).toBeInTheDocument();
    expect(screen.getByText('Liên hệ')).toBeInTheDocument();
    expect(screen.queryByText(/^0/)).toBeNull();
  });
});

describe('ResponsiveImage', () => {
  const image = {
    url: '/media/a.jpg',
    width: 1600,
    height: 1000,
    srcset: [
      { url: '/media/a__w640.webp', width: 640 },
      { url: '/media/a__w320.webp', width: 320 },
    ],
    placeholder: { dominantColor: '#c8b8a0' },
  };

  it('builds srcset/sizes from the variants and reserves the aspect ratio', () => {
    render(<ResponsiveImage image={image} alt="Phòng khách" sizes="50vw" aspectRatio="16 / 10" />);
    const img = screen.getByRole('img', { name: 'Phòng khách' });
    expect(img).toHaveAttribute('srcset', '/media/a__w320.webp 320w, /media/a__w640.webp 640w');
    expect(img).toHaveAttribute('sizes', '50vw');
    expect(img).toHaveAttribute('width', '1600');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img.parentElement).toHaveStyle({ aspectRatio: '16 / 10' });
  });

  it('falls back to a labelled tile when the image fails or is missing', () => {
    render(<ResponsiveImage image={image} alt="Phòng khách" />);
    fireEvent.error(screen.getByRole('img', { name: 'Phòng khách' }));
    expect(screen.getByRole('img', { name: 'Phòng khách: không tải được ảnh' })).toBeInTheDocument();

    render(<ResponsiveImage image={null} alt="" />);
    expect(screen.getByRole('img', { name: 'Chưa có ảnh' })).toBeInTheDocument();
  });

  it('reserves space even without an explicit aspect ratio or dimensions (m10)', () => {
    const { rerender } = render(<ResponsiveImage image={{ ...image, width: 0, height: 0 }} alt="Phòng khách" />);
    expect(screen.getByRole('img', { name: 'Phòng khách' }).parentElement).toHaveStyle({ aspectRatio: '4 / 3' });

    rerender(<ResponsiveImage image={image} alt="Phòng khách" />);
    // Falls back to the photo's own intrinsic ratio when no `aspectRatio` prop is given.
    expect(screen.getByRole('img', { name: 'Phòng khách' }).parentElement).toHaveStyle({ aspectRatio: '1600 / 1000' });

    rerender(<ResponsiveImage image={null} alt="" />);
    expect(screen.getByRole('img', { name: 'Chưa có ảnh' }).parentElement).toHaveStyle({ aspectRatio: '4 / 3' });
  });

  it('adapts legacy URLs without variants', () => {
    const legacy = imageFromUrl('/api/v1/public/media/x.jpg');
    expect(legacy).toEqual({
      url: '/api/v1/public/media/x.jpg',
      width: null,
      height: null,
      srcset: [],
      placeholder: null,
    });
    expect(srcSetOf(legacy!)).toBeUndefined();
    expect(imageFromUrl('')).toBeNull();
  });
});

describe('pagination helpers', () => {
  it('keeps first, last and neighbours with gaps', () => {
    expect(pageWindow(1, 1)).toEqual([1]);
    expect(pageWindow(3, 12)).toEqual([1, 2, 3, 4, null, 12]);
    expect(pageWindow(7, 12)).toEqual([1, null, 6, 7, 8, null, 12]);
  });

  it('never presents the page length as the total', () => {
    expect(loadedSummary(24, true, { value: 10_000, relation: 'gte' }, 'tin')).toBe(
      'Đang hiển thị 24 trên hơn 10.000 tin',
    );
    expect(loadedSummary(24, true, { value: 120, relation: 'eq' }, 'tin')).toBe('Đang hiển thị 24 trên 120 tin');
    expect(loadedSummary(24, true, null, 'tin')).toBe('Đang hiển thị 24 tin');
    expect(loadedSummary(12, false, null, 'tin')).toBe('Đã hiển thị tất cả 12 tin');
  });

  it('marks the current page and requests others', () => {
    const onPageChange = vi.fn();
    render(<Pagination page={3} pageCount={5} onPageChange={onPageChange} />);
    expect(screen.getByRole('button', { name: 'Trang 3' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }));
    expect(onPageChange).toHaveBeenCalledWith(4);
  });
});

interface Row {
  id: string;
  title: string;
}
const rows: Row[] = [
  { id: 'a', title: 'Căn hộ A' },
  { id: 'b', title: 'Nhà phố B' },
];
const columns = [{ key: 'title', header: 'Tin đăng', sortable: true, cell: (row: Row) => row.title }];

describe('DataTable', () => {
  it('reports sort requests and exposes aria-sort', () => {
    const onSortChange = vi.fn();
    const { rerender } = render(
      <DataTable
        caption="Tin"
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        sort={null}
        onSortChange={onSortChange}
      />,
    );
    const header = screen.getByRole('columnheader', { name: /Tin đăng/ });
    expect(header).toHaveAttribute('aria-sort', 'none');
    fireEvent.click(within(header).getByRole('button'));
    expect(onSortChange).toHaveBeenLastCalledWith({ key: 'title', direction: 'asc' });

    const sort: DataTableSort = { key: 'title', direction: 'asc' };
    rerender(
      <DataTable
        caption="Tin"
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        sort={sort}
        onSortChange={onSortChange}
      />,
    );
    expect(screen.getByRole('columnheader', { name: /Tin đăng/ })).toHaveAttribute('aria-sort', 'ascending');
    fireEvent.click(within(screen.getByRole('columnheader', { name: /Tin đăng/ })).getByRole('button'));
    expect(onSortChange).toHaveBeenLastCalledWith({ key: 'title', direction: 'desc' });
  });

  it('selects all rows of the current page only', () => {
    function Harness() {
      const [selected, setSelected] = useState<Set<string>>(new Set(['other-page']));
      return (
        <DataTable
          caption="Tin"
          columns={columns}
          rows={rows}
          getRowId={(row) => row.id}
          selection={{ selectedIds: selected, onChange: setSelected, rowLabel: (row) => row.title }}
        />
      );
    }
    render(<Harness />);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Chọn tất cả các dòng trên trang này' }));
    expect(screen.getByRole('checkbox', { name: 'Chọn Căn hộ A' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Chọn Nhà phố B' })).toBeChecked();
    expect(screen.getByText('Đã chọn 2 dòng trên trang này')).toBeInTheDocument();
  });

  it.each<[DataTableStatus, Row[], RegExp]>([
    ['loading', [], /Đang tải dữ liệu/],
    ['refreshing', rows, /Đang cập nhật/],
    ['ready', [], /Chưa có dữ liệu/],
    ['error', [], /Không tải được dữ liệu/],
    ['partial-error', rows, /Một phần dữ liệu chưa tải được/],
    ['permission-denied', rows, /Bạn không có quyền xem dữ liệu này/],
    ['conflict', rows, /Dữ liệu vừa được người khác thay đổi/],
  ])('renders the %s state', (status, stateRows, message) => {
    render(
      <DataTable
        caption="Tin"
        columns={columns}
        rows={stateRows}
        getRowId={(row) => row.id}
        status={status}
        onRetry={() => undefined}
        onReload={() => undefined}
      />,
    );
    expect(screen.getAllByText(message).length).toBeGreaterThan(0);
    // The caption stays, so every state keeps its context.
    expect(screen.getByRole('table', { name: 'Tin' })).toBeInTheDocument();
    if (status === 'permission-denied') expect(screen.queryByText('Căn hộ A')).toBeNull();
  });
});

describe('InlineFeedback', () => {
  it('uses alert for errors and status otherwise, with an optional action', () => {
    const retry = vi.fn();
    render(
      <>
        <InlineFeedback kind="error" title="Không lưu được" action={{ label: 'Thử lại', onClick: retry }} />
        <InlineFeedback kind="offline" title="Bạn đang ngoại tuyến" />
      </>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Không lưu được');
    expect(screen.getByRole('status')).toHaveTextContent('Bạn đang ngoại tuyến');
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(retry).toHaveBeenCalled();
  });
});
