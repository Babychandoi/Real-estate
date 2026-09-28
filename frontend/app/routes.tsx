import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { RootLayout } from './root';
import { ProtectedRoute } from '@/shared/auth/ProtectedRoute';
import { ROUTE_ACCESS } from '@/shared/auth/routeAccess';
import type { Role } from '@/shared/auth/roles';
import { AdminLoginShell, AdminShell } from '@/shared/admin/AdminShell';

/** The UI kit catalog exists only in dev builds or when VITE_ENABLE_UI_CATALOG=true (a11y suite in CI). */
export const UI_CATALOG_ENABLED = import.meta.env.DEV || import.meta.env.VITE_ENABLE_UI_CATALOG === 'true';
const UiCatalogPage = UI_CATALOG_ENABLED ? lazy(() => import('./routes/__ui')) : null;

const HomePage = lazy(() => import('./routes/_public.home').then((m) => ({ default: m.HomePage })));
const SellerProfilePage = lazy(() => import('./routes/_public.seller').then((m) => ({ default: m.SellerProfilePage })));
const SearchAndMapPage = lazy(() => import('./routes/_public.search').then((m) => ({ default: m.SearchAndMapPage })));
const ListingDetailPage = lazy(() =>
  import('./routes/_public.listings.$listingId').then((m) => ({ default: m.ListingDetailPage })),
);
const CreateListingPage = lazy(() =>
  import('./routes/_public.listings.new').then((m) => ({ default: m.CreateListingPage })),
);
const MyListingsPage = lazy(() => import('./routes/_account.listings').then((m) => ({ default: m.MyListingsPage })));
const ModerationWorkspacePage = lazy(() => import('./routes/_admin.moderation'));
const AdminListingsPage = lazy(() =>
  import('./routes/_admin.listings').then((m) => ({ default: m.AdminListingsPage })),
);
const AdminUsersPage = lazy(() => import('./routes/_admin.users').then((m) => ({ default: m.AdminUsersPage })));
const LeadsAndReportsPage = lazy(() => import('./routes/_admin.leads-and-reports'));
const VerificationDeskPage = lazy(() => import('./routes/_admin.verification'));
const BrokerWorkspacePage = lazy(() =>
  import('./routes/_account.broker-workspace').then((m) => ({ default: m.BrokerWorkspacePage })),
);
const PropertyComparePage = lazy(() =>
  import('./routes/_public.compare').then((m) => ({ default: m.PropertyComparePage })),
);
const ProductAnalyticsPage = lazy(() =>
  import('./routes/_admin.analytics').then((m) => ({ default: m.ProductAnalyticsPage })),
);
const ProjectCatalogPage = lazy(() =>
  import('./routes/_admin.projects').then((m) => ({ default: m.ProjectCatalogPage })),
);
const CmsManagementPage = lazy(() => import('./routes/_admin.cms').then((m) => ({ default: m.CmsManagementPage })));
const BillingPage = lazy(() => import('./routes/_account.billing').then((m) => ({ default: m.BillingPage })));
const AdminBillingPage = lazy(() => import('./routes/_admin.billing').then((m) => ({ default: m.AdminBillingPage })));
const MyLeadsPage = lazy(() => import('./routes/_account.leads').then((m) => ({ default: m.MyLeadsPage })));
const MyInquiriesPage = lazy(() => import('./routes/_account.inquiries').then((m) => ({ default: m.MyInquiriesPage })));
const KycPage = lazy(() => import('./routes/_account.kyc').then((m) => ({ default: m.KycPage })));
const AccountProfilePage = lazy(() =>
  import('./routes/_account.profile').then((m) => ({ default: m.AccountProfilePage })),
);
const VerifyEmailPage = lazy(() =>
  import('./routes/_public.verify-email').then((m) => ({ default: m.VerifyEmailPage })),
);
const ForgotPasswordPage = lazy(() =>
  import('./routes/_public.forgot-password').then((m) => ({ default: m.ForgotPasswordPage })),
);
const ResetPasswordPage = lazy(() =>
  import('./routes/_public.reset-password').then((m) => ({ default: m.ResetPasswordPage })),
);
const AdminLoginPage = lazy(() => import('./routes/_admin.login').then((m) => ({ default: m.AdminLoginPage })));
const InformationPage = lazy(() =>
  import('./routes/_public.information').then((m) => ({ default: m.InformationPage })),
);
const NotFoundPage = lazy(() => import('./routes/_public.information').then((m) => ({ default: m.NotFoundPage })));

const load = (node: ReactNode) => (
  <Suspense
    fallback={
      <div className="max-w-6xl mx-auto p-8" role="status">
        Đang tải nội dung…
      </div>
    }
  >
    {node}
  </Suspense>
);
const protect = (node: ReactNode, moduleName: string, allowedRoles: readonly Role[], adminLogin = false) =>
  load(
    <ProtectedRoute
      moduleName={moduleName}
      allowedRoles={allowedRoles}
      loginPath={adminLogin ? '/2026/nhadatchuan/admin/login' : undefined}
    >
      {node}
    </ProtectedRoute>,
  );

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: load(<HomePage />) },
      { path: 'search', element: load(<SearchAndMapPage />) },
      { path: 'listings/new', element: protect(<CreateListingPage />, 'Đăng tin', ROUTE_ACCESS.createListing) },
      { path: 'listings/:listingId', element: load(<ListingDetailPage />) },
      { path: 'my-listings', element: protect(<MyListingsPage />, 'Kho tin của tôi', ROUTE_ACCESS.myListings) },
      {
        path: 'broker/workspace',
        element: protect(<BrokerWorkspacePage />, 'Không gian môi giới', ROUTE_ACCESS.brokerWorkspace),
      },
      { path: 'compare', element: load(<PropertyComparePage />) },
      { path: 'nguoi-dang/:sellerId', element: load(<SellerProfilePage />) },
      { path: 'billing', element: protect(<BillingPage />, 'Gói đăng tin', ROUTE_ACCESS.billing) },
      { path: 'my-leads', element: protect(<MyLeadsPage />, 'Khách quan tâm', ROUTE_ACCESS.myLeads) },
      { path: 'my-inquiries', element: protect(<MyInquiriesPage />, 'Tin đã liên hệ', ROUTE_ACCESS.myInquiries) },
      { path: 'kyc', element: protect(<KycPage />, 'Xác minh eKYC', ROUTE_ACCESS.kyc) },
      {
        path: 'account',
        element: protect(<AccountProfilePage />, 'Thông tin cá nhân', ROUTE_ACCESS.account),
      },
      { path: 'verify-email', element: load(<VerifyEmailPage />) },
      { path: 'forgot-password', element: load(<ForgotPasswordPage />) },
      { path: 'reset-password', element: load(<ResetPasswordPage />) },
      { path: 'about', element: load(<InformationPage />) },
      { path: 'terms', element: load(<InformationPage />) },
      { path: 'privacy', element: load(<InformationPage />) },
      { path: 'contact', element: load(<InformationPage />) },
      { path: '*', element: load(<NotFoundPage />) },
    ],
  },
  {
    path: '/2026/nhadatchuan/admin/login',
    element: load(
      <AdminLoginShell>
        <AdminLoginPage />
      </AdminLoginShell>,
    ),
  },
  {
    path: '/2026/nhadatchuan/admin',
    element: <AdminShell />,
    children: [
      {
        path: 'moderation',
        element: protect(<ModerationWorkspacePage />, 'Bàn kiểm duyệt', ROUTE_ACCESS.adminModeration, true),
      },
      { path: 'listings', element: protect(<AdminListingsPage />, 'Quản lý tin', ROUTE_ACCESS.adminListings, true) },
      { path: 'users', element: protect(<AdminUsersPage />, 'Quản lý người dùng', ROUTE_ACCESS.adminUsers, true) },
      {
        path: 'leads-and-reports',
        element: protect(<LeadsAndReportsPage />, 'Lead và báo xấu', ROUTE_ACCESS.adminLeadsAndReports, true),
      },
      {
        path: 'verification',
        element: protect(<VerificationDeskPage />, 'Thẩm định', ROUTE_ACCESS.adminVerification, true),
      },
      {
        path: 'billing',
        element: protect(<AdminBillingPage />, 'Đơn hàng và đối soát', ROUTE_ACCESS.adminBilling, true),
      },
      { path: 'analytics', element: protect(<ProductAnalyticsPage />, 'Phân tích', ROUTE_ACCESS.adminAnalytics, true) },
      {
        path: 'projects',
        element: protect(<ProjectCatalogPage />, 'Danh mục dự án', ROUTE_ACCESS.adminProjects, true),
      },
      { path: 'cms', element: protect(<CmsManagementPage />, 'Quản trị nội dung', ROUTE_ACCESS.adminCms, true) },
    ],
  },
  ...(UiCatalogPage ? [{ path: '/__ui', element: load(<UiCatalogPage />) }] : []),
  { path: '/admin/*', element: <Navigate to="/2026/nhadatchuan/admin/moderation" replace /> },
  { path: '/2026/nhadatchua/admin/*', element: <Navigate to="/2026/nhadatchuan/admin/login" replace /> },
]);
