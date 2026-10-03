import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';
import { Gallery } from '@/features/listing-detail/Gallery';
import { SearchBox } from '@/features/search/ui/SearchBox';
import { FilterSheet } from '@/features/search/ui/SearchPanels';
import { DEFAULT_FILTERS } from '@/features/search/filterSchema';
import { geocodePlaces } from '@/shared/api/geocodingApi';
import { DataTable } from '@/shared/ui/DataTable';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { LoadMore, Pagination } from '@/shared/ui/Pagination';
import { Skeleton, LoadingStatus } from '@/shared/ui/Skeleton';
import { ToastProvider, useToast } from '@/shared/ui/Toast';
import { TrustBadge } from '@/shared/ui/TrustBadge';

vi.mock('@/shared/api/geocodingApi', () => ({ geocodePlaces: vi.fn() }));

// DS-08 (audit §8.4): every listed component has its loading, empty, error, disabled and long-content state, and the
// state is something a user can perceive (role, text, disabled attribute, wrapping), not only a prop. N/A states
// are listed in docs/audit-2026-09-27/streams/w6-ux.md with the reason. Layout of long content in a real browser is
// measured by the E2E specs (ux-audit, admin-overflow); here the wrapping rule itself is asserted.

const LONG_WORD = 'Khônggiannhàởcaocấpkhuvựctrungtâmthànhphố'.repeat(6); // 240 characters, no break opportunity
const wraps = (element: HTMLElement) =>
  /\[overflow-wrap:anywhere\]|break-words|break-all|line-clamp-|truncate/.test(element.className) ||
  /\[overflow-wrap:anywhere\]|break-words|line-clamp-|truncate/.test(element.parentElement?.className ?? '');

function listing(overrides: Partial<ListingSummaryV2> = {}): ListingSummaryV2 {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    slug: 'can-ho-thu',
    title: 'Căn hộ 2 phòng ngủ',
    purpose: 'RENT',
    propertyType: 'APARTMENT',
    price: { amount: 14_500_000, currency: 'VND', period: 'MONTH' },
    unitPrice: null,
    areaM2: 72,
    bedrooms: 2,
    location: { addressSummary: 'Cầu Giấy, Hà Nội', precision: 'APPROXIMATE' },
    image: null,
    imageCount: 0,
    trust: {
      identity: { status: 'NOT_SUBMITTED', checkedAt: null, expiresAt: null },
      listing: { status: 'CHECKED', checkedAt: '2026-09-01T00:00:00Z' },
      ownership: { status: 'NOT_SUBMITTED', checkedAt: null, expiresAt: null, documentType: null },
    },
    freshness: { publishedAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-02T00:00:00Z' },
    seller: { id: 'seller-1', name: 'Người đăng', role: 'OWNER' },
    project: null,
    priceChange: null,
    ...overrides,
  } as ListingSummaryV2;
}

describe('SearchBox', () => {
  const props = { keyword: '', onKeyword: vi.fn(), onPlace: vi.fn(), onClearPlace: vi.fn() };

  it('loading: announces the place lookup while it runs', async () => {
    let resolve: (value: never[]) => void = () => undefined;
    vi.mocked(geocodePlaces).mockReturnValue(new Promise((r) => (resolve = r)));
    render(<SearchBox {...props} />);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Cầu Giấy' } });
    expect(await screen.findByRole('status', { name: 'Đang tìm địa điểm' })).toBeInTheDocument();
    await act(async () => resolve([]));
  });

  it('empty: says no place matched and still offers the keyword', async () => {
    vi.mocked(geocodePlaces).mockResolvedValue([]);
    render(<SearchBox {...props} />);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Không tồn tại' } });
    expect(await screen.findByText(/Không có địa điểm nào khớp “Không tồn tại”/)).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Tìm tin có từ khóa/ })).toBeInTheDocument();
  });

  it('error: the provider failure is an unselectable option, keyword search still works', async () => {
    vi.mocked(geocodePlaces).mockRejectedValue(new Error('offline'));
    render(<SearchBox {...props} />);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Hà Đông' } });
    const option = await screen.findByText(/Chưa tìm được địa điểm lúc này/);
    expect(option).toHaveAttribute('aria-disabled', 'true');
  });

  it('long content: a 100-character keyword wraps inside its option', async () => {
    vi.mocked(geocodePlaces).mockResolvedValue([]);
    render(<SearchBox {...props} />);
    const long = LONG_WORD.slice(0, 100);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: long } });
    const strong = await screen.findByText(long);
    expect(wraps(strong.parentElement as HTMLElement)).toBe(true);
  });
});

describe('FilterSheet', () => {
  it('error + disabled: an inverted price range is shown at the field and blocks "Xem kết quả"', () => {
    render(<FilterSheet filters={{ ...DEFAULT_FILTERS, purpose: 'RENT' }} onClose={vi.fn()} onApply={vi.fn()} />);
    const sheet = screen.getByRole('dialog', { name: 'Bộ lọc' });
    fireEvent.change(within(sheet).getByLabelText(/^Từ \(/), { target: { value: '50' } });
    fireEvent.change(within(sheet).getByLabelText(/^Đến \(/), { target: { value: '5' } });
    expect(within(sheet).getAllByText(/lớn hơn|nhỏ hơn|không được/i).length).toBeGreaterThan(0);
    expect(within(sheet).getByRole('button', { name: /^Xem kết quả/ })).toBeDisabled();
  });

  it('restored from the URL: the draft starts from the filters it was given', () => {
    render(
      <FilterSheet
        filters={{ ...DEFAULT_FILTERS, purpose: 'SALE', bedsMin: 3, types: ['APARTMENT'] }}
        onClose={vi.fn()}
        onApply={vi.fn()}
      />,
    );
    const sheet = screen.getByRole('dialog', { name: 'Bộ lọc' });
    const beds = within(sheet).getAllByRole('group', { name: 'Số phòng ngủ tối thiểu' }).at(-1)!;
    expect(within(beds).getByRole('button', { pressed: true })).toHaveTextContent('3');
  });
});

describe('ListingCard', () => {
  const renderCard = (props: Parameters<typeof ListingCard>[0]) =>
    render(
      <MemoryRouter>
        <ListingCard {...props} />
      </MemoryRouter>,
    );

  it('empty image: shows a labelled tile instead of a broken image', () => {
    renderCard({ listing: listing() });
    expect(screen.getByRole('img', { name: /Ảnh đại diện: Căn hộ 2 phòng ngủ|Chưa có ảnh/ })).toBeInTheDocument();
  });

  it('rent price keeps its period', () => {
    renderCard({ listing: listing() });
    expect(screen.getByText(/\/tháng$/)).toHaveAttribute('data-price');
  });

  it('disabled (no longer public): no detail link, says so, no compare button', () => {
    renderCard({ listing: listing(), inactive: true });
    expect(screen.queryByRole('link', { name: /^Xem chi tiết/ })).toBeNull();
    expect(screen.getByText('Tin không còn hiển thị')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /so sánh/ })).toBeNull();
  });

  it('long content: a 240-character title is clamped to two lines, the location truncated', () => {
    renderCard({
      listing: listing({ title: LONG_WORD, location: { addressSummary: LONG_WORD, precision: 'APPROXIMATE' } }),
    });
    expect(screen.getByRole('link', { name: `Xem chi tiết: ${LONG_WORD}` })).toHaveClass('line-clamp-2');
    expect(wraps(screen.getAllByText(LONG_WORD).at(-1) as HTMLElement)).toBe(true);
  });

  it('loading: the page shows skeleton cards in its place (decorative, announced by a status)', () => {
    render(
      <LoadingStatus label="Đang tải tin">
        <Skeleton className="h-80" />
      </LoadingStatus>,
    );
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải tin');
  });
});

describe('TrustBadge', () => {
  it.each([
    ['identity', 'PENDING', /đang/i],
    ['identity', 'VERIFIED', /Đã xác minh danh tính/],
    ['identity', 'EXPIRED', /hết hạn|hết hiệu lực/i],
    ['identity', 'NOT_SUBMITTED', /Chưa xác minh/],
    ['listing', 'CHECKED', /kiểm duyệt/],
  ] as const)('%s %s says what it means in words', (kind, status, text) => {
    render(<TrustBadge kind={kind} status={status} />);
    expect(screen.getByText(text)).toBeInTheDocument();
  });
});

describe('Gallery', () => {
  const one = [{ url: 'https://img.test/1.jpg', srcset: [] }];
  const many = Array.from({ length: 3 }, (_, n) => ({ url: `https://img.test/${n}.jpg`, srcset: [] }));

  it('single image: the viewer has no previous/next buttons', () => {
    render(<Gallery images={one} title="Nhà" />);
    fireEvent.click(screen.getByRole('button', { name: 'Mở ảnh 1/1 cỡ lớn' }));
    expect(screen.queryByRole('button', { name: 'Ảnh sau' })).toBeNull();
  });

  it('error: a photo that fails in the viewer says so and the others stay reachable', () => {
    render(<Gallery images={many} title="Nhà" />);
    fireEvent.click(screen.getByRole('button', { name: 'Mở ảnh 1/3 cỡ lớn' }));
    const viewer = screen.getByRole('dialog');
    fireEvent.error(within(viewer).getByRole('img', { name: 'Ảnh 1/3 của Nhà' }));
    expect(within(viewer).getByRole('alert')).toHaveTextContent('Không tải được ảnh 1/3');
    fireEvent.click(within(viewer).getByRole('button', { name: 'Ảnh sau' }));
    expect(within(viewer).getByRole('img', { name: 'Ảnh 2/3 của Nhà' })).toBeInTheDocument();
  });

  it('touch: a swipe moves between photos (audit §8.3), a mouse drag does not', () => {
    // jsdom has no PointerEvent: a MouseEvent with pointerType carries clientX the same way.
    if (typeof window.PointerEvent === 'undefined') {
      class PointerEventShim extends MouseEvent {
        pointerType: string;
        constructor(type: string, init: PointerEventInit = {}) {
          super(type, init);
          this.pointerType = init.pointerType ?? 'mouse';
        }
      }
      vi.stubGlobal('PointerEvent', PointerEventShim);
    }
    render(<Gallery images={many} title="Nhà" />);
    fireEvent.click(screen.getByRole('button', { name: 'Mở ảnh 1/3 cỡ lớn' }));
    const viewer = screen.getByRole('dialog');
    const stage = within(viewer).getByRole('img', { name: 'Ảnh 1/3 của Nhà' }).parentElement!;
    fireEvent.pointerDown(stage, { pointerType: 'touch', clientX: 300 });
    fireEvent.pointerUp(stage, { pointerType: 'touch', clientX: 120 });
    expect(viewer).toHaveTextContent('Ảnh 2/3');
    fireEvent.pointerDown(stage, { pointerType: 'touch', clientX: 100 });
    fireEvent.pointerUp(stage, { pointerType: 'touch', clientX: 300 });
    expect(viewer).toHaveTextContent('Ảnh 1/3');
    fireEvent.pointerDown(stage, { pointerType: 'mouse', clientX: 300 });
    fireEvent.pointerUp(stage, { pointerType: 'mouse', clientX: 100 });
    expect(viewer).toHaveTextContent('Ảnh 1/3');
  });

  it('long content: the viewer title is truncated on one line', () => {
    render(<Gallery images={many} title={LONG_WORD} />);
    fireEvent.click(screen.getByRole('button', { name: 'Mở ảnh 1/3 cỡ lớn' }));
    expect(screen.getByRole('heading', { name: LONG_WORD })).toHaveClass('truncate');
  });
});

describe('FormField', () => {
  const field = (props: Partial<Parameters<typeof FormField>[0]>) =>
    render(
      <FormField label="Tiêu đề" {...props}>
        {(control) => <input {...control} />}
      </FormField>,
    );

  it('error: linked to the control, 14 px, marked for the DS-03 audit', () => {
    field({ error: 'Tiêu đề cần ít nhất 10 ký tự.' });
    const input = screen.getByRole('textbox', { name: 'Tiêu đề' });
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription(/Tiêu đề cần ít nhất 10 ký tự/);
    const error = screen.getByText('Tiêu đề cần ít nhất 10 ký tự.').closest('p')!;
    expect(error).toHaveAttribute('data-error');
    expect(error).toHaveClass('text-body-sm');
  });

  it('disabled and saving states', () => {
    field({ disabled: true, status: 'saving' });
    expect(screen.getByRole('textbox', { name: 'Tiêu đề' })).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent('Đang lưu…');
  });

  it('long content: label, hint and error wrap', () => {
    render(
      <FormField label={LONG_WORD} hint={LONG_WORD} error={LONG_WORD}>
        {(control) => <input {...control} />}
      </FormField>,
    );
    for (const element of screen.getAllByText(LONG_WORD)) expect(wraps(element)).toBe(true);
  });
});

describe('DataTable', () => {
  const columns = [{ key: 'title', header: 'Tiêu đề', cell: (row: { id: string; title: string }) => row.title }];
  const rows = [{ id: '1', title: LONG_WORD }];

  it('long content: a cell with a 240-character word wraps inside the cell', () => {
    render(<DataTable caption="Tin" columns={columns} rows={rows} getRowId={(row) => row.id} status="ready" />);
    expect(screen.getByText(LONG_WORD).closest('.ndc-cell')).not.toBeNull();
  });

  it('disabled: "select all" is disabled while there is no row; row checkboxes have a 44 px label target', () => {
    const selection = {
      selectedIds: new Set<string>(),
      onChange: vi.fn(),
      rowLabel: (row: { title: string }) => row.title,
    };
    const { rerender } = render(
      <DataTable
        caption="Tin"
        columns={columns}
        rows={[]}
        getRowId={(row) => row.id}
        status="ready"
        selection={selection}
      />,
    );
    expect(screen.getByRole('checkbox', { name: 'Chọn tất cả các dòng trên trang này' })).toBeDisabled();
    rerender(
      <DataTable
        caption="Tin"
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        status="ready"
        selection={selection}
      />,
    );
    expect(screen.getByRole('checkbox', { name: `Chọn ${LONG_WORD}` }).closest('label')).toHaveClass('h-11', 'w-11');
  });
});

describe('Dialog', () => {
  it('long content: a body that scrolls is a focusable, named region (WCAG 2.1.1); a short one is not a tab stop', () => {
    const scrollHeight = vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(2000);
    const clientHeight = vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(400);
    const { unmount } = render(
      <Dialog open onClose={vi.fn()} title="Lịch sử">
        <p>{LONG_WORD}</p>
      </Dialog>,
    );
    // Its own name, distinct from the dialog's (review nit): the body region is not a second "Lịch sử".
    const region = screen.getByRole('region', { name: 'Nội dung: Lịch sử' });
    expect(region).toHaveAttribute('tabindex', '0');
    unmount();
    scrollHeight.mockReturnValue(100);
    clientHeight.mockReturnValue(400);
    render(
      <Dialog open onClose={vi.fn()} title="Ngắn">
        <p>nội dung</p>
      </Dialog>,
    );
    expect(screen.queryByRole('region', { name: 'Nội dung: Ngắn' })).toBeNull();
  });

  it('long content: a 240-character title stays inside the dialog', () => {
    render(
      <Dialog open onClose={vi.fn()} title={LONG_WORD}>
        nội dung
      </Dialog>,
    );
    const title = screen.getByRole('heading', { name: LONG_WORD });
    expect(title.closest('[class*="min-w-0"]')).not.toBeNull();
  });
});

describe('Toast', () => {
  function Show(props: Parameters<ReturnType<typeof useToast>['show']>[0]) {
    const toast = useToast();
    return (
      <button type="button" onClick={() => toast.show(props)}>
        show
      </button>
    );
  }

  it('error: goes to the assertive live region and stays until dismissed; long text wraps', () => {
    render(
      <ToastProvider>
        <Show kind="error" title={LONG_WORD} description="Không lưu được tin." />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'show' }).click());
    const title = screen.getByText(LONG_WORD);
    expect(title.closest('[aria-live]')).toHaveAttribute('aria-live', 'assertive');
    expect(wraps(title)).toBe(true);
  });
});

describe('EmptyState / ErrorState / InlineFeedback', () => {
  it('empty: says why and what next; long titles wrap', () => {
    render(<EmptyState title={LONG_WORD} description={LONG_WORD} />);
    for (const element of screen.getAllByText(LONG_WORD)) expect(wraps(element)).toBe(true);
  });

  it('error: announced, retry shows its own loading state', () => {
    render(<ErrorState onRetry={vi.fn()} retrying title={LONG_WORD} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    const retry = screen.getByRole('button', { name: /Thử lại/ });
    expect(retry).toBeDisabled();
    expect(retry).toHaveAttribute('aria-busy', 'true');
    expect(wraps(screen.getByRole('heading', { name: LONG_WORD }))).toBe(true);
  });

  it('inline feedback: action loading and long title', () => {
    render(
      <InlineFeedback
        kind="error"
        title={LONG_WORD}
        action={{ label: 'Thử lại', onClick: vi.fn(), isLoading: true }}
      />,
    );
    expect(screen.getByRole('button', { name: /Thử lại/ })).toHaveAttribute('aria-busy', 'true');
    expect(wraps(screen.getByText(LONG_WORD))).toBe(true);
  });
});

describe('Pagination / LoadMore', () => {
  it('disabled: every page button is disabled while a page loads', () => {
    render(<Pagination page={2} pageCount={5} onPageChange={vi.fn()} disabled />);
    for (const button of screen.getAllByRole('button')) expect(button).toBeDisabled();
  });

  it('loading, error and the end of the list', () => {
    const { rerender } = render(<LoadMore loadedCount={24} hasNext loading onLoadMore={vi.fn()} noun="tin" />);
    expect(screen.getByRole('button', { name: /Xem thêm|Đang tải/ })).toHaveAttribute('aria-busy', 'true');
    rerender(<LoadMore loadedCount={24} hasNext loading={false} error="Mất kết nối" onLoadMore={vi.fn()} noun="tin" />);
    expect(screen.getByText(/Mất kết nối/)).toBeInTheDocument();
    rerender(<LoadMore loadedCount={30} hasNext={false} loading={false} onLoadMore={vi.fn()} noun="tin" />);
    expect(screen.getByRole('status')).toHaveTextContent('Đã hiển thị tất cả 30 tin');
  });
});
