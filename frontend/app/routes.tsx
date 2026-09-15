import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { RootLayout } from './root';
import { ProtectedRoute } from '@/shared/auth/ProtectedRoute';
import { AdminLoginShell, AdminShell } from '@/shared/admin/AdminShell';

const HomePage = lazy(() => import('./routes/_public.home').then(m => ({ default: m.HomePage })));
const SearchAndMapPage = lazy(() => import('./routes/_public.search').then(m => ({ default: m.SearchAndMapPage })));
const ListingDetailPage = lazy(() => import('./routes/_public.listings.$listingId').then(m => ({ default: m.ListingDetailPage })));
const CreateListingPage = lazy(() => import('./routes/_public.listings.new').then(m => ({ default: m.CreateListingPage })));
const MyListingsPage = lazy(() => import('./routes/_account.listings').then(m => ({ default: m.MyListingsPage })));
const ModerationWorkspacePage = lazy(() => import('./routes/_admin.moderation'));
const AdminListingsPage = lazy(() => import('./routes/_admin.listings').then(m => ({ default: m.AdminListingsPage })));
const LeadsAndReportsPage = lazy(() => import('./routes/_admin.leads-and-reports'));
const VerificationDeskPage = lazy(() => import('./routes/_admin.verification'));
const BrokerWorkspacePage = lazy(() => import('./routes/_account.broker-workspace').then(m => ({ default: m.BrokerWorkspacePage })));
const PropertyComparePage = lazy(() => import('./routes/_public.compare').then(m => ({ default: m.PropertyComparePage })));
const ProductAnalyticsPage = lazy(() => import('./routes/_admin.analytics').then(m => ({ default: m.ProductAnalyticsPage })));
const ProjectCatalogPage = lazy(() => import('./routes/_admin.projects').then(m => ({ default: m.ProjectCatalogPage })));
const CmsManagementPage = lazy(() => import('./routes/_admin.cms').then(m => ({ default: m.CmsManagementPage })));
const BillingPage = lazy(() => import('./routes/_account.billing').then(m => ({ default: m.BillingPage })));
const AdminBillingPage = lazy(() => import('./routes/_admin.billing').then(m => ({ default: m.AdminBillingPage })));
const MyLeadsPage = lazy(() => import('./routes/_account.leads').then(m => ({ default: m.MyLeadsPage })));
const KycPage = lazy(() => import('./routes/_account.kyc').then(m => ({ default: m.KycPage })));
const AccountProfilePage = lazy(() => import('./routes/_account.profile').then(m => ({ default: m.AccountProfilePage })));
const VerifyEmailPage = lazy(() => import('./routes/_public.verify-email').then(m => ({ default: m.VerifyEmailPage })));
const ForgotPasswordPage = lazy(() => import('./routes/_public.forgot-password').then(m => ({ default: m.ForgotPasswordPage })));
const ResetPasswordPage = lazy(() => import('./routes/_public.reset-password').then(m => ({ default: m.ResetPasswordPage })));
const AdminLoginPage = lazy(() => import('./routes/_admin.login').then(m => ({ default: m.AdminLoginPage })));
const InformationPage = lazy(() => import('./routes/_public.information').then(m => ({ default: m.InformationPage })));
const NotFoundPage = lazy(() => import('./routes/_public.information').then(m => ({ default: m.NotFoundPage })));

const load = (node: ReactNode) => <Suspense fallback={<div className="max-w-6xl mx-auto p-8" role="status">Đang tải nội dung…</div>}>{node}</Suspense>;
const protect = (node: ReactNode, moduleName: string, allowedRoles: Array<'ADMIN'|'MODERATOR'|'BROKER'|'USER'>, adminLogin = false) => load(<ProtectedRoute moduleName={moduleName} allowedRoles={allowedRoles} loginPath={adminLogin ? '/2026/nhadatchua/admin/login' : undefined}>{node}</ProtectedRoute>);

export const router = createBrowserRouter([
  { path: '/', element: <RootLayout />, children: [
    { index: true, element: load(<HomePage />) }, { path: 'search', element: load(<SearchAndMapPage />) },
    { path: 'listings/new', element: protect(<CreateListingPage />, 'Đăng tin', ['ADMIN','BROKER','USER']) }, { path: 'listings/:listingId', element: load(<ListingDetailPage />) },
    { path: 'my-listings', element: protect(<MyListingsPage />, 'Kho tin của tôi', ['ADMIN','BROKER','USER']) }, { path: 'broker/workspace', element: protect(<BrokerWorkspacePage />, 'Không gian môi giới', ['ADMIN','BROKER']) },
    { path: 'compare', element: load(<PropertyComparePage />) }, { path: 'billing', element: protect(<BillingPage />, 'Gói đăng tin', ['ADMIN','BROKER','USER']) },
    { path: 'my-leads', element: protect(<MyLeadsPage />, 'Khách quan tâm', ['ADMIN','MODERATOR','BROKER','USER']) }, { path: 'kyc', element: protect(<KycPage />, 'Xác minh eKYC', ['ADMIN','MODERATOR','BROKER','USER']) },
    { path: 'account', element: protect(<AccountProfilePage />, 'Thông tin cá nhân', ['ADMIN','MODERATOR','BROKER','USER']) }, { path: 'verify-email', element: load(<VerifyEmailPage />) },
    { path: 'forgot-password', element: load(<ForgotPasswordPage />) }, { path: 'reset-password', element: load(<ResetPasswordPage />) },
    { path: 'about', element: load(<InformationPage />) }, { path: 'terms', element: load(<InformationPage />) }, { path: 'privacy', element: load(<InformationPage />) }, { path: 'contact', element: load(<InformationPage />) }, { path: '*', element: load(<NotFoundPage />) },
  ]},
  { path: '/2026/nhadatchua/admin/login', element: load(<AdminLoginShell><AdminLoginPage /></AdminLoginShell>) },
  { path: '/admin', element: <AdminShell />, children: [
    { path: 'moderation', element: protect(<ModerationWorkspacePage />, 'Bàn kiểm duyệt', ['ADMIN','MODERATOR'], true) },
    { path: 'listings', element: protect(<AdminListingsPage />, 'Quản lý tin', ['ADMIN'], true) },
    { path: 'leads-and-reports', element: protect(<LeadsAndReportsPage />, 'Lead và báo xấu', ['ADMIN','MODERATOR'], true) },
    { path: 'verification', element: protect(<VerificationDeskPage />, 'Thẩm định', ['ADMIN','MODERATOR'], true) },
    { path: 'billing', element: protect(<AdminBillingPage />, 'Đơn hàng và đối soát', ['ADMIN'], true) },
    { path: 'analytics', element: protect(<ProductAnalyticsPage />, 'Phân tích', ['ADMIN','MODERATOR'], true) },
    { path: 'projects', element: protect(<ProjectCatalogPage />, 'Danh mục dự án', ['ADMIN','MODERATOR'], true) },
    { path: 'cms', element: protect(<CmsManagementPage />, 'Quản trị nội dung', ['ADMIN','MODERATOR'], true) },
  ]},
]);
