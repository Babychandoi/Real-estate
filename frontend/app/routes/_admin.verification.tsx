import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, RefreshCw, ShieldCheck } from 'lucide-react';
import {
  approveKyc, approveVerification, fetchKycQueue, fetchVerificationQueue, rejectKyc, rejectVerification,
} from '@/entities/verification/api/verificationApi';
import type { ListingVerification, UserKycProfile } from '@/entities/verification/model/types';
import { PrivateMediaImage } from '@/shared/ui/PrivateMediaImage';

const KYC_STATUS = { PENDING: 'Chờ duyệt', VERIFIED: 'Đã xác minh', REJECTED: 'Đã từ chối' } as const;
const VERIFICATION_TYPE = {
  CERTIFICATE_OF_OWNERSHIP: 'Giấy chứng nhận quyền sở hữu',
  POWER_OF_ATTORNEY: 'Giấy ủy quyền',
  PROJECT_PURCHASE_CONTRACT: 'Hợp đồng mua bán dự án',
} as const;

export default function VerificationDeskPage() {
  const [kycItems, setKycItems] = useState<UserKycProfile[]>([]);
  const [listingItems, setListingItems] = useState<ListingVerification[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');
  const [reason, setReason] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [kyc, listings] = await Promise.all([fetchKycQueue(), fetchVerificationQueue()]);
      setKycItems(kyc); setListingItems(listings);
    } catch { setError('Không thể tải hàng đợi thẩm định. Vui lòng thử lại.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const act = async (id: string, operation: () => Promise<unknown>) => {
    setBusyId(id); setError('');
    try { await operation(); await load(); }
    catch { setError('Không thể lưu kết quả thẩm định. Kiểm tra dữ liệu và thử lại.'); }
    finally { setBusyId(''); }
  };

  return <main className="mx-auto max-w-6xl px-4 py-8 md:px-8">
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div><div className="flex items-center gap-3"><ShieldCheck className="h-8 w-8 text-emerald-700" /><h1 className="text-3xl font-extrabold">Bàn thẩm định thủ công</h1></div><p className="mt-2 text-slate-600">Đối chiếu hồ sơ eKYC trước, sau đó mới duyệt giấy tờ xác minh tin đăng.</p></div>
      <button type="button" onClick={() => void load()} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border px-4 font-semibold"><RefreshCw className="h-4 w-4" />Tải lại</button>
    </header>
    {error && <p className="mt-6 rounded-xl bg-rose-50 p-4 text-rose-900" role="alert">{error}</p>}
    {loading ? <p className="mt-8" role="status">Đang tải hồ sơ…</p> : <>
      <section className="mt-8" aria-labelledby="kyc-heading">
        <h2 id="kyc-heading" className="text-2xl font-bold">Hồ sơ eKYC <span className="text-base font-normal text-slate-500">({kycItems.length})</span></h2>
        {kycItems.length === 0 ? <p className="mt-4 rounded-2xl border bg-white p-8 text-center text-slate-500">Không có hồ sơ eKYC.</p> : <div className="mt-4 space-y-4">{kycItems.map((item) => <article key={item.id} className="rounded-2xl border bg-white p-5">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="text-lg font-bold">{item.fullName}</h3><p className="text-sm text-slate-600">CCCD {item.maskedIdNumber} · Sinh ngày {item.dob || 'chưa cung cấp'}</p></div><span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold">{KYC_STATUS[item.status]}</span></div>
          <p className="mt-3 text-sm"><span className="text-slate-500">Địa chỉ:</span> {item.address || 'Chưa cung cấp'}</p>
          <div className="mt-4 grid gap-3 sm:grid-cols-3"><PrivateMediaImage src={item.idCardFrontUrl} alt={`Mặt trước CCCD của ${item.fullName}`} /><PrivateMediaImage src={item.idCardBackUrl} alt={`Mặt sau CCCD của ${item.fullName}`} /><PrivateMediaImage src={item.selfieUrl} alt={`Ảnh chân dung của ${item.fullName}`} /></div>
          {item.status === 'PENDING' && <div className="mt-4 grid gap-2 sm:grid-cols-[auto_1fr_auto]"><button disabled={busyId === item.id} onClick={() => void act(item.id, () => approveKyc(item.id))} className="min-h-11 rounded-xl bg-emerald-700 px-4 font-bold text-white disabled:opacity-50">Xác nhận khớp</button><input aria-label={`Lý do từ chối hồ sơ của ${item.fullName}`} value={reason[item.id] || ''} onChange={(event) => setReason({ ...reason, [item.id]: event.target.value })} placeholder="Lý do từ chối cụ thể" className="min-h-11 rounded-xl border px-3" /><button disabled={busyId === item.id || !reason[item.id]?.trim()} onClick={() => void act(item.id, () => rejectKyc(item.id, reason[item.id]))} className="min-h-11 rounded-xl bg-rose-700 px-4 font-bold text-white disabled:opacity-50">Từ chối</button></div>}
        </article>)}</div>}
      </section>

      <section className="mt-10" aria-labelledby="listing-verification-heading">
        <h2 id="listing-verification-heading" className="text-2xl font-bold">Xác minh tin đăng <span className="text-base font-normal text-slate-500">({listingItems.length})</span></h2>
        {listingItems.length === 0 ? <p className="mt-4 rounded-2xl border bg-white p-8 text-center text-slate-500">Không có hồ sơ xác minh tin đăng.</p> : <div className="mt-4 space-y-4">{listingItems.map((item) => <article key={item.id} className="rounded-2xl border bg-white p-5">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-bold">{item.listingTitle || `Tin ${item.listingId.slice(0, 8)}`}</h3><p className="mt-1 text-sm text-slate-600">{VERIFICATION_TYPE[item.verificationType]} · {item.ownerNameOnDoc || 'Chưa có tên người đứng giấy'}</p></div><span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold">{item.status === 'PENDING' ? 'Chờ duyệt' : item.status === 'VERIFIED_OWNER' ? 'Đã xác minh chính chủ' : item.status === 'REJECTED' ? 'Đã từ chối' : 'Đã thu hồi'}</span></div>
          <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Số giấy tờ</dt><dd>{item.certificateNumber || 'Chưa cung cấp'}</dd></div><div><dt className="text-slate-500">Ngày gửi</dt><dd>{new Date(item.createdAt).toLocaleString('vi-VN')}</dd></div></dl>
          {item.status === 'PENDING' && <div className="mt-4 grid gap-2 sm:grid-cols-[auto_1fr_auto]"><button disabled={busyId === item.id} onClick={() => void act(item.id, () => approveVerification(item.id))} className="min-h-11 rounded-xl bg-emerald-700 px-4 font-bold text-white disabled:opacity-50"><span className="inline-flex items-center gap-2"><CheckCircle2 className="h-4 w-4" />Xác nhận đạt</span></button><input aria-label={`Lý do từ chối xác minh tin ${item.listingId}`} value={reason[item.id] || ''} onChange={(event) => setReason({ ...reason, [item.id]: event.target.value })} placeholder="Lý do từ chối cụ thể" className="min-h-11 rounded-xl border px-3" /><button disabled={busyId === item.id || !reason[item.id]?.trim()} onClick={() => void act(item.id, () => rejectVerification(item.id, reason[item.id]))} className="min-h-11 rounded-xl bg-rose-700 px-4 font-bold text-white disabled:opacity-50">Từ chối</button></div>}
        </article>)}</div>}
      </section>
    </>}
  </main>;
}
