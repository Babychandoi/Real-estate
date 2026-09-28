import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { contentApi } from '@/entities/content/api';
import type { HomeData, PublicArticle } from '@/entities/content/model';
import { ArticleCard } from './ArticleCard';
import { AreaLinks, ProjectCardList } from './PlaceSections';

function Heading({ id, title, body, to, more }: { id: string; title: string; body: string; to: string; more: string }) {
  return (
    <div className="ndc-section-heading">
      <div>
        <h2 id={id}>{title}</h2>
        <p>{body}</p>
      </div>
      <Link to={to} className="ndc-text-link">
        {more}
        <ArrowRight className="h-4 w-4" aria-hidden="true" />
      </Link>
    </div>
  );
}

/**
 * Areas, projects and articles on the home page (UI-01), loaded after the listings (own chunk): only what exists now
 * is shown; a section without data is left out rather than filled with placeholders.
 */
export default function HomeExtras() {
  const [home, setHome] = useState<HomeData | null>(null);
  const [articles, setArticles] = useState<PublicArticle[]>([]);
  useEffect(() => {
    const abort = new AbortController();
    contentApi.home(abort.signal).then(setHome, () => setHome(null));
    contentApi.articles(null, 0, 3, abort.signal).then(
      (page) => setArticles(page.items),
      () => setArticles([]),
    );
    return () => abort.abort();
  }, []);
  return (
    <>
      {home && home.areas.length > 0 && (
        <section className="ndc-page pb-10 sm:pb-14" aria-labelledby="home-areas">
          <Heading
            id="home-areas"
            title="Khu vực có nhiều tin"
            body="Số tin đang hiển thị ở mỗi khu vực, theo dữ liệu hiện có."
            to="/khu-vuc"
            more="Tất cả khu vực"
          />
          <AreaLinks areas={home.areas} />
        </section>
      )}
      {home && home.projects.length > 0 && (
        <section className="ndc-page pb-10 sm:pb-14" aria-labelledby="home-projects">
          <Heading
            id="home-projects"
            title="Dự án đang có tin"
            body="Thông tin dự án kèm nguồn và các tin đang bán, cho thuê trong dự án."
            to="/du-an"
            more="Tất cả dự án"
          />
          <ProjectCardList projects={home.projects} />
        </section>
      )}
      {articles.length > 0 && (
        <section className="ndc-page pb-10 sm:pb-14" aria-labelledby="home-articles">
          <Heading
            id="home-articles"
            title="Cẩm nang và tin tức"
            body="Bài viết đã qua biên tập, ghi rõ tác giả và nguồn."
            to="/tin-tuc"
            more="Tất cả bài viết"
          />
          <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {articles.map((article) => (
              <li key={article.id}>
                <ArticleCard article={article} />
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}
