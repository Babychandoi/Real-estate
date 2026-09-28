import { NavLink } from "react-router-dom";
import { useAuth } from "@/shared/auth/AuthContext";
export function AccountNavigation() {
  const { isBroker, user } = useAuth();
  const links = [
    { to: "/account", label: "Tài khoản" },
    ...(isBroker
      ? [
          { to: "/my-listings", label: "Tin đăng" },
          { to: "/my-leads", label: "Khách quan tâm" },
          { to: "/broker/workspace", label: "Không gian môi giới" },
          { to: "/billing", label: "Gói dịch vụ" },
        ]
      : []),
    ...(user?.role === "USER"
      ? [{ to: "/my-inquiries", label: "Yêu cầu đã gửi" }]
      : []),
    { to: "/kyc", label: "Xác minh danh tính" },
  ];
  return (
    <nav className="ndc-account-nav" aria-label="Tài khoản của bạn">
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          className={({ isActive }) =>
            `ndc-account-link ${isActive ? "is-active" : ""}`
          }
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
