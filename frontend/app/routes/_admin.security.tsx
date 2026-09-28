import { useAuth } from '@/shared/auth/AuthContext';
import { AccountSecuritySections } from '@/features/account-security/AccountSecuritySections';

/** Staff account security (UI-17): second factor, devices, password, recent security events. */
export function AdminSecurityPage() {
  const { user } = useAuth();
  return (
    <div className="max-w-4xl" data-ready="true">
      <header>
        <h1 className="text-2xl font-bold">Bảo mật tài khoản</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          {user?.email ? `${user.email} · ` : ''}Phiên quản trị hết hạn sau 8 giờ và tự đăng xuất sau 30 phút không hoạt
          động. Mất điện thoại xác thực? Nhờ một quản trị viên khác đặt lại tại Quản lý người dùng.
        </p>
      </header>
      <AccountSecuritySections staff />
    </div>
  );
}

export default AdminSecurityPage;
