import { apiClient, apiFetch } from '@/shared/api/client';
import { ApiProblemException, type ProblemDetails } from '@/shared/types/problem-details';
import type { Amenity, ArticleCategory, ProjectDetail } from './model';

/** Staff CMS API (`/api/v1/cms/articles`, ADMIN/MODERATOR, no-store). */
export type ArticleStatus = 'DRAFT' | 'SUBMITTED' | 'PUBLISHED' | 'ARCHIVED' | 'REJECTED';
export type RevisionStatus = 'DRAFT' | 'SUBMITTED' | 'SCHEDULED' | 'PUBLISHED' | 'SUPERSEDED' | 'REJECTED' | 'ARCHIVED';

export interface AdminRevision {
  id: string;
  articleId: string;
  revisionNumber: number;
  title: string;
  summary: string | null;
  contentHtml: string;
  coverImageUrl: string | null;
  authorName: string;
  legalReference: string | null;
  metaDescription: string | null;
  canonicalUrl: string | null;
  sourceName: string | null;
  sourceUrl: string | null;
  status: RevisionStatus;
  rejectionReason: string | null;
  createdAt: string;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface AdminArticle {
  id: string;
  slug: string;
  category: ArticleCategory;
  status: ArticleStatus;
  publishedRevisionId: string | null;
  scheduledRevisionId: string | null;
  scheduledPublishAt: string | null;
  publishedAt: string | null;
  firstPublishedAt: string | null;
  unpublishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  publicPath: string;
  revisionCount: number;
  currentRevision: AdminRevision | null;
  revisions: AdminRevision[] | null;
}

export interface RevisionInput {
  title: string;
  summary: string;
  contentHtml: string;
  coverImageUrl: string;
  authorName: string;
  legalReference: string;
  metaDescription: string;
  sourceName: string;
  sourceUrl: string;
}

export interface ArticleInput extends RevisionInput {
  slug: string;
  category: ArticleCategory;
}

const json = (body: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(body) });

export const cmsAdminApi = {
  async list(filters: { status?: string; category?: string; page: number; size: number }, signal?: AbortSignal) {
    const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
    if (filters.status) query.set('status', filters.status);
    if (filters.category) query.set('category', filters.category);
    const response = await apiFetch(`/cms/articles?${query.toString()}`, signal ? { signal } : {});
    if (!response.ok) {
      const problem = (await response
        .json()
        .catch(() => ({ status: response.status, title: 'Lỗi' }))) as ProblemDetails;
      throw new ApiProblemException(problem);
    }
    return {
      items: (await response.json()) as AdminArticle[],
      total: Number(response.headers.get('X-Total-Count') ?? 0),
    };
  },
  detail: (id: string, signal?: AbortSignal) =>
    apiClient<AdminArticle>(`/cms/articles/${id}`, signal ? { signal } : {}),
  create: (input: ArticleInput) => apiClient<AdminArticle>('/cms/articles', json(input)),
  newRevision: (id: string, input: RevisionInput) =>
    apiClient<AdminArticle>(`/cms/articles/${id}/revisions`, json(input)),
  updateDraft: (id: string, revisionId: string, input: RevisionInput) =>
    apiClient<AdminArticle>(`/cms/articles/${id}/revisions/${revisionId}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),
  submit: (id: string, revisionId: string) =>
    apiClient<AdminRevision>(`/cms/articles/${id}/revisions/${revisionId}/submit`, { method: 'POST' }),
  approve: (id: string, revisionId: string, publishAt: string | null) =>
    apiClient<AdminRevision>(`/cms/articles/${id}/revisions/${revisionId}/approve`, json({ publishAt })),
  reject: (id: string, revisionId: string, reason: string) =>
    apiClient<AdminRevision>(`/cms/articles/${id}/revisions/${revisionId}/reject`, json({ reason })),
  cancelSchedule: (id: string) => apiClient<AdminArticle>(`/cms/articles/${id}/schedule`, { method: 'DELETE' }),
  unpublish: (id: string) => apiClient<AdminArticle>(`/cms/articles/${id}/unpublish`, { method: 'POST' }),
  previewLink: (id: string, revisionId: string) =>
    apiClient<{ token: string; path: string; expiresAt: string }>(
      `/cms/articles/${id}/revisions/${revisionId}/preview-link`,
      { method: 'POST' },
    ),
};

export interface PublicProfileInput {
  description: string;
  websiteUrl: string;
  infoSource: string;
  infoCheckedAt: string | null;
  status: string | null;
  amenities: Omit<Amenity, 'id'>[];
}

/** Staff project public profile (`/api/v1/catalog/projects/{id}/public-profile`). */
export const projectProfileApi = {
  get: (id: string) =>
    apiClient<{ project: ProjectDetail; amenities: Amenity[] }>(`/catalog/projects/${id}/public-profile`),
  save: (id: string, input: PublicProfileInput) =>
    apiClient<{ project: ProjectDetail; amenities: Amenity[] }>(`/catalog/projects/${id}/public-profile`, {
      method: 'PUT',
      body: JSON.stringify({
        ...input,
        description: input.description || null,
        websiteUrl: input.websiteUrl || null,
        infoSource: input.infoSource || null,
      }),
    }),
};
