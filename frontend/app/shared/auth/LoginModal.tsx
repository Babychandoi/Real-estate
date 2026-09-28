import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from './AuthContext';
import {
  X,
  KeyRound,
  Mail,
  Lock,
  Eye,
  EyeOff,
  User,
  Briefcase,
  Home,
  Search,
  Loader2,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { useModal } from '@/shared/ui/useModal';
import type { SelfServiceRole } from './roles';

type ModalTab = 'login' | 'register';
type AccountType = SelfServiceRole;

/** Self-service account types; OWNER posts their own home without claiming to be a broker (P-09). */
const ACCOUNT_TYPES: ReadonlyArray<{ value: AccountType; label: string; description: string; icon: typeof Search }> = [
  { value: 'USER', label: 'Người tìm nhà', description: 'Tìm, lưu và so sánh tin; gửi yêu cầu xem nhà.', icon: Search },
  {
    value: 'OWNER',
    label: 'Chủ nhà',
    description: 'Đăng bán hoặc cho thuê nhà của chính bạn và nhận yêu cầu từ người quan tâm.',
    icon: Home,
  },
  {
    value: 'BROKER',
    label: 'Môi giới BĐS',
    description: 'Đăng và quản lý tin cho khách hàng, dùng không gian môi giới.',
    icon: Briefcase,
  },
];
const SUBMIT_LABEL: Record<AccountType, string> = { USER: 'Người tìm nhà', OWNER: 'Chủ nhà', BROKER: 'Môi giới' };

export const LoginModal: React.FC = () => {
  const { isLoginModalOpen, setIsLoginModalOpen, login, register, resendVerification } = useAuth();
  const [activeTab, setActiveTab] = useState<ModalTab>('login');

  // Login form states
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPw, setShowLoginPw] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [resendMessage, setResendMessage] = useState('');

  // Register form states
  const [regName, setRegName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regPasswordConfirm, setRegPasswordConfirm] = useState('');
  const [showRegPw, setShowRegPw] = useState(false);
  const [regAccountType, setRegAccountType] = useState<AccountType>('USER');
  const [regLoading, setRegLoading] = useState(false);
  const [regError, setRegError] = useState('');
  const [verificationSentTo, setVerificationSentTo] = useState('');
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const loginEmailRef = useRef<HTMLInputElement>(null);
  const loginPasswordRef = useRef<HTMLInputElement>(null);

  // Shared modal stack (M2): the same hook Dialog/Sheet use, so this dialog and any kit Sheet/Dialog open at the
  // same time cooperate — Escape and the Tab trap only ever apply to whichever is on top, and closing this one
  // returns focus to whatever opened it.
  useModal({
    open: isLoginModalOpen,
    onClose: () => setIsLoginModalOpen(false),
    panelRef: dialogRef,
    initialFocusRef: loginEmailRef,
  });

  useEffect(() => {
    if (!isLoginModalOpen) return undefined;
    setLoginEmail('');
    setLoginPassword('');
    const clearAutofill = window.requestAnimationFrame(() => {
      if (loginEmailRef.current) loginEmailRef.current.value = '';
      if (loginPasswordRef.current) loginPasswordRef.current.value = '';
    });
    return () => window.cancelAnimationFrame(clearAutofill);
  }, [isLoginModalOpen]);

  const resetForms = () => {
    setLoginEmail('');
    setLoginPassword('');
    setLoginError('');
    setResendMessage('');
    setRegName('');
    setRegEmail('');
    setRegPassword('');
    setRegPasswordConfirm('');
    setRegError('');
    setVerificationSentTo('');
  };

  const handleClose = () => {
    setIsLoginModalOpen(false);
    resetForms();
  };

  const handleTabSwitch = (tab: ModalTab) => {
    setActiveTab(tab);
    setLoginError('');
    setRegError('');
    setVerificationSentTo('');
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError('');
    setLoginLoading(true);
    try {
      const result = await login(loginEmail, loginPassword);
      if (!result.success) {
        setLoginError(result.error || 'Đăng nhập thất bại.');
      }
    } finally {
      setLoginLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setRegError('');

    if (regPassword.length < 10) {
      setRegError('Mật khẩu cần tối thiểu 10 ký tự.');
      return;
    }
    if (regPassword !== regPasswordConfirm) {
      setRegError('Mật khẩu xác nhận không khớp.');
      return;
    }

    setRegLoading(true);
    try {
      const result = await register(regEmail, regPassword, regName, regAccountType);
      if (!result.success) {
        setRegError(result.error || 'Đăng ký thất bại.');
      } else {
        setVerificationSentTo(result.email || regEmail);
      }
    } finally {
      setRegLoading(false);
    }
  };

  if (!isLoginModalOpen) return null;

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      // click (not mousedown): mousedown fires before React finishes closing, so the browser's default focus
      // move to <body> would race useModal's focus-return to the opener (m1).
      onClick={(event) => {
        if (event.target === event.currentTarget) handleClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-dialog-title"
        className="bg-surface w-full max-w-md rounded-2xl shadow-2xl overflow-hidden max-h-[92dvh] overflow-y-auto"
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-outline-variant/30 flex items-center justify-between bg-surface-container/50">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center text-primary">
              <KeyRound className="w-4 h-4" />
            </div>
            <div>
              <h3 id="auth-dialog-title" className="font-bold text-base text-on-surface">
                {activeTab === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
              </h3>
              <p className="text-xs text-on-surface-variant">Nhà Đất Chuẩn • Nền tảng đăng tin có kiểm duyệt</p>
            </div>
          </div>
          <button
            ref={closeRef}
            onClick={handleClose}
            aria-label="Đóng hộp thoại đăng nhập"
            className="min-w-11 min-h-11 rounded-lg hover:bg-surface-container flex items-center justify-center text-on-surface-variant transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Tab switcher */}
        <div className="px-6 pt-4 flex gap-1 bg-surface-container/30">
          <button
            type="button"
            onClick={() => handleTabSwitch('login')}
            className={`flex-1 py-2.5 text-sm font-semibold transition-all border-b-2 ${
              activeTab === 'login'
                ? 'border-primary text-primary bg-surface'
                : 'border-transparent text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Đăng nhập
          </button>
          <button
            type="button"
            onClick={() => handleTabSwitch('register')}
            className={`flex-1 py-2.5 text-sm font-semibold transition-all border-b-2 ${
              activeTab === 'register'
                ? 'border-primary text-primary bg-surface'
                : 'border-transparent text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Đăng ký
          </button>
        </div>

        {/* ═══ TAB: ĐĂNG NHẬP ═══ */}
        {activeTab === 'login' && (
          <form onSubmit={handleLogin} autoComplete="off" className="p-6 flex flex-col gap-4">
            {/* Error alert */}
            {loginError && (
              <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700">
                <div className="flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{loginError}</span>
                </div>
                {loginError.toLowerCase().includes('xác minh email') && (
                  <button
                    type="button"
                    className="mt-2 font-bold underline"
                    onClick={async () => {
                      const result = await resendVerification(loginEmail);
                      setResendMessage(
                        result.success ? 'Đã gửi lại email xác minh.' : result.error || 'Không thể gửi lại email.',
                      );
                    }}
                  >
                    Gửi lại email xác minh
                  </button>
                )}
              </div>
            )}
            {resendMessage && (
              <p className="rounded-xl bg-emerald-50 p-3 text-xs text-emerald-800" role="status">
                {resendMessage}
              </p>
            )}

            {/* Email */}
            <div>
              <label htmlFor="login-email" className="text-xs font-semibold text-on-surface mb-1.5 block">
                Email
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  id="login-email"
                  ref={loginEmailRef}
                  name="public-login-email"
                  type="email"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                  className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="ten@email.com"
                  required
                  autoComplete="off"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label htmlFor="login-password" className="text-xs font-semibold text-on-surface mb-1.5 block">
                Mật khẩu
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  id="login-password"
                  ref={loginPasswordRef}
                  name="public-login-password"
                  type={showLoginPw ? 'text' : 'password'}
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  className="w-full pl-9 pr-12 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="Nhập mật khẩu"
                  required
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  onClick={() => setShowLoginPw(!showLoginPw)}
                  aria-label={showLoginPw ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  className="absolute right-1 top-1/2 grid h-10 w-10 -translate-y-1/2 place-items-center rounded-lg text-on-surface-variant hover:text-on-surface transition-colors"
                >
                  {showLoginPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="-mt-2 flex justify-end">
              <Link
                to="/forgot-password"
                onClick={handleClose}
                className="text-xs font-semibold text-primary hover:underline"
              >
                Quên mật khẩu?
              </Link>
            </div>

            {/* Submit */}
            <Button type="submit" variant="primary" size="md" className="w-full shadow-md mt-1" disabled={loginLoading}>
              {loginLoading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Đang xác thực...
                </span>
              ) : (
                'Đăng nhập'
              )}
            </Button>

            {/* Hint chuyển tab */}
            <p className="text-center text-xs text-on-surface-variant mt-1">
              Chưa có tài khoản?{' '}
              <button
                type="button"
                onClick={() => handleTabSwitch('register')}
                className="text-primary font-semibold hover:underline"
              >
                Đăng ký ngay
              </button>
            </p>
          </form>
        )}

        {/* ═══ TAB: ĐĂNG KÝ ═══ */}
        {activeTab === 'register' && verificationSentTo && (
          <div className="p-6 text-center" role="status">
            <CheckCircle2 className="mx-auto h-11 w-11 text-emerald-600" />
            <h4 className="mt-3 text-lg font-bold text-on-surface">Kiểm tra hộp thư của bạn</h4>
            <p className="mt-2 text-sm text-on-surface-variant">
              Liên kết xác minh đã được gửi đến <strong>{verificationSentTo}</strong> và có hiệu lực trong 24 giờ.
            </p>
            <Button className="mt-5 w-full" onClick={() => handleTabSwitch('login')}>
              Đến đăng nhập
            </Button>
          </div>
        )}
        {activeTab === 'register' && !verificationSentTo && (
          <form onSubmit={handleRegister} className="p-6 flex flex-col gap-4">
            {/* Error alert */}
            {regError && (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{regError}</span>
              </div>
            )}

            {/* Loại tài khoản: người tìm nhà, chủ nhà hoặc môi giới (vai trò quản trị không tự chọn được). */}
            <fieldset>
              <legend className="text-xs font-semibold text-on-surface mb-2">Bạn là</legend>
              <div className="grid gap-2">
                {ACCOUNT_TYPES.map((option) => {
                  const Icon = option.icon;
                  const checked = regAccountType === option.value;
                  return (
                    <label
                      key={option.value}
                      className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors ${
                        checked
                          ? 'border-primary bg-primary/5 ring-2 ring-primary/20'
                          : 'border-outline-variant hover:bg-surface-container'
                      }`}
                    >
                      <input
                        type="radio"
                        name="register-account-type"
                        value={option.value}
                        checked={checked}
                        onChange={() => setRegAccountType(option.value)}
                        className="h-4 w-4 shrink-0 accent-primary"
                      />
                      <span
                        className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${
                          checked ? 'bg-primary/10 text-primary' : 'bg-surface-container text-on-surface-variant'
                        }`}
                      >
                        <Icon className="h-4 w-4" aria-hidden="true" />
                      </span>
                      <span className="flex min-w-0 flex-col">
                        <span className={`text-sm font-bold ${checked ? 'text-primary' : 'text-on-surface'}`}>
                          {option.label}
                        </span>
                        <span className="text-xs text-on-surface-variant">{option.description}</span>
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>

            {/* Họ tên */}
            <div>
              <label htmlFor="register-name" className="text-xs font-semibold text-on-surface mb-1.5 block">
                Họ và tên
              </label>
              <div className="relative">
                <User className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  id="register-name"
                  type="text"
                  value={regName}
                  onChange={(e) => setRegName(e.target.value)}
                  className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="Nguyễn Văn A"
                  required
                />
              </div>
            </div>

            {/* Email */}
            <div>
              <label htmlFor="register-email" className="text-xs font-semibold text-on-surface mb-1.5 block">
                Email
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  id="register-email"
                  type="email"
                  value={regEmail}
                  onChange={(e) => setRegEmail(e.target.value)}
                  className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="ten@email.com"
                  required
                />
              </div>
            </div>

            {/* Mật khẩu */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="register-password" className="text-xs font-semibold text-on-surface mb-1.5 block">
                  Mật khẩu
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                  <input
                    id="register-password"
                    type={showRegPw ? 'text' : 'password'}
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                    placeholder="Tối thiểu 10 ký tự"
                    required
                    minLength={10}
                  />
                </div>
              </div>
              <div>
                <label
                  htmlFor="register-password-confirm"
                  className="text-xs font-semibold text-on-surface mb-1.5 block"
                >
                  Xác nhận
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                  <input
                    id="register-password-confirm"
                    type={showRegPw ? 'text' : 'password'}
                    value={regPasswordConfirm}
                    onChange={(e) => setRegPasswordConfirm(e.target.value)}
                    className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                    placeholder="Nhập lại"
                    required
                  />
                </div>
              </div>
            </div>

            {/* Toggle show password */}
            <label className="flex items-center gap-2 text-xs text-on-surface-variant cursor-pointer select-none">
              <input
                type="checkbox"
                checked={showRegPw}
                onChange={() => setShowRegPw(!showRegPw)}
                className="w-3.5 h-3.5 rounded accent-primary"
              />
              Hiển thị mật khẩu
            </label>

            {/* Submit */}
            <Button type="submit" variant="primary" size="md" className="w-full shadow-md" disabled={regLoading}>
              {regLoading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Đang tạo tài khoản...
                </span>
              ) : (
                `Đăng ký ${SUBMIT_LABEL[regAccountType]}`
              )}
            </Button>

            {/* Hint chuyển tab */}
            <p className="text-center text-xs text-on-surface-variant">
              Đã có tài khoản?{' '}
              <button
                type="button"
                onClick={() => handleTabSwitch('login')}
                className="text-primary font-semibold hover:underline"
              >
                Đăng nhập
              </button>
            </p>
          </form>
        )}
      </div>
    </div>
  );
};
