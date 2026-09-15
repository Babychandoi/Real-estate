import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { BarChart3, ChevronLeft, FileCheck2, FileText, FolderKanban, LogOut, Menu, ShieldCheck, UserCheck, Users, X } from 'lucide-react';
import { AuthProvider, useAuth } from '@/shared/auth/AuthContext';
import './admin-light.css';

const navigation = [
  { to: '/admin/moderation', label: 'Kiểm duyệt tin', icon: FileCheck2 },
  { to: '/admin/verification', label: 'Thẩm định eKYC', icon: UserCheck },
  { to: '/admin/leads-and-reports', label: 'Lead & báo cáo', icon: Users },
  { to: '/admin/analytics', label: 'Phân tích', icon: BarChart3 },
  { to: '/admin/projects', label: 'Dự án BĐS', icon: FolderKanban },
  { to: '/admin/cms', label: 'Nội dung CMS', icon: FileText },
];

const AdminShellContent: React.FC = () => {
  const { user, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const close = () => setMobileOpen(false);
  const sideNav = <nav className="flex h-full flex-col bg-slate-950 px-3 py-4 text-slate-200" aria-label="Điều hướng quản trị">
    <Link to="/admin/moderation" onClick={close} className="mb-6 flex items-center gap-3 px-2 text-white"><span className="grid h-9 w-9 place-items-center rounded-lg bg-primary"><ShieldCheck className="h-5 w-5" /></span><span><span className="block text-sm font-bold leading-tight">Nhà Đất Chuẩn</span><span className="block text-[11px] text-slate-400">QUẢN TRỊ HỆ THỐNG</span></span></Link>
    <div className="space-y-1">{navigation.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} onClick={close} className={({ isActive }) => `flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold transition-colors ${isActive ? 'bg-primary text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}><Icon className="h-4 w-4" />{label}</NavLink>)}</div>
    <div className="mt-auto border-t border-slate-800 pt-3"><Link to="/" className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold text-slate-300 hover:bg-slate-800 hover:text-white"><ChevronLeft className="h-4 w-4" />Về trang công khai</Link></div>
  </nav>;
  return <div className="admin-shell min-h-dvh bg-slate-100">
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:block">{sideNav}</aside>
    {mobileOpen && <div className="fixed inset-0 z-50 bg-slate-950/45 lg:hidden" onMouseDown={(event) => { if (event.target === event.currentTarget) close(); }}><aside className="h-full w-72 shadow-2xl">{sideNav}<button type="button" onClick={close} aria-label="Đóng menu quản trị" className="absolute left-60 top-3 grid h-9 w-9 place-items-center rounded-lg bg-slate-800 text-white"><X className="h-5 w-5" /></button></aside></div>}
    <div className="lg:pl-64"><header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-slate-200 bg-white px-4 sm:px-6"><div className="flex items-center gap-3"><button type="button" onClick={() => setMobileOpen(true)} aria-label="Mở menu quản trị" className="grid h-10 w-10 place-items-center rounded-lg text-slate-700 hover:bg-slate-100 lg:hidden"><Menu className="h-5 w-5" /></button><div><h1 className="text-sm font-bold text-slate-950">Quản trị & vận hành</h1><p className="hidden text-xs text-slate-500 sm:block">Khu vực nội bộ được phân quyền</p></div></div><div className="flex items-center gap-3"><span className="hidden text-right sm:block"><span className="block text-sm font-semibold text-slate-900">{user?.name}</span><span className="block text-xs text-slate-500">{user?.roleLabel}</span></span><span className="grid h-9 w-9 place-items-center rounded-full bg-slate-100 text-sm font-bold text-primary">{user?.avatarInitial}</span><button type="button" onClick={() => { logout(); navigate('/'); }} className="grid h-10 w-10 place-items-center rounded-lg text-slate-600 hover:bg-rose-50 hover:text-rose-700" aria-label="Đăng xuất"><LogOut className="h-5 w-5" /></button></div></header><main className="min-w-0"><Outlet /></main></div>
  </div>;
};

export const AdminShell: React.FC = () => <AuthProvider><AdminShellContent /></AuthProvider>;
export const AdminLoginShell: React.FC<{ children: React.ReactNode }> = ({ children }) => <AuthProvider>{children}</AuthProvider>;
