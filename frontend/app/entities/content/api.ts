import { apiClient } from '@/shared/api/client';
import type {
  AreaCard,
  AreaPageData,
  ArticleCategory,
  ArticlePage,
  HomeData,
  ProjectList,
  ProjectPageData,
  PublicArticle,
  SiteInfo,
} from './model';

const opts = (signal?: AbortSignal): RequestInit => (signal ? { signal } : {});

export const contentApi = {
  home: (signal?: AbortSignal) => apiClient<HomeData>('/api/v2/public/home', opts(signal)),
  siteInfo: (signal?: AbortSignal) => apiClient<SiteInfo>('/api/v1/public/site-info', opts(signal)),
  articles: (category: ArticleCategory | null, page: number, size = 12, signal?: AbortSignal) => {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (category) query.set('category', category);
    return apiClient<ArticlePage>(`/api/v2/public/articles?${query.toString()}`, opts(signal));
  },
  article: (slug: string, signal?: AbortSignal) =>
    apiClient<PublicArticle>(`/api/v2/public/articles/${encodeURIComponent(slug)}`, opts(signal)),
  preview: (token: string, signal?: AbortSignal) =>
    apiClient<PublicArticle>(`/api/v1/public/articles/preview/${encodeURIComponent(token)}`, opts(signal)),
  projects: (page: number, size = 24, signal?: AbortSignal) =>
    apiClient<ProjectList>(`/api/v2/public/projects?page=${page}&size=${size}`, opts(signal)),
  project: (slug: string, signal?: AbortSignal) =>
    apiClient<ProjectPageData>(`/api/v2/public/projects/${encodeURIComponent(slug)}`, opts(signal)),
  areas: (signal?: AbortSignal) =>
    apiClient<{ items: AreaCard[]; dataAsOf: string }>('/api/v2/public/areas', opts(signal)),
  area: (slug: string, signal?: AbortSignal) =>
    apiClient<AreaPageData>(`/api/v2/public/areas/${encodeURIComponent(slug)}`, opts(signal)),
};
