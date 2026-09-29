import { useCallback, useEffect, useMemo, useState } from 'react';
import { Copy, FileSearch, Hand, History, RefreshCw, ShieldCheck, Undo2 } from 'lucide-react';
import { moderationV2Api } from '@/entities/admin/api/adminApi';
import type {
  AuditSample,
  BulkItemResult,
  DecisionView,
  DuplicateCandidate,
  ModerationQueueItem,
  ModerationQueuePage,
  QueueFilter,
  ReasonOption,
} from '@/entities/admin/model/types';
import { moderationApi } from '@/entities/moderation/api/moderationApi';
import type { ListingDiff } from '@/entities/moderation/model/types';
import { formatPriceVnd, formatPropertyType } from '@/entities/listing/model/types';
import { errorMessage } from '@/shared/api/errors';
import { ApiProblemException } from '@/shared/types/problem-details';
import {
  ReasonDialog,
  SlaBadge,
  StatusBadge,
  formatAge,
  formatDateTime,
  type ReasonChoice,
} from '@/shared/admin/adminUi';
import { ClampedText } from '@/shared/ui/ClampedText';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Chip } from '@/shared/ui/Chip';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { EmptyState } from '@/shared/ui/EmptyState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Dialog } from '@/shared/ui/Dialog';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Tabs } from '@/shared/ui/Tabs';

const FILTERS: Array<{ id: QueueFilter; label: string }> = [
  { id: 'ALL', label: 'Tất cả' },
  { id: 'FIRST_SUBMISSION', label: 'Lần đầu' },
  { id: 'EDIT', label: 'Bản sửa' },
  { id: 'SLA_BREACH', label: 'Quá hạn 24 giờ' },
  { id: 'DUPLICATES', label: 'Nghi trùng' },
  { id: 'MINE', label: 'Tôi đang xử lý' },
  { id: 'UNCLAIMED', label: 'Chưa ai nhận' },
];

const OUTCOME_LABELS: Record<string, string> = {
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
  NOT_CLAIMED: 'Bỏ qua: bạn chưa nhận xử lý',
  CLAIM_CONFLICT: 'Bỏ qua: người khác đang xử lý',
  STALE_REVISION: 'Bỏ qua: đã có phiên bản mới',
  NOT_PENDING: 'Bỏ qua: không còn chờ duyệt',
  ALREADY_DECIDED: 'Bỏ qua: vừa được xử lý',
  INVALID: 'Bỏ qua: mục không hợp lệ',
};

const DECISION_LABELS: Record<DecisionView['decision'], string> = {
  APPROVED: 'Phê duyệt',
  REJECTED: 'Từ chối',
  AUDIT_PASSED: 'Kiểm tra lại: đạt',
  AUDIT_FAILED: 'Kiểm tra lại: không đạt',
};

type Decision = { kind: 'approve' | 'reject'; items: ModerationQueueItem[] } | null;

export default function ModerationWorkspacePage() {
  const [tab, setTab] = useState<'queue' | 'audit'>('queue');
  return (
    <div className="space-y-6" data-ready="true">
      <header>
        <h1 className="text-2xl font-bold text-on-surface">Kiểm duyệt tin đăng</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          Ưu tiên tin chờ lâu nhất, nhận xử lý trước khi quyết định, mỗi quyết định đều ghi lý do và người duyệt. Duyệt
          nội dung không có nghĩa là xác minh pháp lý hay quyền sở hữu.
        </p>
      </header>
      <Tabs
        label="Khu vực kiểm duyệt"
        value={tab}
        onChange={setTab}
        items={[
          { id: 'queue', label: 'Hàng đợi', content: <QueuePanel /> },
          { id: 'audit', label: 'Kiểm tra ngẫu nhiên', content: <AuditPanel /> },
        ]}
      />
    </div>
  );
}

function QueuePanel() {
  const [filter, setFilter] = useState<QueueFilter>('ALL');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ModerationQueuePage | null>(null);
  const [status, setStatus] = useState<DataTableStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [open, setOpen] = useState<ModerationQueueItem | null>(null);
  const [reasons, setReasons] = useState<{ approve: ReasonChoice[]; reject: ReasonChoice[] }>({
    approve: [],
    reject: [],
  });
  const [decision, setDecision] = useState<Decision>(null);
  const [feedback, setFeedback] = useState<{
    kind: 'success' | 'error' | 'warning';
    title: string;
    results?: BulkItemResult[];
  } | null>(null);

  const load = useCallback(async () => {
    setStatus((current) => (current === 'loading' ? 'loading' : 'refreshing'));
    try {
      setData(await moderationV2Api.queue(filter, page));
      setStatus('ready');
      setError(null);
    } catch (err) {
      setStatus(err instanceof ApiProblemException && err.problem.status === 403 ? 'permission-denied' : 'error');
      setError(errorMessage(err, 'Không thể tải hàng đợi kiểm duyệt.'));
    }
  }, [filter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const toChoices = (items: ReasonOption[]) => items.map((r) => ({ code: r.code, label: r.vietnameseLabel }));
    moderationV2Api
      .reasons()
      .then((r) => setReasons({ approve: toChoices(r.approve), reject: toChoices(r.reject) }))
      .catch(() => setFeedback({ kind: 'error', title: 'Không tải được danh mục lý do; vui lòng tải lại trang.' }));
  }, []);

  const items = useMemo(() => data?.items ?? [], [data]);
  const selectedItems = items.filter((item) => selected.has(item.listingId));
  const inScope = selectedItems.filter((item) => item.claim?.mine);

  const claim = async (item: ModerationQueueItem) => {
    try {
      await moderationV2Api.claim(item.listingId);
      await load();
    } catch (err) {
      setFeedback({ kind: 'warning', title: errorMessage(err, 'Không thể nhận xử lý tin này.') });
      await load();
    }
  };

  const columns: DataTableColumn<ModerationQueueItem>[] = [
    {
      key: 'listing',
      header: 'Tin đăng',
      cell: (item) => (
        <div className="min-w-[14rem]">
          <ClampedText text={item.title} className="font-semibold text-on-surface" />
          <ClampedText
            text={`${formatPropertyType(item.propertyType)} · ${formatPriceVnd(item.priceVnd)} · ${item.areaM2} m² · ${item.addressSummary ?? 'Chưa có địa chỉ'}`}
            className="text-xs text-on-surface-variant"
          />
          <ClampedText text={`Người đăng: ${item.ownerName}`} lines={1} className="text-xs text-on-surface-variant" />
        </div>
      ),
    },
    {
      key: 'kind',
      header: 'Loại',
      cell: (item) => (
        <Badge variant={item.kind === 'EDIT' ? 'info' : 'primary'}>
          {item.kind === 'EDIT' ? `Bản sửa #${item.revisionNumber}` : 'Lần đầu'}
        </Badge>
      ),
    },
    {
      key: 'age',
      header: 'Đã chờ',
      cell: (item) => (
        <div className="space-y-1">
          <p className="text-sm">{formatAge(item.ageMinutes)}</p>
          <SlaBadge breached={item.slaBreached} dueAt={item.slaDueAt} />
        </div>
      ),
    },
    {
      key: 'duplicates',
      header: 'Nghi trùng',
      cell: (item) =>
        item.openDuplicates > 0 ? (
          <Badge variant="warning" icon={<Copy className="h-3.5 w-3.5" aria-hidden="true" />}>
            {item.openDuplicates} tin
          </Badge>
        ) : (
          <span className="text-sm text-on-surface-variant">Không</span>
        ),
    },
    {
      key: 'claim',
      header: 'Người xử lý',
      cell: (item) =>
        item.claim ? (
          <p className="text-sm">
            {item.claim.mine ? 'Bạn' : item.claim.moderatorName}
            <span className="block text-xs text-on-surface-variant">đến {formatDateTime(item.claim.expiresAt)}</span>
          </p>
        ) : (
          <span className="text-sm text-on-surface-variant">Chưa ai nhận</span>
        ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'end',
      cell: (item) => (
        <div className="flex flex-wrap justify-end gap-2">
          {!item.claim && (
            <Button
              size="sm"
              variant="outline"
              leftIcon={<Hand className="h-4 w-4" />}
              onClick={() => claim(item)}
              aria-label={`Nhận xử lý ${item.title}`}
            >
              Nhận xử lý
            </Button>
          )}
          <Button
            size="sm"
            leftIcon={<FileSearch className="h-4 w-4" />}
            onClick={() => setOpen(item)}
            aria-label={`Đối chiếu ${item.title}`}
          >
            Đối chiếu
          </Button>
        </div>
      ),
    },
  ];

  const pageCount = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <div className="space-y-4 pt-4">
      {data && (
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Stat label="Đang chờ duyệt" value={String(data.stats.total)} />
          <Stat
            label="Quá hạn 24 giờ"
            value={String(data.stats.slaBreached)}
            tone={data.stats.slaBreached > 0 ? 'warning' : undefined}
          />
          <Stat label="Chờ lâu nhất từ" value={formatDateTime(data.stats.oldestSubmittedAt)} />
        </dl>
      )}
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Lọc hàng đợi">
        {FILTERS.map((f) => (
          <Chip
            key={f.id}
            size="sm"
            selected={filter === f.id}
            onClick={() => {
              setFilter(f.id);
              setPage(0);
              setSelected(new Set());
            }}
          >
            {f.label}
          </Chip>
        ))}
        <Button size="sm" variant="ghost" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void load()}>
          Tải lại
        </Button>
      </div>

      {feedback && (
        <InlineFeedback kind={feedback.kind} title={feedback.title}>
          {feedback.results && (
            <ul className="mt-2 space-y-1 text-sm">
              {feedback.results.map((r) => (
                <li key={`${r.listingId}-${r.revisionId}`}>
                  {items.find((i) => i.listingId === r.listingId)?.title ?? r.listingId}:{' '}
                  {OUTCOME_LABELS[r.outcome] ?? r.outcome}
                </li>
              ))}
            </ul>
          )}
        </InlineFeedback>
      )}

      {selectedItems.length > 0 && (
        <section
          aria-label="Thao tác hàng loạt"
          className="rounded-lg border border-outline-variant bg-surface-container-low p-4"
        >
          <p className="text-sm font-semibold">
            Đã chọn {selectedItems.length} tin trên trang này. Chỉ {inScope.length} tin bạn đang nhận xử lý sẽ được áp
            dụng
            {selectedItems.length - inScope.length > 0
              ? `; ${selectedItems.length - inScope.length} tin còn lại sẽ bị bỏ qua`
              : ''}
            .
          </p>
          <div className="mt-3 flex flex-wrap items-center justify-between gap-6">
            <Button disabled={inScope.length === 0} onClick={() => setDecision({ kind: 'approve', items: inScope })}>
              Duyệt {inScope.length} tin
            </Button>
            <Button
              variant="danger"
              disabled={inScope.length === 0}
              onClick={() => setDecision({ kind: 'reject', items: inScope })}
            >
              Từ chối {inScope.length} tin
            </Button>
          </div>
        </section>
      )}

      <DataTable
        caption="Hàng đợi kiểm duyệt, tin chờ lâu nhất ở đầu"
        columns={columns}
        rows={items}
        getRowId={(item) => item.listingId}
        status={status}
        errorMessage={error ?? undefined}
        onRetry={() => void load()}
        selection={{ selectedIds: selected, onChange: setSelected, rowLabel: (item) => item.title }}
        empty={
          <EmptyState
            title="Không có tin nào trong bộ lọc này"
            description="Hàng đợi đã được xử lý hết hoặc hãy chọn bộ lọc khác."
          />
        }
        footer={
          data && data.total > data.size ? (
            <Pagination page={page + 1} pageCount={pageCount} onPageChange={(p) => setPage(p - 1)} />
          ) : undefined
        }
      />

      {open && (
        <ReviewDialog
          item={open}
          onClose={() => setOpen(null)}
          onChanged={() => void load()}
          onDecide={(kind) => setDecision({ kind, items: [open] })}
        />
      )}

      <ReasonDialog
        open={decision !== null}
        title={decision?.kind === 'approve' ? 'Phê duyệt nội dung tin' : 'Từ chối tin'}
        description={
          decision?.kind === 'approve'
            ? 'Tin sẽ hiển thị công khai với nhãn "Nội dung tin đã qua kiểm duyệt". Đây không phải xác nhận pháp lý.'
            : 'Người đăng sẽ thấy lý do và có thể sửa rồi gửi lại.'
        }
        reasons={decision?.kind === 'approve' ? reasons.approve : reasons.reject}
        noteLabel={decision?.kind === 'approve' ? 'Ghi chú nội bộ' : 'Hướng dẫn cho người đăng'}
        noteMinLength={decision?.kind === 'reject' ? 5 : 0}
        confirmLabel={
          decision?.kind === 'approve'
            ? `Phê duyệt ${decision?.items.length ?? 0} tin`
            : `Từ chối ${decision?.items.length ?? 0} tin`
        }
        confirmVariant={decision?.kind === 'approve' ? 'primary' : 'danger'}
        scope={
          decision && (
            <div className="rounded-md bg-surface-container-low p-3 text-sm">
              <p className="font-semibold">Phạm vi áp dụng ({decision.items.length} tin):</p>
              <ul className="mt-1 list-disc pl-5">
                {decision.items.slice(0, 10).map((i) => (
                  <li key={i.listingId}>
                    <ClampedText as="span" text={i.title} />
                  </li>
                ))}
                {decision.items.length > 10 && <li>… và {decision.items.length - 10} tin khác</li>}
              </ul>
            </div>
          )
        }
        onClose={() => setDecision(null)}
        onConfirm={async (code, note) => {
          if (!decision) return;
          if (decision.items.length === 1 && open) {
            const item = decision.items[0];
            if (decision.kind === 'approve') await moderationV2Api.approve(item.listingId, item.revisionId, code, note);
            else await moderationV2Api.reject(item.listingId, item.revisionId, code, note);
            setFeedback({
              kind: 'success',
              title: `${decision.kind === 'approve' ? 'Đã phê duyệt' : 'Đã từ chối'}: ${item.title}`,
            });
            setOpen(null);
          } else {
            const result = await moderationV2Api.bulk(
              decision.kind === 'approve' ? 'APPROVE' : 'REJECT',
              decision.items.map((i) => ({ listingId: i.listingId, revisionId: i.revisionId })),
              code,
              note,
            );
            const done = result.results.filter((r) => r.outcome === 'APPROVED' || r.outcome === 'REJECTED').length;
            setFeedback({
              kind: done === result.results.length ? 'success' : 'warning',
              title: `Đã xử lý ${done}/${result.results.length} tin`,
              results: result.results,
            });
            setSelected(new Set());
          }
          await load();
        }}
      />
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'warning' }) {
  return (
    <div
      className={`rounded-lg border p-3 ${tone === 'warning' ? 'border-warning/40 bg-warning-container' : 'border-outline-variant bg-surface-container-lowest'}`}
    >
      <dt className="text-xs font-semibold uppercase text-on-surface-variant">{label}</dt>
      <dd className="mt-1 text-lg font-bold text-on-surface">{value}</dd>
    </div>
  );
}

function ReviewDialog({
  item,
  onClose,
  onChanged,
  onDecide,
}: {
  item: ModerationQueueItem;
  onClose: () => void;
  onChanged: () => void;
  onDecide: (kind: 'approve' | 'reject') => void;
}) {
  const [diff, setDiff] = useState<ListingDiff | null>(null);
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[]>([]);
  const [history, setHistory] = useState<DecisionView[]>([]);
  const [claim, setClaim] = useState(item.claim);
  const [error, setError] = useState<string | null>(null);

  const loadDetails = useCallback(async () => {
    try {
      const [d, dup, h] = await Promise.all([
        moderationApi.getDiff(item.listingId),
        moderationV2Api.duplicates(item.listingId),
        moderationV2Api.decisions(item.listingId),
      ]);
      setDiff(d);
      setDuplicates(dup);
      setHistory(h);
    } catch (err) {
      setError(errorMessage(err, 'Không tải được chi tiết đối chiếu.'));
    }
  }, [item.listingId]);

  useEffect(() => {
    void loadDetails();
  }, [loadDetails]);

  const takeClaim = async () => {
    try {
      const c = await moderationV2Api.claim(item.listingId);
      setClaim(c);
      setError(null);
      onChanged();
    } catch (err) {
      setError(errorMessage(err, 'Không thể nhận xử lý.'));
    }
  };
  const release = async () => {
    await moderationV2Api.release(item.listingId);
    setClaim(null);
    onChanged();
  };
  const decideDuplicate = async (candidate: DuplicateCandidate, status: 'DISMISSED' | 'CONFIRMED') => {
    await moderationV2Api.decideDuplicate(candidate.id, status);
    await loadDetails();
    onChanged();
  };

  const blocked = claim != null && !claim.mine;
  return (
    <Dialog
      size="xl"
      open
      onClose={onClose}
      title={item.title}
      description={`${item.kind === 'EDIT' ? `Bản sửa #${item.revisionNumber} so với bản đang công khai` : 'Lần gửi duyệt đầu tiên'} · chờ ${formatAge(item.ageMinutes)}`}
      footer={
        <div className="flex w-full flex-wrap items-center justify-between gap-6">
          <Button disabled={blocked} onClick={() => onDecide('approve')}>
            Phê duyệt…
          </Button>
          <Button variant="danger" disabled={blocked} onClick={() => onDecide('reject')}>
            Từ chối…
          </Button>
        </div>
      }
    >
      <div className="space-y-6">
        <section
          aria-label="Người xử lý"
          className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-surface-container-low p-3"
        >
          <p className="text-sm">
            {claim
              ? claim.mine
                ? `Bạn đang nhận xử lý đến ${formatDateTime(claim.expiresAt)}.`
                : `${claim.moderatorName} đang xử lý đến ${formatDateTime(claim.expiresAt)}; bạn chưa thể quyết định.`
              : 'Chưa ai nhận xử lý tin này.'}
          </p>
          {claim?.mine ? (
            <Button size="sm" variant="outline" leftIcon={<Undo2 className="h-4 w-4" />} onClick={release}>
              Trả lại
            </Button>
          ) : (
            !claim && (
              <Button size="sm" variant="outline" leftIcon={<Hand className="h-4 w-4" />} onClick={takeClaim}>
                Nhận xử lý 30 phút
              </Button>
            )
          )}
        </section>
        {error && <InlineFeedback kind="error" title={error} />}

        <section aria-labelledby="diff-heading">
          <h3 id="diff-heading" className="text-base font-bold">
            Bản công khai so với bản gửi duyệt {diff ? `(${diff.changedCount} mục thay đổi)` : ''}
          </h3>
          {!diff ? (
            <Skeleton className="mt-2 h-32" />
          ) : (
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-sm">
                <caption className="sr-only">So sánh từng trường</caption>
                <thead>
                  <tr className="text-left text-xs uppercase text-on-surface-variant">
                    <th scope="col" className="py-2 pr-3">
                      Trường
                    </th>
                    <th scope="col" className="py-2 pr-3">
                      Đang công khai
                    </th>
                    <th scope="col" className="py-2">
                      Gửi duyệt
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {diff.diffs.map((d) => (
                    <tr key={d.fieldName} className={d.isChanged ? 'bg-warning-container/40' : undefined}>
                      <th scope="row" className="py-2 pr-3 text-left font-medium">
                        {d.fieldLabel}
                        {d.isChanged && <span className="ml-1 text-xs font-bold">(đã đổi)</span>}
                      </th>
                      <td className="min-w-24 py-2 pr-3 align-top whitespace-pre-line [overflow-wrap:anywhere]">
                        {d.oldValue || 'Trống'}
                      </td>
                      <td className="min-w-24 py-2 align-top whitespace-pre-line [overflow-wrap:anywhere]">
                        {d.newValue || 'Trống'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section aria-labelledby="dup-heading">
          <h3 id="dup-heading" className="text-base font-bold">
            Tin nghi trùng
          </h3>
          {duplicates.length === 0 ? (
            <p className="mt-1 text-sm text-on-surface-variant">
              Không phát hiện tin nghi trùng trong cùng khu vực, loại hình, diện tích và mức giá.
            </p>
          ) : (
            <ul className="mt-2 space-y-3">
              {duplicates.map((c) => (
                <li key={c.id} className="rounded-md border border-outline-variant p-3 text-sm">
                  <p className="font-semibold">{c.otherTitle ?? c.otherListingId}</p>
                  <p className="text-on-surface-variant">
                    {c.otherAddress ?? 'Chưa có địa chỉ'} ·{' '}
                    {c.otherPriceVnd != null ? formatPriceVnd(c.otherPriceVnd) : 'Chưa có giá'} · {c.otherAreaM2 ?? '?'}{' '}
                    m²
                  </p>
                  <p className="mt-1">
                    Điểm giống: {Math.round(c.score * 100)}% · {c.reasons.map(reasonLabel).join(', ')}
                  </p>
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <StatusBadge
                      label={
                        c.status === 'OPEN'
                          ? 'Chưa kết luận'
                          : c.status === 'CONFIRMED'
                            ? 'Đã xác nhận trùng'
                            : 'Không trùng'
                      }
                      variant={c.status === 'CONFIRMED' ? 'warning' : c.status === 'DISMISSED' ? 'neutral' : 'info'}
                    />
                    {c.status === 'OPEN' && (
                      <>
                        <Button size="sm" variant="outline" onClick={() => decideDuplicate(c, 'CONFIRMED')}>
                          Xác nhận trùng
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => decideDuplicate(c, 'DISMISSED')}>
                          Không trùng
                        </Button>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="history-heading">
          <h3 id="history-heading" className="flex items-center gap-2 text-base font-bold">
            <History className="h-4 w-4" aria-hidden="true" /> Lịch sử quyết định
          </h3>
          {history.length === 0 ? (
            <p className="mt-1 text-sm text-on-surface-variant">Chưa có quyết định nào.</p>
          ) : (
            <ol className="mt-2 space-y-2 text-sm">
              {history.map((h) => (
                <li key={h.id} className="rounded-md bg-surface-container-low p-2">
                  <span className="font-semibold">{DECISION_LABELS[h.decision]}</span> bản #{h.revisionNumber} ·{' '}
                  {h.moderatorName ?? 'Không rõ'} · {formatDateTime(h.createdAt)}
                  <span className="block text-on-surface-variant">
                    Lý do: {h.reasonCode}
                    {h.note ? ` — ${h.note}` : ''}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </Dialog>
  );
}

function reasonLabel(reason: string): string {
  if (reason.startsWith('TEXT_SIMILARITY:'))
    return `mô tả/địa chỉ giống ${Math.round(Number(reason.split(':')[1]) * 100)}%`;
  return (
    {
      EXACT_FINGERPRINT: 'trùng khớp địa chỉ/diện tích/loại',
      SAME_DISTRICT_TYPE: 'cùng quận và loại hình',
      AREA_WITHIN_5_PERCENT: 'diện tích lệch dưới 5%',
      PRICE_BUCKET: 'mức giá tương đương',
      SAME_OWNER: 'cùng người đăng',
    }[reason] ?? reason
  );
}

function AuditPanel() {
  const [status, setStatus] = useState<'OPEN' | 'PASSED' | 'FAILED'>('OPEN');
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<AuditSample[]>([]);
  const [total, setTotal] = useState(0);
  const [tableStatus, setTableStatus] = useState<DataTableStatus>('loading');
  const [reviewing, setReviewing] = useState<{ sample: AuditSample; outcome: 'PASSED' | 'FAILED' } | null>(null);
  const [reasons, setReasons] = useState<{ approve: ReasonChoice[]; reject: ReasonChoice[] }>({
    approve: [],
    reject: [],
  });

  const load = useCallback(async () => {
    setTableStatus('refreshing');
    try {
      const result = await moderationV2Api.auditSamples(status, page);
      setRows(result.items);
      setTotal(result.total);
      setTableStatus('ready');
    } catch {
      setTableStatus('error');
    }
  }, [status, page]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    moderationV2Api
      .reasons()
      .then((r) =>
        setReasons({
          approve: r.approve.map((x) => ({ code: x.code, label: x.vietnameseLabel })),
          reject: r.reject.map((x) => ({ code: x.code, label: x.vietnameseLabel })),
        }),
      )
      .catch(() => undefined);
  }, []);

  const columns: DataTableColumn<AuditSample>[] = [
    { key: 'week', header: 'Tuần', cell: (s) => `Từ ${s.weekStart}` },
    { key: 'title', header: 'Tin đã duyệt', cell: (s) => <ClampedText text={s.title} className="font-semibold" /> },
    {
      key: 'by',
      header: 'Người duyệt gốc',
      cell: (s) => `${s.originalModeratorName ?? 'Không rõ'} · ${formatDateTime(s.approvedAt)}`,
    },
    {
      key: 'actions',
      header: 'Kết luận',
      align: 'end',
      cell: (s) =>
        s.status === 'OPEN' ? (
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              size="sm"
              variant="outline"
              leftIcon={<ShieldCheck className="h-4 w-4" />}
              onClick={() => setReviewing({ sample: s, outcome: 'PASSED' })}
            >
              Đạt
            </Button>
            <Button size="sm" variant="danger" onClick={() => setReviewing({ sample: s, outcome: 'FAILED' })}>
              Không đạt
            </Button>
          </div>
        ) : (
          <span className="text-sm">
            {s.status === 'PASSED' ? 'Đạt' : 'Không đạt'} · {s.reviewerName ?? ''}
          </span>
        ),
    },
  ];

  return (
    <div className="space-y-4 pt-4">
      <p className="text-sm text-on-surface-variant">
        Mỗi thứ Hai hệ thống chọn ngẫu nhiên 5% (tối đa 20) tin đã được duyệt trong tuần trước để một người khác xem
        lại.
      </p>
      <div className="flex gap-2" role="group" aria-label="Lọc mẫu kiểm tra">
        {(['OPEN', 'PASSED', 'FAILED'] as const).map((s) => (
          <Chip
            key={s}
            size="sm"
            selected={status === s}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
          >
            {s === 'OPEN' ? 'Chưa xem lại' : s === 'PASSED' ? 'Đạt' : 'Không đạt'}
          </Chip>
        ))}
      </div>
      <DataTable
        caption="Mẫu kiểm tra ngẫu nhiên"
        columns={columns}
        rows={rows}
        getRowId={(s) => s.id}
        status={tableStatus}
        onRetry={() => void load()}
        empty={<EmptyState title="Không có mẫu nào" description="Mẫu của tuần trước được tạo tự động vào thứ Hai." />}
        footer={
          total > 20 ? (
            <Pagination page={page + 1} pageCount={Math.ceil(total / 20)} onPageChange={(p) => setPage(p - 1)} />
          ) : undefined
        }
      />
      <ReasonDialog
        open={reviewing !== null}
        title={reviewing?.outcome === 'PASSED' ? 'Xác nhận quyết định duyệt là đúng' : 'Quyết định duyệt chưa đúng'}
        reasons={reviewing?.outcome === 'PASSED' ? reasons.approve : reasons.reject}
        noteMinLength={reviewing?.outcome === 'FAILED' ? 5 : 0}
        confirmLabel="Lưu kết luận"
        confirmVariant={reviewing?.outcome === 'FAILED' ? 'danger' : 'primary'}
        onClose={() => setReviewing(null)}
        onConfirm={async (code, note) => {
          if (!reviewing) return;
          await moderationV2Api.reviewSample(reviewing.sample.id, reviewing.outcome, code, note);
          await load();
        }}
      />
    </div>
  );
}
