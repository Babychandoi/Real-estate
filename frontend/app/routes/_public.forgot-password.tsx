import { useState, type FormEvent } from 'react';
import { CheckCircle2, CircleAlert, LogIn, Mail, RotateCcw, Send } from 'lucide-react';
import { Link } from 'react-router-dom';
import { apiClient } from '@/shared/api/client';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSending(true); setError('');
    try {
      await apiClient<void>('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
      setSent(true);
    } catch {
      setError('Chưa thể gửi yêu cầu lúc này. Vui lòng kiểm tra kết nối và thử lại.');
    } finally { setSending(false); }
  };

  if (sent) return <main className="mx-auto max-w-md px-4 py-12"><section className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8">
    <div className="grid h-12 w-12 place-items-center rounded-full bg-emerald-50 text-emerald-700"><CheckCircle2 className="h-7 w-7" /></div>
    <h1 className="mt-5 text-2xl font-bold text-on-surface">Đã tiếp nhận yêu cầu</h1>
    <p className="mt-3 text-sm leading-6 text-on-surface-variant">Nếu email này thuộc một tài khoản đang hoạt động, chúng tôi đã gửi liên kết đặt lại mật khẩu. Liên kết chỉ dùng một lần và hết hạn sau 30 phút.</p>
    <div className="mt-5 rounded-xl bg-slate-50 p-4 text-sm leading-6 text-on-surface-variant"><strong className="text-on-surface">Chưa thấy email?</strong><br />Kiểm tra thư mục Spam hoặc chờ vài phút trước khi gửi lại.</div>
    <div className="mt-6 flex flex-wrap gap-3"><button type="button" onClick={() => { setSent(false); setError(''); }} className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-outline-variant bg-white px-4 text-sm font-bold text-on-surface hover:bg-slate-50"><RotateCcw className="h-4 w-4" />Gửi lại</button><Link to="/" className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-primary px-4 text-sm font-bold text-white"><LogIn className="h-4 w-4" />Quay lại đăng nhập</Link></div>
  </section></main>;

  return <main className="mx-auto max-w-md px-4 py-12"><section className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8">
    <Mail className="h-8 w-8 text-primary" /><h1 className="mt-4 text-2xl font-bold text-on-surface">Quên mật khẩu</h1>
    <p className="mt-3 text-sm leading-6 text-on-surface-variant">Nhập email đăng ký. Chúng tôi sẽ gửi liên kết để bạn đặt mật khẩu mới.</p>
    <form onSubmit={submit} className="mt-5 space-y-4"><label className="grid gap-2 text-sm font-semibold text-on-surface">Email<span className="relative"><Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input required autoFocus type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} className="min-h-11 w-full rounded-lg border border-outline-variant bg-white px-10 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15" /></span></label>{error && <p role="alert" className="flex items-start gap-2 text-sm leading-5 text-rose-700"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />{error}</p>}<button disabled={sending} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-primary px-5 text-sm font-bold text-white disabled:opacity-60"><Send className="h-4 w-4" />{sending ? 'Đang gửi liên kết…' : 'Gửi liên kết đặt lại'}</button></form>
  </section></main>;
}

export default ForgotPasswordPage;
