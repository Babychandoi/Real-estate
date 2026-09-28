import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { ShieldAlert, LogIn, ArrowLeft } from 'lucide-react';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { hasRole, SEEKERS, STAFF, type Role } from './roles';

interface ProtectedRouteProps {
  children: React.ReactNode;
  /** Capability list from `./roles` (POSTERS, STAFF, BROKER_WORKSPACE…). */
  allowedRoles?: readonly Role[];
  moduleName?: string;
  loginPath?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  allowedRoles = STAFF,
  moduleName = 'Phân hệ Nội bộ',
  loginPath,
}) => {
  const { user, isAuthenticated, isAuthLoading, setIsLoginModalOpen, sessionEnded } = useAuth();

  // A stored session is still being checked: do not flash "please log in" to a signed-in person.
  if (isAuthLoading) {
    return (
      <div className="min-h-[70vh] grid place-items-center p-6" role="status">
        <p className="text-sm text-on-surface-variant">Đang kiểm tra phiên đăng nhập…</p>
      </div>
    );
  }

  const hasAccess = isAuthenticated && user && hasRole(user.role, allowedRoles);

  if (!hasAccess) {
    // Phân biệt: chưa đăng nhập vs đã đăng nhập nhưng không đủ quyền
    const isLoggedInButNoPermission = isAuthenticated && user;

    if (isLoggedInButNoPermission) {
      return <Navigate to={hasRole(user.role, SEEKERS) ? '/my-inquiries' : '/'} replace />;
    }

    return (
      <div className="min-h-[70vh] flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-surface rounded-2xl shadow-xl border border-outline-variant/40 p-8 text-center flex flex-col items-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-center text-rose-600 shadow-inner">
            <ShieldAlert className="w-8 h-8" />
          </div>

          <div>
            <span className="inline-block px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-800 font-bold text-xs uppercase tracking-wider mb-2">
              {isLoggedInButNoPermission ? 'Không đủ quyền' : 'Yêu cầu đăng nhập'}
            </span>
            <h2 className="text-xl font-bold text-on-surface">
              {isLoggedInButNoPermission ? 'Quyền truy cập bị giới hạn' : 'Vui lòng đăng nhập'}
            </h2>
            <p className="text-xs text-on-surface-variant mt-2 leading-relaxed">
              {isLoggedInButNoPermission ? (
                <>
                  Tài khoản <strong className="text-on-surface">{user.email}</strong> không có quyền truy cập phân hệ{' '}
                  <strong className="text-on-surface">"{moduleName}"</strong>. Vui lòng liên hệ quản trị viên hệ thống
                  để được cấp quyền.
                </>
              ) : (
                <>
                  {sessionEnded && (
                    <span role="status" className="mb-2 block font-semibold text-on-surface">
                      Phiên đăng nhập đã kết thúc (không hoạt động quá lâu, hết hạn hoặc đã được đăng xuất ở thiết bị
                      khác).
                    </span>
                  )}
                  Bạn cần đăng nhập để truy cập phân hệ <strong className="text-on-surface">"{moduleName}"</strong>.
                </>
              )}
            </p>
          </div>

          {/* Thông tin phiên - KHÔNG lộ danh sách vai trò cho phép */}
          <div className="w-full bg-surface-container/60 rounded-xl p-3 text-xs text-left border border-outline-variant/30 flex flex-col gap-1 text-on-surface-variant">
            <div className="flex justify-between">
              <span>Trạng thái phiên:</span>
              <strong className={isAuthenticated ? 'text-blue-600' : 'text-rose-600'}>
                {isAuthenticated ? `Đã đăng nhập` : sessionEnded ? 'Đã kết thúc' : 'Chưa đăng nhập'}
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
            <ButtonLink
              to="/"
              variant="outline"
              size="sm"
              leftIcon={<ArrowLeft className="w-4 h-4" />}
              className="w-full sm:w-1/2 w-full"
            >
              Về Trang chủ
            </ButtonLink>
            {loginPath ? (
              <ButtonLink
                to={loginPath}
                variant="primary"
                size="sm"
                leftIcon={<LogIn className="w-4 h-4" />}
                className="w-full sm:w-1/2 w-full"
              >
                Đăng nhập quản trị
              </ButtonLink>
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
