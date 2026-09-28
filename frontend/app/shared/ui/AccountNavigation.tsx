import { NavLink } from 'react-router-dom';
import { useAuth } from '@/shared/auth/AuthContext';
import { canOpen } from '@/shared/auth/routeAccess';
export function AccountNavigation() {
  const { isPoster, user } = useAuth();
  const role = user?.role;
  const links = [
    { to: '/account', label: 'Tài khoản' },
    ...(isPoster
      ? [
          { to: '/my-listings', label: 'Tin đăng' },
          { to: '/my-leads', label: 'Hộp thư khách' },
        ]
      : []),
    ...(canOpen(role, 'brokerWorkspace') ? [{ to: '/broker/workspace', label: 'Không gian môi giới' }] : []),
    ...(canOpen(role, 'billing') ? [{ to: '/billing', label: 'Gói dịch vụ' }] : []),
    ...(canOpen(role, 'myInquiries') ? [{ to: '/my-inquiries', label: 'Yêu cầu đã gửi' }] : []),
    { to: '/kyc', label: 'Xác minh danh tính' },
  ];
  return (
    <nav className="ndc-account-nav" aria-label="Tài khoản của bạn">
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          className={({ isActive }) => `ndc-account-link ${isActive ? 'is-active' : ''}`}
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
