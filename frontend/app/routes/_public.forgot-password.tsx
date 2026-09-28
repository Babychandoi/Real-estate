import { useState, type FormEvent } from 'react';
import { CheckCircle2, LogIn, Mail, RotateCcw, Send } from 'lucide-react';
import { useAuth } from '@/shared/auth/AuthContext';
import { Button } from '@/shared/ui/Button';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { TextInput } from '@/shared/ui/TextInput';
import {
  formatWait,
  RESEND_COOLDOWN_SECONDS,
  sendEmailRequest,
  useCooldown,
} from '@/features/account-security/emailRequests';

/** UI-14: neutral answer (never says whether the address has an account), cooldown and server throttling respected. */
export function ForgotPasswordPage() {
  const { setIsLoginModalOpen } = useAuth();
  const [email, setEmail] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [wait, startWait] = useCooldown();

  const send = async () => {
    setSending(true);
    setError('');
    const result = await sendEmailRequest('/auth/forgot-password', email.trim());
    setSending(false);
    if (result.ok) {
      setSent(true);
      startWait(RESEND_COOLDOWN_SECONDS);
    } else if (result.retryAfterSeconds) {
      startWait(result.retryAfterSeconds);
      setError(`Bạn đã yêu cầu nhiều lần. Hãy thử lại sau ${formatWait(result.retryAfterSeconds)}.`);
    } else {
      setError('Chưa thể gửi yêu cầu lúc này. Vui lòng kiểm tra kết nối và thử lại.');
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void send();
  };

  return (
    <div className="mx-auto max-w-md px-4 py-12" data-ready="true">
      <section className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-6 md:p-8">
        {sent ? (
          <>
            <div className="grid h-12 w-12 place-items-center rounded-full bg-success-container text-success-on-container">
              <CheckCircle2 className="h-7 w-7" aria-hidden="true" />
            </div>
            <h1 className="mt-5 text-2xl font-bold text-on-surface">Đã tiếp nhận yêu cầu</h1>
            <p className="mt-3 text-sm leading-6 text-on-surface-variant" role="status">
              Nếu <strong className="text-on-surface">{email.trim()}</strong> thuộc một tài khoản đang hoạt động, chúng
              tôi đã gửi liên kết đặt lại mật khẩu. Liên kết chỉ dùng một lần, hết hạn sau 30 phút, và liên kết gửi
              trước đó không còn dùng được.
            </p>
            <div className="mt-5 rounded-xl bg-surface-container-low p-4 text-sm leading-6 text-on-surface-variant">
              <strong className="text-on-surface">Chưa thấy email?</strong>
              <br />
              Kiểm tra thư mục Spam, kiểm tra lại địa chỉ đã nhập, hoặc gửi lại sau khi hết thời gian chờ.
            </div>
            {error && <InlineFeedback kind="error" title={error} className="mt-4" />}
            <div className="mt-6 flex flex-wrap gap-3">
              <Button
                variant="outline"
                onClick={() => void send()}
                disabled={wait > 0}
                isLoading={sending}
                leftIcon={<RotateCcw className="h-4 w-4" />}
              >
                {wait > 0 ? `Gửi lại sau ${formatWait(wait)}` : 'Gửi lại'}
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setSent(false);
                  setError('');
                }}
              >
                Dùng email khác
              </Button>
              <Button leftIcon={<LogIn className="h-4 w-4" />} onClick={() => setIsLoginModalOpen(true)}>
                Quay lại đăng nhập
              </Button>
            </div>
          </>
        ) : (
          <>
            <Mail className="h-8 w-8 text-primary" aria-hidden="true" />
            <h1 className="mt-4 text-2xl font-bold text-on-surface">Quên mật khẩu</h1>
            <p className="mt-3 text-sm leading-6 text-on-surface-variant">
              Nhập email đăng ký. Chúng tôi sẽ gửi liên kết để bạn đặt mật khẩu mới.
            </p>
            <form onSubmit={submit} className="mt-5 space-y-4">
              <FormField label="Email" required>
                {(control) => (
                  <TextInput
                    {...control}
                    // The page exists only to collect this address, so the cursor starts there.
                    // eslint-disable-next-line jsx-a11y/no-autofocus
                    autoFocus
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    leadingIcon={<Mail className="h-4 w-4" />}
                  />
                )}
              </FormField>
              {error && <InlineFeedback kind="error" title={error} />}
              <Button type="submit" isLoading={sending} disabled={wait > 0} leftIcon={<Send className="h-4 w-4" />}>
                {wait > 0 ? `Thử lại sau ${formatWait(wait)}` : sending ? 'Đang gửi liên kết…' : 'Gửi liên kết đặt lại'}
              </Button>
            </form>
          </>
        )}
      </section>
    </div>
  );
}

export default ForgotPasswordPage;
