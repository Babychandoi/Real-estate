import { type FormEvent, useCallback, useEffect, useState } from 'react';
import {
  Ban,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Eye,
  RefreshCw,
  Search,
  ShieldCheck,
  UserRoundCheck,
  Users,
  X,
} from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { Button } from '@/shared/ui/Button';
import { approveKyc, fetchKycByUserId, rejectKyc } from '@/entities/verification/api/verificationApi';
import type { UserKycProfile } from '@/entities/verification/model/types';
import { PrivateMediaImage } from '@/shared/ui/PrivateMediaImage';

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
}
interface UserPage {
  items: UserItem[];
  page: number;
  size: number;
  total: number;
}

const ROLE_LABELS: Record<string, string> = {
  USER: 'Người dùng',
  OWNER: 'Chủ nhà',
  BROKER: 'Môi giới',
  MODERATOR: 'Kiểm duyệt viên',
  ADMIN: 'Quản trị viên',
};
const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  SUSPENDED: 'Đã khóa',
  PENDING_EMAIL_VERIFICATION: 'Chờ xác minh email',
};
const KYC_LABELS: Record<string, string> = {
  NOT_SUBMITTED: 'Chưa gửi',
  PENDING: 'Đang chờ',
  VERIFIED: 'Đã xác minh',
  REJECTED: 'Bị từ chối',
};
const formatDate = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
    : 'Chưa có';

export function AdminUsersPage() {
  const [data, setData] = useState<UserPage>({ items: [], page: 0, size: 20, total: 0 });
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [selectedKyc, setSelectedKyc] = useState<UserKycProfile | null>(null);
  const [kycLoading, setKycLoading] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  const load = useCallback(
    async (page = 0) => {
      setLoading(true);
      setError('');
      const params = new URLSearchParams({ page: String(page), size: '20' });
      if (appliedQuery) params.set('query', appliedQuery);
      if (role) params.set('role', role);
      if (status) params.set('status', status);
      try {
        setData(await apiClient<UserPage>(`/admin/users?${params}`));
      } catch {
        setError('Không thể tải danh sách người dùng. Vui lòng thử lại.');
      } finally {
        setLoading(false);
      }
    },
    [appliedQuery, role, status],
  );

  useEffect(() => {
    void load(0);
  }, [load]);
  const search = (event: FormEvent) => {
    event.preventDefault();
    setAppliedQuery(query.trim());
  };
  const changeStatus = async (user: UserItem, nextStatus: 'ACTIVE' | 'SUSPENDED') => {
    const action = nextStatus === 'SUSPENDED' ? 'khóa' : 'mở khóa';
    if (!window.confirm(`Xác nhận ${action} tài khoản ${user.fullName}?`)) return;
    setBusy(user.id);
    setError('');
    try {
      await apiClient(`/admin/users/${user.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: nextStatus }),
      });
      await load(data.page);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : `Không thể ${action} tài khoản.`);
    } finally {
      setBusy('');
    }
  };

  const pageCount = Math.max(1, Math.ceil(data.total / data.size));
  const openKyc = async (user: UserItem) => {
    setKycLoading(true);
    setError('');
    try {
      setSelectedKyc(await fetchKycByUserId(user.id));
    } catch {
      setError('Không thể tải hồ sơ eKYC của người dùng này.');
    } finally {
      setKycLoading(false);
    }
  };
  const reviewKyc = async (action: 'approve' | 'reject') => {
    if (!selectedKyc) return;
    if (action === 'reject' && !rejectReason.trim()) return;
    setBusy(selectedKyc.id);
    try {
      const updated =
        action === 'approve' ? await approveKyc(selectedKyc.id) : await rejectKyc(selectedKyc.id, rejectReason.trim());
      setSelectedKyc(updated);
      setRejectReason('');
      await load(data.page);
    } catch {
      setError('Không thể lưu kết quả xác minh. Vui lòng thử lại.');
    } finally {
      setBusy('');
    }
  };
  return (
    <main className="mx-auto max-w-7xl space-y-6 p-4 sm:p-6 lg:p-8">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950">Quản lý người dùng</h1>
          <p className="mt-2 text-sm text-slate-600">
            Tra cứu tài khoản, theo dõi xác minh và kiểm soát quyền truy cập hệ thống.
          </p>
        </div>
        <Button variant="outline" onClick={() => void load(data.page)} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Tải lại
        </Button>
      </header>

      <section className="grid gap-3 sm:grid-cols-3" aria-label="Tổng quan người dùng">
        <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
          <Users className="h-5 w-5 text-blue-700" />
          <div>
            <p className="text-xs font-medium text-slate-500">Tài khoản phù hợp bộ lọc</p>
            <p className="text-2xl font-bold tabular-nums text-slate-950">{data.total.toLocaleString('vi-VN')}</p>
          </div>
        </div>
        <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
          <ShieldCheck className="h-5 w-5 text-emerald-700" />
          <div>
            <p className="text-xs font-medium text-slate-500">Dữ liệu KYC</p>
            <p className="font-bold text-slate-950">Chỉ hiển thị trạng thái</p>
          </div>
        </div>
        <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
          <UserRoundCheck className="h-5 w-5 text-blue-700" />
          <div>
            <p className="text-xs font-medium text-slate-500">Phân trang máy chủ</p>
            <p className="font-bold text-slate-950">20 tài khoản / trang</p>
          </div>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-4">
        <form onSubmit={search} className="grid gap-3 lg:grid-cols-[minmax(260px,1fr)_190px_220px_auto]">
          <label className="relative">
            <span className="sr-only">Tìm người dùng</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              maxLength={150}
              className="min-h-11 w-full rounded-lg border border-slate-300 pl-10 pr-3 text-base outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
              placeholder="Tìm theo họ tên hoặc email"
            />
          </label>
          <select
            aria-label="Lọc theo vai trò"
            value={role}
            onChange={(event) => setRole(event.target.value)}
            className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-base"
          >
            <option value="">Tất cả vai trò</option>
            {Object.entries(ROLE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <select
            aria-label="Lọc theo trạng thái"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-base"
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <Button type="submit">Tìm kiếm</Button>
        </form>
      </section>

      {error && (
        <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
          {error}
        </p>
      )}
      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1050px] text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-5 py-3">Người dùng</th>
                <th className="px-5 py-3">Vai trò</th>
                <th className="px-5 py-3">Trạng thái</th>
                <th className="px-5 py-3">KYC</th>
                <th className="px-5 py-3">Gói / tin đăng</th>
                <th className="px-5 py-3">Hoạt động gần nhất</th>
                <th className="px-5 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.items.map((user) => (
                <tr key={user.id} className="align-top">
                  <td className="px-5 py-4">
                    <p className="font-semibold text-slate-950">{user.fullName}</p>
                    <p className="mt-1 text-xs text-slate-500">{user.email ?? 'Chưa có email'}</p>
                    <p className="mt-1 text-xs text-slate-400">Tham gia {formatDate(user.createdAt)}</p>
                  </td>
                  <td className="px-5 py-4 font-medium text-slate-700">{ROLE_LABELS[user.role] ?? user.role}</td>
                  <td className="px-5 py-4">
                    <span
                      className={`rounded-md px-2 py-1 text-xs font-semibold ${user.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-800' : user.status === 'SUSPENDED' ? 'bg-rose-50 text-rose-800' : 'bg-amber-50 text-amber-800'}`}
                    >
                      {STATUS_LABELS[user.status] ?? user.status}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <p className="text-slate-700">{KYC_LABELS[user.kycStatus] ?? user.kycStatus}</p>
                    {user.kycStatus !== 'NOT_SUBMITTED' && (
                      <button
                        type="button"
                        disabled={kycLoading}
                        onClick={() => void openKyc(user)}
                        className="mt-2 inline-flex min-h-9 items-center gap-1 rounded-lg border border-slate-300 px-3 text-xs font-bold text-slate-700 hover:bg-slate-50"
                      >
                        <Eye className="h-4 w-4" />
                        Xem hồ sơ
                      </button>
                    )}
                  </td>
                  <td className="px-5 py-4">
                    <p className="font-semibold text-slate-800">{user.planCode}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {user.listingCount} tin · còn {user.listingQuotaRemaining} lượt
                    </p>
                  </td>
                  <td className="px-5 py-4 text-slate-600">{formatDate(user.lastLoginAt)}</td>
                  <td className="px-5 py-4 text-right">
                    {user.role !== 'ADMIN' && user.status === 'ACTIVE' && (
                      <button
                        type="button"
                        disabled={busy === user.id}
                        onClick={() => void changeStatus(user, 'SUSPENDED')}
                        className="inline-flex min-h-9 items-center gap-1 rounded-lg border border-rose-200 px-3 text-xs font-bold text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                      >
                        <Ban className="h-4 w-4" />
                        Khóa
                      </button>
                    )}
                    {user.role !== 'ADMIN' && user.status === 'SUSPENDED' && (
                      <button
                        type="button"
                        disabled={busy === user.id}
                        onClick={() => void changeStatus(user, 'ACTIVE')}
                        className="inline-flex min-h-9 items-center gap-1 rounded-lg border border-emerald-200 px-3 text-xs font-bold text-emerald-700 hover:bg-emerald-50 disabled:opacity-50"
                      >
                        <CheckCircle2 className="h-4 w-4" />
                        Mở khóa
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {!loading && data.items.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-5 py-16 text-center text-slate-500">
                    Không tìm thấy tài khoản phù hợp.
                  </td>
                </tr>
              )}
              {loading && (
                <tr>
                  <td colSpan={7} className="px-5 py-16 text-center text-slate-500">
                    Đang tải danh sách người dùng…
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <footer className="flex items-center justify-between border-t border-slate-200 px-5 py-3 text-sm text-slate-600">
          <span>
            Trang {data.page + 1}/{pageCount} · {data.total.toLocaleString('vi-VN')} tài khoản
          </span>
          <div className="flex gap-2">
            <button
              aria-label="Trang trước"
              disabled={data.page === 0 || loading}
              onClick={() => void load(data.page - 1)}
              className="grid h-10 w-10 place-items-center rounded-lg border border-slate-300 disabled:opacity-40"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              aria-label="Trang sau"
              disabled={data.page + 1 >= pageCount || loading}
              onClick={() => void load(data.page + 1)}
              className="grid h-10 w-10 place-items-center rounded-lg border border-slate-300 disabled:opacity-40"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </footer>
      </section>
      {selectedKyc && (
        <div
          role="presentation"
          className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/50 p-0 sm:items-center sm:p-6"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setSelectedKyc(null);
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="kyc-detail-title"
            className="max-h-[92dvh] w-full max-w-5xl overflow-y-auto rounded-t-xl bg-white p-5 shadow-xl sm:rounded-xl sm:p-6"
          >
            <header className="flex items-start justify-between gap-4">
              <div>
                <h2 id="kyc-detail-title" className="text-xl font-bold text-slate-950">
                  Hồ sơ xác minh: {selectedKyc.fullName}
                </h2>
                <p className="mt-1 text-sm text-slate-600">
                  CCCD {selectedKyc.maskedIdNumber} · Sinh ngày {selectedKyc.dob || 'chưa cung cấp'}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedKyc(null)}
                aria-label="Đóng hồ sơ"
                className="grid h-10 w-10 place-items-center rounded-lg hover:bg-slate-100"
              >
                <X className="h-5 w-5" />
              </button>
            </header>
            <dl className="mt-5 grid gap-4 border-y border-slate-200 py-4 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-slate-500">Trạng thái</dt>
                <dd className="mt-1 font-semibold">{KYC_LABELS[selectedKyc.status]}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Ngày gửi</dt>
                <dd className="mt-1 font-semibold">{formatDate(selectedKyc.createdAt)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Địa chỉ</dt>
                <dd className="mt-1 font-semibold">{selectedKyc.address || 'Chưa cung cấp'}</dd>
              </div>
            </dl>
            <div className="mt-5 grid gap-4 sm:grid-cols-3">
              <div>
                <p className="mb-2 text-sm font-semibold">Mặt trước CCCD</p>
                <PrivateMediaImage
                  src={selectedKyc.idCardFrontUrl}
                  alt={`Mặt trước CCCD của ${selectedKyc.fullName}`}
                />
              </div>
              <div>
                <p className="mb-2 text-sm font-semibold">Mặt sau CCCD</p>
                <PrivateMediaImage src={selectedKyc.idCardBackUrl} alt={`Mặt sau CCCD của ${selectedKyc.fullName}`} />
              </div>
              <div>
                <p className="mb-2 text-sm font-semibold">Ảnh chân dung</p>
                <PrivateMediaImage src={selectedKyc.selfieUrl} alt={`Ảnh chân dung của ${selectedKyc.fullName}`} />
              </div>
            </div>
            {selectedKyc.status === 'PENDING' && (
              <div className="mt-6 grid gap-3 border-t border-slate-200 pt-5 sm:grid-cols-[auto_1fr_auto]">
                <Button disabled={busy === selectedKyc.id} onClick={() => void reviewKyc('approve')}>
                  Xác nhận khớp
                </Button>
                <input
                  value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value)}
                  maxLength={500}
                  placeholder="Nhập lý do nếu từ chối"
                  className="min-h-11 rounded-lg border border-slate-300 px-3 text-base"
                />
                <button
                  type="button"
                  disabled={busy === selectedKyc.id || !rejectReason.trim()}
                  onClick={() => void reviewKyc('reject')}
                  className="min-h-11 rounded-lg bg-rose-700 px-4 font-bold text-white disabled:opacity-40"
                >
                  Từ chối
                </button>
              </div>
            )}
          </section>
        </div>
      )}
    </main>
  );
}

export default AdminUsersPage;
