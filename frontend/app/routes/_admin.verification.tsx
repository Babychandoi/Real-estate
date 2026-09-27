import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, FileCheck2, RefreshCw } from 'lucide-react';
import {
  approveVerification,
  fetchVerificationQueue,
  rejectVerification,
} from '@/entities/verification/api/verificationApi';
import type { ListingVerification } from '@/entities/verification/model/types';

const VERIFICATION_TYPE = {
  CERTIFICATE_OF_OWNERSHIP: 'Giấy chứng nhận quyền sở hữu',
  POWER_OF_ATTORNEY: 'Giấy ủy quyền',
  PROJECT_PURCHASE_CONTRACT: 'Hợp đồng mua bán dự án',
} as const;

export default function VerificationDeskPage() {
  const [items, setItems] = useState<ListingVerification[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');
  const [reason, setReason] = useState<Record<string, string>>({});
  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await fetchVerificationQueue());
    } catch {
      setError('Không thể tải danh sách giấy tờ tin đăng. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  const act = async (id: string, operation: () => Promise<unknown>) => {
    setBusyId(id);
    setError('');
    try {
      await operation();
      await load();
    } catch {
      setError('Không thể lưu kết quả thẩm định. Kiểm tra dữ liệu và thử lại.');
    } finally {
      setBusyId('');
    }
  };
  return (
    <div className="mx-auto max-w-6xl px-4 py-8 md:px-8">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <div className="flex items-center gap-3">
            <FileCheck2 className="h-7 w-7 text-emerald-700" />
            <h1 className="text-3xl font-bold tracking-tight text-slate-950">Thẩm định giấy tờ tin đăng</h1>
          </div>
          <p className="mt-2 text-sm text-slate-600">
            Hồ sơ danh tính cá nhân đã được chuyển sang mục Quản lý người dùng.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 font-semibold"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Tải lại
        </button>
      </header>
      {error && (
        <p className="mt-6 rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-900" role="alert">
          {error}
        </p>
      )}
      {loading ? (
        <p className="mt-8" role="status">
          Đang tải hồ sơ…
        </p>
      ) : items.length === 0 ? (
        <div className="mt-8 rounded-xl border border-slate-200 bg-white p-12 text-center text-slate-500">
          Không có giấy tờ tin đăng cần thẩm định.
        </div>
      ) : (
        <div className="mt-8 divide-y divide-slate-200 border-y border-slate-200">
          {items.map((item) => (
            <article key={item.id} className="py-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-bold text-slate-950">
                    {item.listingTitle || `Tin ${item.listingId.slice(0, 8)}`}
                  </h2>
                  <p className="mt-1 text-sm text-slate-600">
                    {VERIFICATION_TYPE[item.verificationType]} · {item.ownerNameOnDoc || 'Chưa có tên người đứng giấy'}
                  </p>
                </div>
                <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
                  {item.status === 'PENDING'
                    ? 'Chờ duyệt'
                    : item.status === 'VERIFIED_OWNER'
                      ? 'Đã xác minh chính chủ'
                      : item.status === 'REJECTED'
                        ? 'Đã từ chối'
                        : 'Đã thu hồi'}
                </span>
              </div>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-slate-500">Số giấy tờ</dt>
                  <dd>{item.certificateNumber || 'Chưa cung cấp'}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Ngày gửi</dt>
                  <dd>{new Date(item.createdAt).toLocaleString('vi-VN')}</dd>
                </div>
              </dl>
              {item.status === 'PENDING' && (
                <div className="mt-4 grid gap-2 sm:grid-cols-[auto_1fr_auto]">
                  <button
                    disabled={busyId === item.id}
                    onClick={() => void act(item.id, () => approveVerification(item.id))}
                    className="min-h-11 rounded-lg bg-emerald-700 px-4 font-bold text-white disabled:opacity-50"
                  >
                    <span className="inline-flex items-center gap-2">
                      <CheckCircle2 className="h-4 w-4" />
                      Xác nhận đạt
                    </span>
                  </button>
                  <input
                    aria-label={`Lý do từ chối xác minh tin ${item.listingId}`}
                    value={reason[item.id] || ''}
                    onChange={(event) => setReason({ ...reason, [item.id]: event.target.value })}
                    placeholder="Lý do từ chối cụ thể"
                    className="min-h-11 rounded-lg border border-slate-300 px-3 text-base"
                  />
                  <button
                    disabled={busyId === item.id || !reason[item.id]?.trim()}
                    onClick={() => void act(item.id, () => rejectVerification(item.id, reason[item.id]))}
                    className="min-h-11 rounded-lg bg-rose-700 px-4 font-bold text-white disabled:opacity-50"
                  >
                    Từ chối
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
