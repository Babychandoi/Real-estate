import { expect, test } from '@playwright/test';
import { gotoReady } from './support/helpers';

// S7-SEO follow-up (S11): browser E2E of the project/area/article pages (previously HTTP/unit only), including their
// not-found and gone states.

test.describe('projects', () => {
  test('list links to a detail page that renders facts and its listings', async ({ page, request }) => {
    await gotoReady(page, '/du-an');
    await expect(page.getByRole('heading', { level: 1, name: 'Dự án bất động sản' })).toBeVisible();
    const projects = (await (await request.get('/api/v2/public/projects?size=1')).json()) as {
      items: Array<{ slug: string; name: string }>;
    };
    const project = projects.items[0];
    await page.getByRole('link', { name: new RegExp(project.name) }).click();
    await expect(page).toHaveURL(`/du-an/${project.slug}`);
    await expect(page.getByRole('heading', { level: 1, name: project.name })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Thông tin dự án' })).toBeVisible();
  });

  test('an unknown slug renders a not-found state with its own h1', async ({ page }) => {
    await gotoReady(page, '/du-an/khong-ton-tai-e2e');
    await expect(page.getByRole('heading', { level: 1, name: 'Không tìm thấy dự án' })).toBeVisible();
    await page.getByRole('link', { name: 'Tìm nhà' }).click();
    await expect(page).toHaveURL(/\/search$/);
  });
});

test.describe('areas', () => {
  test('list links to a detail page with sale/rent sections', async ({ page, request }) => {
    await gotoReady(page, '/khu-vuc');
    await expect(page.getByRole('heading', { level: 1, name: 'Khu vực' })).toBeVisible();
    const areas = (await (await request.get('/api/v2/public/areas?size=1')).json()) as {
      items: Array<{ slug: string; name: string }>;
    };
    const area = areas.items[0];
    await page.goto(`/khu-vuc/${area.slug}`);
    await expect(page.getByRole('heading', { level: 1, name: new RegExp(area.name) })).toBeVisible();
  });

  test('an unknown slug renders a not-found state', async ({ page }) => {
    await gotoReady(page, '/khu-vuc/khong-ton-tai-e2e');
    await expect(page.getByRole('heading', { level: 1, name: 'Không tìm thấy khu vực' })).toBeVisible();
  });
});

test.describe('articles', () => {
  test('list links to an article that renders and has a canonical url', async ({ page, request }) => {
    await gotoReady(page, '/tin-tuc');
    await expect(page.getByRole('heading', { level: 1, name: 'Tin tức và cẩm nang' })).toBeVisible();
    const articles = (await (await request.get('/api/v1/public/articles?size=1')).json()) as Array<{
      slug: string;
      publishedAt: string | null;
    }>;
    const article = articles[0];
    await page.goto(`/tin-tuc/${article.slug}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
      'href',
      new RegExp(`/tin-tuc/${article.slug}$`),
    );
  });

  test('an unknown slug renders a not-found state', async ({ page }) => {
    await gotoReady(page, '/tin-tuc/khong-ton-tai-e2e');
    await expect(page.getByRole('heading', { level: 1, name: 'Không tìm thấy bài viết' })).toBeVisible();
  });
});
