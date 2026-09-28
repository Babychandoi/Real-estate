import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { contentApi } from '@/entities/content/api';
import type { AreaCard, AreaPageData } from '@/entities/content/model';
import {
  AreaLinks,
  Breadcrumbs,
  InventoryPanel,
  ListingStrip,
  NotFoundState,
  ProjectCardList,
} from '@/features/places/PlaceSections';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { ApiProblemException } from '@/shared/types/problem-details';
import { ListingSkeleton, StatePanel } from '@/shared/ui/Feedback';

/** `/khu-vuc`: every search area with its live listing count (P-06). */
export function AreaListPage() {
  const [areas, setAreas] = useState<AreaCard[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setFailed(false);
    contentApi
      .areas(abort.signal)
      .then((result) => setAreas(result.items))
      .catch(() => {
        if (!abort.signal.aborted) setFailed(true);
      });
    return () => abort.abort();
  }, [attempt]);

  useDocumentMeta({
    title: 'Nhà đất theo khu vực tại Hà Nội | Nhà Đất Chuẩn',
    description: 'Khu vực tìm nhà tại Hà Nội và số tin đang hiển thị ở mỗi khu vực.',
    canonical: '/khu-vuc',
  });

  return (
    <div className="ndc-page py-8 sm:py-12" data-ready={areas || failed ? 'true' : 'false'}>
      <Breadcrumbs items={[{ label: 'Trang chủ', to: '/' }, { label: 'Khu vực' }]} />
      <h1 className="text-headline-lg text-on-surface">Khu vực</h1>
      <p className="mt-2 max-w-2xl text-on-surface-variant">
        Khu vực tìm kiếm theo địa giới quận/huyện trước ngày 01/07/2025, vì tin đăng và người tìm nhà vẫn dùng tên này.
      </p>
      <div className="mt-8">
        {failed ? (
          <StatePanel error onRetry={() => setAttempt((value) => value + 1)} />
        ) : !areas ? (
          <ListingSkeleton count={3} />
        ) : (
          <AreaLinks areas={areas} />
        )}
      </div>
    </div>
  );
}

/** `/khu-vuc/:slug`: statistics with method, projects and newest listings of one area (P-06). */
export function AreaDetailPage() {
  const { slug = '' } = useParams<{ slug: string }>();
  const [data, setData] = useState<AreaPageData | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setState('loading');
    contentApi
      .area(slug, abort.signal)
      .then((result) => {
        setData(result);
        setState('ready');
      })
      .catch((error: unknown) => {
        if (abort.signal.aborted) return;
        setState(error instanceof ApiProblemException && error.problem.status === 404 ? 'missing' : 'error');
      });
    return () => abort.abort();
  }, [slug, attempt]);

  const area = data?.area;
  useDocumentMeta(
    area && state === 'ready'
      ? {
          title: `Nhà đất ${area.name} — bán, cho thuê | Nhà Đất Chuẩn`,
          description: `Nhà đất bán và cho thuê tại ${area.name}, ${area.provinceName}: tin đang hiển thị, thống kê giá chào có phương pháp và dự án trong khu vực.`,
          canonical: `/khu-vuc/${area.slug}`,
          og: { title: `Nhà đất ${area.name}`, type: 'website', url: `/khu-vuc/${area.slug}` },
        }
      : state === 'missing'
        ? { title: 'Không tìm thấy khu vực | Nhà Đất Chuẩn', robots: 'noindex,follow' }
        : null,
  );

  if (state === 'loading') {
    return (
      <div className="ndc-page py-10" data-ready="false">
        <ListingSkeleton count={3} />
      </div>
    );
  }
  if (state === 'missing') return <NotFoundState gone={false} what="Khu vực" />;
  if (state === 'error' || !data || !area) {
    return (
      <div className="ndc-page py-16" data-ready="true">
        <StatePanel error onRetry={() => setAttempt((value) => value + 1)} />
      </div>
    );
  }
  return (
    <div className="ndc-page py-8 sm:py-12" data-ready="true">
      <Breadcrumbs
        items={[{ label: 'Trang chủ', to: '/' }, { label: 'Khu vực', to: '/khu-vuc' }, { label: area.name }]}
      />
      <h1 className="text-headline-lg text-on-surface">Nhà đất {area.name}</h1>
      <p className="mt-2 text-on-surface-variant">
        {area.name}, {area.provinceName} · khu vực tìm kiếm theo địa giới trước ngày 01/07/2025.
      </p>
      <div className="mt-6">
        <InventoryPanel inventory={data.inventory} scope={area.name} />
      </div>
      <ListingStrip
        title={`Tin bán mới tại ${area.name}`}
        params={{ purpose: 'SALE', district: area.districtCode }}
        moreHref={`/search?purpose=SALE&district=${area.districtCode}`}
      />
      <ListingStrip
        title={`Tin cho thuê mới tại ${area.name}`}
        params={{ purpose: 'RENT', district: area.districtCode }}
        moreHref={`/search?purpose=RENT&district=${area.districtCode}`}
        size={3}
      />
      {data.projects.length > 0 && (
        <section className="mt-10" aria-labelledby="area-projects">
          <h2 id="area-projects" className="mb-4 text-lg font-semibold">
            Dự án tại {area.name}
          </h2>
          <ProjectCardList projects={data.projects} />
        </section>
      )}
    </div>
  );
}
