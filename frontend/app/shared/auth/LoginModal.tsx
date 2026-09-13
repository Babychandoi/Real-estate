import React, { useEffect, useRef, useState } from 'react';
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
  Loader2,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';

type ModalTab = 'login' | 'register';
type AccountType = 'BROKER' | 'USER';

export const LoginModal: React.FC = () => {
  const { isLoginModalOpen, setIsLoginModalOpen, login, register, resendVerification } = useAuth();
  const [activeTab, setActiveTab] = useState<ModalTab>('login');

  // Login form states
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginMfaCode, setLoginMfaCode] = useState('');
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

  useEffect(() => {
    if (!isLoginModalOpen) return;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden'; closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsLoginModalOpen(false);
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const items = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),summary'));
      if (!items.length) return;
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items[items.length - 1].focus(); }
      else if (!event.shiftKey && document.activeElement === items[items.length - 1]) { event.preventDefault(); items[0].focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = overflow; previous?.focus(); };
  }, [isLoginModalOpen, setIsLoginModalOpen]);

  const resetForms = () => {
    setLoginEmail('');
    setLoginPassword('');
    setLoginMfaCode('');
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
      const result = await login(loginEmail, loginPassword, loginMfaCode || undefined);
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) handleClose(); }}>
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="auth-dialog-title" className="bg-surface w-full max-w-md rounded-2xl shadow-2xl overflow-hidden max-h-[92dvh] overflow-y-auto">
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
              <p className="text-[11px] text-on-surface-variant">
                Nhà Đất Chuẩn • Nền tảng đăng tin có kiểm duyệt
              </p>
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
          <form onSubmit={handleLogin} className="p-6 flex flex-col gap-4">
            {/* Error alert */}
            {loginError && (
              <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700">
                <div className="flex items-center gap-2"><AlertCircle className="w-4 h-4 shrink-0" /><span>{loginError}</span></div>
                {loginError.toLowerCase().includes('xác minh email') && <button type="button" className="mt-2 font-bold underline" onClick={async()=>{const result=await resendVerification(loginEmail);setResendMessage(result.success?'Đã gửi lại email xác minh.':result.error||'Không thể gửi lại email.')}}>Gửi lại email xác minh</button>}
              </div>
            )}
            {resendMessage && <p className="rounded-xl bg-emerald-50 p-3 text-xs text-emerald-800" role="status">{resendMessage}</p>}

            {/* Email */}
            <div>
              <label className="text-xs font-semibold text-on-surface mb-1.5 block">Email</label>
              <div className="relative">
                <Mail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  type="email"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                  className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="ten@email.com"
                  required
                  autoFocus
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label className="text-xs font-semibold text-on-surface mb-1.5 block">Mật khẩu</label>
              <div className="relative">
                <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  type={showLoginPw ? 'text' : 'password'}
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  className="w-full pl-9 pr-10 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="Nhập mật khẩu"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowLoginPw(!showLoginPw)}
                  aria-label={showLoginPw ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-on-surface transition-colors"
                >
                  {showLoginPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-on-surface mb-1.5 block">
                Mã MFA <span className="font-normal text-on-surface-variant">(chỉ tài khoản quản trị)</span>
              </label>
              <div className="relative">
                <KeyRound className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  value={loginMfaCode}
                  onChange={(event) => setLoginMfaCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                  className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                  placeholder="6 chữ số"
                  pattern="\d{6}"
                />
              </div>
            </div>

            {/* Submit */}
            <Button
              type="submit"
              variant="primary"
              size="md"
              className="w-full shadow-md mt-1"
              disabled={loginLoading}
            >
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
            <p className="mt-2 text-sm text-on-surface-variant">Liên kết xác minh đã được gửi đến <strong>{verificationSentTo}</strong> và có hiệu lực trong 24 giờ.</p>
            <Button className="mt-5 w-full" onClick={() => handleTabSwitch('login')}>Đến đăng nhập</Button>
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

            {/* Loại tài khoản - CHỈ User hoặc Broker */}
            <div>
              <label className="text-xs font-semibold text-on-surface mb-2 block">
                Bạn là
              </label>
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  type="button"
                  onClick={() => setRegAccountType('USER')}
                  className={`p-3 rounded-xl border text-left flex items-center gap-2.5 transition-all ${
                    regAccountType === 'USER'
                      ? 'border-primary bg-primary/5 ring-2 ring-primary/20'
                      : 'border-outline-variant/40 hover:bg-surface-container'
                  }`}
                >
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                    regAccountType === 'USER' ? 'bg-primary/10 text-primary' : 'bg-surface-container text-on-surface-variant'
                  }`}>
                    <Home className="w-4 h-4" />
                  </div>
                  <div className="flex flex-col">
                    <span className={`text-xs font-bold ${regAccountType === 'USER' ? 'text-primary' : 'text-on-surface'}`}>
                      Người tìm nhà
                    </span>
                    <span className="text-[10px] text-on-surface-variant">Tìm kiếm & so sánh BĐS</span>
                  </div>
                  {regAccountType === 'USER' && <CheckCircle2 className="w-4 h-4 text-primary ml-auto shrink-0" />}
                </button>

                <button
                  type="button"
                  onClick={() => setRegAccountType('BROKER')}
                  className={`p-3 rounded-xl border text-left flex items-center gap-2.5 transition-all ${
                    regAccountType === 'BROKER'
                      ? 'border-blue-600 bg-blue-50 ring-2 ring-blue-500/20'
                      : 'border-outline-variant/40 hover:bg-surface-container'
                  }`}
                >
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                    regAccountType === 'BROKER' ? 'bg-blue-100 text-blue-700' : 'bg-surface-container text-on-surface-variant'
                  }`}>
                    <Briefcase className="w-4 h-4" />
                  </div>
                  <div className="flex flex-col">
                    <span className={`text-xs font-bold ${regAccountType === 'BROKER' ? 'text-blue-800' : 'text-on-surface'}`}>
                      Môi giới BĐS
                    </span>
                    <span className="text-[10px] text-on-surface-variant">Đăng tin & quản lý BĐS</span>
                  </div>
                  {regAccountType === 'BROKER' && <CheckCircle2 className="w-4 h-4 text-blue-600 ml-auto shrink-0" />}
                </button>
              </div>
            </div>

            {/* Họ tên */}
            <div>
              <label className="text-xs font-semibold text-on-surface mb-1.5 block">Họ và tên</label>
              <div className="relative">
                <User className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
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
              <label className="text-xs font-semibold text-on-surface mb-1.5 block">Email</label>
              <div className="relative">
                <Mail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                <input
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
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-semibold text-on-surface mb-1.5 block">Mật khẩu</label>
                <div className="relative">
                  <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                  <input
                    type={showRegPw ? 'text' : 'password'}
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-outline-variant/50 bg-surface-container/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40"
                    placeholder="Tối thiểu 6 ký tự"
                    required
                    minLength={6}
                  />
                </div>
              </div>
              <div>
                <label className="text-xs font-semibold text-on-surface mb-1.5 block">Xác nhận</label>
                <div className="relative">
                  <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
                  <input
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
            <Button
              type="submit"
              variant="primary"
              size="md"
              className="w-full shadow-md"
              disabled={regLoading}
            >
              {regLoading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Đang tạo tài khoản...
                </span>
              ) : (
                `Đăng ký ${regAccountType === 'BROKER' ? 'Môi giới' : 'Người tìm nhà'}`
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
