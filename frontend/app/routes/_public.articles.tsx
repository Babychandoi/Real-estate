import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { CalendarDays, ExternalLink, EyeOff, UserRound } from 'lucide-react';
import { contentApi } from '@/entities/content/api';
import {
  ARTICLE_CATEGORIES,
  articleCategoryLabel,
  REVISION_STATUS_LABELS,
  type ArticleCategory,
  type ArticlePage,
  type PublicArticle,
} from '@/entities/content/model';
import { ArticleCard, formatArticleDate } from '@/features/places/ArticleCard';
import { Breadcrumbs, NotFoundState, formatDateTime } from '@/features/places/PlaceSections';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { ApiProblemException } from '@/shared/types/problem-details';
import { ListingSkeleton, StatePanel } from '@/shared/ui/Feedback';

function isCategory(value: string | null): value is ArticleCategory {
  return ARTICLE_CATEGORIES.some((item) => item.value === value);
}

/** `/tin-tuc`: published CMS articles, newest first, by category (P-07). */
export function ArticleListPage() {
  const [params, setParams] = useSearchParams();
  const rawCategory = params.get('category');
  const category = isCategory(rawCategory) ? rawCategory : null;
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);
  const [data, setData] = useState<ArticlePage | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    setData(null);
    setFailed(false);
    contentApi
      .articles(category, page, 12, abort.signal)
      .then((value) => {
        if (!abort.signal.aborted) setData(value);
      })
      .catch(() => {
        if (!abort.signal.aborted) setFailed(true);
      });
    return () => abort.abort();
  }, [category, page, attempt]);

  const heading = category ? articleCategoryLabel(category) : 'Tin tức và cẩm nang';
  const base = category ? `/tin-tuc?category=${category}` : '/tin-tuc';
  useDocumentMeta({
    title: `${heading} | Nhà Đất Chuẩn`,
    description: 'Bài viết đã qua biên tập về pháp lý, kiến thức và thị trường nhà đất, ghi rõ tác giả và nguồn.',
    canonical: page > 0 ? `${base}${category ? '&' : '?'}page=${page}` : base,
  });

  const select = (next: ArticleCategory | null) => setParams(next ? { category: next } : {});
  return (
    <div className="ndc-page py-8 sm:py-12" data-ready={data || failed ? 'true' : 'false'}>
      <Breadcrumbs items={[{ label: 'Trang chủ', to: '/' }, { label: 'Tin tức' }]} />
      <h1 className="text-headline-lg text-on-surface">{heading}</h1>
      <div className="mt-5 flex flex-wrap gap-2" role="group" aria-label="Chuyên mục">
        {[{ value: null, label: 'Tất cả' }, ...ARTICLE_CATEGORIES].map((item) => (
          <button
            key={item.label}
            type="button"
            aria-pressed={category === item.value}
            onClick={() => select(item.value)}
            className="min-h-11 rounded-full border border-outline-variant/60 px-4 text-sm font-medium aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-white"
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="mt-8">
        {failed ? (
          <StatePanel error onRetry={() => setAttempt((value) => value + 1)} />
        ) : !data ? (
          <ListingSkeleton count={3} />
        ) : data.items.length === 0 ? (
          <StatePanel title="Chưa có bài viết trong chuyên mục này" />
        ) : (
          <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {data.items.map((article) => (
              <li key={article.id} className="min-w-0">
                <ArticleCard article={article} />
              </li>
            ))}
          </ul>
        )}
      </div>
      {data && (page > 0 || data.hasNext) && (
        <nav aria-label="Phân trang" className="mt-8 flex gap-3">
          {page > 0 && (
            <button
              type="button"
              className="ndc-text-link min-h-11"
              onClick={() =>
                setParams({ ...(category ? { category } : {}), ...(page > 1 ? { page: String(page - 1) } : {}) })
              }
            >
              Trang trước
            </button>
          )}
          {data.hasNext && (
            <button
              type="button"
              className="ndc-text-link min-h-11"
              onClick={() => setParams({ ...(category ? { category } : {}), page: String(page + 1) })}
            >
              Trang sau
            </button>
          )}
        </nav>
      )}
    </div>
  );
}

function ArticleBody({ article, preview }: { article: PublicArticle; preview?: boolean }) {
  const revision = article.currentRevision;
  return (
    <article className="mx-auto max-w-3xl">
      <Breadcrumbs
        items={[
          { label: 'Trang chủ', to: '/' },
          { label: 'Tin tức', to: '/tin-tuc' },
          { label: article.categoryLabel, to: `/tin-tuc?category=${article.category}` },
        ]}
      />
      <h1 className="text-headline-lg text-on-surface">{revision.title}</h1>
      <p className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-on-surface-variant">
        <span className="inline-flex items-center gap-1">
          <UserRound className="h-4 w-4" aria-hidden="true" /> {revision.authorName}
        </span>
        {!preview && article.publishedAt && (
          <span className="inline-flex items-center gap-1">
            <CalendarDays className="h-4 w-4" aria-hidden="true" /> Xuất bản {formatArticleDate(article.publishedAt)}
          </span>
        )}
        {revision.reviewedAt && <span>Biên tập duyệt {formatDateTime(revision.reviewedAt)}</span>}
      </p>
      {revision.summary && <p className="mt-5 text-lg leading-8 text-on-surface">{revision.summary}</p>}
      {revision.coverImageUrl && (
        <img src={revision.coverImageUrl} alt="" className="mt-6 w-full rounded-2xl object-cover" />
      )}
      {/* The body is sanitised by the backend (jsoup allow-list on write and on read): no scripts, handlers or
          javascript: URLs can reach this element. */}
      <div className="ndc-prose mt-6" dangerouslySetInnerHTML={{ __html: revision.contentHtml }} />
      {(revision.sourceName || revision.legalReference) && (
        <section className="mt-8 rounded-xl bg-surface-container-low p-5 text-sm" aria-labelledby="sources">
          <h2 id="sources" className="font-semibold">
            Nguồn tham khảo
          </h2>
          <ul className="mt-2 space-y-1">
            {revision.sourceName && (
              <li>
                {revision.sourceUrl ? (
                  <a
                    className="inline-flex min-h-11 items-center gap-1 text-primary underline"
                    href={revision.sourceUrl}
                    rel="nofollow noopener noreferrer"
                    target="_blank"
                  >
                    {revision.sourceName} <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                  </a>
                ) : (
                  revision.sourceName
                )}
              </li>
            )}
            {revision.legalReference && <li>{revision.legalReference}</li>}
          </ul>
        </section>
      )}
      <p className="mt-8 text-xs text-on-surface-variant">
        Nội dung mang tính tham khảo, không thay thế tư vấn pháp lý cho từng giao dịch.
      </p>
    </article>
  );
}

/** `/tin-tuc/:slug`: one published article with author, dates and sources; 404 unknown, 410 unpublished. */
export function ArticleDetailPage() {
  const { slug = '' } = useParams<{ slug: string }>();
  const [article, setArticle] = useState<PublicArticle | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'missing' | 'gone' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setState('loading');
    contentApi
      .article(slug, abort.signal)
      .then((result) => {
        setArticle(result);
        setState('ready');
      })
      .catch((error: unknown) => {
        if (abort.signal.aborted) return;
        const status = error instanceof ApiProblemException ? error.problem.status : 0;
        setState(status === 404 ? 'missing' : status === 410 ? 'gone' : 'error');
      });
    return () => abort.abort();
  }, [slug, attempt]);

  const revision = article?.currentRevision;
  useDocumentMeta(
    article && revision && state === 'ready'
      ? {
          title: `${revision.title} | Nhà Đất Chuẩn`,
          description: revision.metaDescription ?? revision.summary ?? undefined,
          canonical: article.path,
          og: { title: revision.title, type: 'article', url: article.path, image: revision.coverImageUrl ?? undefined },
          jsonLd: {
            '@context': 'https://schema.org',
            '@type': 'Article',
            headline: revision.title.slice(0, 110),
            author: { '@type': 'Person', name: revision.authorName },
            datePublished: article.publishedAt ?? undefined,
            dateModified: article.updatedAt ?? undefined,
            publisher: { '@type': 'Organization', name: 'Nhà Đất Chuẩn' },
          },
        }
      : state === 'missing' || state === 'gone'
        ? { title: 'Không tìm thấy bài viết | Nhà Đất Chuẩn', robots: 'noindex,follow' }
        : null,
  );

  if (state === 'loading') {
    return (
      <div className="ndc-page py-10" data-ready="false">
        <ListingSkeleton count={1} />
      </div>
    );
  }
  if (state === 'missing' || state === 'gone') return <NotFoundState gone={state === 'gone'} what="Bài viết" />;
  if (state === 'error' || !article) {
    return (
      <div className="ndc-page py-16" data-ready="true">
        <StatePanel headingLevel={1} error onRetry={() => setAttempt((value) => value + 1)} />
      </div>
    );
  }
  return (
    <div className="ndc-page py-8 sm:py-12" data-ready="true">
      <ArticleBody article={article} />
    </div>
  );
}

/** `/tin-tuc/xem-truoc/:token`: staff preview of any revision; never indexed (the server also sends noindex). */
export function ArticlePreviewPage() {
  const { token = '' } = useParams<{ token: string }>();
  const [article, setArticle] = useState<PublicArticle | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'missing'>('loading');
  useEffect(() => {
    const abort = new AbortController();
    contentApi
      .preview(token, abort.signal)
      .then((result) => {
        setArticle(result);
        setState('ready');
      })
      .catch(() => {
        if (!abort.signal.aborted) setState('missing');
      });
    return () => abort.abort();
  }, [token]);

  useDocumentMeta({ title: 'Xem trước bài viết | Nhà Đất Chuẩn', robots: 'noindex,nofollow' });

  if (state === 'loading') {
    return (
      <div className="ndc-page py-10" data-ready="false">
        <ListingSkeleton count={1} />
      </div>
    );
  }
  if (state === 'missing' || !article) {
    return (
      <div className="ndc-page py-16" data-ready="true">
        <StatePanel
          title="Liên kết xem trước không hợp lệ"
          description="Liên kết đã hết hạn (24 giờ) hoặc không đúng. Hãy tạo liên kết mới trong trang quản trị nội dung."
        />
      </div>
    );
  }
  return (
    <div className="ndc-page py-8 sm:py-12" data-ready="true">
      <p
        className="mx-auto mb-6 flex max-w-3xl items-center gap-2 rounded-xl bg-warning-container px-4 py-3 text-sm text-warning-on-container"
        role="note"
      >
        <EyeOff className="h-4 w-4" aria-hidden="true" />
        Bản xem trước (phiên bản {article.currentRevision.revisionNumber},{' '}
        {(REVISION_STATUS_LABELS[article.status] ?? 'chưa công khai').toLowerCase()}) — chỉ người có liên kết mới xem
        được.
      </p>
      <ArticleBody article={article} preview />
    </div>
  );
}
