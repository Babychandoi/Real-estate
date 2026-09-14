import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, Send } from 'lucide-react';
import { apiClient } from '@/shared/api/client';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setSending(true);
    try { await apiClient('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) }); setSent(true); }
    finally { setSending(false); }
  };
  return <main className="mx-auto max-w-md px-4 py-12"><section className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8">
    <Mail className="h-8 w-8 text-primary" /><h1 className="mt-4 text-2xl font-bold text-on-surface">Quên mật khẩu</h1>
    {sent ? <div className="mt-3 text-sm leading-relaxed text-on-surface-variant"><p>Nếu email này thuộc một tài khoản đang hoạt động, liên kết đặt lại mật khẩu đã được gửi.</p><p className="mt-2">Liên kết có hiệu lực 30 phút và chỉ dùng một lần.</p><Link to="/" className="mt-5 inline-block font-semibold text-primary hover:underline">Quay lại trang chủ</Link></div> : <form onSubmit={submit} className="mt-5 space-y-4"><p className="text-sm text-on-surface-variant">Nhập email đăng ký. Chúng tôi sẽ gửi liên kết đặt mật khẩu mới.</p><label className="grid gap-2 text-sm font-semibold text-on-surface">Email<span className="relative"><Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input required autoFocus type="email" value={email} onChange={(event) => setEmail(event.target.value)} className="min-h-11 w-full rounded-lg border border-outline-variant bg-white px-10 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15" /></span></label><button disabled={sending} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-primary px-5 text-sm font-bold text-white disabled:opacity-60"><Send className="h-4 w-4" />{sending ? 'Đang gửi…' : 'Gửi liên kết đặt lại'}</button></form>}
  </section></main>;
}

export default ForgotPasswordPage;
