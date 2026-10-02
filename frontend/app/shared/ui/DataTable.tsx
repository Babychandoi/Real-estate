import React, { useId } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, Lock } from 'lucide-react';
import { cn } from './cn';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { InlineFeedback } from './InlineFeedback';
import { Skeleton } from './Skeleton';

export type SortDirection = 'asc' | 'desc';
export interface DataTableSort {
  key: string;
  direction: SortDirection;
}

export interface DataTableColumn<Row> {
  key: string;
  header: React.ReactNode;
  cell: (row: Row) => React.ReactNode;
  /** The server can sort by this column (the table only reports the request). */
  sortable?: boolean;
  align?: 'start' | 'end';
  className?: string;
}

/**
 * - `loading`: first load, skeleton rows
 * - `refreshing`: rows stay visible while a new page/filter/sort loads
 * - `error`: nothing could be loaded (retry)
 * - `partial-error`: rows shown, some data missing (retry)
 * - `permission-denied`: the account may not see this data
 * - `conflict`: someone else changed the data; reload before acting
 */
export type DataTableStatus =
  'ready' | 'loading' | 'refreshing' | 'error' | 'partial-error' | 'permission-denied' | 'conflict';

export interface DataTableSelection<Row> {
  selectedIds: ReadonlySet<string>;
  onChange: (ids: Set<string>) => void;
  /** Accessible name of a row's checkbox, e.g. the listing title. */
  rowLabel: (row: Row) => string;
}

interface DataTableProps<Row> {
  /** Table caption (what the rows are); visually hidden unless `captionVisible`. */
  caption: string;
  captionVisible?: boolean;
  columns: ReadonlyArray<DataTableColumn<Row>>;
  rows: readonly Row[];
  getRowId: (row: Row) => string;
  status?: DataTableStatus;
  sort?: DataTableSort | null;
  onSortChange?: (sort: DataTableSort) => void;
  /** Row selection for bulk actions; the scope is always "this page". */
  selection?: DataTableSelection<Row>;
  onRetry?: () => void;
  /** Conflict state action ("Tải lại"). */
  onReload?: () => void;
  errorMessage?: string;
  empty?: React.ReactNode;
  /** Pagination/LoadMore or bulk actions under the table. */
  footer?: React.ReactNode;
  skeletonRows?: number;
  className?: string;
}

/** Server-driven data table: pages, filters and sort are done by the API; this renders every state. */
export function DataTable<Row>({
  caption,
  captionVisible = false,
  columns,
  rows,
  getRowId,
  status = 'ready',
  sort,
  onSortChange,
  selection,
  onRetry,
  onReload,
  errorMessage,
  empty,
  footer,
  skeletonRows = 5,
  className,
}: DataTableProps<Row>) {
  const captionId = `table${useId().replace(/:/g, '')}-caption`;

  // Blocking states replace the rows but keep the caption and headers, so the context stays visible.
  const blocking: React.ReactNode =
    status === 'permission-denied' ? (
      <ErrorState
        icon={Lock}
        title="Bạn không có quyền xem dữ liệu này"
        description="Tài khoản hiện tại không được cấp quyền cho mục này. Liên hệ quản trị viên nếu bạn cần truy cập."
        className="border-0"
      />
    ) : status === 'error' && rows.length === 0 ? (
      <ErrorState
        description={errorMessage ?? 'Không tải được danh sách. Kiểm tra kết nối rồi thử lại.'}
        onRetry={onRetry}
        className="border-0"
      />
    ) : null;
  const visibleRows = blocking ? [] : rows;

  const busy = status === 'loading' || status === 'refreshing';
  const ids = visibleRows.map(getRowId);
  const selectedOnPage = selection ? ids.filter((id) => selection.selectedIds.has(id)).length : 0;
  const allSelected = selection != null && visibleRows.length > 0 && selectedOnPage === visibleRows.length;
  const someSelected = selectedOnPage > 0 && !allSelected;
  const columnCount = columns.length + (selection ? 1 : 0);

  const toggleAll = () => {
    if (!selection) return;
    const next = new Set(selection.selectedIds);
    if (allSelected) ids.forEach((id) => next.delete(id));
    else ids.forEach((id) => next.add(id));
    selection.onChange(next);
  };
  const toggleRow = (id: string) => {
    if (!selection) return;
    const next = new Set(selection.selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    selection.onChange(next);
  };

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      {status === 'partial-error' && (
        <InlineFeedback
          kind="warning"
          title="Một phần dữ liệu chưa tải được"
          action={onRetry ? { label: 'Thử lại', onClick: onRetry } : undefined}
        >
          {errorMessage ?? 'Các dòng dưới đây có thể chưa đầy đủ.'}
        </InlineFeedback>
      )}
      {status === 'conflict' && (
        <InlineFeedback
          kind="conflict"
          title="Dữ liệu vừa được người khác thay đổi"
          action={onReload ? { label: 'Tải lại', onClick: onReload } : undefined}
        >
          Tải lại để xem bản mới nhất trước khi thao tác tiếp.
        </InlineFeedback>
      )}
      {status === 'error' && rows.length > 0 && (
        <InlineFeedback
          kind="error"
          title={errorMessage ?? 'Không cập nhật được danh sách'}
          action={onRetry ? { label: 'Thử lại', onClick: onRetry } : undefined}
        >
          Đang hiển thị dữ liệu của lần tải trước.
        </InlineFeedback>
      )}
      <div className="flex min-h-6 items-center justify-between gap-3 text-body-sm text-on-surface-variant">
        <p role="status">
          {status === 'loading' ? 'Đang tải dữ liệu…' : status === 'refreshing' ? 'Đang cập nhật…' : ''}
        </p>
        {selection && selectedOnPage > 0 && <p>Đã chọn {selectedOnPage} dòng trên trang này</p>}
      </div>
      <div
        // `relative`: absolutely positioned content (sr-only header text) is placed inside the scroll area, not at the far right of the page.
        className="relative overflow-x-auto rounded-card border border-outline-variant bg-surface-container-lowest"
        role="region"
        aria-labelledby={captionId}
        // Keyboard users can scroll wide tables: the scroll area is focusable and named by the caption.
        // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
        tabIndex={0}
      >
        {/* Refresh keeps rows readable (no dimming, which would break contrast); a bar shows the activity. */}
        {status === 'refreshing' && (
          <div className="h-1 w-full bg-primary/60 motion-safe:animate-pulse" aria-hidden="true" />
        )}
        <table className="w-full min-w-[40rem] border-collapse text-left text-body-sm" aria-busy={busy || undefined}>
          <caption
            id={captionId}
            className={cn(captionVisible ? 'px-4 pt-4 text-left text-headline-sm text-on-surface' : 'sr-only')}
          >
            {caption}
          </caption>
          <thead className="bg-surface-container-low text-label uppercase tracking-wide text-on-surface-variant">
            <tr>
              {selection && (
                <th scope="col" className="w-12 px-4 py-3">
                  {/* The wrapping label is a 44 × 44 touch target; its negative margin keeps the cell's layout. */}
                  <label className="-m-3 grid h-11 w-11 cursor-pointer place-items-center">
                    <input
                      type="checkbox"
                      className="h-5 w-5 cursor-pointer accent-primary"
                      aria-label="Chọn tất cả các dòng trên trang này"
                      checked={allSelected}
                      ref={(element) => {
                        if (element) element.indeterminate = someSelected;
                      }}
                      onChange={toggleAll}
                      disabled={visibleRows.length === 0}
                    />
                  </label>
                </th>
              )}
              {columns.map((column) => {
                const active = sort?.key === column.key;
                const ariaSort = active
                  ? sort.direction === 'asc'
                    ? 'ascending'
                    : 'descending'
                  : column.sortable
                    ? 'none'
                    : undefined;
                const SortIcon = active ? (sort.direction === 'asc' ? ArrowUp : ArrowDown) : ArrowUpDown;
                return (
                  <th
                    key={column.key}
                    scope="col"
                    aria-sort={ariaSort}
                    className={cn('px-4 py-3 font-semibold', column.align === 'end' && 'text-right', column.className)}
                  >
                    {column.sortable && onSortChange ? (
                      <button
                        type="button"
                        onClick={() =>
                          onSortChange({
                            key: column.key,
                            direction: active && sort.direction === 'asc' ? 'desc' : 'asc',
                          })
                        }
                        className={cn(
                          'inline-flex min-h-control-sm items-center gap-1 rounded-input uppercase tracking-wide hover:text-on-surface',
                          active && 'text-primary',
                        )}
                      >
                        {column.header}
                        <SortIcon className="h-4 w-4" aria-hidden="true" />
                      </button>
                    ) : (
                      column.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {status === 'loading'
              ? Array.from({ length: skeletonRows }, (_, index) => (
                  <tr key={`skeleton-${index}`}>
                    {Array.from({ length: columnCount }, (__, cell) => (
                      <td key={cell} className="px-4 py-3">
                        <Skeleton className="h-4 w-full max-w-[12rem]" />
                      </td>
                    ))}
                  </tr>
                ))
              : visibleRows.map((row) => {
                  const id = getRowId(row);
                  const checked = selection?.selectedIds.has(id) ?? false;
                  return (
                    <tr
                      key={id}
                      className={cn('transition-colors hover:bg-surface-container-low', checked && 'bg-primary/5')}
                    >
                      {selection && (
                        <td className="px-4 py-3">
                          <label className="-m-3 grid h-11 w-11 cursor-pointer place-items-center">
                            <input
                              type="checkbox"
                              className="h-5 w-5 cursor-pointer accent-primary"
                              aria-label={`Chọn ${selection.rowLabel(row)}`}
                              checked={checked}
                              onChange={() => toggleRow(id)}
                            />
                          </label>
                        </td>
                      )}
                      {columns.map((column) => (
                        <td
                          key={column.key}
                          className={cn(
                            'px-4 py-3 align-top text-on-surface',
                            column.align === 'end' && 'text-right',
                            column.className,
                          )}
                        >
                          <div className={cn('ndc-cell', column.align === 'end' && 'ndc-cell-end')}>
                            {column.cell(row)}
                          </div>
                        </td>
                      ))}
                    </tr>
                  );
                })}
            {blocking && (
              <tr>
                <td colSpan={columnCount} className="p-4">
                  {blocking}
                </td>
              </tr>
            )}
            {!blocking && status !== 'loading' && rows.length === 0 && (
              <tr>
                <td colSpan={columnCount} className="p-4">
                  {empty ?? (
                    <EmptyState
                      title="Chưa có dữ liệu"
                      description="Khi có mục mới, chúng sẽ hiển thị tại đây."
                      className="border-0"
                    />
                  )}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {footer}
    </div>
  );
}
