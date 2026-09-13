import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { RootLayout } from './root';
import { ProtectedRoute } from '@/shared/auth/ProtectedRoute';

const HomePage = lazy(() => import('./routes/_public.home').then(m => ({ default: m.HomePage })));
const SearchAndMapPage = lazy(() => import('./routes/_public.search').then(m => ({ default: m.SearchAndMapPage })));
const ListingDetailPage = lazy(() => import('./routes/_public.listings.$listingId').then(m => ({ default: m.ListingDetailPage })));
const CreateListingPage = lazy(() => import('./routes/_public.listings.new').then(m => ({ default: m.CreateListingPage })));
const MyListingsPage = lazy(() => import('./routes/_account.listings').then(m => ({ default: m.MyListingsPage })));
const ModerationWorkspacePage = lazy(() => import('./routes/_admin.moderation'));
const LeadsAndReportsPage = lazy(() => import('./routes/_admin.leads-and-reports'));
const VerificationDeskPage = lazy(() => import('./routes/_admin.verification'));
const BrokerWorkspacePage = lazy(() => import('./routes/_account.broker-workspace').then(m => ({ default: m.BrokerWorkspacePage })));
const PropertyComparePage = lazy(() => import('./routes/_public.compare').then(m => ({ default: m.PropertyComparePage })));
const ProductAnalyticsPage = lazy(() => import('./routes/_admin.analytics').then(m => ({ default: m.ProductAnalyticsPage })));
const ProjectCatalogPage = lazy(() => import('./routes/_admin.projects').then(m => ({ default: m.ProjectCatalogPage })));
const CmsManagementPage = lazy(() => import('./routes/_admin.cms').then(m => ({ default: m.CmsManagementPage })));
const BillingPage = lazy(() => import('./routes/_account.billing').then(m => ({ default: m.BillingPage })));
const VerifyEmailPage = lazy(() => import('./routes/_public.verify-email').then(m => ({ default: m.VerifyEmailPage })));

const load = (node: ReactNode) => <Suspense fallback={<div className="max-w-6xl mx-auto p-8" role="status">Đang tải nội dung…</div>}>{node}</Suspense>;
const protect = (node: ReactNode, moduleName: string, allowedRoles: Array<'ADMIN'|'MODERATOR'|'BROKER'|'USER'>) =>
  load(<ProtectedRoute moduleName={moduleName} allowedRoles={allowedRoles}>{node}</ProtectedRoute>);

export const router = createBrowserRouter([{ path: '/', element: <RootLayout />, children: [
  { index: true, element: load(<HomePage />) },
  { path: 'search', element: load(<SearchAndMapPage />) },
  { path: 'listings/new', element: protect(<CreateListingPage />, 'Đăng tin', ['ADMIN','BROKER','USER']) },
  { path: 'listings/:listingId', element: load(<ListingDetailPage />) },
  { path: 'my-listings', element: protect(<MyListingsPage />, 'Kho tin của tôi', ['ADMIN','BROKER','USER']) },
  { path: 'admin/moderation', element: protect(<ModerationWorkspacePage />, 'Bàn kiểm duyệt', ['ADMIN','MODERATOR']) },
  { path: 'admin/leads-and-reports', element: protect(<LeadsAndReportsPage />, 'Lead và báo xấu', ['ADMIN','MODERATOR']) },
  { path: 'admin/verification', element: protect(<VerificationDeskPage />, 'Thẩm định', ['ADMIN','MODERATOR']) },
  { path: 'admin/analytics', element: protect(<ProductAnalyticsPage />, 'Phân tích', ['ADMIN','MODERATOR']) },
  { path: 'admin/projects', element: protect(<ProjectCatalogPage />, 'Danh mục dự án', ['ADMIN','MODERATOR']) },
  { path: 'admin/cms', element: protect(<CmsManagementPage />, 'Quản trị nội dung', ['ADMIN','MODERATOR']) },
  { path: 'broker/workspace', element: protect(<BrokerWorkspacePage />, 'Không gian môi giới', ['ADMIN','BROKER']) },
  { path: 'compare', element: load(<PropertyComparePage />) },
  { path: 'billing', element: protect(<BillingPage />, 'Gói đăng tin', ['ADMIN','BROKER','USER']) },
  { path: 'verify-email', element: load(<VerifyEmailPage />) },
  { path: '*', element: load(<HomePage />) },
]}]);
