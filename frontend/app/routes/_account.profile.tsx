import { useEffect, useMemo, useState } from 'react';
import { Camera, CheckCircle2, Mail, Phone, Save, ShieldCheck, UserRound } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';

type UploadedImage = { url: string };

export function AccountProfilePage() {
  const { user, refreshUser } = useAuth();
  const [name, setName] = useState(user?.name ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [avatarMediaUrl, setAvatarMediaUrl] = useState(user?.avatarMediaUrl ?? '');
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => { setName(user?.name ?? ''); setPhone(user?.phone ?? ''); setAvatarMediaUrl(user?.avatarMediaUrl ?? ''); }, [user]);
  const initials = useMemo(() => name.trim().split(/\s+/).filter(Boolean).slice(-2).map((part) => part[0]).join('').toLocaleUpperCase('vi-VN'), [name]);

  const uploadAvatar = async (file?: File) => {
    if (!file) return;
    setMessage(''); setUploading(true);
    try {
      const body = new FormData(); body.append('file', file);
      const uploaded = await apiClient<UploadedImage>('/media/images', { method: 'POST', body });
      setAvatarMediaUrl(uploaded.url);
    } catch { setMessage('Không thể tải ảnh. Chỉ nhận JPEG, PNG, WebP hoặc AVIF, tối đa 10 MB.'); }
    finally { setUploading(false); }
  };

  const save = async (event: React.FormEvent) => {
    event.preventDefault(); setMessage(''); setSaving(true);
    try {
      await apiClient('/auth/me', { method: 'PUT', body: JSON.stringify({ name: name.trim(), phone: phone.trim(), avatarMediaUrl: avatarMediaUrl || null }) });
      await refreshUser(); setMessage('Đã lưu thông tin cá nhân.');
    } catch { setMessage('Không thể lưu hồ sơ. Kiểm tra họ tên và số điện thoại rồi thử lại.'); }
    finally { setSaving(false); }
  };

  return <main className="mx-auto max-w-3xl px-4 py-8 md:px-8">
    <header className="border-b border-outline-variant/40 pb-6">
      <h1 className="text-2xl font-bold text-on-surface">Thông tin cá nhân</h1>
      <p className="mt-2 text-sm text-on-surface-variant">Quản lý tên hiển thị, ảnh đại diện và số điện thoại liên hệ của bạn.</p>
    </header>
    <form onSubmit={save} className="mt-6 space-y-6 rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-5 md:p-7">
      <section className="flex items-center gap-5 border-b border-outline-variant/40 pb-6">
        <label className="group relative grid h-20 w-20 shrink-0 cursor-pointer place-items-center overflow-hidden rounded-full bg-primary/10 text-lg font-bold text-primary focus-within:ring-2 focus-within:ring-primary focus-within:ring-offset-2">
          {avatarMediaUrl ? <img src={avatarMediaUrl} alt="Ảnh đại diện" className="h-full w-full object-cover" /> : initials || <UserRound className="h-7 w-7" />}
          <span className="absolute inset-0 grid place-items-center bg-slate-950/45 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"><Camera className="h-5 w-5" /></span>
          <span className="absolute bottom-0.5 right-0.5 grid h-7 w-7 place-items-center rounded-full bg-primary text-white shadow-sm"><Camera className="h-3.5 w-3.5" /></span>
          <input aria-label="Đổi ảnh đại diện" className="sr-only" type="file" accept="image/jpeg,image/png,image/webp,image/avif" disabled={uploading} onChange={(event) => void uploadAvatar(event.target.files?.[0])} />
        </label>
        <div><p className="text-sm font-semibold text-on-surface">{uploading ? 'Đang tải ảnh…' : 'Chạm vào ảnh để thay đổi'}</p><p className="mt-1 text-xs text-on-surface-variant">Ảnh này được hiển thị công khai cùng tin đăng của bạn.</p></div>
      </section>
      <label className="grid gap-2 text-sm font-semibold text-on-surface">Họ và tên<input required minLength={2} maxLength={150} value={name} onChange={(event) => setName(event.target.value)} className="min-h-11 rounded-lg border border-outline-variant bg-white px-3 font-normal focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15" /></label>
      <label className="grid gap-2 text-sm font-semibold text-on-surface">Email đăng nhập<span className="relative"><Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input value={user?.email ?? ''} readOnly className="min-h-11 w-full rounded-lg border border-outline-variant bg-surface-container px-10 text-sm font-normal text-on-surface-variant" /></span><span className="text-xs font-normal text-on-surface-variant">Email là định danh đăng nhập nên không thể thay đổi.</span></label>
      <label className="grid gap-2 text-sm font-semibold text-on-surface">Số điện thoại liên hệ<span className="relative"><Phone className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input required inputMode="tel" pattern="^(0|\\+84)[35789][0-9]{8}$" placeholder="Ví dụ: 0912345678" value={phone} onChange={(event) => setPhone(event.target.value.replace(/[\s-]/g, ''))} className="min-h-11 w-full rounded-lg border border-outline-variant bg-white px-10 text-sm font-normal focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15" /></span><span className="text-xs font-normal text-on-surface-variant">Số điện thoại được mã hóa; chỉ dùng cho liên hệ khi quy trình eKYC cho phép.</span></label>
      <div className="flex items-start gap-3 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-900"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0" /><span><strong>Tài khoản đã xác minh email.</strong> Thông tin eKYC và giấy tờ định danh được quản lý tại mục xác minh riêng.</span></div>
      {message && <p role="status" className={`flex items-center gap-2 text-sm ${message.startsWith('Đã') ? 'text-emerald-800' : 'text-rose-700'}`}>{message.startsWith('Đã') && <CheckCircle2 className="h-4 w-4" />}{message}</p>}
      <button disabled={saving || uploading} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-primary px-5 text-sm font-bold text-white hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"><Save className="h-4 w-4" />{saving ? 'Đang lưu…' : 'Lưu thay đổi'}</button>
    </form>
  </main>;
}

export default AccountProfilePage;
