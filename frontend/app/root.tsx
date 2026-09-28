import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { Building2, ChevronDown, LogIn, FileText, History, LogOut, Menu, Plus, UserCheck, Users } from 'lucide-react';
import { CompareTray } from '@/features/compare/CompareControls';
import { AuthProvider, useAuth } from '@/shared/auth/AuthContext';
import { LoginModal } from '@/shared/auth/LoginModal';
import { Avatar } from '@/shared/ui/Avatar';
import { Dialog } from '@/shared/ui/Dialog';
import { AccountNavigation } from '@/shared/ui/AccountNavigation';
import { ui } from '@/i18n/vi/ui';
import { canOpen } from '@/shared/auth/routeAccess';
import { ROLES } from '@/shared/auth/roles';
const accountPaths = new Set([
  '/account',
  '/my-listings',
  '/my-leads',
  '/my-inquiries',
  '/broker/workspace',
  '/billing',
  '/kyc',
  '/become-owner',
]);
/** Account dropdown in the header (UI design styling, audit role gating and labels). */
function AccountMenu() {
  const { user, isPoster, logout } = useAuth();
  const role = user?.role;
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);
  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  const item = 'ndc-nav-link w-full justify-start gap-3';
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-label="Mở menu tài khoản"
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-11 items-center gap-2 rounded-lg px-1.5 hover:bg-surface-container-low focus-visible:ring-2 focus-visible:ring-primary"
      >
        <Avatar name={user?.name || ''} src={user?.avatarMediaUrl} size="sm" />
        <span className="hidden max-w-28 truncate text-sm font-semibold xl:inline">{user?.name}</span>
        <ChevronDown
          className={`hidden h-4 w-4 text-on-surface-variant transition-transform sm:block ${open ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>
      {open && (
        <div
          className="absolute right-0 top-[calc(100%+0.5rem)] z-50 w-64 rounded-xl border bg-white p-2 shadow-xl"
          style={{ borderColor: 'var(--ndc-border)' }}
        >
          <Link to="/account" className="flex items-center gap-3 rounded-lg px-3 py-3 hover:bg-surface-container-low">
            <Avatar name={user?.name || ''} src={user?.avatarMediaUrl} size="md" />
            <span className="min-w-0">
              <span className="block truncate text-sm font-bold text-on-surface">{user?.name}</span>
              <span className="block truncate text-xs text-on-surface-variant">Thông tin cá nhân</span>
            </span>
          </Link>
          <Link to="/kyc" className={item}>
            <UserCheck className="h-4 w-4 text-primary" aria-hidden="true" />
            Xác minh eKYC
          </Link>
          <div className="my-1 border-t" style={{ borderColor: 'var(--ndc-border)' }} />
          {isPoster ? (
            <>
              <Link to="/my-listings" className={item}>
                <FileText className="h-4 w-4 text-primary" aria-hidden="true" />
                Kho tin của tôi
              </Link>
              <Link to="/my-leads" className={item}>
                <Users className="h-4 w-4 text-primary" aria-hidden="true" />
                Khách quan tâm
              </Link>
            </>
          ) : canOpen(role, 'myInquiries') ? (
            <Link to="/my-inquiries" className={item}>
              <History className="h-4 w-4 text-primary" aria-hidden="true" />
              Tin đã liên hệ
            </Link>
          ) : null}
          <div className="my-1 border-t" style={{ borderColor: 'var(--ndc-border)' }} />
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              logout();
            }}
            className={`${item} text-rose-700 hover:text-rose-700`}
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Đăng xuất
          </button>
        </div>
      )}
    </div>
  );
}
function RootLayoutContent() {
  const [menuOpen, setMenuOpen] = useState(false);
  const { user, isAuthenticated, isPoster, isAdminOrModerator, setIsLoginModalOpen, logout } = useAuth();
  const role = user?.role;
  // Role gating mirrors the route guards (routeAccess.ts): a menu never offers a page the guard then refuses.
  const location = useLocation();
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname, location.search]);
  const nav = [
    { to: '/search?purpose=SALE', label: ui.sale },
    { to: '/search?purpose=RENT', label: ui.rent },
    { to: '/compare', label: ui.compare },
  ];
  const selected = (to: string) => {
    const target = new URL(to, 'https://example.invalid');
    return (
      location.pathname === target.pathname &&
      (!target.search ||
        (new URLSearchParams(location.search).get('purpose') || 'SALE') === target.searchParams.get('purpose'))
    );
  };
  return (
    <div className="ndc-app">
      <a href="#main-content" className="skip-link">
        Bỏ qua điều hướng
      </a>
      {import.meta.env.MODE === 'ui-preview' && (
        <div className="bg-amber-100 px-4 py-2 text-center text-xs text-amber-900">
          Bản xem thử giao diện · Dữ liệu mô phỏng · Không có giao dịch thật
        </div>
      )}
      <header className="ndc-header">
        <div className="ndc-header-inner">
          <Link to="/" className="ndc-brand" aria-label="Nhà Đất Chuẩn — trang chủ">
            <span className="ndc-brand-symbol">
              <Building2 className="h-6 w-6" aria-hidden="true" />
            </span>
            <span>
              <strong>
                nhà đất chuẩn<span className="text-secondary">.</span>
              </strong>
              <small>Tìm nhà, rõ từng thông tin</small>
            </span>
          </Link>
          <nav className="hidden items-center gap-1 lg:flex" aria-label="Điều hướng chính">
            {nav.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                aria-current={selected(item.to) ? 'page' : undefined}
                className={`ndc-nav-link ${selected(item.to) ? 'is-active' : ''}`}
              >
                {item.label}
              </Link>
            ))}
            {canOpen(role, 'brokerWorkspace') && (
              <Link to="/broker/workspace" className="ndc-nav-link">
                Môi giới
              </Link>
            )}
            {isAdminOrModerator && (
              <Link to="/2026/nhadatchuan/admin/moderation" className="ndc-nav-link">
                Quản trị
              </Link>
            )}
          </nav>
          <div className="flex items-center gap-2">
            {!isAuthenticated ? (
              <button
                className="ndc-nav-link max-sm:px-2"
                type="button"
                aria-label="Đăng nhập"
                onClick={() => setIsLoginModalOpen(true)}
              >
                <LogIn className="h-5 w-5 sm:hidden" aria-hidden="true" />
                <span className="hidden sm:inline">Đăng nhập</span>
              </button>
            ) : (
              <AccountMenu />
            )}
            {isAuthenticated && isPoster ? (
              <Link to="/listings/new" className="ndc-primary-link max-sm:px-2.5" aria-label="Đăng tin">
                <Plus className="h-4 w-4" aria-hidden="true" />
                <span className="hidden sm:inline">Đăng tin</span>
              </Link>
            ) : isAuthenticated && role === ROLES.USER ? (
              <Link to="/become-owner" className="ndc-primary-link max-sm:px-2.5" aria-label="Đăng tin">
                <Plus className="h-4 w-4" aria-hidden="true" />
                <span className="hidden sm:inline">Đăng tin</span>
              </Link>
            ) : null}
            <button
              className="ndc-icon-button lg:hidden"
              type="button"
              aria-label="Mở menu"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen(true)}
            >
              <Menu aria-hidden="true" />
            </button>
            {isAuthenticated && (
              <button
                className="ndc-icon-button hidden lg:inline-flex"
                type="button"
                onClick={logout}
                aria-label="Đăng xuất"
              >
                <LogOut aria-hidden="true" />
              </button>
            )}
          </div>
        </div>
      </header>
      <Dialog open={menuOpen} onClose={() => setMenuOpen(false)} title="Khám phá Nhà Đất Chuẩn">
        <nav className="grid gap-1 p-5" aria-label="Điều hướng di động">
          <Link to="/" className="ndc-nav-link">
            Trang chủ
          </Link>
          {nav.map((item) => (
            <Link key={item.to} to={item.to} className="ndc-nav-link">
              {item.label}
            </Link>
          ))}
          {isAuthenticated && (
            <>
              <Link to="/account" className="ndc-nav-link">
                Tài khoản của tôi
              </Link>
              <Link to="/kyc" className="ndc-nav-link">
                Xác minh danh tính
              </Link>
            </>
          )}
          {isPoster && (
            <>
              <Link to="/my-listings" className="ndc-nav-link">
                Tin đăng của tôi
              </Link>
              <Link to="/my-leads" className="ndc-nav-link">
                Khách quan tâm
              </Link>
            </>
          )}
          {canOpen(role, 'brokerWorkspace') && (
            <Link to="/broker/workspace" className="ndc-nav-link">
              Không gian môi giới
            </Link>
          )}
          {canOpen(role, 'billing') && (
            <Link to="/billing" className="ndc-nav-link">
              Gói dịch vụ
            </Link>
          )}
          {isPoster && (
            <Link to="/listings/new" className="ndc-primary-link">
              Đăng tin mới
            </Link>
          )}
          {isAuthenticated && role === ROLES.USER && (
            <Link to="/become-owner" className="ndc-primary-link">
              Đăng tin với vai trò Chủ nhà
            </Link>
          )}
          {canOpen(role, 'myInquiries') && (
            <Link to="/my-inquiries" className="ndc-nav-link">
              Yêu cầu đã gửi
            </Link>
          )}
          {isAdminOrModerator && (
            <Link to="/2026/nhadatchuan/admin/moderation" className="ndc-nav-link">
              Quản trị
            </Link>
          )}
          <button
            type="button"
            className="ndc-nav-link mt-3 border-t"
            onClick={() => {
              setMenuOpen(false);
              if (isAuthenticated) logout();
              else setIsLoginModalOpen(true);
            }}
          >
            {isAuthenticated ? 'Đăng xuất' : 'Đăng nhập / Đăng ký'}
          </button>
        </nav>
      </Dialog>
      <main id="main-content" tabIndex={-1} className="min-w-0 flex-1">
        {isAuthenticated && accountPaths.has(location.pathname) && (
          <div className="border-b bg-white">
            <AccountNavigation />
          </div>
        )}
        <Outlet />
      </main>
      <LoginModal />
      <footer className="ndc-footer">
        <div className="ndc-page grid gap-8 py-10 md:grid-cols-[2fr_1fr_1fr]">
          <div>
            <Link to="/" className="text-xl font-bold text-primary">
              nhà đất chuẩn.
            </Link>
            <p className="mt-3 max-w-md text-sm leading-7 text-on-surface-variant">
              Kết nối người tìm nhà và người đăng tin qua thông tin rõ ràng. Hãy kiểm tra hiện trạng và pháp lý trước
              giao dịch.
            </p>
          </div>
          <nav className="grid content-start gap-3 text-sm" aria-label="Về nền tảng">
            <strong>Về Nhà Đất Chuẩn</strong>
            <Link to="/about">Giới thiệu</Link>
            <Link to="/terms">Điều khoản sử dụng</Link>
            <Link to="/privacy">Quyền riêng tư</Link>
          </nav>
          <nav className="grid content-start gap-3 text-sm" aria-label="Hỗ trợ">
            <strong>Đồng hành cùng bạn</strong>
            <Link to="/contact">Trung tâm hỗ trợ</Link>
            <Link to="/contact#report">Báo cáo tin vi phạm</Link>
            <Link to="/search">Tìm bất động sản</Link>
          </nav>
        </div>
        <div className="ndc-page border-t py-5 text-xs text-on-surface-variant">
          © {new Date().getFullYear()} Nhà Đất Chuẩn.
        </div>
      </footer>
      <CompareTray />
    </div>
  );
}
export function RootLayout() {
  return (
    <AuthProvider>
      <RootLayoutContent />
    </AuthProvider>
  );
}
