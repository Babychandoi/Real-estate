import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { ToastProvider } from '@/shared/ui/Toast';
import { ArticleDetailPage, ArticlePreviewPage } from './_public.articles';
import { AreaDetailPage } from './_public.areas';
import { InformationPage } from './_public.information';
import { ProjectDetailPage } from './_public.projects';

type Handler = (url: string) => Response | undefined;

function mockFetch(handler: Handler) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      return (
        handler(url) ?? Response.json({ title: 'Not found', status: 404, detail: 'Không tìm thấy' }, { status: 404 })
      );
    }),
  );
}

function renderAt(path: string, route: string, element: React.ReactNode) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path={route} element={element} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const emptySearch = () =>
  Response.json({
    items: [],
    pageInfo: { hasNext: false, nextCursor: null, size: 6 },
    total: { value: 0, relation: 'eq' },
    engine: 'database',
    degraded: false,
    notices: [],
    suggestions: [],
    dataAsOf: '2026-09-28T00:00:00Z',
  });

const inventory = {
  total: 7,
  statistics: [
    {
      purpose: 'SALE',
      count: 5,
      median: 55_000_000,
      unit: 'VND_PER_M2',
      minSamples: 5,
      method: 'Trung vị giá chào bán trên mỗi m².',
    },
    {
      purpose: 'RENT',
      count: 2,
      median: null,
      unit: 'VND_PER_MONTH',
      minSamples: 5,
      method: 'Trung vị giá chào thuê theo tháng.',
    },
  ],
  types: [],
  lastListingUpdate: '2026-09-27T03:00:00Z',
  dataAsOf: '2026-09-28T03:00:00Z',
};

afterEach(() => {
  vi.unstubAllGlobals();
  document.head.innerHTML = '';
  document.title = '';
});

describe('project page (P-06)', () => {
  it('shows facts with their source, amenities with sources and a median only where there are enough listings', async () => {
    mockFetch((url) => {
      if (url.includes('/api/v2/public/projects/khu-a')) {
        return Response.json({
          project: {
            id: 'p-1',
            slug: 'khu-a',
            name: 'Khu căn hộ A',
            developerName: 'Công ty A',
            provinceCode: '01',
            districtCode: '005',
            districtName: 'Cầu Giấy',
            areaSlug: 'cau-giay',
            address: 'Đường Trần Duy Hưng',
            totalAreaM2: null,
            totalBlocks: 2,
            totalUnits: 500,
            handoverYear: 2027,
            legalLicenseNumber: 'GP-1',
            status: 'ACTIVE',
            description: 'Mô tả dự án.',
            websiteUrl: null,
            infoSource: 'Hồ sơ chủ đầu tư',
            infoCheckedAt: '2026-09-01',
            updatedAt: '2026-09-01T00:00:00Z',
          },
          amenities: [
            {
              id: 'a-1',
              name: 'Trường tiểu học Dịch Vọng',
              category: 'EDUCATION',
              distanceM: 400,
              sourceName: 'Bản đồ quy hoạch',
              sourceUrl: 'https://example.org/qh',
              checkedAt: '2026-09-01',
            },
          ],
          inventory,
        });
      }
      if (url.includes('/api/v2/listings/search')) return emptySearch();
      return undefined;
    });

    renderAt('/du-an/khu-a', '/du-an/:slug', <ProjectDetailPage />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Khu căn hộ A' })).toBeInTheDocument();
    expect(screen.getByText(/Hồ sơ chủ đầu tư/)).toBeInTheDocument();
    expect(screen.getByText('Trường tiểu học Dịch Vọng')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bản đồ quy hoạch' })).toHaveAttribute('href', 'https://example.org/qh');
    expect(screen.getByText('55 triệu/m²')).toBeInTheDocument();
    expect(screen.getByText('Chưa đủ 5 tin để tính giá trung vị.')).toBeInTheDocument();
    expect(document.title).toBe('Khu căn hộ A — dự án tại Cầu Giấy | Nhà Đất Chuẩn');
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toMatch(/\/du-an\/khu-a$/);
  });

  it('says a locked project is gone and marks the page noindex', async () => {
    mockFetch((url) =>
      url.includes('/api/v2/public/projects/')
        ? Response.json({ title: 'Gone', status: 410, detail: 'Dự án không còn hiển thị công khai.' }, { status: 410 })
        : undefined,
    );
    renderAt('/du-an/da-khoa', '/du-an/:slug', <ProjectDetailPage />);
    expect(await screen.findByText('Dự án không còn hiển thị')).toBeInTheDocument();
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex,follow');
  });
});

describe('area page (P-06)', () => {
  it('shows the statistics with their method and a not-found state for unknown areas', async () => {
    mockFetch((url) => {
      if (url.includes('/api/v2/public/areas/cau-giay')) {
        return Response.json({
          area: { provinceCode: '01', districtCode: '005', provinceName: 'Hà Nội', name: 'Cầu Giấy', slug: 'cau-giay' },
          inventory,
          projects: [],
        });
      }
      if (url.includes('/api/v2/listings/search')) return emptySearch();
      return undefined;
    });
    renderAt('/khu-vuc/cau-giay', '/khu-vuc/:slug', <AreaDetailPage />);
    expect(await screen.findByRole('heading', { level: 1, name: 'Nhà đất Cầu Giấy' })).toBeInTheDocument();
    expect(screen.getByText('Cách tính')).toBeInTheDocument();
    expect(screen.getByText('Trung vị giá chào bán trên mỗi m².')).toBeInTheDocument();
  });

  it('renders not found for an unknown area', async () => {
    mockFetch(() => undefined);
    renderAt('/khu-vuc/khong-co', '/khu-vuc/:slug', <AreaDetailPage />);
    expect(await screen.findByText('Không tìm thấy khu vực')).toBeInTheDocument();
  });
});

const article = {
  id: 'art-1',
  slug: 'kiem-tra-phap-ly',
  category: 'KNOWLEDGE',
  categoryLabel: 'Kiến thức',
  status: 'PUBLISHED',
  path: '/tin-tuc/kiem-tra-phap-ly',
  publishedAt: '2026-09-20T02:00:00Z',
  firstPublishedAt: '2026-09-20T02:00:00Z',
  updatedAt: '2026-09-20T02:00:00Z',
  currentRevision: {
    id: 'rev-1',
    revisionNumber: 2,
    title: 'Kiểm tra pháp lý trước khi đặt cọc',
    summary: 'Tóm tắt ngắn.',
    contentHtml: '<h2>Bước 1</h2><p>Xem sổ đỏ.</p>',
    coverImageUrl: null,
    authorName: 'Ban biên tập',
    legalReference: 'Luật Đất đai 2024',
    metaDescription: null,
    sourceName: 'Bộ Xây dựng',
    sourceUrl: 'https://moc.gov.vn/',
    reviewedAt: '2026-09-19T10:00:00Z',
  },
};

describe('article pages (P-07)', () => {
  it('shows the body, author, date, sources and Article structured data', async () => {
    mockFetch((url) => (url.includes('/api/v2/public/articles/kiem-tra-phap-ly') ? Response.json(article) : undefined));
    renderAt('/tin-tuc/kiem-tra-phap-ly', '/tin-tuc/:slug', <ArticleDetailPage />);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Kiểm tra pháp lý trước khi đặt cọc' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Bước 1' })).toBeInTheDocument();
    const sources = screen.getByRole('region', { name: 'Nguồn tham khảo' });
    expect(within(sources).getByRole('link', { name: /Bộ Xây dựng/ })).toHaveAttribute(
      'rel',
      'nofollow noopener noreferrer',
    );
    expect(within(sources).getByText('Luật Đất đai 2024')).toBeInTheDocument();
    const jsonLd = JSON.parse(document.head.querySelector('script[type="application/ld+json"]')?.textContent ?? '{}');
    expect(jsonLd['@type']).toBe('Article');
    expect(jsonLd.author.name).toBe('Ban biên tập');
  });

  it('says an unpublished article is no longer shown', async () => {
    mockFetch(() => Response.json({ title: 'Gone', status: 410, detail: 'Đã gỡ' }, { status: 410 }));
    renderAt('/tin-tuc/da-go', '/tin-tuc/:slug', <ArticleDetailPage />);
    expect(await screen.findByText('Bài viết không còn hiển thị')).toBeInTheDocument();
  });

  it('marks a preview as not public and never indexable', async () => {
    mockFetch((url) =>
      url.includes('/api/v1/public/articles/preview/')
        ? Response.json({ ...article, status: 'DRAFT', publishedAt: null })
        : undefined,
    );
    renderAt('/tin-tuc/xem-truoc/tok', '/tin-tuc/xem-truoc/:token', <ArticlePreviewPage />);
    expect(await screen.findByRole('note')).toHaveTextContent('bản nháp');
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex,nofollow');
  });
});

describe('information pages (UI-15)', () => {
  it('shows configured operator details and says which are missing, with approved policy links', async () => {
    mockFetch((url) =>
      url.includes('/api/v1/public/site-info')
        ? Response.json({
            operator: {
              siteName: 'Nhà Đất Chuẩn',
              legalName: 'Công ty TNHH Ví Dụ',
              businessRegistration: null,
              taxCode: null,
              address: null,
              representative: null,
              email: 'hotro@example.test',
              phone: null,
              hotlineHours: null,
              complete: false,
            },
            policies: [{ title: 'Quy chế hoạt động', path: '/tin-tuc/quy-che', summary: null, publishedAt: null }],
          })
        : undefined,
    );
    renderAt('/terms', '/terms', <InformationPage />);
    expect(await screen.findByText('Công ty TNHH Ví Dụ')).toBeInTheDocument();
    expect(screen.getAllByText('Chưa có dữ liệu').length).toBeGreaterThan(3);
    expect(screen.getByRole('link', { name: 'Quy chế hoạt động' })).toHaveAttribute('href', '/tin-tuc/quy-che');
    expect(document.title).toBe('Điều khoản sử dụng | Nhà Đất Chuẩn');
  });
});
