import { useEffect, useRef, useState } from 'react';
import { CheckCircle2, Clock3, Loader2, LogIn, MailWarning } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';
import { problemCode, problemStatus } from '@/shared/auth/securityApi';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { ResendVerificationForm } from '@/features/account-security/ResendVerificationForm';
import { useTokenFromUrl } from '@/features/account-security/useTokenFromUrl';

type State =
  | { kind: 'loading' }
  | { kind: 'verified'; already: boolean; returnTo: string | null }
  | { kind: 'expired' | 'superseded' | 'used' | 'invalid' | 'network' };

/** Only a same-site path (the server already filters it; checked again before navigating). */
function safeReturnTo(value: string | null | undefined): string | null {
  return value && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\') ? value : null;
}

const FAILURES: Record<Exclude<State['kind'], 'loading' | 'verified'>, { title: string; body: string }> = {
  expired: {
    title: 'Liên kết xác minh đã hết hạn',
    body: 'Liên kết chỉ có hiệu lực 24 giờ. Nhập email để nhận liên kết mới.',
  },
  superseded: {
    title: 'Liên kết này đã được thay bằng liên kết mới hơn',
    body: 'Hãy mở email xác minh gần nhất, hoặc yêu cầu một liên kết mới bên dưới.',
  },
  used: {
    title: 'Liên kết đã được sử dụng',
    body: 'Nếu bạn không đăng nhập được, hãy dùng "Quên mật khẩu" hoặc liên hệ hỗ trợ.',
  },
  invalid: {
    title: 'Liên kết xác minh không hợp lệ',
    body: 'Liên kết có thể bị cắt khi sao chép. Hãy mở lại từ email, hoặc yêu cầu liên kết mới.',
  },
  network: {
    title: 'Chưa kết nối được máy chủ',
    body: 'Kiểm tra mạng rồi thử lại. Liên kết vẫn còn dùng được nếu chưa hết hạn.',
  },
};

export function VerifyEmailPage() {
  const token = useTokenFromUrl();
  const navigate = useNavigate();
  const { isAuthenticated, setIsLoginModalOpen } = useAuth();
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const started = useRef(-1);

  useEffect(() => {
    // One request per attempt even when StrictMode runs effects twice (a second call would report "already used").
    if (started.current === attempt) return;
    started.current = attempt;
    if (!token) {
      setState({ kind: 'invalid' });
      return;
    }
    setState({ kind: 'loading' });
    apiClient<{ status: 'VERIFIED' | 'ALREADY_VERIFIED'; returnTo: string | null }>('/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ token }),
    })
      .then((result) =>
        setState({
          kind: 'verified',
          already: result.status === 'ALREADY_VERIFIED',
          returnTo: safeReturnTo(result.returnTo),
        }),
      )
      .catch((error) => {
        const code = problemCode(error);
        if (code === 'TOKEN_EXPIRED') setState({ kind: 'expired' });
        else if (code === 'TOKEN_SUPERSEDED') setState({ kind: 'superseded' });
        else if (code === 'TOKEN_USED') setState({ kind: 'used' });
        else if (code === 'TOKEN_INVALID' || problemStatus(error) === 400) setState({ kind: 'invalid' });
        else setState({ kind: 'network' });
      });
  }, [token, attempt]);

  // After signing in from this page, continue where the person registered (DS-11).
  const returnTo = state.kind === 'verified' ? state.returnTo : null;
  useEffect(() => {
    if (state.kind === 'verified' && isAuthenticated) navigate(returnTo ?? '/', { replace: true });
  }, [state.kind, isAuthenticated, returnTo, navigate]);

  return (
    <div className="mx-auto flex min-h-[65vh] max-w-xl items-center px-4 py-12" data-ready={state.kind !== 'loading'}>
      <section
        className="w-full rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 text-center shadow-sm md:p-8"
        aria-live="polite"
      >
        {state.kind === 'loading' && (
          <>
            <Loader2 className="mx-auto h-10 w-10 animate-spin text-primary" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">Đang xác minh email</h1>
          </>
        )}
        {state.kind === 'verified' && (
          <>
            <CheckCircle2 className="mx-auto h-12 w-12 text-success" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">
              {state.already ? 'Email đã được xác minh trước đó' : 'Email đã được xác minh'}
            </h1>
            <p className="mt-2 text-on-surface-variant">
              {state.returnTo
                ? 'Tài khoản đã sẵn sàng. Đăng nhập để quay lại trang bạn đang xem.'
                : 'Tài khoản đã sẵn sàng. Bạn có thể đăng nhập ngay.'}
            </p>
            <Button className="mt-6" leftIcon={<LogIn className="h-4 w-4" />} onClick={() => setIsLoginModalOpen(true)}>
              {state.returnTo ? 'Đăng nhập để tiếp tục' : 'Đăng nhập'}
            </Button>
          </>
        )}
        {state.kind !== 'loading' && state.kind !== 'verified' && (
          <>
            {state.kind === 'expired' ? (
              <Clock3 className="mx-auto h-12 w-12 text-warning" aria-hidden="true" />
            ) : (
              <MailWarning className="mx-auto h-12 w-12 text-warning" aria-hidden="true" />
            )}
            <h1 className="mt-4 text-2xl font-bold text-on-surface">{FAILURES[state.kind].title}</h1>
            <p className="mt-2 text-on-surface-variant">{FAILURES[state.kind].body}</p>
            {state.kind === 'network' ? (
              <Button className="mt-6" onClick={() => setAttempt((value) => value + 1)}>
                Thử lại
              </Button>
            ) : state.kind === 'used' ? (
              <div className="mt-6 flex flex-wrap justify-center gap-3">
                <Button leftIcon={<LogIn className="h-4 w-4" />} onClick={() => setIsLoginModalOpen(true)}>
                  Đăng nhập
                </Button>
                <ButtonLink to="/forgot-password" variant="outline">
                  Quên mật khẩu
                </ButtonLink>
              </div>
            ) : (
              <div className="mx-auto mt-6 max-w-sm">
                <ResendVerificationForm />
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
export default VerifyEmailPage;
