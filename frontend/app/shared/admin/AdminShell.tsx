import { useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  BarChart3,
  ChevronLeft,
  CreditCard,
  FileCheck2,
  FileText,
  FolderKanban,
  KeyRound,
  ListChecks,
  LogOut,
  Menu,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  Users,
  X,
} from 'lucide-react';
import { AuthProvider, useAuth } from '@/shared/auth/AuthContext';
import { canOpen, type ProtectedPage } from '@/shared/auth/routeAccess';
import { useModal } from '@/shared/ui/useModal';
import './admin-light.css';

const navigation: Array<{ to: string; label: string; icon: typeof FileCheck2; page: ProtectedPage }> = [
  { to: '/2026/nhadatchuan/admin/moderation', label: 'Kiểm duyệt tin', icon: FileCheck2, page: 'adminModeration' },
  { to: '/2026/nhadatchuan/admin/listings', label: 'Quản lý tất cả tin', icon: ListChecks, page: 'adminListings' },
  { to: '/2026/nhadatchuan/admin/users', label: 'Quản lý người dùng', icon: Users, page: 'adminUsers' },
  {
    to: '/2026/nhadatchuan/admin/verification',
    label: 'Giấy tờ tin đăng',
    icon: UserCheck,
    page: 'adminVerification',
  },
  { to: '/2026/nhadatchuan/admin/billing', label: 'Đơn hàng & đối soát', icon: CreditCard, page: 'adminBilling' },
  {
    to: '/2026/nhadatchuan/admin/leads-and-reports',
    label: 'Giám sát khách quan tâm',
    icon: Users,
    page: 'adminLeadsAndReports',
  },
  { to: '/2026/nhadatchuan/admin/reports', label: 'Báo cáo vi phạm', icon: ShieldAlert, page: 'adminReports' },
  { to: '/2026/nhadatchuan/admin/analytics', label: 'Phân tích', icon: BarChart3, page: 'adminAnalytics' },
  { to: '/2026/nhadatchuan/admin/projects', label: 'Dự án BĐS', icon: FolderKanban, page: 'adminProjects' },
  { to: '/2026/nhadatchuan/admin/cms', label: 'Nội dung CMS', icon: FileText, page: 'adminCms' },
  { to: '/2026/nhadatchuan/admin/security', label: 'Bảo mật tài khoản', icon: KeyRound, page: 'adminSecurity' },
];

const AdminShellContent: React.FC = () => {
  const { user, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const close = () => setMobileOpen(false);
  const mobilePanelRef = useRef<HTMLElement>(null);
  // Shared modal stack (M2): this drawer had no focus trap and no Escape handling at all.
  useModal({ open: mobileOpen, onClose: close, panelRef: mobilePanelRef });
  const visibleNavigation = navigation.filter((item) => canOpen(user?.role, item.page));
  const sideNav = (
    <nav className="ndc-admin-sidebar" aria-label="Điều hướng quản trị">
      <Link
        to="/2026/nhadatchuan/admin/moderation"
        onClick={close}
        className="mb-6 flex items-center gap-3 px-2 text-primary"
      >
        <span className="grid h-9 w-9 place-items-center rounded-lg bg-primary text-white">
          <ShieldCheck className="h-5 w-5" />
        </span>
        <span>
          <span className="block text-sm font-bold leading-tight">Nhà Đất Chuẩn</span>
          <span className="block text-xs text-on-surface-variant">QUẢN TRỊ HỆ THỐNG</span>
        </span>
      </Link>
      <div className="space-y-1">
        {visibleNavigation.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={close}
            className={({ isActive }) =>
              `flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold transition-colors ${isActive ? 'bg-primary text-white' : 'text-on-surface-variant hover:bg-surface-container-low hover:text-primary'}`
            }
          >
            <Icon className="h-4 w-4" />
            {label}
          </NavLink>
        ))}
      </div>
      <div className="mt-auto border-t border-outline-variant/40 pt-3">
        <Link
          to="/"
          className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold text-on-surface-variant hover:bg-surface-container-low hover:text-primary"
        >
          <ChevronLeft className="h-4 w-4" />
          Về trang công khai
        </Link>
      </div>
    </nav>
  );
  return (
    <div className="admin-shell min-h-dvh bg-surface">
      <a href="#admin-content" className="skip-link">
        Bỏ qua điều hướng
      </a>
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:block">{sideNav}</aside>
      {mobileOpen && (
        <div
          role="presentation"
          className="fixed inset-0 z-50 bg-slate-950/45 lg:hidden"
          // click (not mousedown): see shared/ui/Dialog.tsx for why mousedown races focus-return on close (m1).
          onClick={(event) => {
            if (event.target === event.currentTarget) close();
          }}
        >
          <aside
            ref={mobilePanelRef}
            tabIndex={-1}
            role="dialog"
            aria-modal="true"
            aria-label="Điều hướng quản trị"
            className="h-full w-72 shadow-2xl"
          >
            {sideNav}
            <button
              type="button"
              onClick={close}
              aria-label="Đóng menu quản trị"
              className="absolute left-60 top-3 grid h-9 w-9 place-items-center rounded-lg bg-slate-800 text-white"
            >
              <X className="h-5 w-5" />
            </button>
          </aside>
        </div>
      )}
      <div className="lg:pl-64">
        <header className="ndc-admin-header">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              aria-label="Mở menu quản trị"
              className="grid h-10 w-10 place-items-center rounded-lg text-slate-700 hover:bg-surface lg:hidden"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div>
              <p className="text-sm font-bold text-slate-950">Quản trị & vận hành</p>
              <p className="hidden text-xs text-slate-500 sm:block">Khu vực nội bộ được phân quyền</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden text-right sm:block">
              <span className="block text-sm font-semibold text-slate-900">{user?.name}</span>
              <span className="block text-xs text-slate-500">{user?.roleLabel}</span>
            </span>
            <span className="grid h-9 w-9 place-items-center rounded-full bg-surface text-sm font-bold text-primary">
              {user?.avatarInitial}
            </span>
            <button
              type="button"
              onClick={() => {
                logout();
                navigate('/');
              }}
              className="grid h-10 w-10 place-items-center rounded-lg text-primary hover:bg-rose-50 hover:text-rose-700"
              aria-label="Đăng xuất"
            >
              <LogOut className="h-5 w-5" />
            </button>
          </div>
        </header>
        <main id="admin-content" className="ndc-admin-page" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export const AdminShell: React.FC = () => (
  <AuthProvider>
    <AdminShellContent />
  </AuthProvider>
);
export const AdminLoginShell: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <AuthProvider>{children}</AuthProvider>
);
