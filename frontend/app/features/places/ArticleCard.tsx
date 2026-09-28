import { Link } from 'react-router-dom';
import type { PublicArticle } from '@/entities/content/model';

const date = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'long', timeZone: 'Asia/Ho_Chi_Minh' });

export const formatArticleDate = (value: string | null) => (value ? date.format(new Date(value)) : '');

/** One published article as a card: category, title, summary, author and publish date (the whole card is the link). */
export function ArticleCard({ article }: { article: PublicArticle }) {
  const revision = article.currentRevision;
  return (
    <Link
      to={article.path}
      className="flex h-full flex-col overflow-hidden rounded-2xl border border-outline-variant/40 bg-white transition hover:border-primary/40 hover:shadow-md"
    >
      {revision.coverImageUrl && (
        <img src={revision.coverImageUrl} alt="" loading="lazy" className="aspect-[16/9] w-full object-cover" />
      )}
      <span className="flex flex-1 flex-col gap-2 p-5">
        <span className="text-xs font-semibold uppercase tracking-wide text-secondary">{article.categoryLabel}</span>
        <span className="text-base font-semibold text-on-surface">{revision.title}</span>
        {revision.summary && <span className="line-clamp-3 text-sm text-on-surface-variant">{revision.summary}</span>}
        <span className="mt-auto text-xs text-on-surface-variant">
          {revision.authorName} · {formatArticleDate(article.publishedAt)}
        </span>
      </span>
    </Link>
  );
}
