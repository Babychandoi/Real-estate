import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, MailWarning } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { apiClient } from '@/shared/api/client';
import { Button } from '@/shared/ui/Button';

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const [state, setState] = useState<'loading'|'success'|'error'>('loading');
  useEffect(() => {
    const token = params.get('token');
    if (!token) { setState('error'); return; }
    apiClient<void>(`/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(() => setState('success')).catch(() => setState('error'));
  }, [params]);
  return <main className="mx-auto flex min-h-[65vh] max-w-xl items-center px-4 py-12"><section className="w-full rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm" aria-live="polite">
    {state === 'loading' && <><Loader2 className="mx-auto h-10 w-10 animate-spin text-primary"/><h1 className="mt-4 text-2xl font-bold">Đang xác minh email</h1></>}
    {state === 'success' && <><CheckCircle2 className="mx-auto h-12 w-12 text-emerald-600"/><h1 className="mt-4 text-2xl font-bold">Email đã được xác minh</h1><p className="mt-2 text-slate-600">Tài khoản đã được kích hoạt. Bạn có thể đăng nhập ngay.</p><Link to="/" className="mt-6 inline-block"><Button>Về trang chủ để đăng nhập</Button></Link></>}
    {state === 'error' && <><MailWarning className="mx-auto h-12 w-12 text-amber-600"/><h1 className="mt-4 text-2xl font-bold">Không thể xác minh email</h1><p className="mt-2 text-slate-600">Liên kết không hợp lệ, đã được dùng hoặc đã hết hạn.</p><Link to="/" className="mt-6 inline-block"><Button>Về trang chủ</Button></Link></>}
  </section></main>;
}
export default VerifyEmailPage;
