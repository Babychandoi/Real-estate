import { useCallback, useEffect, useState } from 'react';
import { Eye, History, Lock, RefreshCw, Search } from 'lucide-react';
import { adminListingsApi, type AdminListingFilters } from '@/entities/admin/api/adminApi';
import type { AdminListingRow, ListingPreview, RevisionRow, StatusHistoryRow } from '@/entities/admin/model/types';
import { formatPriceVnd, formatPropertyType } from '@/entities/listing/model/types';
import { errorMessage } from '@/shared/api/errors';
import { ReasonDialog, StatusBadge, formatDateTime } from '@/shared/admin/adminUi';
import { ClampedText } from '@/shared/ui/ClampedText';
import type { BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { EmptyState } from '@/shared/ui/EmptyState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Select } from '@/shared/ui/Select';
import { Dialog } from '@/shared/ui/Dialog';
import { Skeleton } from '@/shared/ui/Skeleton';
import { TextInput } from '@/shared/ui/TextInput';

const STATUS: Record<string, { label: string; variant: BadgeVariant }> = {
  ACTIVE: { label: 'Đang hiển thị', variant: 'success' },
  PAUSED: { label: 'Đang ẩn', variant: 'warning' },
  DRAFT: { label: 'Bản nháp', variant: 'neutral' },
  PENDING_REVIEW: { label: 'Chờ duyệt', variant: 'info' },
  REJECTED: { label: 'Bị từ chối', variant: 'error' },
  LOCKED: { label: 'Đã khóa', variant: 'error' },
  EXPIRED: { label: 'Hết hạn', variant: 'neutral' },
};
const ACTION_LABELS: Record<string, string> = {
  LOCK: 'Khóa tin',
  UNLOCK: 'Mở khóa',
  HIDE: 'Ẩn tin',
  UNHIDE: 'Hiển thị lại',
  EMERGENCY_HIDE: 'Tạm ẩn khẩn cấp (báo cáo)',
  REPORT_LOCK: 'Khóa theo báo cáo',
  REPORT_RESUME: 'Khôi phục theo báo cáo',
  AUTO_PAUSE: 'Tự động tạm ẩn',
};
type StatusAction = 'LOCK' | 'UNLOCK' | 'HIDE' | 'UNHIDE';

export function AdminListingsPage() {
  const [filters, setFilters] = useState<AdminListingFilters>({});
  const [draft, setDraft] = useState<AdminListingFilters>({});
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<AdminListingRow[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<DataTableStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [action, setAction] = useState<{ row: AdminListingRow; action: StatusAction } | null>(null);
  const [detail, setDetail] = useState<AdminListingRow | null>(null);

  const load = useCallback(async () => {
    setStatus((s) => (s === 'loading' ? 'loading' : 'refreshing'));
    try {
      const result = await adminListingsApi.search(filters, page);
      setRows(result.items);
      setTotal(result.total);
      setStatus('ready');
      setError(null);
    } catch (err) {
      setStatus('error');
      setError(errorMessage(err, 'Không thể tải danh sách tin.'));
    }
  }, [filters, page]);
  useEffect(() => {
    void load();
  }, [load]);

  const actionsFor = (row: AdminListingRow): StatusAction[] => {
    if (row.status === 'LOCKED') return ['UNLOCK'];
    if (row.status === 'ACTIVE') return ['HIDE', 'LOCK'];
    if (row.status === 'PAUSED' && row.hasPublicRevision) return ['UNHIDE', 'LOCK'];
    return ['LOCK'];
  };

  const columns: DataTableColumn<AdminListingRow>[] = [
    {
      key: 'title',
      header: 'Tin đăng',
      cell: (row) => (
        <div className="min-w-[14rem]">
          <ClampedText text={row.title} className="font-semibold" />
          <ClampedText
            text={`${formatPropertyType(row.propertyType)} · ${formatPriceVnd(row.priceVnd)} · ${row.areaM2} m² · ${row.addressSummary ?? 'Chưa có địa chỉ'}`}
            className="text-xs text-on-surface-variant"
          />
          <ClampedText
            text={`${row.ownerName} · nguồn ${row.source} · tạo ${formatDateTime(row.createdAt)}`}
            lines={1}
            className="text-xs text-on-surface-variant"
          />
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (row) => (
        <div className="space-y-1">
          <StatusBadge {...(STATUS[row.status] ?? { label: row.status, variant: 'neutral' as BadgeVariant })} />
          {row.hasPendingEdit && <p className="text-xs font-semibold text-on-surface-variant">Có bản sửa chờ duyệt</p>}
        </div>
      ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'end',
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<History className="h-4 w-4" />}
            onClick={() => setDetail(row)}
            aria-label={`Chi tiết ${row.title}`}
          >
            Chi tiết
          </Button>
          {actionsFor(row).map((a) => (
            <Button
              key={a}
              size="sm"
              variant={a === 'LOCK' || a === 'HIDE' ? 'danger' : 'secondary'}
              leftIcon={a === 'LOCK' ? <Lock className="h-4 w-4" /> : undefined}
              onClick={() => setAction({ row, action: a })}
              aria-label={`${ACTION_LABELS[a]}: ${row.title}`}
            >
              {ACTION_LABELS[a]}
            </Button>
          ))}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6" data-ready={status === 'loading' ? undefined : 'true'}>
      <header>
        <h1 className="text-2xl font-bold">Quản lý tất cả tin</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          Mọi thao tác khóa/ẩn đều cần lý do và được ghi vào lịch sử của tin.
        </p>
      </header>
      <form
        className="grid grid-cols-1 items-start gap-3 rounded-lg border border-outline-variant p-4 sm:grid-cols-2 lg:grid-cols-5"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setFilters(draft);
        }}
      >
        <FormField label="Từ khóa" className="lg:col-span-2">
          {(control) => (
            <TextInput
              {...control}
              placeholder="Tiêu đề, địa chỉ, người đăng, mã tin"
              value={draft.q ?? ''}
              onChange={(e) => setDraft({ ...draft, q: e.target.value })}
            />
          )}
        </FormField>
        <FormField label="Trạng thái">
          {(control) => (
            <Select
              {...control}
              value={draft.status ?? ''}
              onChange={(e) => setDraft({ ...draft, status: e.target.value })}
              options={[
                { value: '', label: 'Tất cả' },
                ...Object.entries(STATUS).map(([value, s]) => ({ value, label: s.label })),
              ]}
            />
          )}
        </FormField>
        <FormField label="Nguồn">
          {(control) => (
            <Select
              {...control}
              value={draft.source ?? ''}
              onChange={(e) => setDraft({ ...draft, source: e.target.value })}
              options={[
                { value: '', label: 'Tất cả' },
                { value: 'DIRECT', label: 'Đăng trực tiếp' },
                { value: 'IMPORT', label: 'Nhập hàng loạt' },
                { value: 'SEED', label: 'Dữ liệu mẫu' },
              ]}
            />
          )}
        </FormField>
        <FormField label="Mã quận/huyện">
          {(control) => (
            <TextInput
              {...control}
              value={draft.district ?? ''}
              onChange={(e) => setDraft({ ...draft, district: e.target.value })}
            />
          )}
        </FormField>
        <div className="flex flex-wrap items-end gap-4 sm:col-span-2 lg:col-span-5">
          <Checkbox
            label="Chỉ tin có bản sửa chờ duyệt"
            checked={draft.pendingEdit ?? false}
            onChange={(e) => setDraft({ ...draft, pendingEdit: e.target.checked })}
          />
          <Button type="submit" leftIcon={<Search className="h-4 w-4" />}>
            Lọc
          </Button>
          <Button
            type="button"
            variant="ghost"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => void load()}
          >
            Tải lại
          </Button>
        </div>
      </form>
      {feedback && <InlineFeedback kind="success" title={feedback} />}
      <DataTable
        caption={`Danh sách tin (${total})`}
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        status={status}
        errorMessage={error ?? undefined}
        onRetry={() => void load()}
        empty={<EmptyState title="Không có tin phù hợp bộ lọc" />}
        footer={
          total > 20 ? (
            <Pagination page={page + 1} pageCount={Math.ceil(total / 20)} onPageChange={(p) => setPage(p - 1)} />
          ) : undefined
        }
      />
      <ReasonDialog
        open={action !== null}
        title={action ? `${ACTION_LABELS[action.action]}: ${action.row.title}` : ''}
        description="Lý do được lưu vào lịch sử trạng thái của tin cùng tên người thực hiện."
        noteLabel="Lý do"
        noteMinLength={5}
        confirmLabel={action ? ACTION_LABELS[action.action] : ''}
        confirmVariant={action?.action === 'LOCK' || action?.action === 'HIDE' ? 'danger' : 'primary'}
        onClose={() => setAction(null)}
        onConfirm={async (_code, reason) => {
          if (!action) return;
          const result = await adminListingsApi.changeStatus(action.row.id, action.action, reason);
          setFeedback(
            `${ACTION_LABELS[action.action]}: ${action.row.title} sang ${STATUS[result.toStatus]?.label ?? result.toStatus}`,
          );
          await load();
        }}
      />
      {detail && <ListingDetailDialog row={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}

function ListingDetailDialog({ row, onClose }: { row: AdminListingRow; onClose: () => void }) {
  const [revisions, setRevisions] = useState<RevisionRow[] | null>(null);
  const [history, setHistory] = useState<StatusHistoryRow[]>([]);
  const [preview, setPreview] = useState<ListingPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    Promise.all([adminListingsApi.revisions(row.id), adminListingsApi.history(row.id)])
      .then(([r, h]) => {
        setRevisions(r);
        setHistory(h);
      })
      .catch((err) => setError(errorMessage(err, 'Không tải được lịch sử.')));
  }, [row.id]);
  const openPreview = async (revisionId?: string) => {
    try {
      setPreview(await adminListingsApi.preview(row.id, revisionId));
    } catch (err) {
      setError(errorMessage(err, 'Không mở được bản xem trước.'));
    }
  };
  return (
    <Dialog
      size="xl"
      open
      onClose={onClose}
      title={row.title}
      description="Phiên bản, lịch sử trạng thái và bản xem trước riêng tư"
      footer={
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      <div className="space-y-6">
        {error && <InlineFeedback kind="error" title={error} />}
        <section aria-labelledby="rev-heading">
          <h3 id="rev-heading" className="text-base font-bold">
            Phiên bản
          </h3>
          {!revisions ? (
            <Skeleton className="mt-2 h-24" />
          ) : (
            <ul className="mt-2 space-y-2 text-sm">
              {revisions.map((r) => (
                <li
                  key={r.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-surface-container-low p-2"
                >
                  <span>
                    #{r.revisionNumber} · {r.status}
                    {r.isPublic ? ' · đang công khai' : ''} · {formatPriceVnd(r.priceVnd)} ·{' '}
                    {formatDateTime(r.submittedAt ?? r.createdAt)}
                    {r.moderationNote ? (
                      <span className="block text-on-surface-variant">Ghi chú duyệt: {r.moderationNote}</span>
                    ) : null}
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    leftIcon={<Eye className="h-4 w-4" />}
                    onClick={() => openPreview(r.id)}
                    aria-label={`Xem trước phiên bản ${r.revisionNumber}`}
                  >
                    Xem trước
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
        {preview && (
          <section aria-labelledby="preview-heading" className="rounded-md border border-outline-variant p-3">
            <h3 id="preview-heading" className="text-base font-bold">
              Xem trước riêng tư — phiên bản #{preview.revisionNumber} ({preview.revisionStatus})
            </h3>
            <p className="text-xs text-on-surface-variant">
              Chỉ quản trị viên xem được; không lưu cache, không công khai.
            </p>
            <p className="mt-2 font-semibold">{preview.title}</p>
            <p className="text-sm">
              {formatPriceVnd(preview.priceVnd)} · {preview.areaM2} m² · {preview.addressSummary ?? 'Chưa có địa chỉ'}
            </p>
            <p className="mt-2 whitespace-pre-line text-sm">{preview.description ?? 'Chưa có mô tả.'}</p>
            <p className="mt-2 text-xs text-on-surface-variant">{preview.mediaUrls.length} ảnh đính kèm</p>
          </section>
        )}
        <section aria-labelledby="hist-heading">
          <h3 id="hist-heading" className="text-base font-bold">
            Lịch sử trạng thái
          </h3>
          {history.length === 0 ? (
            <p className="mt-1 text-sm text-on-surface-variant">Chưa có thao tác quản trị nào.</p>
          ) : (
            <ol className="mt-2 space-y-2 text-sm">
              {history.map((h) => (
                <li key={h.id} className="rounded-md bg-surface-container-low p-2">
                  <span className="font-semibold">{ACTION_LABELS[h.action] ?? h.action}</span> ({h.fromStatus ?? '?'}{' '}
                  sang {h.toStatus}) · {h.actorName ?? 'Hệ thống'} · {formatDateTime(h.createdAt)}
                  <span className="block text-on-surface-variant">Lý do: {h.reason}</span>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </Dialog>
  );
}
