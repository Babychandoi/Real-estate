import { useEffect, useRef, useState, type FormEvent } from 'react';
import { CheckCircle2, Clock3, Loader2, Lock, LogIn, MailWarning } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';
import { problemCode } from '@/shared/auth/securityApi';
import { validationMessage } from '@/shared/types/problem-details';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { TextInput } from '@/shared/ui/TextInput';
import { useTokenFromUrl } from '@/features/account-security/useTokenFromUrl';

type LinkState = 'checking' | 'VALID' | 'EXPIRED' | 'USED' | 'SUPERSEDED' | 'INVALID' | 'NETWORK' | 'DONE';

const BROKEN_LINK: Record<'EXPIRED' | 'USED' | 'SUPERSEDED' | 'INVALID', { title: string; body: string }> = {
  EXPIRED: {
    title: 'Liên kết đặt lại mật khẩu đã hết hạn',
    body: 'Liên kết chỉ có hiệu lực 30 phút. Hãy yêu cầu một liên kết mới.',
  },
  USED: {
    title: 'Liên kết này đã được dùng',
    body: 'Mật khẩu đã được đổi bằng liên kết này. Nếu không phải bạn, hãy yêu cầu liên kết mới ngay.',
  },
  SUPERSEDED: {
    title: 'Đã có liên kết mới hơn',
    body: 'Bạn đã yêu cầu đặt lại nhiều lần; chỉ liên kết trong email gần nhất còn dùng được.',
  },
  INVALID: {
    title: 'Liên kết không hợp lệ',
    body: 'Liên kết có thể bị cắt khi sao chép. Hãy mở lại từ email hoặc yêu cầu liên kết mới.',
  },
};

/** UI-14: the link state is checked before the form (without consuming it); the token never stays in the URL. */
export function ResetPasswordPage() {
  const token = useTokenFromUrl();
  const { setIsLoginModalOpen } = useAuth();
  const [linkState, setLinkState] = useState<LinkState>('checking');
  const [attempt, setAttempt] = useState(0);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const checked = useRef(-1);

  useEffect(() => {
    if (checked.current === attempt) return;
    checked.current = attempt;
    if (token.length < 32) {
      setLinkState('INVALID');
      return;
    }
    setLinkState('checking');
    apiClient<{ status: Exclude<LinkState, 'checking' | 'NETWORK' | 'DONE'> }>('/auth/password-reset/status', {
      method: 'POST',
      body: JSON.stringify({ token }),
    })
      .then((result) => setLinkState(result.status))
      .catch(() => setLinkState('NETWORK'));
  }, [token, attempt]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    if (password.length < 10) {
      setError('Mật khẩu cần tối thiểu 10 ký tự.');
      return;
    }
    if (password !== confirmation) {
      setError('Xác nhận mật khẩu không khớp.');
      return;
    }
    setSaving(true);
    try {
      await apiClient('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, password }) });
      setPassword('');
      setConfirmation('');
      setLinkState('DONE');
    } catch (caught) {
      const code = problemCode(caught);
      if (code === 'TOKEN_EXPIRED') setLinkState('EXPIRED');
      else if (code === 'TOKEN_USED') setLinkState('USED');
      else if (code === 'TOKEN_SUPERSEDED') setLinkState('SUPERSEDED');
      else if (code === 'TOKEN_INVALID') setLinkState('INVALID');
      else setError(validationMessage(caught, 'Chưa đổi được mật khẩu. Vui lòng thử lại.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-md px-4 py-12" data-ready={linkState !== 'checking'}>
      <section
        className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8"
        aria-live="polite"
      >
        {linkState === 'checking' && (
          <div className="text-center">
            <Loader2 className="mx-auto h-8 w-8 animate-spin text-primary" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">Đang kiểm tra liên kết</h1>
          </div>
        )}
        {linkState === 'NETWORK' && (
          <div className="text-center">
            <MailWarning className="mx-auto h-10 w-10 text-warning" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">Chưa kết nối được máy chủ</h1>
            <p className="mt-2 text-sm text-on-surface-variant">Kiểm tra mạng rồi thử lại. Liên kết chưa bị dùng.</p>
            <Button className="mt-5" onClick={() => setAttempt((value) => value + 1)}>
              Thử lại
            </Button>
          </div>
        )}
        {(linkState === 'EXPIRED' || linkState === 'USED' || linkState === 'SUPERSEDED' || linkState === 'INVALID') && (
          <div className="text-center">
            {linkState === 'EXPIRED' ? (
              <Clock3 className="mx-auto h-10 w-10 text-warning" aria-hidden="true" />
            ) : (
              <MailWarning className="mx-auto h-10 w-10 text-warning" aria-hidden="true" />
            )}
            <h1 className="mt-4 text-2xl font-bold text-on-surface">{BROKEN_LINK[linkState].title}</h1>
            <p className="mt-2 text-sm text-on-surface-variant">{BROKEN_LINK[linkState].body}</p>
            <ButtonLink to="/forgot-password" className="mt-5">
              Yêu cầu liên kết mới
            </ButtonLink>
          </div>
        )}
        {linkState === 'DONE' && (
          <div className="text-center">
            <CheckCircle2 className="mx-auto h-10 w-10 text-success" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">Đã đổi mật khẩu</h1>
            <p className="mt-2 text-sm text-on-surface-variant">
              Mọi phiên đăng nhập cũ trên các thiết bị đã được đăng xuất. Hãy đăng nhập bằng mật khẩu mới.
            </p>
            <Button className="mt-5" leftIcon={<LogIn className="h-4 w-4" />} onClick={() => setIsLoginModalOpen(true)}>
              Đăng nhập
            </Button>
          </div>
        )}
        {linkState === 'VALID' && (
          <>
            <Lock className="h-8 w-8 text-primary" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">Đặt mật khẩu mới</h1>
            <form onSubmit={submit} className="mt-5 space-y-4">
              <p className="text-sm text-on-surface-variant">
                Tối thiểu 10 ký tự. Sau khi đổi, mọi thiết bị đang đăng nhập sẽ bị đăng xuất.
              </p>
              <FormField label="Mật khẩu mới" required>
                {(control) => (
                  <TextInput
                    {...control}
                    type="password"
                    autoComplete="new-password"
                    minLength={10}
                    maxLength={72}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Xác nhận mật khẩu" required>
                {(control) => (
                  <TextInput
                    {...control}
                    type="password"
                    autoComplete="new-password"
                    maxLength={72}
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                  />
                )}
              </FormField>
              {error && <InlineFeedback kind="error" title={error} />}
              <Button type="submit" isLoading={saving}>
                {saving ? 'Đang đổi mật khẩu…' : 'Đổi mật khẩu'}
              </Button>
            </form>
          </>
        )}
      </section>
    </div>
  );
}

export default ResetPasswordPage;
