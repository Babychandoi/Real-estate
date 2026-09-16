import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, LockKeyhole, Mail, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/shared/auth/AuthContext';
import { Button } from '@/shared/ui/Button';

export const AdminLoginPage: React.FC = () => {
  const { adminLogin } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = await adminLogin(email, password);
      if (result.success) navigate('/2026/nhadatchuan/admin/moderation', { replace: true });
      else setError(result.error ?? 'Không thể đăng nhập cổng quản trị.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="mx-auto flex min-h-[calc(100dvh-16rem)] w-full max-w-md items-center px-4 py-10">
      <form onSubmit={submit} className="w-full rounded-2xl border border-outline-variant/60 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex items-start gap-3">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-primary text-white"><ShieldCheck className="h-5 w-5" /></div>
          <div>
            <h1 className="text-xl font-bold text-on-surface">Đăng nhập quản trị</h1>
            <p className="mt-1 text-sm text-on-surface-variant">Cổng nội bộ Nhà Đất Chuẩn</p>
          </div>
        </div>

        {error && <p role="alert" className="mt-5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2.5 text-sm text-rose-800">{error}</p>}

        <div className="mt-6 space-y-4">
          <label className="block text-sm font-semibold text-on-surface">Email
            <span className="relative mt-1.5 block"><Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" required className="min-h-11 w-full rounded-lg border border-outline-variant bg-white py-2 pl-10 pr-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" placeholder="admin@nhadatchuan.online" /></span>
          </label>
          <label className="block text-sm font-semibold text-on-surface">Mật khẩu
            <span className="relative mt-1.5 block"><LockKeyhole className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" /><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required className="min-h-11 w-full rounded-lg border border-outline-variant bg-white py-2 pl-10 pr-11 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" placeholder="Nhập mật khẩu" /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} className="absolute right-1 top-0 grid h-11 w-11 place-items-center text-on-surface-variant hover:text-on-surface">{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></span>
          </label>
        </div>

        <Button type="submit" variant="primary" className="mt-6 w-full" disabled={loading}>{loading ? 'Đang xác thực…' : 'Vào trang quản trị'}</Button>
        <div className="mt-5 flex items-center justify-between text-sm"><Link to="/forgot-password" className="font-semibold text-primary hover:underline">Quên mật khẩu?</Link><Link to="/" className="text-on-surface-variant hover:text-on-surface hover:underline">Về trang chính</Link></div>
      </form>
    </section>
  );
};
