import { useCallback, useEffect, useState } from 'react';
import { EyeOff, Hand, History, RefreshCw, Undo2 } from 'lucide-react';
import { reportDeskApi, type ReportQueueFilters } from '@/entities/admin/api/adminApi';
import type { ReportEvent, ReportQueueItem } from '@/entities/admin/model/types';
import { errorMessage } from '@/shared/api/errors';
import { ReasonDialog, SlaBadge, StatusBadge, formatDateTime } from '@/shared/admin/adminUi';
import type { BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { Chip } from '@/shared/ui/Chip';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { EmptyState } from '@/shared/ui/EmptyState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Select } from '@/shared/ui/Select';
import { Sheet } from '@/shared/ui/Sheet';

const SEVERITY: Record<ReportQueueItem['severity'], { label: string; variant: BadgeVariant; sla: string }> = {
  P0_EMERGENCY: { label: 'Khẩn cấp (P0)', variant: 'error', sla: '1 giờ' },
  HIGH: { label: 'Cao', variant: 'warning', sla: '4 giờ' },
  MEDIUM: { label: 'Trung bình', variant: 'info', sla: '24 giờ' },
  LOW: { label: 'Thấp', variant: 'neutral', sla: '72 giờ' },
};
const STATUS: Record<ReportQueueItem['status'], string> = {
  PENDING: 'Chờ xử lý',
  WAITING_REPLY: 'Chờ phản hồi',
  APPEALED: 'Có khiếu nại',
  RESOLVED: 'Đã giải quyết',
  DISMISSED: 'Đã bác bỏ',
};
const CATEGORY: Record<ReportQueueItem['category'], string> = {
  SCAM_DEPOSIT: 'Nghi lừa đặt cọc',
  FAKE_SOLD: 'Đã bán/cho thuê nhưng còn treo',
  INCORRECT_PRICE: 'Sai giá',
  OTHER: 'Khác',
};
const EVENT_LABELS: Record<string, string> = {
  SUBMITTED: 'Tiếp nhận báo cáo',
  CLAIMED: 'Nhận xử lý',
  RELEASED: 'Trả lại hàng đợi',
  EMERGENCY_HIDDEN: 'Tạm ẩn tin khẩn cấp',
  RESOLVED: 'Kết luận vi phạm',
  DISMISSED: 'Bác bỏ báo cáo',
  APPEALED: 'Người đăng khiếu nại',
  OWNER_RESPONSE: 'Chủ tin phản hồi',
  AUTO_PAUSED: 'Tự tạm ẩn (chủ tin không xác nhận)',
  ESCALATED: 'Đổi mức độ ưu tiên',
  NOTE: 'Ghi chú',
};
const OUTCOME: Record<string, string> = {
  OWNER_RESPONSE: 'Chủ tin đã phản hồi',
  AUTO_PAUSED: 'Tin đã tự tạm ẩn do chủ tin không xác nhận còn hàng',
};

type Action = { item: ReportQueueItem; kind: 'hide' | 'resolve' | 'dismiss' | 'escalate' | 'note' } | null;

export function ReportsQueuePage() {
  const [filters, setFilters] = useState<ReportQueueFilters>({ status: 'OPEN' });
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<ReportQueueItem[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<DataTableStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'warning'; title: string } | null>(null);
  const [open, setOpen] = useState<ReportQueueItem | null>(null);
  const [action, setAction] = useState<Action>(null);
  const [lockListing, setLockListing] = useState(false);

  const load = useCallback(async () => {
    setStatus((s) => (s === 'loading' ? 'loading' : 'refreshing'));
    try {
      const result = await reportDeskApi.queue(filters, page);
      setRows(result.items);
      setTotal(result.total);
      setStatus('ready');
      setError(null);
      setOpen((current) => (current ? (result.items.find((i) => i.id === current.id) ?? current) : null));
    } catch (err) {
      setStatus('error');
      setError(errorMessage(err, 'Không thể tải hàng đợi báo cáo.'));
    }
  }, [filters, page]);
  useEffect(() => {
    void load();
  }, [load]);

  const claim = async (item: ReportQueueItem) => {
    try {
      await reportDeskApi.claim(item.id);
    } catch (err) {
      setFeedback({ kind: 'warning', title: errorMessage(err, 'Không thể nhận xử lý.') });
    }
    await load();
  };

  const columns: DataTableColumn<ReportQueueItem>[] = [
    {
      key: 'case',
      header: 'Vụ việc',
      cell: (r) => (
        <div className="min-w-[12rem]">
          <p className="font-semibold">
            {r.caseNumber} · {CATEGORY[r.category]}
          </p>
          <p className="text-xs text-on-surface-variant">{r.listingTitle ?? r.listingId}</p>
          {r.ownerOutcome && <p className="text-xs font-semibold">{OUTCOME[r.ownerOutcome]}</p>}
        </div>
      ),
    },
    {
      key: 'severity',
      header: 'Mức độ',
      cell: (r) => (
        <StatusBadge
          label={`${SEVERITY[r.severity].label} · SLA ${SEVERITY[r.severity].sla}`}
          variant={SEVERITY[r.severity].variant}
        />
      ),
    },
    {
      key: 'sla',
      header: 'Hạn xử lý',
      cell: (r) =>
        ['RESOLVED', 'DISMISSED'].includes(r.status) ? (
          <span className="text-sm">
            {STATUS[r.status]} · {formatDateTime(r.resolvedAt)}
          </span>
        ) : (
          <SlaBadge breached={r.slaBreached} dueAt={r.slaDueAt} minutesToDue={r.minutesToDue} />
        ),
    },
    { key: 'status', header: 'Trạng thái', cell: (r) => STATUS[r.status] },
    {
      key: 'claim',
      header: 'Người xử lý',
      cell: (r) =>
        r.claim ? (
          r.claim.mine ? (
            'Bạn'
          ) : (
            r.claim.staffName
          )
        ) : (
          <span className="text-on-surface-variant">Chưa ai nhận</span>
        ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'end',
      cell: (r) => (
        <div className="flex justify-end gap-2">
          {!r.claim && ['PENDING', 'WAITING_REPLY', 'APPEALED'].includes(r.status) && (
            <Button
              size="sm"
              variant="outline"
              leftIcon={<Hand className="h-4 w-4" />}
              onClick={() => claim(r)}
              aria-label={`Nhận xử lý ${r.caseNumber}`}
            >
              Nhận xử lý
            </Button>
          )}
          <Button size="sm" onClick={() => setOpen(r)} aria-label={`Mở vụ việc ${r.caseNumber}`}>
            Mở
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6" data-ready={status === 'loading' ? undefined : 'true'}>
      <header>
        <h1 className="text-2xl font-bold">Báo cáo vi phạm</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          Hạn xử lý theo mức độ: khẩn cấp 1 giờ, cao 4 giờ, trung bình 24 giờ, thấp 72 giờ. Nhận xử lý trước khi hành
          động; mọi bước đều được ghi lịch sử. Số điện thoại người báo cáo luôn được che.
        </p>
      </header>
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc trạng thái">
          {[
            { id: 'OPEN', label: 'Đang mở' },
            { id: 'ALL', label: 'Tất cả' },
            { id: 'RESOLVED', label: 'Đã giải quyết' },
            { id: 'DISMISSED', label: 'Đã bác bỏ' },
          ].map((f) => (
            <Chip
              key={f.id}
              size="sm"
              selected={filters.status === f.id}
              onClick={() => {
                setPage(0);
                setFilters({ ...filters, status: f.id });
              }}
            >
              {f.label}
            </Chip>
          ))}
          <Chip
            size="sm"
            selected={Boolean(filters.breached)}
            onClick={() => {
              setPage(0);
              setFilters({ ...filters, breached: !filters.breached });
            }}
          >
            Quá hạn SLA
          </Chip>
          <Chip
            size="sm"
            selected={Boolean(filters.mine)}
            onClick={() => {
              setPage(0);
              setFilters({ ...filters, mine: !filters.mine });
            }}
          >
            Tôi đang xử lý
          </Chip>
        </div>
        <FormField label="Mức độ">
          {(control) => (
            <Select
              {...control}
              value={filters.severity ?? ''}
              onChange={(e) => {
                setPage(0);
                setFilters({ ...filters, severity: e.target.value || undefined });
              }}
              options={[
                { value: '', label: 'Tất cả' },
                ...Object.entries(SEVERITY).map(([value, s]) => ({ value, label: s.label })),
              ]}
            />
          )}
        </FormField>
        <Button size="sm" variant="ghost" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void load()}>
          Tải lại
        </Button>
      </div>
      {feedback && <InlineFeedback kind={feedback.kind} title={feedback.title} />}
      <DataTable
        caption="Hàng đợi báo cáo, hạn gần nhất ở đầu"
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
        status={status}
        errorMessage={error ?? undefined}
        onRetry={() => void load()}
        empty={<EmptyState title="Không có vụ việc nào trong bộ lọc này" />}
        footer={
          total > 20 ? (
            <Pagination page={page + 1} pageCount={Math.ceil(total / 20)} onPageChange={(p) => setPage(p - 1)} />
          ) : undefined
        }
      />
      {open && (
        <CaseSheet
          item={open}
          onClose={() => setOpen(null)}
          onClaim={() => claim(open)}
          onRelease={async () => {
            await reportDeskApi.release(open.id);
            await load();
          }}
          onAction={(kind) => {
            setLockListing(false);
            setAction({ item: open, kind });
          }}
        />
      )}
      <ReasonDialog
        open={action !== null}
        title={
          action?.kind === 'hide'
            ? 'Tạm ẩn tin khẩn cấp'
            : action?.kind === 'resolve'
              ? 'Kết luận có vi phạm'
              : action?.kind === 'dismiss'
                ? 'Bác bỏ báo cáo'
                : action?.kind === 'escalate'
                  ? 'Đổi mức độ'
                  : 'Thêm ghi chú'
        }
        description={action ? `${action.item.caseNumber} · ${action.item.listingTitle ?? ''}` : undefined}
        choiceLabel="Mức độ mới"
        reasons={
          action?.kind === 'escalate'
            ? Object.entries(SEVERITY).map(([code, s]) => ({ code, label: `${s.label} (SLA ${s.sla})` }))
            : undefined
        }
        noteLabel={action?.kind === 'note' ? 'Ghi chú' : 'Lý do / ghi chú xử lý'}
        noteMinLength={5}
        scope={
          action?.kind === 'resolve' ? (
            <Checkbox
              label="Khóa tin vĩnh viễn"
              description="Tin sẽ không thể hiển thị lại nếu không có quyết định mở khóa."
              checked={lockListing}
              onChange={(e) => setLockListing(e.target.checked)}
            />
          ) : action?.kind === 'dismiss' ? (
            <p className="text-sm">Nếu tin đang bị tạm ẩn vì báo cáo này, tin sẽ được hiển thị lại.</p>
          ) : undefined
        }
        confirmLabel={
          action?.kind === 'hide'
            ? 'Tạm ẩn tin'
            : action?.kind === 'resolve'
              ? 'Lưu kết luận'
              : action?.kind === 'dismiss'
                ? 'Bác bỏ'
                : 'Lưu'
        }
        confirmVariant={action?.kind === 'hide' || action?.kind === 'resolve' ? 'danger' : 'primary'}
        onClose={() => setAction(null)}
        onConfirm={async (code, note) => {
          if (!action) return;
          const id = action.item.id;
          if (action.kind === 'hide') await reportDeskApi.emergencyHide(id, note);
          if (action.kind === 'resolve') await reportDeskApi.resolve(id, note, lockListing);
          if (action.kind === 'dismiss') await reportDeskApi.dismiss(id, note, true);
          if (action.kind === 'escalate') await reportDeskApi.escalate(id, code, note);
          if (action.kind === 'note') await reportDeskApi.note(id, note);
          setFeedback({ kind: 'success', title: `Đã cập nhật ${action.item.caseNumber}` });
          await load();
        }}
      />
    </div>
  );
}

function CaseSheet({
  item,
  onClose,
  onClaim,
  onRelease,
  onAction,
}: {
  item: ReportQueueItem;
  onClose: () => void;
  onClaim: () => void;
  onRelease: () => void;
  onAction: (kind: 'hide' | 'resolve' | 'dismiss' | 'escalate' | 'note') => void;
}) {
  const [events, setEvents] = useState<ReportEvent[] | null>(null);
  useEffect(() => {
    reportDeskApi
      .events(item.id)
      .then(setEvents)
      .catch(() => setEvents([]));
  }, [item]);
  const closed = ['RESOLVED', 'DISMISSED'].includes(item.status);
  const blocked = item.claim != null && !item.claim.mine;
  return (
    <Sheet
      open
      onClose={onClose}
      title={`${item.caseNumber} · ${CATEGORY[item.category]}`}
      description={`${SEVERITY[item.severity].label} · ${STATUS[item.status]} · tiếp nhận ${formatDateTime(item.createdAt)}`}
      footer={
        closed ? undefined : (
          <div className="flex flex-wrap items-center justify-between gap-6">
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" disabled={blocked} onClick={() => onAction('dismiss')}>
                Bác bỏ…
              </Button>
              <Button variant="ghost" disabled={blocked} onClick={() => onAction('note')}>
                Ghi chú…
              </Button>
              <Button variant="ghost" disabled={blocked} onClick={() => onAction('escalate')}>
                Đổi mức độ…
              </Button>
            </div>
            <div className="flex flex-wrap gap-2">
              {item.listingStatus === 'ACTIVE' && (
                <Button
                  variant="danger"
                  disabled={blocked}
                  leftIcon={<EyeOff className="h-4 w-4" />}
                  onClick={() => onAction('hide')}
                >
                  Tạm ẩn tin…
                </Button>
              )}
              <Button variant="danger" disabled={blocked} onClick={() => onAction('resolve')}>
                Kết luận vi phạm…
              </Button>
            </div>
          </div>
        )
      }
    >
      <div className="space-y-5 text-sm">
        <section
          className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-surface-container-low p-3"
          aria-label="Người xử lý"
        >
          <p>
            {item.claim
              ? item.claim.mine
                ? `Bạn đang nhận xử lý đến ${formatDateTime(item.claim.expiresAt)}.`
                : `${item.claim.staffName} đang xử lý; bạn chưa thể thao tác.`
              : closed
                ? 'Vụ việc đã đóng.'
                : 'Chưa ai nhận xử lý.'}
          </p>
          {!closed &&
            (item.claim?.mine ? (
              <Button size="sm" variant="outline" leftIcon={<Undo2 className="h-4 w-4" />} onClick={onRelease}>
                Trả lại
              </Button>
            ) : (
              !item.claim && (
                <Button size="sm" variant="outline" leftIcon={<Hand className="h-4 w-4" />} onClick={onClaim}>
                  Nhận xử lý 30 phút
                </Button>
              )
            ))}
        </section>
        <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <div>
            <dt className="text-on-surface-variant">Tin bị báo cáo</dt>
            <dd className="font-semibold">{item.listingTitle ?? item.listingId}</dd>
          </div>
          <div>
            <dt className="text-on-surface-variant">Trạng thái tin</dt>
            <dd>{item.listingStatus ?? 'Không rõ'}</dd>
          </div>
          <div>
            <dt className="text-on-surface-variant">Người báo cáo</dt>
            <dd>{item.reporterPhoneMasked ?? 'Ẩn danh, không để lại số'}</dd>
          </div>
          <div>
            <dt className="text-on-surface-variant">Kết quả phía chủ tin</dt>
            <dd>{item.ownerOutcome ? OUTCOME[item.ownerOutcome] : 'Chưa có'}</dd>
          </div>
        </dl>
        <div>
          <p className="text-on-surface-variant">Nội dung báo cáo</p>
          <p className="mt-1 whitespace-pre-line">{item.description}</p>
        </div>
        {item.resolutionNote && (
          <InlineFeedback kind="info" title="Ghi chú kết luận">
            {item.resolutionNote}
          </InlineFeedback>
        )}
        <section aria-labelledby="case-history">
          <h3 id="case-history" className="flex items-center gap-2 text-base font-bold">
            <History className="h-4 w-4" aria-hidden="true" /> Lịch sử xử lý
          </h3>
          <ol className="mt-2 space-y-2">
            {(events ?? []).map((e) => (
              <li key={e.id} className="rounded-md bg-surface-container-low p-2">
                <span className="font-semibold">{EVENT_LABELS[e.type] ?? e.type}</span> · {e.actorName ?? 'Hệ thống'} ·{' '}
                {formatDateTime(e.createdAt)}
                {e.note && <span className="block text-on-surface-variant">{e.note}</span>}
              </li>
            ))}
          </ol>
        </section>
      </div>
    </Sheet>
  );
}
