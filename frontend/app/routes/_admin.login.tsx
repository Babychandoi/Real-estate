import { lazy, Suspense, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Eye, EyeOff, KeyRound, LockKeyhole, Mail, ShieldCheck, Smartphone } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { useAuth, type AuthResult, type MfaChallenge } from '@/shared/auth/AuthContext';
import { formatDateTime, problemCode } from '@/shared/auth/securityApi';
import { ApiProblemException } from '@/shared/types/problem-details';
import { Button } from '@/shared/ui/Button';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { TextInput } from '@/shared/ui/TextInput';
import { RecoveryCodesPanel } from '@/features/account-security/RecoveryCodesPanel';

const TotpQrCode = lazy(() => import('@/features/account-security/TotpQrCode'));

const ADMIN_HOME = '/2026/nhadatchuan/admin/moderation';

interface Enrollment {
  secret: string;
  otpauthUri: string;
}

type Step =
  | { kind: 'password' }
  | { kind: 'verify'; challenge: MfaChallenge }
  | { kind: 'enroll'; challenge: MfaChallenge; enrollment: Enrollment | null }
  | { kind: 'codes'; codes: string[]; session: AuthResult };

function problemMessage(error: unknown, fallback: string): string {
  return error instanceof ApiProblemException && error.problem.detail ? error.problem.detail : fallback;
}

/** "ABCD EFGH …": easier to type into an authenticator app by hand. */
function groupSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}

/** Staff portal (UI-17): password, then a TOTP code or a recovery code; first sign-in enrols an authenticator. */
export const AdminLoginPage: React.FC = () => {
  const { adminLogin, acceptSession } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>({ kind: 'password' });
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [code, setCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const headingRef = useRef<HTMLHeadingElement>(null);

  // Each step moves focus to its heading so keyboard and screen-reader users know the form changed.
  useEffect(() => {
    headingRef.current?.focus();
  }, [step.kind]);

  // A challenge lives five minutes; after that the person starts again with the password.
  useEffect(() => {
    if (step.kind !== 'verify' && step.kind !== 'enroll') return;
    const remaining = new Date(step.challenge.challengeExpiresAt).getTime() - Date.now();
    const timer = window.setTimeout(
      () => {
        setStep({ kind: 'password' });
        setError('Phiên xác thực hai lớp đã hết hạn. Hãy đăng nhập lại.');
      },
      Math.max(0, remaining),
    );
    return () => window.clearTimeout(timer);
  }, [step]);

  const restart = (message = '') => {
    setStep({ kind: 'password' });
    setCode('');
    setUseRecovery(false);
    setError(message);
  };

  const finish = (session: AuthResult) => {
    acceptSession(session);
    navigate(ADMIN_HOME, { replace: true });
  };

  const handleChallengeError = (caught: unknown, fallback: string) => {
    const machine = problemCode(caught);
    if (machine === 'MFA_CHALLENGE_LOCKED' || machine === 'MFA_CHALLENGE_INVALID' || machine === 'MFA_NOT_ENROLLED') {
      restart(problemMessage(caught, fallback));
      return;
    }
    setError(problemMessage(caught, fallback));
  };

  const submitPassword = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = await adminLogin(email, password);
      if (!result.success) {
        setError(result.error);
        return;
      }
      setPassword('');
      if (!result.mfa) {
        navigate(ADMIN_HOME, { replace: true });
        return;
      }
      setCode('');
      setStep(
        result.mfa.state === 'ENROLL'
          ? { kind: 'enroll', challenge: result.mfa, enrollment: null }
          : { kind: 'verify', challenge: result.mfa },
      );
    } finally {
      setLoading(false);
    }
  };

  const submitVerify = async (event: FormEvent) => {
    event.preventDefault();
    if (step.kind !== 'verify') return;
    setError('');
    setLoading(true);
    try {
      const body = useRecovery
        ? { challengeToken: step.challenge.challengeToken, recoveryCode: code.trim() }
        : { challengeToken: step.challenge.challengeToken, code: code.trim() };
      finish(await apiClient<AuthResult>('/auth/admin/mfa/verify', { method: 'POST', body: JSON.stringify(body) }));
    } catch (caught) {
      handleChallengeError(caught, 'Không xác thực được mã. Vui lòng thử lại.');
      setCode('');
    } finally {
      setLoading(false);
    }
  };

  const startEnrollment = async () => {
    if (step.kind !== 'enroll') return;
    setError('');
    setLoading(true);
    try {
      const enrollment = await apiClient<Enrollment>('/auth/admin/mfa/enroll', {
        method: 'POST',
        body: JSON.stringify({ challengeToken: step.challenge.challengeToken }),
      });
      setStep({ ...step, enrollment });
    } catch (caught) {
      handleChallengeError(caught, 'Không tạo được khóa xác thực. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  const confirmEnrollment = async (event: FormEvent) => {
    event.preventDefault();
    if (step.kind !== 'enroll') return;
    setError('');
    setLoading(true);
    try {
      const result = await apiClient<{ session: AuthResult; recoveryCodes: string[] }>(
        '/auth/admin/mfa/enroll/confirm',
        { method: 'POST', body: JSON.stringify({ challengeToken: step.challenge.challengeToken, code: code.trim() }) },
      );
      setCode('');
      setStep({ kind: 'codes', codes: result.recoveryCodes, session: result.session });
    } catch (caught) {
      handleChallengeError(caught, 'Mã chưa đúng. Kiểm tra giờ trên điện thoại rồi thử lại.');
      setCode('');
    } finally {
      setLoading(false);
    }
  };

  const title =
    step.kind === 'password'
      ? 'Đăng nhập quản trị'
      : step.kind === 'verify'
        ? 'Xác thực hai lớp'
        : step.kind === 'enroll'
          ? 'Thiết lập xác thực hai lớp'
          : 'Hoàn tất thiết lập';

  return (
    <main className="mx-auto flex min-h-[calc(100dvh-16rem)] w-full max-w-md items-center px-4 py-10" data-ready="true">
      <div className="w-full rounded-2xl border border-outline-variant/60 bg-surface-container-lowest p-6 shadow-sm sm:p-8">
        <div className="flex items-start gap-3">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-primary text-primary-on">
            <ShieldCheck className="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <h1 ref={headingRef} tabIndex={-1} className="text-xl font-bold text-on-surface outline-none">
              {title}
            </h1>
            <p className="mt-1 text-sm text-on-surface-variant">Cổng nội bộ Nhà Đất Chuẩn</p>
          </div>
        </div>

        {error && <InlineFeedback kind="error" title={error} className="mt-5" />}

        {step.kind === 'password' && (
          <form onSubmit={submitPassword} className="mt-6 space-y-4">
            <FormField label="Email" required>
              {(control) => (
                <TextInput
                  {...control}
                  name="admin-email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  autoComplete="section-admin username"
                  leadingIcon={<Mail className="h-4 w-4" />}
                  placeholder="admin@nhadatchuan.online"
                />
              )}
            </FormField>
            <FormField label="Mật khẩu" required>
              {(control) => (
                <TextInput
                  {...control}
                  name="admin-password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="section-admin current-password"
                  leadingIcon={<LockKeyhole className="h-4 w-4" />}
                  placeholder="Nhập mật khẩu"
                  trailing={
                    <button
                      type="button"
                      onClick={() => setShowPassword((value) => !value)}
                      aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                      className="grid h-11 w-11 place-items-center text-on-surface-variant hover:text-on-surface"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  }
                />
              )}
            </FormField>
            <Button type="submit" className="w-full" isLoading={loading}>
              {loading ? 'Đang xác thực…' : 'Tiếp tục'}
            </Button>
            <div className="flex items-center justify-between text-sm">
              <Link
                to="/forgot-password"
                className="inline-flex min-h-11 items-center font-semibold text-primary hover:underline"
              >
                Quên mật khẩu?
              </Link>
              <Link
                to="/"
                className="inline-flex min-h-11 items-center text-on-surface-variant hover:text-on-surface hover:underline"
              >
                Về trang chính
              </Link>
            </div>
          </form>
        )}

        {step.kind === 'verify' && (
          <form onSubmit={submitVerify} className="mt-6 space-y-4">
            <p className="text-body-sm text-on-surface-variant">
              {useRecovery
                ? 'Nhập một mã khôi phục chưa dùng (dạng XXXXX-XXXXX). Mã sẽ bị vô hiệu sau khi dùng.'
                : 'Mở ứng dụng xác thực trên điện thoại và nhập mã 6 số đang hiển thị.'}
            </p>
            <FormField
              label={useRecovery ? 'Mã khôi phục' : 'Mã xác thực'}
              hint={`Lượt xác thực hết hạn lúc ${formatDateTime(step.challenge.challengeExpiresAt)}.`}
              required
            >
              {(control) => (
                <TextInput
                  {...control}
                  name="one-time-code"
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  autoComplete="one-time-code"
                  inputMode={useRecovery ? 'text' : 'numeric'}
                  pattern={useRecovery ? undefined : '\\s*\\d{3}\\s?\\d{3}\\s*'}
                  maxLength={useRecovery ? 16 : 7}
                  leadingIcon={useRecovery ? <KeyRound className="h-4 w-4" /> : <Smartphone className="h-4 w-4" />}
                  className="font-mono tracking-widest"
                />
              )}
            </FormField>
            <Button type="submit" className="w-full" isLoading={loading}>
              Xác nhận
            </Button>
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <button
                type="button"
                className="min-h-11 font-semibold text-primary hover:underline"
                onClick={() => {
                  setUseRecovery((value) => !value);
                  setCode('');
                  setError('');
                }}
              >
                {useRecovery ? 'Dùng mã từ ứng dụng xác thực' : 'Dùng mã khôi phục'}
              </button>
              <button
                type="button"
                className="inline-flex min-h-11 items-center gap-1 text-on-surface-variant hover:text-on-surface"
                onClick={() => restart()}
              >
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />
                Đăng nhập lại
              </button>
            </div>
            <p className="text-xs text-on-surface-variant">
              Mất cả điện thoại và mã khôi phục? Liên hệ một quản trị viên khác để đặt lại xác thực hai lớp.
            </p>
          </form>
        )}

        {step.kind === 'enroll' && (
          <div className="mt-6 space-y-4">
            <InlineFeedback kind="info" title="Tài khoản nhân viên bắt buộc có xác thực hai lớp">
              Bạn cần một ứng dụng xác thực (Google Authenticator, Microsoft Authenticator, 1Password…). Sau khi thiết
              lập, mỗi lần đăng nhập sẽ cần mã 6 số từ ứng dụng.
            </InlineFeedback>
            {!step.enrollment ? (
              <Button className="w-full" isLoading={loading} onClick={() => void startEnrollment()}>
                Tạo khóa cho ứng dụng xác thực
              </Button>
            ) : (
              <form onSubmit={confirmEnrollment} className="space-y-4">
                <ol className="list-decimal space-y-3 pl-5 text-body-sm text-on-surface">
                  <li>
                    Mở ứng dụng xác thực trên điện thoại, chọn <strong>Thêm tài khoản</strong> rồi{' '}
                    <strong>Quét mã QR</strong>:
                    <div className="mt-2 flex justify-center">
                      <Suspense
                        fallback={
                          <div
                            aria-hidden="true"
                            className="h-52 w-52 animate-pulse rounded-card bg-surface-container"
                          />
                        }
                      >
                        <TotpQrCode uri={step.enrollment.otpauthUri} />
                      </Suspense>
                    </div>
                    <span className="mt-2 block text-label text-on-surface-variant">
                      Đang dùng chính điện thoại này? Mở{' '}
                      <a href={step.enrollment.otpauthUri} className="font-semibold text-primary underline">
                        liên kết thiết lập
                      </a>
                      .
                    </span>
                  </li>
                  <li>
                    Không quét được? Chọn <strong>Nhập khóa thiết lập</strong>, nhập khóa này và chọn loại{' '}
                    <strong>theo thời gian</strong>:
                    <code
                      className="mt-2 block select-all break-all rounded-card border border-outline-variant bg-surface-container-low px-3 py-2 font-mono text-body-sm tracking-wider"
                      aria-label="Khóa thiết lập"
                    >
                      {groupSecret(step.enrollment.secret)}
                    </code>
                  </li>
                  <li>Nhập mã 6 số ứng dụng hiển thị để xác nhận.</li>
                </ol>
                <FormField label="Mã xác thực" required>
                  {(control) => (
                    <TextInput
                      {...control}
                      name="one-time-code"
                      value={code}
                      onChange={(event) => setCode(event.target.value)}
                      autoComplete="one-time-code"
                      inputMode="numeric"
                      pattern="\s*\d{3}\s?\d{3}\s*"
                      maxLength={7}
                      leadingIcon={<Smartphone className="h-4 w-4" />}
                      className="font-mono tracking-widest"
                    />
                  )}
                </FormField>
                <Button type="submit" className="w-full" isLoading={loading}>
                  Xác nhận và bật xác thực hai lớp
                </Button>
              </form>
            )}
            <button
              type="button"
              className="inline-flex min-h-11 items-center gap-1 text-sm text-on-surface-variant hover:text-on-surface"
              onClick={() => restart()}
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Đăng nhập lại
            </button>
          </div>
        )}

        {step.kind === 'codes' && (
          <div className="mt-6">
            <RecoveryCodesPanel codes={step.codes} doneLabel="Vào trang quản trị" onDone={() => finish(step.session)} />
          </div>
        )}
      </div>
    </main>
  );
};
