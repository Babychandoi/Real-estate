import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { FileLock2, History, Lock, LogOut, Search, ShieldCheck, ShieldOff, Unlock, UserCog } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { adminUsersApi, trustApi } from '@/entities/admin/api/adminApi';
import type {
  AdminAction,
  KycAccessLogEntry,
  KycDocumentAccess,
  TrustReasonOption,
} from '@/entities/admin/model/types';
import { fetchKycByUserId } from '@/entities/verification/api/verificationApi';
import type { UserKycProfile } from '@/entities/verification/model/types';
import { useAuth } from '@/shared/auth/AuthContext';
import { ROLE_LABELS, ROLE_PRIORITY } from '@/shared/auth/roles';
import { errorMessage } from '@/shared/api/errors';
import { ClampedText, PasswordReasonDialog, ReasonDialog, StatusBadge, formatDateTime } from '@/shared/admin/adminUi';
import type { BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { EmptyState } from '@/shared/ui/EmptyState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { PrivateMediaImage } from '@/shared/ui/PrivateMediaImage';
import { Select } from '@/shared/ui/Select';
import { Sheet } from '@/shared/ui/Sheet';
import { TextInput } from '@/shared/ui/TextInput';

interface UserItem {
  id: string;
  fullName: string;
  email: string | null;
  role: string;
  status: string;
  kycStatus: string;
  planCode: string;
  listingQuotaRemaining: number;
  listingCount: number;
  createdAt: string;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  planExpiresAt: string | null;
  mfaEnrolled?: boolean;
}
interface UserPage {
  items: UserItem[];
  page: number;
  size: number;
  total: number;
}

const STATUS: Record<string, { label: string; variant: BadgeVariant }> = {
  ACTIVE: { label: 'Đang hoạt động', variant: 'success' },
  SUSPENDED: { label: 'Đã khóa', variant: 'error' },
  PENDING_EMAIL_VERIFICATION: { label: 'Chờ xác minh email', variant: 'warning' },
};
const KYC: Record<string, { label: string; variant: BadgeVariant }> = {
  NOT_SUBMITTED: { label: 'Chưa gửi', variant: 'neutral' },
  PENDING: { label: 'Đang chờ duyệt', variant: 'info' },
  VERIFIED: { label: 'Đã xác minh', variant: 'success' },
  REJECTED: { label: 'Bị từ chối', variant: 'error' },
};
const ACTION_LABELS: Record<AdminAction['action'], string> = {
  ROLE_CHANGE: 'Đổi vai trò',
  LOCK: 'Khóa tài khoản',
  UNLOCK: 'Mở khóa',
  MFA_RESET: 'Đặt lại xác thực hai lớp',
  SESSIONS_REVOKE: 'Đăng xuất mọi thiết bị',
};
const isStaffRole = (role: string) => role === 'ADMIN' || role === 'MODERATOR';
const roleLabel = (role: string | null) =>
  role ? ((ROLE_LABELS as Record<string, string>)[role] ?? role) : 'Không rõ';

export function AdminUsersPage() {
  const { user: me } = useAuth();
  const [data, setData] = useState<UserPage>({ items: [], page: 0, size: 20, total: 0 });
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState({ query: '', role: '', status: '' });
  const [page, setPage] = useState(0);
  const [tableStatus, setTableStatus] = useState<DataTableStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [roleTarget, setRoleTarget] = useState<UserItem | null>(null);
  const [statusTarget, setStatusTarget] = useState<{ user: UserItem; next: 'ACTIVE' | 'SUSPENDED' } | null>(null);
  const [historyTarget, setHistoryTarget] = useState<UserItem | null>(null);
  const [kycTarget, setKycTarget] = useState<UserItem | null>(null);
  const [securityTarget, setSecurityTarget] = useState<{ user: UserItem; action: 'mfa' | 'sessions' } | null>(null);

  const load = useCallback(async () => {
    setTableStatus((s) => (s === 'loading' ? 'loading' : 'refreshing'));
    const params = new URLSearchParams({ page: String(page), size: '20' });
    if (filters.query) params.set('query', filters.query);
    if (filters.role) params.set('role', filters.role);
    if (filters.status) params.set('status', filters.status);
    try {
      setData(await apiClient<UserPage>(`/admin/users?${params}`));
      setTableStatus('ready');
      setError(null);
    } catch (err) {
      setTableStatus('error');
      setError(errorMessage(err, 'Không thể tải danh sách người dùng.'));
    }
  }, [filters, page]);
  useEffect(() => {
    void load();
  }, [load]);

  const columns: DataTableColumn<UserItem>[] = [
    {
      key: 'user',
      header: 'Người dùng',
      cell: (u) => (
        <div className="min-w-[12rem]">
          <ClampedText text={u.fullName} className="font-semibold" />
          <ClampedText text={u.email ?? 'Không có email'} className="text-xs text-on-surface-variant" />
          <p className="text-xs text-on-surface-variant">Tham gia {formatDateTime(u.createdAt)}</p>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Vai trò',
      cell: (u) => (
        <div className="flex flex-col items-start gap-1">
          <span>{roleLabel(u.role)}</span>
          {isStaffRole(u.role) && (
            <StatusBadge
              label={u.mfaEnrolled ? 'Đã bật MFA' : 'Chưa bật MFA'}
              variant={u.mfaEnrolled ? 'success' : 'warning'}
            />
          )}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (u) => <StatusBadge {...(STATUS[u.status] ?? { label: u.status, variant: 'neutral' as BadgeVariant })} />,
    },
    {
      key: 'kyc',
      header: 'Định danh',
      cell: (u) => (
        <StatusBadge {...(KYC[u.kycStatus] ?? { label: u.kycStatus, variant: 'neutral' as BadgeVariant })} />
      ),
    },
    {
      key: 'plan',
      header: 'Gói / lượt đăng',
      cell: (u) => `${u.planCode} · ${u.listingQuotaRemaining} lượt · ${u.listingCount} tin`,
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'end',
      cell: (u) => {
        const self = me?.id === u.id;
        return (
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              size="sm"
              variant="ghost"
              leftIcon={<History className="h-4 w-4" />}
              onClick={() => setHistoryTarget(u)}
              aria-label={`Lịch sử ${u.fullName}`}
            >
              Lịch sử
            </Button>
            {u.kycStatus !== 'NOT_SUBMITTED' && (
              <Button
                size="sm"
                variant="ghost"
                leftIcon={<FileLock2 className="h-4 w-4" />}
                onClick={() => setKycTarget(u)}
                aria-label={`Hồ sơ định danh ${u.fullName}`}
              >
                Định danh
              </Button>
            )}
            {!self && (
              <Button
                size="sm"
                variant="outline"
                leftIcon={<UserCog className="h-4 w-4" />}
                onClick={() => setRoleTarget(u)}
                aria-label={`Đổi vai trò ${u.fullName}`}
              >
                Đổi vai trò
              </Button>
            )}
            {!self && (
              <Button
                size="sm"
                variant="outline"
                leftIcon={<LogOut className="h-4 w-4" />}
                onClick={() => setSecurityTarget({ user: u, action: 'sessions' })}
                aria-label={`Đăng xuất mọi thiết bị của ${u.fullName}`}
              >
                Đăng xuất mọi nơi
              </Button>
            )}
            {!self && u.mfaEnrolled && (
              <Button
                size="sm"
                variant="outline"
                leftIcon={<ShieldOff className="h-4 w-4" />}
                onClick={() => setSecurityTarget({ user: u, action: 'mfa' })}
                aria-label={`Đặt lại xác thực hai lớp của ${u.fullName}`}
              >
                Đặt lại MFA
              </Button>
            )}
            {!self && u.role !== 'ADMIN' && u.status === 'ACTIVE' && (
              <Button
                size="sm"
                variant="danger"
                leftIcon={<Lock className="h-4 w-4" />}
                onClick={() => setStatusTarget({ user: u, next: 'SUSPENDED' })}
                aria-label={`Khóa ${u.fullName}`}
              >
                Khóa
              </Button>
            )}
            {!self && u.status === 'SUSPENDED' && (
              <Button
                size="sm"
                variant="secondary"
                leftIcon={<Unlock className="h-4 w-4" />}
                onClick={() => setStatusTarget({ user: u, next: 'ACTIVE' })}
                aria-label={`Mở khóa ${u.fullName}`}
              >
                Mở khóa
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setFilters((f) => ({ ...f, query: query.trim() }));
  };

  return (
    <div className="space-y-6" data-ready={tableStatus === 'loading' ? undefined : 'true'}>
      <header>
        <h1 className="text-2xl font-bold">Quản lý người dùng</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          Danh sách không hiển thị số điện thoại. Đổi vai trò, khóa, đăng xuất mọi nơi, đặt lại xác thực hai lớp hay xem
          giấy tờ định danh đều cần lý do và được ghi lịch sử. Đổi vai trò cũng đăng xuất tài khoản đó.
        </p>
      </header>
      <form
        onSubmit={submit}
        className="grid grid-cols-1 gap-3 rounded-lg border border-outline-variant p-4 sm:grid-cols-4"
      >
        <FormField label="Tìm theo tên hoặc email" className="sm:col-span-2">
          {(control) => <TextInput {...control} value={query} onChange={(e) => setQuery(e.target.value)} />}
        </FormField>
        <FormField label="Vai trò">
          {(control) => (
            <Select
              {...control}
              value={filters.role}
              onChange={(e) => {
                setPage(0);
                setFilters((f) => ({ ...f, role: e.target.value }));
              }}
              options={[
                { value: '', label: 'Tất cả' },
                ...ROLE_PRIORITY.map((r) => ({ value: r, label: ROLE_LABELS[r] })),
              ]}
            />
          )}
        </FormField>
        <FormField label="Trạng thái">
          {(control) => (
            <Select
              {...control}
              value={filters.status}
              onChange={(e) => {
                setPage(0);
                setFilters((f) => ({ ...f, status: e.target.value }));
              }}
              options={[
                { value: '', label: 'Tất cả' },
                ...Object.entries(STATUS).map(([value, s]) => ({ value, label: s.label })),
              ]}
            />
          )}
        </FormField>
        <div className="sm:col-span-4">
          <Button type="submit" leftIcon={<Search className="h-4 w-4" />}>
            Tìm
          </Button>
        </div>
      </form>
      {feedback && <InlineFeedback kind="success" title={feedback} />}
      <DataTable
        caption={`Người dùng (${data.total})`}
        columns={columns}
        rows={data.items}
        getRowId={(u) => u.id}
        status={tableStatus}
        errorMessage={error ?? undefined}
        onRetry={() => void load()}
        empty={<EmptyState title="Không có người dùng phù hợp" />}
        footer={
          data.total > data.size ? (
            <Pagination
              page={page + 1}
              pageCount={Math.ceil(data.total / data.size)}
              onPageChange={(p) => setPage(p - 1)}
            />
          ) : undefined
        }
      />

      <ReasonDialog
        open={roleTarget !== null}
        title={roleTarget ? `Đổi vai trò: ${roleTarget.fullName}` : ''}
        description={
          roleTarget
            ? `Vai trò hiện tại: ${roleLabel(roleTarget.role)}. Không thể hạ quyền quản trị viên cuối cùng.`
            : undefined
        }
        choiceLabel="Vai trò mới"
        reasons={ROLE_PRIORITY.filter((r) => r !== roleTarget?.role).map((r) => ({ code: r, label: ROLE_LABELS[r] }))}
        noteLabel="Lý do"
        noteMinLength={5}
        confirmLabel="Đổi vai trò"
        onClose={() => setRoleTarget(null)}
        onConfirm={async (role, reason) => {
          if (!roleTarget) return;
          const result = await adminUsersApi.changeRole(roleTarget.id, role, reason);
          setFeedback(`${roleTarget.fullName}: ${roleLabel(result.fromRole)} sang ${roleLabel(result.toRole)}`);
          await load();
        }}
      />
      <ReasonDialog
        open={statusTarget !== null}
        title={
          statusTarget
            ? `${statusTarget.next === 'SUSPENDED' ? 'Khóa' : 'Mở khóa'} tài khoản: ${statusTarget.user.fullName}`
            : ''
        }
        description={
          statusTarget?.next === 'SUSPENDED' ? 'Mọi phiên đăng nhập của tài khoản sẽ bị thu hồi ngay.' : undefined
        }
        noteLabel="Lý do"
        noteMinLength={5}
        confirmLabel={statusTarget?.next === 'SUSPENDED' ? 'Khóa tài khoản' : 'Mở khóa'}
        confirmVariant={statusTarget?.next === 'SUSPENDED' ? 'danger' : 'primary'}
        onClose={() => setStatusTarget(null)}
        onConfirm={async (_code, reason) => {
          if (!statusTarget) return;
          await adminUsersApi.changeStatus(statusTarget.user.id, statusTarget.next, reason);
          setFeedback(`Đã ${statusTarget.next === 'SUSPENDED' ? 'khóa' : 'mở khóa'} ${statusTarget.user.fullName}`);
          await load();
        }}
      />
      <ReasonDialog
        open={securityTarget !== null}
        title={
          securityTarget
            ? `${securityTarget.action === 'mfa' ? 'Đặt lại xác thực hai lớp' : 'Đăng xuất mọi thiết bị'}: ${securityTarget.user.fullName}`
            : ''
        }
        description={
          securityTarget?.action === 'mfa'
            ? 'Chỉ làm khi đã xác minh chính chủ qua kênh khác (gọi điện, gặp trực tiếp). Ứng dụng xác thực và mã khôi phục cũ ngừng hoạt động, mọi phiên bị đăng xuất; lần đăng nhập sau phải thiết lập lại.'
            : 'Mọi phiên đăng nhập của tài khoản trên mọi thiết bị bị thu hồi ngay. Dùng khi nghi tài khoản bị lộ.'
        }
        noteLabel="Lý do"
        noteMinLength={5}
        confirmLabel={securityTarget?.action === 'mfa' ? 'Đặt lại MFA' : 'Đăng xuất mọi nơi'}
        confirmVariant="danger"
        onClose={() => setSecurityTarget(null)}
        onConfirm={async (_code, reason) => {
          if (!securityTarget) return;
          if (securityTarget.action === 'mfa') {
            await adminUsersApi.resetMfa(securityTarget.user.id, reason);
            setFeedback(`Đã đặt lại xác thực hai lớp của ${securityTarget.user.fullName}`);
          } else {
            const result = await adminUsersApi.revokeSessions(securityTarget.user.id, reason);
            setFeedback(`Đã đăng xuất ${result.revokedSessions} phiên của ${securityTarget.user.fullName}`);
          }
          await load();
        }}
      />
      {historyTarget && <HistorySheet user={historyTarget} onClose={() => setHistoryTarget(null)} />}
      {kycTarget && <KycSheet user={kycTarget} onClose={() => setKycTarget(null)} onChanged={() => void load()} />}
    </div>
  );
}

function HistorySheet({ user, onClose }: { user: UserItem; onClose: () => void }) {
  const [actions, setActions] = useState<AdminAction[] | null>(null);
  const [access, setAccess] = useState<KycAccessLogEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    Promise.all([adminUsersApi.history(user.id), adminUsersApi.kycAccessLog(user.id)])
      .then(([a, k]) => {
        setActions(a);
        setAccess(k);
      })
      .catch((err) => setError(errorMessage(err, 'Không tải được lịch sử.')));
  }, [user.id]);
  return (
    <Sheet
      open
      onClose={onClose}
      title={`Lịch sử: ${user.fullName}`}
      description="Đổi vai trò, khóa/mở khóa và các lần xem giấy tờ định danh"
    >
      <div className="space-y-6">
        {error && <InlineFeedback kind="error" title={error} />}
        <section aria-labelledby="actions-heading">
          <h3 id="actions-heading" className="text-base font-bold">
            Thao tác quản trị
          </h3>
          {actions && actions.length === 0 && (
            <p className="mt-1 text-sm text-on-surface-variant">Chưa có thao tác nào.</p>
          )}
          <ol className="mt-2 space-y-2 text-sm">
            {(actions ?? []).map((a) => (
              <li key={a.id} className="rounded-md bg-surface-container-low p-2">
                <span className="font-semibold">{ACTION_LABELS[a.action]}</span>
                {a.action === 'ROLE_CHANGE' ? `: ${roleLabel(a.fromValue)} sang ${roleLabel(a.toValue)}` : ''} ·{' '}
                {a.actorName ?? 'Không rõ'} · {formatDateTime(a.createdAt)}
                <span className="block text-on-surface-variant">Lý do: {a.reason}</span>
              </li>
            ))}
          </ol>
        </section>
        <section aria-labelledby="access-heading">
          <h3 id="access-heading" className="text-base font-bold">
            Lần xem giấy tờ định danh
          </h3>
          {access.length === 0 ? (
            <p className="mt-1 text-sm text-on-surface-variant">Chưa ai mở giấy tờ của tài khoản này.</p>
          ) : (
            <ol className="mt-2 space-y-2 text-sm">
              {access.map((e) => (
                <li key={e.id} className="rounded-md bg-surface-container-low p-2">
                  {e.actorName ?? e.actorId} · {formatDateTime(e.createdAt)}
                  <span className="block text-on-surface-variant">Lý do: {e.reason}</span>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </Sheet>
  );
}

function KycSheet({ user, onClose, onChanged }: { user: UserItem; onClose: () => void; onChanged: () => void }) {
  const [kyc, setKyc] = useState<UserKycProfile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reasons, setReasons] = useState<{ approve: TrustReasonOption[]; reject: TrustReasonOption[] }>({
    approve: [],
    reject: [],
  });
  const [decision, setDecision] = useState<'approve' | 'reject' | null>(null);
  const [asking, setAsking] = useState(false);
  const [docs, setDocs] = useState<KycDocumentAccess | null>(null);
  useEffect(() => {
    fetchKycByUserId(user.id)
      .then(setKyc)
      .catch((err) => setError(errorMessage(err, 'Không tải được hồ sơ định danh.')));
    trustApi
      .reasons()
      .then((r) => setReasons({ approve: r.approve, reject: r.reject }))
      .catch(() => undefined);
  }, [user.id]);
  return (
    <Sheet
      open
      onClose={onClose}
      title={`Hồ sơ định danh: ${user.fullName}`}
      description="Ảnh giấy tờ chỉ mở sau khi bạn nhập lại mật khẩu và nêu lý do; mỗi lần mở đều được ghi lại."
      footer={
        kyc?.status === 'PENDING' ? (
          <div className="flex flex-wrap items-center justify-between gap-6">
            <Button leftIcon={<ShieldCheck className="h-4 w-4" />} onClick={() => setDecision('approve')}>
              Xác minh danh tính…
            </Button>
            <Button variant="danger" onClick={() => setDecision('reject')}>
              Từ chối…
            </Button>
          </div>
        ) : undefined
      }
    >
      <div className="space-y-4 text-sm">
        {error && <InlineFeedback kind="error" title={error} />}
        {kyc && (
          <dl className="grid grid-cols-2 gap-2">
            <dt className="text-on-surface-variant">Họ tên trên CCCD</dt>
            <dd>{kyc.fullName}</dd>
            <dt className="text-on-surface-variant">Số CCCD</dt>
            <dd>{kyc.maskedIdNumber}</dd>
            <dt className="text-on-surface-variant">Trạng thái</dt>
            <dd>{KYC[kyc.status]?.label ?? kyc.status}</dd>
            <dt className="text-on-surface-variant">Gửi lúc</dt>
            <dd>{formatDateTime(kyc.createdAt)}</dd>
            {kyc.rejectionReason && (
              <>
                <dt className="text-on-surface-variant">Lý do từ chối</dt>
                <dd>{kyc.rejectionReason}</dd>
              </>
            )}
          </dl>
        )}
        {!docs ? (
          <Button variant="outline" leftIcon={<FileLock2 className="h-4 w-4" />} onClick={() => setAsking(true)}>
            Xem ảnh giấy tờ…
          </Button>
        ) : (
          <div className="space-y-2">
            <p className="text-xs text-on-surface-variant">Quyền xem hết hạn lúc {formatDateTime(docs.expiresAt)}.</p>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <PrivateMediaImage
                src={docs.identity?.idCardFrontUrl ?? undefined}
                alt={`Mặt trước CCCD của ${user.fullName}`}
                accessToken={docs.token}
              />
              <PrivateMediaImage
                src={docs.identity?.idCardBackUrl ?? undefined}
                alt={`Mặt sau CCCD của ${user.fullName}`}
                accessToken={docs.token}
              />
              <PrivateMediaImage
                src={docs.identity?.selfieUrl ?? undefined}
                alt={`Ảnh chân dung của ${user.fullName}`}
                accessToken={docs.token}
              />
            </div>
          </div>
        )}
      </div>
      <PasswordReasonDialog
        open={asking}
        title="Mở giấy tờ định danh"
        description="Nhập lại mật khẩu của bạn và lý do. Lần mở này được ghi vào lịch sử của tài khoản."
        onClose={() => setAsking(false)}
        onConfirm={async (password, reason) => setDocs(await adminUsersApi.openKycDocuments(user.id, password, reason))}
      />
      <ReasonDialog
        open={decision !== null}
        title={decision === 'approve' ? 'Xác minh danh tính' : 'Từ chối hồ sơ định danh'}
        description={
          decision === 'approve'
            ? 'Hiệu lực 24 tháng. Xác minh danh tính không bảo đảm quyền sở hữu hay pháp lý giao dịch.'
            : 'Người dùng thấy lý do và có thể gửi lại hồ sơ.'
        }
        reasons={(decision === 'approve' ? reasons.approve : reasons.reject).map((r) => ({
          code: r.code,
          label: r.label,
        }))}
        noteMinLength={decision === 'reject' ? 5 : 0}
        confirmLabel={decision === 'approve' ? 'Xác minh' : 'Từ chối'}
        confirmVariant={decision === 'approve' ? 'primary' : 'danger'}
        onClose={() => setDecision(null)}
        onConfirm={async (code, note) => {
          if (!kyc) return;
          if (decision === 'approve') await trustApi.approveKyc(kyc.id, code, note);
          else await trustApi.rejectKyc(kyc.id, code, note);
          setKyc(await fetchKycByUserId(user.id));
          onChanged();
        }}
      />
    </Sheet>
  );
}
