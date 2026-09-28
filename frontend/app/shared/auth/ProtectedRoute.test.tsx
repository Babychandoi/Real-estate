import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthContext';
import { ProtectedRoute } from './ProtectedRoute';
import { ROUTE_ACCESS } from './routeAccess';
import type { Role } from './roles';

function mockSession(role: Role) {
  sessionStorage.setItem('bds_access_token', 'test-token');
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return new Response(
          JSON.stringify({
            id: 'u-1',
            name: 'Tài khoản thử',
            email: 'thu@example.invalid',
            role,
            planCode: 'FREE',
            listingQuotaRemaining: 2,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      // Notification stream: close immediately.
      return new Response('', { status: 200 });
    }),
  );
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<p>Trang chủ</p>} />
          <Route path="/my-inquiries" element={<p>Tin đã liên hệ</p>} />
          <Route
            path="/listings/new"
            element={
              <ProtectedRoute moduleName="Đăng tin" allowedRoles={ROUTE_ACCESS.createListing}>
                <p>Trang đăng tin</p>
              </ProtectedRoute>
            }
          />
          <Route
            path="/my-leads"
            element={
              <ProtectedRoute moduleName="Khách quan tâm" allowedRoles={ROUTE_ACCESS.myLeads}>
                <p>Khách quan tâm của tôi</p>
              </ProtectedRoute>
            }
          />
          <Route
            path="/broker/workspace"
            element={
              <ProtectedRoute moduleName="Không gian môi giới" allowedRoles={ROUTE_ACCESS.brokerWorkspace}>
                <p>Không gian môi giới</p>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  it('shows a neutral status while the stored session is checked, then the page for an OWNER', async () => {
    mockSession('OWNER');
    renderAt('/listings/new');
    expect(screen.getByRole('status')).toHaveTextContent('Đang kiểm tra phiên đăng nhập');
    expect(screen.queryByText('Vui lòng đăng nhập')).toBeNull();
    expect(await screen.findByText('Trang đăng tin')).toBeInTheDocument();
  });

  it('lets an OWNER open the lead inbox', async () => {
    mockSession('OWNER');
    renderAt('/my-leads');
    expect(await screen.findByText('Khách quan tâm của tôi')).toBeInTheDocument();
  });

  it('sends an OWNER away from the broker workspace', async () => {
    mockSession('OWNER');
    renderAt('/broker/workspace');
    expect(await screen.findByText('Trang chủ')).toBeInTheDocument();
  });

  it('redirects a home seeker from posting pages to their inquiries', async () => {
    mockSession('USER');
    renderAt('/listings/new');
    expect(await screen.findByText('Tin đã liên hệ')).toBeInTheDocument();
  });

  it('asks an anonymous visitor to log in', () => {
    vi.stubGlobal('fetch', vi.fn());
    renderAt('/listings/new');
    expect(screen.getByRole('heading', { name: 'Vui lòng đăng nhập' })).toBeInTheDocument();
  });
});
