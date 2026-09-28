import { useCallback, useEffect, useState } from 'react';
import { FileLock2, History, RefreshCw } from 'lucide-react';
import { trustApi } from '@/entities/admin/api/adminApi';
import type {
  EvidenceComparison,
  KycDocumentAccess,
  TrustReasonOption,
  VerificationEvidence,
} from '@/entities/admin/model/types';
import { fetchVerificationQueue } from '@/entities/verification/api/verificationApi';
import type { ListingVerification, VerificationStatus } from '@/entities/verification/model/types';
import { errorMessage } from '@/shared/api/errors';
import { PasswordReasonDialog, ReasonDialog, StatusBadge, formatDate, formatDateTime } from '@/shared/admin/adminUi';
import type { BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Chip } from '@/shared/ui/Chip';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { EmptyState } from '@/shared/ui/EmptyState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { PrivateMediaImage } from '@/shared/ui/PrivateMediaImage';
import { Sheet } from '@/shared/ui/Sheet';
import { Skeleton } from '@/shared/ui/Skeleton';

const STATUS: Record<VerificationStatus, { label: string; variant: BadgeVariant }> = {
  PENDING: { label: 'Chờ đối chiếu', variant: 'info' },
  VERIFIED_OWNER: { label: 'Đã đối chiếu giấy tờ', variant: 'success' },
  REJECTED: { label: 'Bị từ chối', variant: 'error' },
  REVOKED: { label: 'Đã thu hồi', variant: 'warning' },
};
const RESULT: Record<EvidenceComparison['result'], { label: string; variant: BadgeVariant }> = {
  MATCH: { label: 'Khớp', variant: 'success' },
  MISMATCH: { label: 'Không khớp', variant: 'error' },
  MISSING: { label: 'Thiếu dữ liệu', variant: 'warning' },
  UNIQUE: { label: 'Chưa dùng cho người khác', variant: 'success' },
  USED_ELSEWHERE: { label: 'Đã dùng cho tin của người khác', variant: 'error' },
  SIMILAR: { label: 'Gần giống', variant: 'success' },
  DIFFERENT: { label: 'Khác nhau — kiểm tra thêm', variant: 'warning' },
};
const DECISION: Record<string, string> = {
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  REVOKED: 'Thu hồi',
  EXPIRED: 'Hết hiệu lực',
};

export default function VerificationDeskPage() {
  const [status, setStatus] = useState<VerificationStatus>('PENDING');
  const [rows, setRows] = useState<ListingVerification[]>([]);
  const [tableStatus, setTableStatus] = useState<DataTableStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<ListingVerification | null>(null);

  const load = useCallback(async () => {
    setTableStatus((s) => (s === 'loading' ? 'loading' : 'refreshing'));
    try {
      setRows(await fetchVerificationQueue(status));
      setTableStatus('ready');
      setError(null);
    } catch (err) {
      setTableStatus('error');
      setError(errorMessage(err, 'Không thể tải hồ sơ giấy tờ.'));
    }
  }, [status]);
  useEffect(() => {
    void load();
  }, [load]);

  const columns: DataTableColumn<ListingVerification>[] = [
    {
      key: 'listing',
      header: 'Tin đăng',
      cell: (v) => (
        <div className="min-w-[12rem]">
          <p className="font-semibold">{v.listingTitle ?? 'Chưa có tiêu đề'}</p>
          <p className="text-xs text-on-surface-variant">{v.listingAddress ?? ''}</p>
        </div>
      ),
    },
    { key: 'owner', header: 'Tên trên giấy tờ', cell: (v) => v.ownerNameOnDoc },
    { key: 'submitted', header: 'Gửi lúc', cell: (v) => formatDateTime(v.createdAt) },
    { key: 'status', header: 'Trạng thái', cell: (v) => <StatusBadge {...STATUS[v.status]} /> },
    {
      key: 'open',
      header: 'Thao tác',
      align: 'end',
      cell: (v) => (
        <Button size="sm" onClick={() => setOpen(v)} aria-label={`Đối chiếu ${v.listingTitle ?? v.id}`}>
          Đối chiếu
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6" data-ready={tableStatus === 'loading' ? undefined : 'true'}>
      <header>
        <h1 className="text-2xl font-bold">Đối chiếu giấy tờ chủ sở hữu</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          So khớp danh tính người đăng với giấy tờ và tin đăng trước khi quyết định. Đã đối chiếu có hiệu lực 180 ngày;
          không phải xác nhận pháp lý giao dịch.
        </p>
      </header>
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Lọc trạng thái">
        {(Object.keys(STATUS) as VerificationStatus[]).map((s) => (
          <Chip key={s} size="sm" selected={status === s} onClick={() => setStatus(s)}>
            {STATUS[s].label}
          </Chip>
        ))}
        <Button size="sm" variant="ghost" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void load()}>
          Tải lại
        </Button>
      </div>
      <DataTable
        caption="Hồ sơ giấy tờ"
        columns={columns}
        rows={rows}
        getRowId={(v) => v.id}
        status={tableStatus}
        errorMessage={error ?? undefined}
        onRetry={() => void load()}
        empty={<EmptyState title="Không có hồ sơ nào" />}
      />
      {open && <EvidenceSheet verificationId={open.id} onClose={() => setOpen(null)} onChanged={() => void load()} />}
    </div>
  );
}

function EvidenceSheet({
  verificationId,
  onClose,
  onChanged,
}: {
  verificationId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [evidence, setEvidence] = useState<VerificationEvidence | null>(null);
  const [reasons, setReasons] = useState<{
    approve: TrustReasonOption[];
    reject: TrustReasonOption[];
    revoke: TrustReasonOption[];
  } | null>(null);
  const [decision, setDecision] = useState<'approve' | 'reject' | 'revoke' | null>(null);
  const [asking, setAsking] = useState(false);
  const [docs, setDocs] = useState<KycDocumentAccess | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    try {
      setEvidence(await trustApi.evidence(verificationId));
    } catch (err) {
      setError(errorMessage(err, 'Không tải được bằng chứng.'));
    }
  }, [verificationId]);
  useEffect(() => {
    void load();
    trustApi
      .reasons()
      .then(setReasons)
      .catch(() => undefined);
  }, [load]);

  const choices = (kind: 'approve' | 'reject' | 'revoke') =>
    (reasons?.[kind] ?? []).map((r) => ({ code: r.code, label: r.label }));
  const privateUrls = [
    ...(docs?.ownershipDocumentUrls ?? [])
      .flatMap((u) => u.split(/[,;\s]+/))
      .filter((u) => u.startsWith('/api/v1/media/kyc/')),
  ];

  return (
    <Sheet
      open
      onClose={onClose}
      title={evidence?.listingTitle ?? 'Hồ sơ giấy tờ'}
      description={
        evidence ? `${STATUS[evidence.status].label} · gửi ${formatDateTime(evidence.submittedAt)}` : undefined
      }
      footer={
        evidence?.status === 'PENDING' ? (
          <div className="flex flex-wrap items-center justify-between gap-6">
            <Button onClick={() => setDecision('approve')}>Xác nhận đã đối chiếu…</Button>
            <Button variant="danger" onClick={() => setDecision('reject')}>
              Từ chối…
            </Button>
          </div>
        ) : evidence?.status === 'VERIFIED_OWNER' ? (
          <div className="flex justify-end">
            <Button variant="danger" onClick={() => setDecision('revoke')}>
              Thu hồi…
            </Button>
          </div>
        ) : undefined
      }
    >
      {error && <InlineFeedback kind="error" title={error} />}
      {!evidence ? (
        <Skeleton className="h-40" />
      ) : (
        <div className="space-y-6 text-sm">
          <section aria-labelledby="compare-heading">
            <h3 id="compare-heading" className="text-base font-bold">
              Đối chiếu bằng chứng
            </h3>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full">
                <caption className="sr-only">Danh tính người đăng so với giấy tờ và tin đăng</caption>
                <thead>
                  <tr className="text-left text-xs uppercase text-on-surface-variant">
                    <th scope="col" className="py-2 pr-3">
                      Nội dung
                    </th>
                    <th scope="col" className="py-2 pr-3">
                      Hồ sơ định danh
                    </th>
                    <th scope="col" className="py-2 pr-3">
                      Giấy tờ / tin đăng
                    </th>
                    <th scope="col" className="py-2">
                      Kết quả
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {evidence.comparisons.map((c) => (
                    <tr key={c.field} className="border-t border-outline-variant">
                      <th scope="row" className="py-2 pr-3 text-left font-medium">
                        {c.label}
                      </th>
                      <td className="py-2 pr-3">{c.identityValue ?? '—'}</td>
                      <td className="py-2 pr-3">{c.documentValue ?? '—'}</td>
                      <td className="py-2">
                        <StatusBadge {...RESULT[c.result]} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <div>
              <dt className="text-on-surface-variant">Danh tính người đăng</dt>
              <dd>
                {evidence.identity.status === 'VERIFIED'
                  ? `Đã xác minh, hiệu lực đến ${formatDate(evidence.identity.expiresAt)}`
                  : evidence.identity.status === 'EXPIRED'
                    ? 'Xác minh đã hết hạn'
                    : evidence.identity.status}
              </dd>
            </div>
            <div>
              <dt className="text-on-surface-variant">Loại giấy tờ · số</dt>
              <dd>
                {evidence.verificationType} · {evidence.certificateNumber ?? 'Không có số'}
              </dd>
            </div>
            <div>
              <dt className="text-on-surface-variant">Địa chỉ tin đăng</dt>
              <dd>{evidence.listingAddress ?? 'Chưa có'}</dd>
            </div>
            {evidence.expiresAt && (
              <div>
                <dt className="text-on-surface-variant">Hiệu lực đối chiếu</dt>
                <dd>đến {formatDate(evidence.expiresAt)}</dd>
              </div>
            )}
          </dl>
          <section aria-labelledby="docs-heading">
            <h3 id="docs-heading" className="text-base font-bold">
              Ảnh giấy tờ (riêng tư)
            </h3>
            {!docs ? (
              <Button
                className="mt-2"
                variant="outline"
                leftIcon={<FileLock2 className="h-4 w-4" />}
                onClick={() => setAsking(true)}
              >
                Mở ảnh giấy tờ…
              </Button>
            ) : (
              <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <PrivateMediaImage
                  src={docs.identity?.idCardFrontUrl ?? undefined}
                  alt="Mặt trước CCCD người đăng"
                  accessToken={docs.token}
                />
                {privateUrls.map((url, index) => (
                  <PrivateMediaImage key={url} src={url} alt={`Giấy tờ sở hữu ${index + 1}`} accessToken={docs.token} />
                ))}
                {evidence.documentUrls
                  .filter((u) => !u.startsWith('/api/v1/media/kyc/'))
                  .map((u) => (
                    <p key={u} className="break-all text-xs">
                      Tài liệu ngoài hệ thống: {u}
                    </p>
                  ))}
              </div>
            )}
          </section>
          <section aria-labelledby="trust-history">
            <h3 id="trust-history" className="flex items-center gap-2 text-base font-bold">
              <History className="h-4 w-4" aria-hidden="true" /> Lịch sử quyết định
            </h3>
            {evidence.history.length === 0 ? (
              <p className="mt-1 text-on-surface-variant">Chưa có quyết định.</p>
            ) : (
              <ol className="mt-2 space-y-2">
                {evidence.history.map((h) => (
                  <li key={h.id} className="rounded-md bg-surface-container-low p-2">
                    <span className="font-semibold">{DECISION[h.decision] ?? h.decision}</span> ·{' '}
                    {h.actorName ?? 'Hệ thống'} · {formatDateTime(h.createdAt)}
                    <span className="block text-on-surface-variant">
                      {h.reasonLabel}
                      {h.note ? ` — ${h.note}` : ''}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      )}
      <PasswordReasonDialog
        open={asking}
        title="Mở giấy tờ riêng tư"
        description="Nhập lại mật khẩu và lý do; lần mở này được ghi vào lịch sử của người đăng."
        onClose={() => setAsking(false)}
        onConfirm={async (password, reason) => setDocs(await trustApi.openDocuments(verificationId, password, reason))}
      />
      <ReasonDialog
        open={decision !== null}
        title={
          decision === 'approve'
            ? 'Xác nhận đã đối chiếu giấy tờ'
            : decision === 'reject'
              ? 'Từ chối hồ sơ giấy tờ'
              : 'Thu hồi đối chiếu'
        }
        description={
          decision === 'approve'
            ? 'Tin sẽ có nhãn "Đã đối chiếu giấy tờ chủ sở hữu" trong 180 ngày.'
            : 'Người đăng sẽ thấy lý do.'
        }
        reasons={decision ? choices(decision) : []}
        noteMinLength={decision === 'approve' ? 0 : 5}
        confirmLabel={decision === 'approve' ? 'Xác nhận' : decision === 'reject' ? 'Từ chối' : 'Thu hồi'}
        confirmVariant={decision === 'approve' ? 'primary' : 'danger'}
        onClose={() => setDecision(null)}
        onConfirm={async (code, note) => {
          if (decision === 'approve') await trustApi.approveOwnership(verificationId, code, note);
          if (decision === 'reject') await trustApi.rejectOwnership(verificationId, code, note);
          if (decision === 'revoke') await trustApi.revokeOwnership(verificationId, code, note);
          await load();
          onChanged();
        }}
      />
    </Sheet>
  );
}
