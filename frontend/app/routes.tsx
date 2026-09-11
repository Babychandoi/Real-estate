import { createBrowserRouter } from 'react-router-dom';
import { RootLayout } from './root';
import { HomePage } from './routes/_public.home';
import { ListingDetailPage } from './routes/_public.listings.$listingId';
import { CreateListingPage } from './routes/_public.listings.new';
import { MyListingsPage } from './routes/_account.listings';
import ModerationWorkspacePage from './routes/_admin.moderation';
import { SearchAndMapPage } from './routes/_public.search';
import LeadsAndReportsPage from './routes/_admin.leads-and-reports';
import VerificationDeskPage from './routes/_admin.verification';
import { DepositContractPage } from './routes/_account.contracts';
import { BrokerWorkspacePage } from './routes/_account.broker-workspace';
import { PropertyComparePage } from './routes/_public.compare';
import { ProductAnalyticsPage } from './routes/_admin.analytics';
import { ProjectCatalogPage } from './routes/_admin.projects';
import { CmsManagementPage } from './routes/_admin.cms';
import { ProtectedRoute } from '@/shared/auth/ProtectedRoute';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'search',
        element: <SearchAndMapPage />,
      },
      {
        path: 'listings/new',
        element: <CreateListingPage />,
      },
      {
        path: 'listings/:listingId',
        element: <ListingDetailPage />,
      },
      {
        path: 'my-listings',
        element: (
          <ProtectedRoute moduleName="Kho Tin Của Tôi" allowedRoles={['ADMIN', 'BROKER', 'USER']}>
            <MyListingsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin/moderation',
        element: (
          <ProtectedRoute moduleName="Bàn Kiểm Duyệt Tin Đăng" allowedRoles={['ADMIN', 'MODERATOR']}>
            <ModerationWorkspacePage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin/leads-and-reports',
        element: (
          <ProtectedRoute moduleName="Bàn Lead CRM & Báo Xấu" allowedRoles={['ADMIN', 'MODERATOR']}>
            <LeadsAndReportsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin/verification',
        element: (
          <ProtectedRoute moduleName="Bàn Thẩm Định eKYC Chính Chủ" allowedRoles={['ADMIN', 'MODERATOR']}>
            <VerificationDeskPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin/analytics',
        element: (
          <ProtectedRoute moduleName="Báo Cáo Phễu Chuyển Đổi FR29" allowedRoles={['ADMIN', 'MODERATOR']}>
            <ProductAnalyticsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin/projects',
        element: (
          <ProtectedRoute moduleName="Quản Lý Danh Mục Dự Án Master" allowedRoles={['ADMIN', 'MODERATOR']}>
            <ProjectCatalogPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin/cms',
        element: (
          <ProtectedRoute moduleName="Quản Trị Xuất Bản CMS Bài Viết" allowedRoles={['ADMIN', 'MODERATOR']}>
            <CmsManagementPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'contracts/:contractId',
        element: <DepositContractPage />,
      },
      {
        path: 'contracts',
        element: <DepositContractPage />,
      },
      {
        path: 'broker/workspace',
        element: (
          <ProtectedRoute moduleName="Không Gian Môi Giới BĐS Pro" allowedRoles={['ADMIN', 'BROKER']}>
            <BrokerWorkspacePage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'compare',
        element: <PropertyComparePage />,
      },
      {
        path: '*',
        element: <HomePage />,
      },
    ],
  },
]);
