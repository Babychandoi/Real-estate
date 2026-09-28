import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CmsManagementPage } from './_admin.cms';

const revision = (status: string, number: number) => ({
  id: `rev-${number}`,
  articleId: 'art-1',
  revisionNumber: number,
  title: `Bài kiểm thử bản ${number}`,
  summary: null,
  contentHtml: '<p>x</p>',
  coverImageUrl: null,
  authorName: 'Ban biên tập',
  legalReference: null,
  metaDescription: null,
  canonicalUrl: null,
  sourceName: 'Bộ Xây dựng',
  sourceUrl: null,
  status,
  rejectionReason: null,
  createdAt: '2026-09-20T02:00:00Z',
  submittedAt: null,
  reviewedAt: null,
  reviewedBy: null,
});

const article = (revisions: ReturnType<typeof revision>[], status = 'SUBMITTED') => ({
  id: 'art-1',
  slug: 'bai-kiem-thu',
  category: 'KNOWLEDGE',
  status,
  publishedRevisionId: null,
  scheduledRevisionId: null,
  scheduledPublishAt: null,
  publishedAt: null,
  firstPublishedAt: null,
  unpublishedAt: null,
  createdAt: '2026-09-20T02:00:00Z',
  updatedAt: '2026-09-20T02:00:00Z',
  publicPath: '/tin-tuc/bai-kiem-thu',
  revisionCount: revisions.length,
  currentRevision: revisions[0],
  revisions,
});

afterEach(() => vi.unstubAllGlobals());

describe('CMS admin (P-07, UI-25)', () => {
  it('lists articles, opens one and approves the submitted revision at a scheduled time', async () => {
    const calls: { url: string; method: string; body: string | null }[] = [];
    let approved = false;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        calls.push({ url, method, body: typeof init?.body === 'string' ? init.body : null });
        if (url.includes('/cms/articles?')) {
          return Response.json([article([revision('SUBMITTED', 1)])], { headers: { 'X-Total-Count': '1' } });
        }
        if (url.endsWith('/cms/articles/art-1/revisions/rev-1/approve')) {
          approved = true;
          return Response.json(revision('SCHEDULED', 1));
        }
        if (url.endsWith('/cms/articles/art-1')) {
          return Response.json(
            approved
              ? {
                  ...article([revision('SCHEDULED', 1)]),
                  scheduledRevisionId: 'rev-1',
                  scheduledPublishAt: '2026-10-01T02:00:00Z',
                }
              : article([revision('SUBMITTED', 1)]),
          );
        }
        return Response.json({ title: 'x', status: 404 }, { status: 404 });
      }),
    );

    render(
      <MemoryRouter>
        <CmsManagementPage />
      </MemoryRouter>,
    );
    const table = await screen.findByRole('table');
    expect(within(table).getByText('Bài kiểm thử bản 1')).toBeInTheDocument();
    expect(within(table).getByText('Chờ duyệt')).toBeInTheDocument();

    fireEvent.click(within(table).getByRole('button', { name: 'Mở' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText(/Bản #1 · Chờ duyệt/)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Hẹn giờ xuất bản'), { target: { value: '2026-10-01T09:00' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Duyệt và hẹn giờ' }));

    await waitFor(() => expect(approved).toBe(true));
    const approve = calls.find((call) => call.url.endsWith('/approve'));
    expect(approve?.method).toBe('POST');
    expect(JSON.parse(approve?.body ?? '{}').publishAt).toBe(new Date('2026-10-01T09:00').toISOString());
    expect(await within(dialog).findByText(/Hẹn xuất bản lúc/)).toBeInTheDocument();
  });
});
