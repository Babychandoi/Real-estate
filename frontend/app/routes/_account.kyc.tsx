import { useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2, Eye, FileImage, LockKeyhole, ShieldCheck, Upload } from 'lucide-react';
import type { UserKycProfile } from '@/entities/verification/model/types';
import { apiClient, apiFetch } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';

type DocumentField = 'idCardFrontUrl' | 'idCardBackUrl' | 'selfieUrl';
type UploadedImage = { url: string };
type DocumentAccess = { token: string; expiresAt: string };
type KycDocuments = Record<DocumentField, string>;
type PreviewUrls = Partial<Record<DocumentField, string>>;

const DOCUMENTS: Array<{ field: DocumentField; label: string; help: string }> = [
  { field: 'idCardFrontUrl', label: 'Mặt trước CCCD', help: 'Ảnh rõ bốn góc, không lóa sáng.' },
  { field: 'idCardBackUrl', label: 'Mặt sau CCCD', help: 'Ảnh rõ mã QR và ngày cấp.' },
  { field: 'selfieUrl', label: 'Ảnh chân dung', help: 'Chụp chính diện, đủ sáng, không dùng ảnh giấy tờ.' },
];
const STATUS_TEXT = { PENDING: 'Đang chờ duyệt thủ công', VERIFIED: 'Đã xác minh', REJECTED: 'Cần gửi lại hồ sơ' } as const;

export function KycPage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<UserKycProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState<DocumentField | ''>('');
  const [error, setError] = useState('');
  const [viewPassword, setViewPassword] = useState('');
  const [unlocking, setUnlocking] = useState(false);
  const [access, setAccess] = useState<DocumentAccess | null>(null);
  const [documents, setDocuments] = useState<KycDocuments | null>(null);
  const [previews, setPreviews] = useState<PreviewUrls>({});
  const [form, setForm] = useState({ idNumber: '', fullName: user?.name || '', dob: '', address: '', idCardFrontUrl: '', idCardBackUrl: '', selfieUrl: '' });

  useEffect(() => {
    if (!user) return;
    apiClient<UserKycProfile>(`/kyc/user/${user.id}`).then(setProfile).catch((reason: unknown) => {
      if (!(reason && typeof reason === 'object' && 'problem' in reason && (reason as { problem: { status?: number } }).problem.status === 404)) setError('Không thể kiểm tra trạng thái eKYC. Vui lòng tải lại trang.');
    }).finally(() => setLoading(false));
  }, [user]);

  useEffect(() => {
    if (!access || !documents) return;
    let cancelled = false;
    const objectUrls: string[] = [];
    void Promise.all(DOCUMENTS.map(async ({ field }) => {
      const response = await apiFetch(documents[field], { headers: { 'X-Kyc-Document-Access': access.token, Accept: 'image/*' } });
      if (!response.ok) throw new Error('Không thể tải ảnh định danh.');
      const objectUrl = URL.createObjectURL(await response.blob()); objectUrls.push(objectUrl);
      return [field, objectUrl] as const;
    })).then((items) => { if (!cancelled) setPreviews(Object.fromEntries(items)); }).catch(() => { if (!cancelled) setError('Phiên xem ảnh đã hết hạn hoặc ảnh không còn khả dụng. Hãy xác nhận lại mật khẩu.'); });
    return () => { cancelled = true; objectUrls.forEach(URL.revokeObjectURL); };
  }, [access, documents]);

  const upload = async (field: DocumentField, file?: File) => {
    if (!file) return;
    setUploading(field); setError('');
    try { const body = new FormData(); body.append('file', file); const result = await apiClient<UploadedImage>('/media/kyc', { method: 'POST', body }); setForm((current) => ({ ...current, [field]: result.url })); }
    catch { setError('Không thể tải ảnh lên. Chỉ dùng JPEG, PNG, WebP hoặc AVIF tối đa 10 MB.'); }
    finally { setUploading(''); }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError('');
    if (!DOCUMENTS.every(({ field }) => form[field])) { setError('Vui lòng tải đủ ba ảnh bắt buộc.'); return; }
    setSubmitting(true);
    try { setProfile(await apiClient<UserKycProfile>('/kyc/submit', { method: 'POST', body: JSON.stringify(form) })); }
    catch { setError('Không thể gửi hồ sơ eKYC. Kiểm tra thông tin và thử lại.'); }
    finally { setSubmitting(false); }
  };

  const unlockDocuments = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setUnlocking(true);
    try {
      const grant = await apiClient<DocumentAccess>('/kyc/documents/access', { method: 'POST', body: JSON.stringify({ password: viewPassword }) });
      if (!user) return;
      setAccess(grant); setViewPassword('');
      setDocuments(await apiClient<KycDocuments>(`/kyc/user/${user.id}/documents`, { headers: { 'X-Kyc-Document-Access': grant.token } }));
    } catch { setError('Mật khẩu xác nhận không đúng hoặc không thể mở ảnh. Vui lòng thử lại.'); }
    finally { setUnlocking(false); }
  };

  if (loading) return <main className="mx-auto max-w-5xl px-4 py-10" role="status">Đang kiểm tra hồ sơ eKYC…</main>;
  if (profile && profile.status !== 'REJECTED') return <main className="mx-auto max-w-3xl px-4 py-10">
    <section className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8"><ShieldCheck className="h-10 w-10 text-emerald-700" /><h1 className="mt-4 text-3xl font-extrabold text-on-surface">{STATUS_TEXT[profile.status]}</h1><p className="mt-2 text-on-surface-variant">Hồ sơ của {profile.fullName} · CCCD {profile.maskedIdNumber}</p>{profile.status === 'PENDING' && <p className="mt-5 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Nhân sự kiểm duyệt sẽ đối chiếu ba ảnh bạn đã gửi. Bạn sẽ nhận thông báo ngay khi có kết quả.</p>}</section>
    <section className="mt-6 rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8"><div className="flex items-start gap-3"><LockKeyhole className="mt-0.5 h-5 w-5 shrink-0 text-primary" /><div><h2 className="font-bold text-on-surface">Xem ảnh định danh của tôi</h2><p className="mt-1 text-sm leading-6 text-on-surface-variant">Để bảo vệ CCCD và ảnh khuôn mặt, hãy nhập lại mật khẩu. Quyền xem chỉ có hiệu lực 10 phút và ảnh không được lưu trong bộ nhớ đệm.</p></div></div>
      {!documents ? <form onSubmit={unlockDocuments} className="mt-5 flex max-w-md flex-col gap-3 sm:flex-row"><label className="sr-only" htmlFor="kyc-view-password">Mật khẩu hiện tại</label><input id="kyc-view-password" required autoComplete="current-password" type="password" value={viewPassword} onChange={(event) => setViewPassword(event.target.value)} placeholder="Nhập mật khẩu hiện tại" className="min-h-11 flex-1 rounded-lg border border-outline-variant bg-white px-3 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15" /><button disabled={unlocking} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-bold text-white disabled:opacity-60"><Eye className="h-4 w-4" />{unlocking ? 'Đang xác nhận…' : 'Mở ảnh'}</button></form> : <><p className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-emerald-800"><CheckCircle2 className="h-4 w-4" />Ảnh chỉ hiển thị trên thiết bị này đến {new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(new Date(access!.expiresAt))}.</p><div className="mt-5 grid gap-4 md:grid-cols-3">{DOCUMENTS.map(({ field, label }) => <figure key={field} className="overflow-hidden rounded-xl border border-outline-variant/50 bg-white"><div className="aspect-[4/3] bg-slate-100">{previews[field] ? <img src={previews[field]} alt={label} className="h-full w-full object-contain" /> : <div className="grid h-full place-items-center text-sm text-on-surface-variant">Đang tải ảnh…</div>}</div><figcaption className="border-t border-outline-variant/40 px-4 py-3 text-sm font-semibold text-on-surface">{label}</figcaption></figure>)}</div></>}
      {error && <p role="alert" className="mt-4 text-sm text-rose-700">{error}</p>}</section>
  </main>;

  return <main className="mx-auto max-w-5xl px-4 py-8 md:px-8"><header><div className="flex items-center gap-3"><ShieldCheck className="h-8 w-8 text-emerald-700" /><h1 className="text-3xl font-extrabold">Xác minh danh tính eKYC</h1></div><p className="mt-3 max-w-3xl text-on-surface-variant">Gửi mặt trước, mặt sau CCCD và ảnh chân dung. Hồ sơ được duyệt thủ công và chỉ nhân sự có thẩm quyền mới xem được.</p></header>{profile?.status === 'REJECTED' && <p className="mt-6 rounded-xl bg-rose-50 p-4 text-rose-900"><strong>Hồ sơ cần gửi lại:</strong> {profile.rejectionReason || 'Ảnh hoặc thông tin chưa đủ rõ để đối chiếu.'}</p>}{error && <p role="alert" className="mt-6 rounded-xl bg-rose-50 p-4 text-rose-900">{error}</p>}<form onSubmit={submit} className="mt-6 space-y-6"><section className="grid gap-4 rounded-2xl border bg-white p-5 sm:grid-cols-2"><label className="grid gap-2 text-sm font-semibold">Họ và tên trên CCCD<input required maxLength={150} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} className="min-h-11 rounded-xl border px-3 font-normal" /></label><label className="grid gap-2 text-sm font-semibold">Số CCCD<input required inputMode="numeric" pattern="[0-9]{9,12}" value={form.idNumber} onChange={(e) => setForm({ ...form, idNumber: e.target.value.replace(/\D/g, '') })} className="min-h-11 rounded-xl border px-3 font-normal" /></label><label className="grid gap-2 text-sm font-semibold">Ngày sinh<input required type="date" value={form.dob} onChange={(e) => setForm({ ...form, dob: e.target.value })} className="min-h-11 rounded-xl border px-3 font-normal" /></label><label className="grid gap-2 text-sm font-semibold">Địa chỉ thường trú<input required maxLength={300} value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} className="min-h-11 rounded-xl border px-3 font-normal" /></label></section><section className="grid gap-4 md:grid-cols-3">{DOCUMENTS.map(({ field, label, help }) => <label key={field} className="flex min-h-48 cursor-pointer flex-col justify-between rounded-2xl border bg-white p-5 focus-within:ring-2 focus-within:ring-primary"><span><span className="flex items-center gap-2 font-bold"><FileImage className="h-5 w-5" />{label}</span><span className="mt-2 block text-sm text-on-surface-variant">{help}</span></span><span className={`mt-5 inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-3 font-semibold ${form[field] ? 'bg-emerald-50 text-emerald-800' : 'bg-slate-100 text-primary'}`}>{form[field] ? <><CheckCircle2 className="h-4 w-4" />Đã tải lên</> : <><Upload className="h-4 w-4" />{uploading === field ? 'Đang tải…' : 'Chọn ảnh'}</>}</span><input className="sr-only" type="file" accept="image/jpeg,image/png,image/webp,image/avif" disabled={!!uploading || submitting} onChange={(e) => void upload(field, e.target.files?.[0])} /></label>)}</section><button type="submit" disabled={submitting || !!uploading} className="min-h-12 w-full rounded-xl bg-primary px-5 font-bold text-white disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto">{submitting ? 'Đang gửi hồ sơ…' : 'Gửi hồ sơ để duyệt'}</button></form></main>;
}

export default KycPage;
