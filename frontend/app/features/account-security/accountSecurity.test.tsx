import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { AdminLoginPage } from '@/routes/_admin.login';
import { VerifyEmailPage } from '@/routes/_public.verify-email';
import { ResetPasswordPage } from '@/routes/_public.reset-password';
import { ForgotPasswordPage } from '@/routes/_public.forgot-password';
import { SessionsSection } from './AccountSecuritySections';
import { problem, stubApi } from './testFetch';

const TOKEN = 'a'.repeat(43);
const USER = {
  id: 'u-1',
  name: 'Quản trị thử',
  email: 'admin@example.invalid',
  role: 'ADMIN',
  planCode: 'FREE',
  listingQuotaRemaining: 0,
};
const SESSION = { accessToken: 'new-session', expiresAt: '2030-01-01T00:00:00Z', idleExpiresAt: null, user: USER };
const inFiveMinutes = () => new Date(Date.now() + 5 * 60_000).toISOString();

function renderAdminLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<AdminLoginPage />} />
          <Route path="/2026/nhadatchuan/admin/moderation" element={<p>Bàn kiểm duyệt</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function renderPage(element: React.ReactNode) {
  return render(
    <MemoryRouter>
      <AuthProvider>{element}</AuthProvider>
    </MemoryRouter>,
  );
}

async function submitPassword() {
  fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: 'admin@example.invalid' } });
  fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'Mật-khẩu-thử-2026' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }));
}

beforeEach(() => sessionStorage.clear());
afterEach(() => window.history.replaceState(null, '', '/'));

describe('staff login with a second factor (UI-17)', () => {
  it('asks for the code after the password and only then stores a session', async () => {
    const calls = stubApi({
      'POST /auth/admin/login': {
        body: {
          mfaRequired: true,
          mfaState: 'VERIFY',
          challengeToken: 'challenge-1',
          challengeExpiresAt: inFiveMinutes(),
        },
      },
      'POST /auth/admin/mfa/verify': ({ body }) =>
        (body as { code?: string }).code === '123456' ? { body: SESSION } : problem(400, 'MFA_CODE_INVALID', 'Mã sai'),
    });
    renderAdminLogin();
    await submitPassword();
    expect(await screen.findByRole('heading', { name: 'Xác thực hai lớp' })).toBeInTheDocument();
    expect(sessionStorage.getItem('bds_access_token')).toBeNull();

    fireEvent.change(screen.getByLabelText(/^Mã xác thực/), { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }));
    expect(await screen.findByText('Mã sai')).toBeInTheDocument();
    expect(sessionStorage.getItem('bds_access_token')).toBeNull();

    fireEvent.change(screen.getByLabelText(/^Mã xác thực/), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }));
    expect(await screen.findByText('Bàn kiểm duyệt')).toBeInTheDocument();
    expect(sessionStorage.getItem('bds_access_token')).toBe('new-session');
    expect(calls.filter((call) => call.path === '/auth/admin/mfa/verify').at(-1)?.body).toEqual({
      challengeToken: 'challenge-1',
      code: '123456',
    });
  });

  it('accepts a recovery code instead, and a burnt challenge sends the person back to the password', async () => {
    const calls = stubApi({
      'POST /auth/admin/login': {
        body: {
          mfaRequired: true,
          mfaState: 'VERIFY',
          challengeToken: 'challenge-2',
          challengeExpiresAt: inFiveMinutes(),
        },
      },
      'POST /auth/admin/mfa/verify': problem(401, 'MFA_CHALLENGE_LOCKED', 'Nhập sai mã quá nhiều lần.'),
    });
    renderAdminLogin();
    await submitPassword();
    fireEvent.click(await screen.findByRole('button', { name: 'Dùng mã khôi phục' }));
    fireEvent.change(screen.getByLabelText(/^Mã khôi phục/), { target: { value: 'ABCDE-FGHJK' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }));
    expect(await screen.findByRole('heading', { name: 'Đăng nhập quản trị' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Nhập sai mã quá nhiều lần.');
    expect(calls.find((call) => call.path === '/auth/admin/mfa/verify')?.body).toEqual({
      challengeToken: 'challenge-2',
      recoveryCode: 'ABCDE-FGHJK',
    });
  });

  it('enrols an authenticator and shows the recovery codes once before entering the admin', async () => {
    stubApi({
      'POST /auth/admin/login': {
        body: {
          mfaRequired: true,
          mfaState: 'ENROLL',
          challengeToken: 'challenge-3',
          challengeExpiresAt: inFiveMinutes(),
        },
      },
      'POST /auth/admin/mfa/enroll': {
        body: { secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/x?secret=JBSW' },
      },
      'POST /auth/admin/mfa/enroll/confirm': {
        body: { session: SESSION, recoveryCodes: ['AAAAA-BBBBB', 'CCCCC-DDDDD'] },
      },
    });
    renderAdminLogin();
    await submitPassword();
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo khóa cho ứng dụng xác thực' }));
    expect(await screen.findByLabelText('Khóa thiết lập')).toHaveTextContent('JBSW Y3DP EHPK 3PXP');
    fireEvent.change(screen.getByLabelText(/^Mã xác thực/), { target: { value: '654321' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận và bật xác thực hai lớp' }));
    expect(await screen.findByText('AAAAA-BBBBB')).toBeInTheDocument();
    // Not signed in until the codes are acknowledged.
    expect(sessionStorage.getItem('bds_access_token')).toBeNull();
    const enter = screen.getByRole('button', { name: 'Vào trang quản trị' });
    expect(enter).toBeDisabled();
    fireEvent.click(screen.getByLabelText('Tôi đã lưu các mã này ở nơi an toàn'));
    fireEvent.click(enter);
    expect(await screen.findByText('Bàn kiểm duyệt')).toBeInTheDocument();
    expect(sessionStorage.getItem('bds_access_token')).toBe('new-session');
  });
});

describe('token pages (UI-14)', () => {
  it('verifies once, removes the token from the address bar and offers to continue', async () => {
    window.history.replaceState(null, '', `/verify-email?token=${TOKEN}`);
    const calls = stubApi({
      'POST /auth/verify-email': { body: { status: 'VERIFIED', returnTo: '/search?purpose=RENT' } },
    });
    renderPage(<VerifyEmailPage />);
    expect(await screen.findByRole('heading', { name: 'Email đã được xác minh' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng nhập để tiếp tục' })).toBeInTheDocument();
    expect(window.location.search).toBe('');
    expect(calls.filter((call) => call.path === '/auth/verify-email')).toHaveLength(1);
    expect(calls[0].body).toEqual({ token: TOKEN });
  });

  it('tells an expired link apart and resends with a neutral answer and a cooldown', async () => {
    window.history.replaceState(null, '', `/verify-email?token=${TOKEN}`);
    stubApi({
      'POST /auth/verify-email': problem(410, 'TOKEN_EXPIRED'),
      'POST /auth/resend-verification': { status: 202 },
    });
    renderPage(<VerifyEmailPage />);
    expect(await screen.findByRole('heading', { name: 'Liên kết xác minh đã hết hạn' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Email đã đăng ký/), { target: { value: 'ai-do@example.invalid' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi liên kết xác minh mới' }));
    expect(await screen.findByText('Đã tiếp nhận yêu cầu')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Gửi lại sau/ })).toBeDisabled();
  });

  it('shows superseded and used links with their own guidance', async () => {
    window.history.replaceState(null, '', `/verify-email?token=${TOKEN}`);
    stubApi({ 'POST /auth/verify-email': problem(409, 'TOKEN_SUPERSEDED') });
    renderPage(<VerifyEmailPage />);
    expect(
      await screen.findByRole('heading', { name: 'Liên kết này đã được thay bằng liên kết mới hơn' }),
    ).toBeInTheDocument();
  });

  it('checks a reset link before showing the form and explains an expired one', async () => {
    window.history.replaceState(null, '', `/reset-password?token=${TOKEN}`);
    stubApi({ 'POST /auth/password-reset/status': { body: { status: 'EXPIRED' } } });
    renderPage(<ResetPasswordPage />);
    expect(await screen.findByRole('heading', { name: 'Liên kết đặt lại mật khẩu đã hết hạn' })).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Mật khẩu mới/)).not.toBeInTheDocument();
    expect(window.location.search).toBe('');
  });

  it('resets with a valid link and reports a link used meanwhile', async () => {
    window.history.replaceState(null, '', `/reset-password?token=${TOKEN}`);
    stubApi({
      'POST /auth/password-reset/status': { body: { status: 'VALID' } },
      'POST /auth/reset-password': problem(409, 'TOKEN_USED'),
    });
    renderPage(<ResetPasswordPage />);
    fireEvent.change(await screen.findByLabelText(/^Mật khẩu mới/), { target: { value: 'Mat-khau-moi-2026' } });
    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu/), { target: { value: 'Mat-khau-moi-2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));
    expect(await screen.findByRole('heading', { name: 'Liên kết này đã được dùng' })).toBeInTheDocument();
  });

  it('forgot password honours Retry-After from the server', async () => {
    stubApi({
      'POST /auth/forgot-password': {
        status: 429,
        body: { title: 'Quá nhiều', status: 429 },
        headers: { 'Retry-After': '120' },
      },
    });
    renderPage(<ForgotPasswordPage />);
    fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: 'ai-do@example.invalid' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi liên kết đặt lại' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Hãy thử lại sau 2 phút.');
    expect(screen.getByRole('button', { name: /Thử lại sau/ })).toBeDisabled();
  });
});

describe('sessions of the account (F20.2)', () => {
  it('lists devices without addresses and signs out the other ones', async () => {
    sessionStorage.setItem('bds_access_token', 'current');
    let others = 1;
    stubApi({
      'GET /auth/me': { body: USER },
      'GET /me/sessions': () => ({
        body: [
          {
            id: 's-1',
            current: true,
            createdAt: '2026-09-28T01:00:00Z',
            lastSeenAt: '2026-09-28T02:00:00Z',
            expiresAt: '2026-09-28T09:00:00Z',
            idleExpiresAt: '2026-09-28T02:30:00Z',
            device: 'Chrome trên Windows',
            ipHint: '203.0.113.x',
            mfaVerified: true,
          },
          ...(others
            ? [
                {
                  id: 's-2',
                  current: false,
                  createdAt: '2026-09-27T01:00:00Z',
                  lastSeenAt: '2026-09-27T02:00:00Z',
                  expiresAt: '2026-09-27T13:00:00Z',
                  idleExpiresAt: null,
                  device: 'Safari trên iOS',
                  ipHint: '198.51.100.x',
                  mfaVerified: false,
                },
              ]
            : []),
        ],
      }),
      'POST /me/sessions/revoke-others': () => {
        others = 0;
        return { body: { revokedSessions: 1 } };
      },
    });
    renderPage(<SessionsSection />);
    expect(await screen.findByText(/Safari trên iOS · 198\.51\.100\.x/)).toBeInTheDocument();
    expect(screen.getByText('Thiết bị này')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất tất cả thiết bị khác' }));
    expect(await screen.findByText('Đã đăng xuất 1 thiết bị khác.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/Safari trên iOS/)).not.toBeInTheDocument());
  });
});
