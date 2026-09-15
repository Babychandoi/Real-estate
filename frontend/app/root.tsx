import React, { useEffect, useRef, useState } from 'react';
import { Outlet, Link } from 'react-router-dom';
import {
  Building2,
  PlusCircle,
  ChevronDown,
  FileCheck2,
  UserCheck,
  ShieldAlert,
  BarChart3,
  Layers,
  FileText,
  ExternalLink,
  Briefcase,
  LogOut,
  LogIn,
  User as UserIcon,
  Menu,
  X,
  Search,
  Users
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { AuthProvider, useAuth } from '@/shared/auth/AuthContext';
import { LoginModal } from '@/shared/auth/LoginModal';

const RootLayoutContent: React.FC = () => {
  const [isAdminOpen, setIsAdminOpen] = useState(false);
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const mobilePanelRef = useRef<HTMLDivElement>(null);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const { user, isAuthenticated, isAdminOrModerator, isBroker, setIsLoginModalOpen, logout } = useAuth();

  useEffect(() => {
    if (!isMobileOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const first = mobilePanelRef.current?.querySelector<HTMLElement>('button,a'); first?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsMobileOpen(false);
      if (event.key !== 'Tab' || !mobilePanelRef.current) return;
      const items = Array.from(mobilePanelRef.current.querySelectorAll<HTMLElement>('button:not([disabled]),a[href]'));
      if (!items.length) return;
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items[items.length - 1].focus(); }
      else if (!event.shiftKey && document.activeElement === items[items.length - 1]) { event.preventDefault(); items[0].focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.body.style.overflow = previousOverflow; document.removeEventListener('keydown', onKey); menuButtonRef.current?.focus(); };
  }, [isMobileOpen]);

  useEffect(() => {
    if (!isUserMenuOpen) return;
    const closeMenu = (event: MouseEvent) => {
      if (!userMenuRef.current?.contains(event.target as Node)) setIsUserMenuOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsUserMenuOpen(false);
    };
    document.addEventListener('mousedown', closeMenu);
    document.addEventListener('keydown', closeOnEscape);
    return () => { document.removeEventListener('mousedown', closeMenu); document.removeEventListener('keydown', closeOnEscape); };
  }, [isUserMenuOpen]);

  return (
    <div className="min-h-screen flex flex-col bg-surface text-on-surface">
      <a href="#main-content" className="skip-link">Bỏ qua điều hướng</a>
      {/* Header điều hướng */}
      <header className="sticky top-0 z-40 bg-surface/95 backdrop-blur-md border-b border-outline-variant/30 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 lg:px-8 h-16 flex items-center justify-between gap-2">
          {/* Logo & Thương hiệu */}
          <Link to="/" className="flex items-center gap-2.5 shrink-0">
            <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-primary-on font-bold shadow-md shadow-primary/20">
              <Building2 className="w-5 h-5 text-white" />
            </div>
            <div className="flex flex-col">
              <span className="font-extrabold text-base tracking-tight text-primary leading-none">
                NHÀ ĐẤT CHUẨN
              </span>
              <span className="text-[10px] text-emerald-800 font-bold uppercase tracking-wider mt-0.5">
                Minh bạch 2026
              </span>
            </div>
          </Link>

          {/* Menu chính */}
          <nav className="hidden xl:flex items-center gap-1 xl:gap-2 text-sm font-medium text-on-surface-variant">
            <Link
              to="/"
              className="px-3 py-2 rounded-lg hover:text-primary hover:bg-surface-container transition-colors whitespace-nowrap"
            >
              Khám phá
            </Link>

            <Link
              to="/search"
              className="px-3 py-2 rounded-lg text-primary hover:bg-primary/10 transition-colors flex items-center gap-1.5 font-semibold whitespace-nowrap"
            >
              <span className="text-sm">🗺️</span>
              Tìm kiếm & Bản đồ
            </Link>

            <Link
              to="/compare"
              className="px-3 py-2 rounded-lg hover:text-primary hover:bg-surface-container transition-colors whitespace-nowrap"
            >
              So sánh BĐS
            </Link>

            {/* Chỉ hiển thị cho vai trò Môi giới Pro hoặc Admin */}
            {isBroker && (
              <Link
                to="/broker/workspace"
                className="px-3 py-1.5 rounded-lg bg-blue-50 text-blue-800 hover:bg-blue-100 transition-colors flex items-center gap-1.5 font-semibold text-xs border border-blue-200/60 whitespace-nowrap"
              >
                <Briefcase className="w-3.5 h-3.5 text-blue-600" />
                Môi giới Pro
              </Link>
            )}

            {/* CHỈ HIỂN THỊ KHI ĐĂNG NHẬP VAI TRÒ ADMIN HOẶC MODERATOR (FR02 / NFR12) */}
            {isAdminOrModerator && (
              <div
                className="relative"
                onMouseEnter={() => setIsAdminOpen(true)}
                onMouseLeave={() => setIsAdminOpen(false)}
              >
                <button
                  type="button"
                  onClick={() => setIsAdminOpen(!isAdminOpen)}
                  className="px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-900 hover:bg-emerald-100 transition-all flex items-center gap-1.5 font-semibold text-xs border border-emerald-200 shadow-sm whitespace-nowrap"
                >
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                  <span>Bàn Quản trị & Vận hành</span>
                  <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 ${isAdminOpen ? 'rotate-180' : ''}`} />
                </button>

                {isAdminOpen && (
                  <div className="absolute left-0 mt-1 w-80 bg-surface rounded-2xl shadow-2xl border border-outline-variant/40 p-2 z-50 animate-in fade-in slide-in-from-top-2 duration-150 backdrop-blur-xl">
                    <div className="px-3 py-2 border-b border-outline-variant/20 mb-1 flex items-center justify-between">
                      <span className="text-[11px] font-bold uppercase tracking-wider text-slate-700">
                        Phân Hệ Quản Trị
                      </span>
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-semibold">
                        Đã xác thực
                      </span>
                    </div>

                    <div className="flex flex-col gap-0.5">
                      <Link
                        to="/admin/moderation"
                        onClick={() => setIsAdminOpen(false)}
                        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-primary/10 transition-colors group"
                      >
                        <div className="w-7 h-7 rounded-lg bg-primary/10 flex items-center justify-center text-primary group-hover:scale-105 transition-transform">
                          <FileCheck2 className="w-4 h-4" />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-xs font-semibold text-on-surface group-hover:text-primary whitespace-nowrap">
                            Bàn kiểm duyệt tin đăng
                          </span>
                          <span className="text-[10px] text-on-surface-variant">
                            So sánh Diff ContentRevision, SLA 8h
                          </span>
                        </div>
                      </Link>

                      <Link
                        to="/admin/verification"
                        onClick={() => setIsAdminOpen(false)}
                        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-emerald-50 transition-colors group"
                      >
                        <div className="w-7 h-7 rounded-lg bg-emerald-100 flex items-center justify-center text-emerald-700 group-hover:scale-105 transition-transform">
                          <UserCheck className="w-4 h-4" />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-xs font-semibold text-emerald-900 group-hover:text-emerald-700 whitespace-nowrap">
                            Bàn Thẩm định eKYC Chính chủ
                          </span>
                          <span className="text-[10px] text-emerald-700/80">
                            Đối soát CCCD & Sổ hồng cấp nhãn
                          </span>
                        </div>
                      </Link>

                      <Link
                        to="/admin/leads-and-reports"
                        onClick={() => setIsAdminOpen(false)}
                        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-rose-50 transition-colors group"
                      >
                        <div className="w-7 h-7 rounded-lg bg-rose-100 flex items-center justify-center text-rose-700 group-hover:scale-105 transition-transform">
                          <ShieldAlert className="w-4 h-4" />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-xs font-semibold text-rose-900 group-hover:text-rose-700 whitespace-nowrap">
                            Bàn Lead CRM & Báo xấu
                          </span>
                          <span className="text-[10px] text-rose-700/80">
                            Điều phối lead, xử lý vi phạm SLA 24h
                          </span>
                        </div>
                      </Link>

                      <Link
                        to="/admin/analytics"
                        onClick={() => setIsAdminOpen(false)}
                        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-purple-50 transition-colors group"
                      >
                        <div className="w-7 h-7 rounded-lg bg-purple-100 flex items-center justify-center text-purple-700 group-hover:scale-105 transition-transform">
                          <BarChart3 className="w-4 h-4" />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-xs font-semibold text-purple-900 group-hover:text-purple-700 whitespace-nowrap">
                            Báo cáo phễu chuyển đổi
                          </span>
                          <span className="text-[10px] text-purple-700/80">
                            Analytics 5 tầng & Hiệu suất Môi giới
                          </span>
                        </div>
                      </Link>

                      <Link
                        to="/admin/projects"
                        onClick={() => setIsAdminOpen(false)}
                        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-amber-50 transition-colors group"
                      >
                        <div className="w-7 h-7 rounded-lg bg-amber-100 flex items-center justify-center text-amber-800 group-hover:scale-105 transition-transform">
                          <Layers className="w-4 h-4" />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-xs font-semibold text-amber-950 group-hover:text-amber-800 whitespace-nowrap">
                            Quản lý Dự án BĐS Master
                          </span>
                          <span className="text-[10px] text-amber-800/80">
                            Hồ sơ 1/500, Giỏ căn hộ, Ưu đãi
                          </span>
                        </div>
                      </Link>

                      <Link
                        to="/admin/cms"
                        onClick={() => setIsAdminOpen(false)}
                        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-indigo-50 transition-colors group"
                      >
                        <div className="w-7 h-7 rounded-lg bg-indigo-100 flex items-center justify-center text-indigo-700 group-hover:scale-105 transition-transform">
                          <FileText className="w-4 h-4" />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-xs font-semibold text-indigo-950 group-hover:text-indigo-700 whitespace-nowrap">
                            Quản trị CMS Bài viết
                          </span>
                          <span className="text-[10px] text-indigo-700/80">
                            Pháp lý, cẩm nang, xuất bản Clean HTML
                          </span>
                        </div>
                      </Link>

                      <div className="my-1 border-t border-outline-variant/20"></div>

                      <a
                        href="http://localhost:8080/swagger-ui/index.html"
                        target="_blank"
                        rel="noreferrer"
                        className="flex items-center justify-between px-3 py-2 rounded-xl bg-slate-50 hover:bg-slate-100 transition-colors text-slate-800"
                      >
                        <div className="flex items-center gap-2">
                          <span className="text-xs">⚡</span>
                          <span className="text-xs font-semibold">Tài liệu API Swagger 3.0</span>
                        </div>
                        <ExternalLink className="w-3.5 h-3.5 text-slate-500" />
                      </a>
                    </div>
                  </div>
                )}
              </div>
            )}
          </nav>

          {/* Hành động người dùng & Đăng nhập */}
          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            <button ref={menuButtonRef} type="button" aria-label="Mở menu chính" aria-expanded={isMobileOpen} onClick={() => setIsMobileOpen(true)} className="order-2 xl:hidden min-w-11 min-h-11 rounded-lg grid place-items-center border border-outline-variant hover:bg-surface-container focus-visible:ring-2 focus-visible:ring-primary"><Menu className="w-5 h-5" /></button>
            {isAuthenticated ? (
              <Link to="/listings/new">
                <Button
                  variant="outline"
                  size="sm"
                  leftIcon={<PlusCircle className="w-4 h-4 text-primary" />}
                  className="hidden sm:inline-flex whitespace-nowrap"
                >
                  Đăng tin
                </Button>
              </Link>
            ) : (
              <Button
                variant="outline"
                size="sm"
                leftIcon={<PlusCircle className="w-4 h-4 text-primary" />}
                className="hidden sm:inline-flex whitespace-nowrap"
                onClick={() => setIsLoginModalOpen(true)}
              >
                Đăng tin
              </Button>
            )}

            {!isAuthenticated ? (
              <Button
                variant="primary"
                size="sm"
                className="whitespace-nowrap shadow-sm max-sm:px-2"
                aria-label="Đăng nhập"
                leftIcon={<LogIn className="w-4 h-4" />}
                onClick={() => setIsLoginModalOpen(true)}
              >
                <span className="hidden sm:inline">Đăng nhập</span>
              </Button>
            ) : (
              <div ref={userMenuRef} className="relative pl-2 border-l border-outline-variant/30">
                <button type="button" aria-label="Mở menu tài khoản" aria-expanded={isUserMenuOpen} onClick={() => setIsUserMenuOpen((open) => !open)} className="flex items-center gap-2 px-2 py-1 rounded-xl hover:bg-surface-container focus-visible:ring-2 focus-visible:ring-primary">
                  <div className="w-8 h-8 overflow-hidden rounded-lg bg-primary/10 flex items-center justify-center text-primary font-bold text-xs">
                    {user?.avatarMediaUrl ? <img src={user.avatarMediaUrl} alt="" className="h-full w-full object-cover" /> : <UserIcon className="w-4 h-4" />}
                  </div>
                  <div className="hidden md:flex flex-col items-start text-left">
                    <span className="text-xs font-bold text-on-surface leading-tight whitespace-nowrap">
                      {user?.name}
                    </span>
                    <span className={`text-[10px] font-semibold px-1.5 py-0.2 rounded-full mt-0.5 ${
                      user?.role === 'ADMIN'
                        ? 'bg-emerald-100 text-emerald-800'
                        : user?.role === 'MODERATOR'
                        ? 'bg-primary/10 text-primary'
                        : user?.role === 'BROKER'
                        ? 'bg-blue-100 text-blue-800'
                        : 'bg-slate-100 text-slate-800'
                    }`}>
                      {user?.roleLabel}
                    </span>
                  </div>
                  <ChevronDown className={`hidden md:block w-3.5 h-3.5 text-on-surface-variant transition-transform ${isUserMenuOpen ? 'rotate-180' : ''}`} />
                </button>
                {isUserMenuOpen && (
                  <div className="absolute right-0 top-[calc(100%+0.5rem)] w-64 rounded-xl bg-surface p-2 shadow-xl border border-outline-variant/50 z-50">
                    <Link to="/account" onClick={() => setIsUserMenuOpen(false)} className="flex items-center gap-3 rounded-lg px-3 py-3 hover:bg-surface-container transition-colors">
                      <div className="grid h-9 w-9 place-items-center overflow-hidden rounded-lg bg-primary/10 text-primary">{user?.avatarMediaUrl ? <img src={user.avatarMediaUrl} alt="" className="h-full w-full object-cover" /> : <UserIcon className="w-4 h-4" />}</div>
                      <div className="min-w-0"><p className="truncate text-sm font-bold text-on-surface">{user?.name}</p><p className="truncate text-xs text-on-surface-variant">Thông tin cá nhân</p></div>
                    </Link>
                    <Link to="/kyc" onClick={() => setIsUserMenuOpen(false)} className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold text-on-surface hover:bg-surface-container"><UserCheck className="w-4 h-4 text-primary" />Xác minh eKYC</Link>
                    <div className="my-1 border-t border-outline-variant/40" />
                    <Link to="/my-listings" onClick={() => setIsUserMenuOpen(false)} className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold text-on-surface hover:bg-surface-container focus-visible:ring-2 focus-visible:ring-primary"><FileText className="w-4 h-4 text-primary" />Kho tin của tôi</Link>
                    <Link to="/billing" onClick={() => setIsUserMenuOpen(false)} className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold text-on-surface hover:bg-surface-container focus-visible:ring-2 focus-visible:ring-primary"><Layers className="w-4 h-4 text-primary" />Gói đăng tin</Link>
                    <Link to="/my-leads" onClick={() => setIsUserMenuOpen(false)} className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold text-on-surface hover:bg-surface-container focus-visible:ring-2 focus-visible:ring-primary"><Users className="w-4 h-4 text-primary" />Khách quan tâm</Link>
                    <div className="my-1 border-t border-outline-variant/40" />
                    <button type="button" onClick={() => { logout(); setIsUserMenuOpen(false); }} className="flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-sm font-semibold text-rose-700 hover:bg-rose-50 focus-visible:ring-2 focus-visible:ring-primary"><LogOut className="w-4 h-4" />Đăng xuất</button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </header>

      {isMobileOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 xl:hidden" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setIsMobileOpen(false); }}>
          <div ref={mobilePanelRef} role="dialog" aria-modal="true" aria-label="Menu chính" className="ml-auto h-full w-[min(22rem,88vw)] bg-surface p-5 shadow-2xl flex flex-col">
            <div className="flex items-center justify-between pb-4 border-b border-outline-variant"><strong className="text-lg">Điều hướng</strong><button type="button" aria-label="Đóng menu" onClick={() => setIsMobileOpen(false)} className="min-w-11 min-h-11 rounded-lg grid place-items-center hover:bg-surface-container"><X className="w-5 h-5" /></button></div>
            <nav className="py-5 flex flex-col gap-2 text-base" onClick={() => setIsMobileOpen(false)}>
              <Link to="/search" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><Search className="w-5 h-5" />Tìm kiếm</Link>
              <Link to="/compare" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><Layers className="w-5 h-5" />So sánh BĐS</Link>
              <Link to="/listings/new" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><PlusCircle className="w-5 h-5" />Đăng tin</Link>
              {isAuthenticated && <><Link to="/account" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><UserIcon className="w-5 h-5" />Thông tin cá nhân</Link><Link to="/kyc" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><UserCheck className="w-5 h-5" />Xác minh eKYC</Link></>}
              {isBroker && <Link to="/broker/workspace" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><Briefcase className="w-5 h-5" />Không gian môi giới</Link>}
              {isAdminOrModerator && <Link to="/admin/moderation" className="min-h-11 px-3 rounded-lg flex items-center gap-3 hover:bg-surface-container"><FileCheck2 className="w-5 h-5" />Bàn quản trị</Link>}
            </nav>
            <div className="mt-auto pt-4 border-t border-outline-variant">
              {isAuthenticated ? <button type="button" onClick={() => { logout(); setIsMobileOpen(false); }} className="w-full min-h-11 rounded-lg bg-surface-container font-semibold">Đăng xuất</button> : <button type="button" onClick={() => { setIsMobileOpen(false); setIsLoginModalOpen(true); }} className="w-full min-h-11 rounded-lg bg-primary text-white font-semibold">Đăng nhập / Đăng ký</button>}
            </div>
          </div>
        </div>
      )}

      {/* Main Content */}
      <main id="main-content" tabIndex={-1} className="flex-1">
        <Outlet />
      </main>

      {/* Login Modal */}
      <LoginModal />

      {/* Footer */}
      <footer className="bg-surface-container border-t border-outline-variant/40 pt-10 pb-8 text-xs text-on-surface-variant">
        <div className="max-w-6xl mx-auto px-4 md:px-8 flex flex-col md:flex-row justify-between gap-6">
          <div className="flex flex-col gap-2 max-w-sm">
            <span className="font-bold text-base text-primary">Nhà Đất Chuẩn</span>
            <p>
              Nền tảng đăng và tìm kiếm bất động sản có kiểm duyệt nội dung. Người dùng chủ động liên hệ,
              xác minh thông tin và trao đổi trực tiếp với nhau; chúng tôi không làm trung gian giao dịch.
            </p>
          </div>
          <div className="flex gap-8">
            <div className="flex flex-col gap-2">
              <span className="font-bold text-on-surface">Về chúng tôi</span>
              <Link to="/about" className="hover:underline">Giới thiệu</Link>
              <Link to="/terms" className="hover:underline">Điều khoản sử dụng</Link>
              <Link to="/privacy" className="hover:underline">Chính sách quyền riêng tư</Link>
            </div>
            <div className="flex flex-col gap-2">
              <span className="font-bold text-on-surface">Hỗ trợ</span>
              <Link to="/contact" className="hover:underline">Liên hệ tư vấn</Link>
              <Link to="/search" className="hover:underline">Báo cáo tin vi phạm</Link>
            </div>
          </div>
        </div>
        <div className="max-w-6xl mx-auto px-4 md:px-8 mt-8 pt-4 border-t border-outline-variant/20 text-center text-[11px] text-slate-700">
          © 2026 Nhà Đất Chuẩn. Bảo lưu mọi quyền.
        </div>
      </footer>
    </div>
  );
};

export const RootLayout: React.FC = () => {
  return (
    <AuthProvider>
      <RootLayoutContent />
    </AuthProvider>
  );
};
