import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { History, KeyRound, LogOut, MonitorSmartphone, ShieldCheck, ShieldAlert } from 'lucide-react';
import { useAuth } from '@/shared/auth/AuthContext';
import {
  describeClient,
  formatDateTime,
  problemCode,
  SECURITY_EVENT_LABELS,
  securityApi,
  WARNING_EVENTS,
  type MfaStatus,
  type SecurityEvent,
  type SessionView,
} from '@/shared/auth/securityApi';
import { validationMessage } from '@/shared/types/problem-details';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { TextInput } from '@/shared/ui/TextInput';
import { RecoveryCodesPanel } from './RecoveryCodesPanel';

const card = 'mt-6 rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-5 md:p-7';

function SectionHeader({
  id,
  icon,
  title,
  description,
}: {
  id: string;
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <header className="flex items-start gap-3">
      <span
        className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary"
        aria-hidden="true"
      >
        {icon}
      </span>
      <div>
        <h2 id={id} className="text-headline-sm font-bold text-on-surface">
          {title}
        </h2>
        <p className="mt-1 text-body-sm text-on-surface-variant">{description}</p>
      </div>
    </header>
  );
}

type Load<T> = { state: 'loading' } | { state: 'error' } | { state: 'ready'; value: T };

function useLoad<T>(load: () => Promise<T>): [Load<T>, () => void] {
  const [value, setValue] = useState<Load<T>>({ state: 'loading' });
  const reload = useCallback(() => {
    setValue((current) => (current.state === 'ready' ? current : { state: 'loading' }));
    load()
      .then((result) => setValue({ state: 'ready', value: result }))
      .catch(() => setValue({ state: 'error' }));
  }, [load]);
  useEffect(() => {
    reload();
  }, [reload]);
  return [value, reload];
}

/** Open sessions of the account; revoke one, or every other device (F20.2). */
export function SessionsSection({ onChanged }: { onChanged?: () => void }) {
  const { logout } = useAuth();
  const [sessions, reload] = useLoad(securityApi.sessions);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

  const revoke = async (session: SessionView) => {
    setBusy(session.id);
    setMessage(null);
    try {
      await securityApi.revokeSession(session.id);
      if (session.current) {
        logout();
        return;
      }
      setMessage({ kind: 'success', text: `Đã đăng xuất ${session.device ?? 'thiết bị'}.` });
      reload();
      onChanged?.();
    } catch {
      setMessage({ kind: 'error', text: 'Chưa đăng xuất được thiết bị này. Vui lòng thử lại.' });
    } finally {
      setBusy(null);
    }
  };

  const revokeOthers = async () => {
    setBusy('others');
    setMessage(null);
    try {
      const result = await securityApi.revokeOtherSessions();
      setMessage({
        kind: 'success',
        text:
          result.revokedSessions > 0
            ? `Đã đăng xuất ${result.revokedSessions} thiết bị khác.`
            : 'Không có thiết bị nào khác đang đăng nhập.',
      });
      reload();
      onChanged?.();
    } catch {
      setMessage({ kind: 'error', text: 'Chưa đăng xuất được các thiết bị khác. Vui lòng thử lại.' });
    } finally {
      setBusy(null);
    }
  };

  const others = sessions.state === 'ready' ? sessions.value.filter((session) => !session.current).length : 0;

  return (
    <section className={card} aria-labelledby="sessions-title">
      <SectionHeader
        id="sessions-title"
        icon={<MonitorSmartphone className="h-5 w-5" />}
        title="Thiết bị đang đăng nhập"
        description="Chỉ hiển thị trình duyệt và dải mạng gần đúng. Đăng xuất ngay thiết bị bạn không nhận ra."
      />
      {message && <InlineFeedback kind={message.kind} title={message.text} className="mt-4" />}
      {sessions.state === 'loading' && (
        <p role="status" className="mt-4 text-body-sm text-on-surface-variant">
          Đang tải danh sách thiết bị…
        </p>
      )}
      {sessions.state === 'error' && (
        <InlineFeedback
          kind="error"
          title="Không tải được danh sách thiết bị."
          action={{ label: 'Thử lại', onClick: reload }}
          className="mt-4"
        />
      )}
      {sessions.state === 'ready' && (
        <ul className="mt-4 divide-y divide-outline-variant/50">
          {sessions.value.map((session) => (
            <li key={session.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-body-sm font-semibold text-on-surface">
                  {describeClient(session.device, session.ipHint)}
                  {session.current && <Badge variant="primary">Thiết bị này</Badge>}
                  {session.mfaVerified && <Badge variant="neutral">Đã xác thực hai lớp</Badge>}
                </p>
                <p className="mt-0.5 text-xs text-on-surface-variant">
                  Đăng nhập {formatDateTime(session.createdAt)} · Hoạt động gần nhất{' '}
                  {formatDateTime(session.lastSeenAt)}
                  {' · '}
                  {session.idleExpiresAt
                    ? `Tự đăng xuất nếu không hoạt động đến ${formatDateTime(session.idleExpiresAt)}`
                    : `Hết hạn ${formatDateTime(session.expiresAt)}`}
                </p>
              </div>
              <Button
                size="sm"
                variant="outline"
                isLoading={busy === session.id}
                disabled={busy !== null && busy !== session.id}
                leftIcon={<LogOut className="h-4 w-4" />}
                onClick={() => void revoke(session)}
                aria-label={
                  session.current
                    ? 'Đăng xuất thiết bị này'
                    : `Đăng xuất ${describeClient(session.device, session.ipHint)}`
                }
              >
                Đăng xuất
              </Button>
            </li>
          ))}
        </ul>
      )}
      {others > 0 && (
        <Button
          variant="danger"
          className="mt-4"
          isLoading={busy === 'others'}
          disabled={busy !== null && busy !== 'others'}
          onClick={() => void revokeOthers()}
        >
          Đăng xuất tất cả thiết bị khác
        </Button>
      )}
    </section>
  );
}

/** Password change with the current password; every other device is signed out (F20.2). */
export function ChangePasswordSection({ onChanged }: { onChanged?: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setDone('');
    if (next.length < 10) {
      setError('Mật khẩu mới cần tối thiểu 10 ký tự.');
      return;
    }
    if (next !== confirmation) {
      setError('Xác nhận mật khẩu không khớp.');
      return;
    }
    setSaving(true);
    try {
      const result = await securityApi.changePassword(current, next);
      setCurrent('');
      setNext('');
      setConfirmation('');
      setDone(
        result.revokedSessions > 0
          ? `Đã đổi mật khẩu và đăng xuất ${result.revokedSessions} thiết bị khác.`
          : 'Đã đổi mật khẩu.',
      );
      onChanged?.();
    } catch (caught) {
      const code = problemCode(caught);
      setError(
        code === 'CURRENT_PASSWORD_INVALID'
          ? 'Mật khẩu hiện tại không đúng.'
          : code === 'PASSWORD_UNCHANGED'
            ? 'Mật khẩu mới phải khác mật khẩu hiện tại.'
            : validationMessage(caught, 'Chưa đổi được mật khẩu. Vui lòng thử lại sau ít phút.'),
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className={card} aria-labelledby="change-password-title">
      <SectionHeader
        id="change-password-title"
        icon={<KeyRound className="h-5 w-5" />}
        title="Đổi mật khẩu"
        description="Sau khi đổi, các thiết bị khác sẽ bị đăng xuất và email của bạn nhận thông báo."
      />
      <form onSubmit={submit} className="mt-4 grid gap-4 md:grid-cols-3">
        <FormField label="Mật khẩu hiện tại" required>
          {(control) => (
            <TextInput
              {...control}
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
            />
          )}
        </FormField>
        <FormField label="Mật khẩu mới" hint="Tối thiểu 10 ký tự." required>
          {(control) => (
            <TextInput
              {...control}
              type="password"
              autoComplete="new-password"
              maxLength={72}
              value={next}
              onChange={(event) => setNext(event.target.value)}
            />
          )}
        </FormField>
        <FormField label="Nhập lại mật khẩu mới" required>
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
        <div className="md:col-span-3 space-y-3">
          {error && <InlineFeedback kind="error" title={error} />}
          {done && <InlineFeedback kind="success" title={done} />}
          <Button type="submit" isLoading={saving}>
            Đổi mật khẩu
          </Button>
        </div>
      </form>
    </section>
  );
}

/** Recent security events of the account (UI-17 audit), newest first. */
export function SecurityEventsSection({ refreshKey = 0 }: { refreshKey?: number }) {
  const [events, reload] = useLoad(securityApi.events);
  useEffect(() => {
    if (refreshKey > 0) reload();
  }, [refreshKey, reload]);
  return (
    <section className={card} aria-labelledby="security-events-title">
      <SectionHeader
        id="security-events-title"
        icon={<History className="h-5 w-5" />}
        title="Hoạt động bảo mật gần đây"
        description="30 sự kiện gần nhất. Nếu có mục bạn không thực hiện, hãy đổi mật khẩu và đăng xuất các thiết bị khác."
      />
      {events.state === 'loading' && (
        <p role="status" className="mt-4 text-body-sm text-on-surface-variant">
          Đang tải hoạt động…
        </p>
      )}
      {events.state === 'error' && (
        <InlineFeedback
          kind="error"
          title="Không tải được hoạt động bảo mật."
          action={{ label: 'Thử lại', onClick: reload }}
          className="mt-4"
        />
      )}
      {events.state === 'ready' && events.value.length === 0 && (
        <p className="mt-4 text-body-sm text-on-surface-variant">Chưa có hoạt động nào được ghi nhận.</p>
      )}
      {events.state === 'ready' && events.value.length > 0 && (
        <ol className="mt-4 divide-y divide-outline-variant/50">
          {events.value.map((event: SecurityEvent) => (
            <li key={event.id} className="flex flex-wrap items-start justify-between gap-2 py-2.5 text-body-sm">
              <span className="flex items-center gap-2 font-semibold text-on-surface">
                {WARNING_EVENTS.has(event.type) ? (
                  <ShieldAlert className="h-4 w-4 text-warning" aria-hidden="true" />
                ) : (
                  <ShieldCheck className="h-4 w-4 text-success" aria-hidden="true" />
                )}
                {SECURITY_EVENT_LABELS[event.type] ?? event.type}
              </span>
              <span className="text-xs text-on-surface-variant">
                {formatDateTime(event.occurredAt)}
                {(event.device || event.ipHint) && ` · ${describeClient(event.device, event.ipHint)}`}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

/** Staff second factor status and recovery-code regeneration (needs a fresh code). */
export function MfaSection() {
  const [status, reload] = useLoad<MfaStatus>(securityApi.mfaStatus);
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [codes, setCodes] = useState<string[] | null>(null);

  const close = () => {
    setOpen(false);
    setCode('');
    setError('');
    setCodes(null);
    reload();
  };

  const regenerate = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setSaving(true);
    try {
      setCodes((await securityApi.regenerateRecoveryCodes(code.trim())).recoveryCodes);
      setCode('');
    } catch (caught) {
      setError(
        problemCode(caught) === 'MFA_CODE_INVALID'
          ? 'Mã xác thực không đúng hoặc đã được dùng. Chờ mã mới rồi thử lại.'
          : 'Chưa tạo được mã mới. Vui lòng thử lại.',
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className={card} aria-labelledby="mfa-title">
      <SectionHeader
        id="mfa-title"
        icon={<ShieldCheck className="h-5 w-5" />}
        title="Xác thực hai lớp"
        description="Bắt buộc với tài khoản nhân viên: mỗi lần đăng nhập cần mã 6 số từ ứng dụng xác thực."
      />
      {status.state === 'loading' && (
        <p role="status" className="mt-4 text-body-sm text-on-surface-variant">
          Đang tải trạng thái…
        </p>
      )}
      {status.state === 'error' && (
        <InlineFeedback
          kind="error"
          title="Không tải được trạng thái xác thực hai lớp."
          action={{ label: 'Thử lại', onClick: reload }}
          className="mt-4"
        />
      )}
      {status.state === 'ready' && (
        <div className="mt-4 space-y-3 text-body-sm">
          {status.value.enrolled ? (
            <>
              <p className="flex flex-wrap items-center gap-2 text-on-surface">
                <Badge variant="success">Đang bật</Badge>
                từ {formatDateTime(status.value.enrolledAt)}
              </p>
              <p
                className={
                  status.value.recoveryCodesRemaining <= 2 ? 'font-semibold text-warning' : 'text-on-surface-variant'
                }
              >
                Còn {status.value.recoveryCodesRemaining} mã khôi phục chưa dùng.
                {status.value.recoveryCodesRemaining <= 2 && ' Hãy tạo bộ mã mới.'}
              </p>
              <Button variant="outline" onClick={() => setOpen(true)}>
                Tạo lại mã khôi phục
              </Button>
            </>
          ) : (
            <InlineFeedback kind="warning" title="Chưa bật xác thực hai lớp">
              {status.value.required
                ? 'Bạn sẽ được yêu cầu thiết lập ở lần đăng nhập cổng quản trị tiếp theo.'
                : 'Môi trường này không bắt buộc; lần đăng nhập cổng quản trị kế tiếp sẽ hướng dẫn thiết lập khi được bật.'}
            </InlineFeedback>
          )}
        </div>
      )}
      <Dialog
        open={open}
        onClose={close}
        title={codes ? 'Mã khôi phục mới' : 'Tạo lại mã khôi phục'}
        description={codes ? undefined : 'Các mã cũ sẽ ngừng hoạt động ngay khi bộ mã mới được tạo.'}
        closeOnBackdrop={!codes}
      >
        {codes ? (
          <RecoveryCodesPanel codes={codes} onDone={close} doneLabel="Xong" />
        ) : (
          <form onSubmit={regenerate} className="space-y-4">
            <FormField label="Mã từ ứng dụng xác thực" required>
              {(control) => (
                <TextInput
                  {...control}
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="\s*\d{3}\s?\d{3}\s*"
                  maxLength={7}
                  className="font-mono tracking-widest"
                />
              )}
            </FormField>
            {error && <InlineFeedback kind="error" title={error} />}
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={close}>
                Hủy
              </Button>
              <Button type="submit" isLoading={saving}>
                Tạo mã mới
              </Button>
            </div>
          </form>
        )}
      </Dialog>
    </section>
  );
}

/** Everything an account manages about its own security, in page order. */
export function AccountSecuritySections({ staff = false }: { staff?: boolean }) {
  const [refreshKey, setRefreshKey] = useState(0);
  const changed = () => setRefreshKey((key) => key + 1);
  return (
    <>
      {staff && <MfaSection />}
      <SessionsSection onChanged={changed} />
      <ChangePasswordSection onChanged={changed} />
      <SecurityEventsSection refreshKey={refreshKey} />
    </>
  );
}
