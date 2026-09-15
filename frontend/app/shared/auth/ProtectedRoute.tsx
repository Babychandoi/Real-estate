import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { ShieldAlert, LogIn, ArrowLeft } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import type { UserRole } from './AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: UserRole[];
  moduleName?: string;
  loginPath?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  allowedRoles = ['ADMIN', 'MODERATOR'],
  moduleName = 'Phân hệ Nội bộ',
  loginPath,
}) => {
  const { user, isAuthenticated, setIsLoginModalOpen } = useAuth();

  const hasAccess = isAuthenticated && user && allowedRoles.includes(user.role);

  if (!hasAccess) {
    // Phân biệt: chưa đăng nhập vs đã đăng nhập nhưng không đủ quyền
    const isLoggedInButNoPermission = isAuthenticated && user;

    return (
      <div className="min-h-[70vh] flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-surface rounded-2xl shadow-xl border border-outline-variant/40 p-8 text-center flex flex-col items-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-center text-rose-600 shadow-inner">
            <ShieldAlert className="w-8 h-8" />
          </div>

          <div>
            <span className="inline-block px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-800 font-bold text-[11px] uppercase tracking-wider mb-2">
              {isLoggedInButNoPermission ? 'Không đủ quyền' : 'Yêu cầu đăng nhập'}
            </span>
            <h2 className="text-xl font-bold text-on-surface">
              {isLoggedInButNoPermission
                ? 'Quyền truy cập bị giới hạn'
                : 'Vui lòng đăng nhập'}
            </h2>
            <p className="text-xs text-on-surface-variant mt-2 leading-relaxed">
              {isLoggedInButNoPermission ? (
                <>
                  Tài khoản <strong className="text-on-surface">{user.email}</strong> không có quyền
                  truy cập phân hệ <strong className="text-on-surface">"{moduleName}"</strong>.
                  Vui lòng liên hệ quản trị viên hệ thống để được cấp quyền.
                </>
              ) : (
                <>
                  Bạn cần đăng nhập để truy cập phân hệ{' '}
                  <strong className="text-on-surface">"{moduleName}"</strong>.
                </>
              )}
            </p>
          </div>

          {/* Thông tin phiên - KHÔNG lộ danh sách vai trò cho phép */}
          <div className="w-full bg-surface-container/60 rounded-xl p-3 text-xs text-left border border-outline-variant/30 flex flex-col gap-1 text-on-surface-variant">
            <div className="flex justify-between">
              <span>Trạng thái phiên:</span>
              <strong className={isAuthenticated ? 'text-blue-600' : 'text-rose-600'}>
                {isAuthenticated ? `Đã đăng nhập` : 'Chưa đăng nhập'}
              </strong>
            </div>
            {isLoggedInButNoPermission && (
              <div className="flex justify-between">
                <span>Tài khoản:</span>
                <strong className="text-on-surface">{user.email}</strong>
              </div>
            )}
          </div>

          <div className="flex flex-col sm:flex-row items-center gap-3 w-full pt-2">
            <Link to="/" className="w-full sm:w-1/2">
              <Button variant="outline" size="sm" className="w-full" leftIcon={<ArrowLeft className="w-4 h-4" />}>
                Về Trang chủ
              </Button>
            </Link>
            {isLoggedInButNoPermission ? (
              <Link to="/" className="w-full sm:w-1/2">
                <Button
                  variant="primary"
                  size="sm"
                  className="w-full shadow-md"
                >
                  Liên hệ hỗ trợ
                </Button>
              </Link>
            ) : loginPath ? (
              <Link to={loginPath} className="w-full sm:w-1/2">
                <Button variant="primary" size="sm" className="w-full" leftIcon={<LogIn className="w-4 h-4" />}>Đăng nhập quản trị</Button>
              </Link>
            ) : (
              <Button
                variant="primary"
                size="sm"
                className="w-full sm:w-1/2 bg-rose-600 hover:bg-rose-700 text-white border-none shadow-md shadow-rose-600/20"
                leftIcon={<LogIn className="w-4 h-4" />}
                onClick={() => setIsLoginModalOpen(true)}
              >
                Đăng nhập ngay
              </Button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
};
