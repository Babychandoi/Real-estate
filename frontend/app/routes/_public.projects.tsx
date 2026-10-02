import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ExternalLink, MapPin } from 'lucide-react';
import { contentApi } from '@/entities/content/api';
import {
  AMENITY_CATEGORIES,
  PROJECT_STATUS_LABELS,
  type ProjectList,
  type ProjectPageData,
} from '@/entities/content/model';
import {
  Breadcrumbs,
  InventoryPanel,
  ListingStrip,
  NotFoundState,
  ProjectCardList,
} from '@/features/places/PlaceSections';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { ApiProblemException } from '@/shared/types/problem-details';
import { ListingSkeleton, StatePanel } from '@/shared/ui/Feedback';

type LoadState = 'loading' | 'ready' | 'missing' | 'gone' | 'error';

function statusOf(error: unknown): LoadState {
  const status = error instanceof ApiProblemException ? error.problem.status : 0;
  return status === 404 ? 'missing' : status === 410 ? 'gone' : 'error';
}

/** `/du-an`: public projects with their live listing counts (P-06). */
export function ProjectListPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);
  const [data, setData] = useState<ProjectList | null>(null);
  const [state, setState] = useState<LoadState>('loading');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    setState('loading');
    contentApi
      .projects(page, 24, abort.signal)
      .then((result) => {
        setData(result);
        setState('ready');
      })
      .catch((error: unknown) => {
        if (!abort.signal.aborted) setState(statusOf(error));
      });
    return () => abort.abort();
  }, [page, attempt]);

  useDocumentMeta({
    title: 'Dự án bất động sản tại Hà Nội | Nhà Đất Chuẩn',
    description: 'Danh sách dự án nhà ở tại Hà Nội kèm số tin đang bán, cho thuê và thông tin có nguồn.',
    canonical: page > 0 ? `/du-an?page=${page}` : '/du-an',
  });

  const hasNext = data ? (data.page + 1) * data.size < data.total : false;
  return (
    <div className="ndc-page py-8 sm:py-12" data-ready={state === 'loading' ? 'false' : 'true'}>
      <Breadcrumbs items={[{ label: 'Trang chủ', to: '/' }, { label: 'Dự án' }]} />
      <h1 className="text-headline-lg text-on-surface">Dự án bất động sản</h1>
      <p className="mt-2 max-w-2xl text-on-surface-variant">
        Thông tin dự án do ban quản trị cập nhật kèm nguồn; số tin là các tin đang hiển thị trên Nhà Đất Chuẩn.
      </p>
      <div className="mt-8">
        {state === 'loading' ? (
          <ListingSkeleton count={3} />
        ) : state !== 'ready' || !data ? (
          <StatePanel error onRetry={() => setAttempt((value) => value + 1)} />
        ) : data.items.length === 0 ? (
          <StatePanel title="Chưa có dự án công khai" />
        ) : (
          <ProjectCardList projects={data.items} />
        )}
      </div>
      {(page > 0 || hasNext) && (
        <nav aria-label="Phân trang" className="mt-8 flex gap-3">
          {page > 0 && (
            <button
              type="button"
              className="ndc-text-link min-h-11"
              onClick={() => setParams(page === 1 ? {} : { page: String(page - 1) })}
            >
              Trang trước
            </button>
          )}
          {hasNext && (
            <button
              type="button"
              className="ndc-text-link min-h-11"
              onClick={() => setParams({ page: String(page + 1) })}
            >
              Trang sau
            </button>
          )}
        </nav>
      )}
    </div>
  );
}

/** `/du-an/:slug`: project facts with their source, amenities with sources, live inventory and listings (P-06). */
export function ProjectDetailPage() {
  const { slug = '' } = useParams<{ slug: string }>();
  const [data, setData] = useState<ProjectPageData | null>(null);
  const [state, setState] = useState<LoadState>('loading');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    setState('loading');
    contentApi
      .project(slug, abort.signal)
      .then((result) => {
        setData(result);
        setState('ready');
      })
      .catch((error: unknown) => {
        if (!abort.signal.aborted) setState(statusOf(error));
      });
    return () => abort.abort();
  }, [slug, attempt]);

  const project = data?.project;
  useDocumentMeta(
    project && state === 'ready'
      ? {
          title: `${project.name} — dự án tại ${project.districtName ?? 'Hà Nội'} | Nhà Đất Chuẩn`,
          description:
            project.description?.slice(0, 300) ??
            `${project.name} của ${project.developerName} tại ${project.address}. Xem tin đang bán, cho thuê và thống kê giá chào.`,
          canonical: `/du-an/${project.slug}`,
          og: { title: project.name, type: 'website', url: `/du-an/${project.slug}` },
          jsonLd: {
            '@context': 'https://schema.org',
            '@type': 'Place',
            name: project.name,
            address: {
              '@type': 'PostalAddress',
              streetAddress: project.address,
              addressLocality: 'Hà Nội',
              addressCountry: 'VN',
            },
          },
        }
      : state === 'missing' || state === 'gone'
        ? { title: 'Không tìm thấy dự án | Nhà Đất Chuẩn', robots: 'noindex,follow' }
        : null,
  );

  if (state === 'loading') {
    return (
      <div className="ndc-page py-10" data-ready="false">
        <ListingSkeleton count={3} />
      </div>
    );
  }
  if (state === 'missing' || state === 'gone') return <NotFoundState gone={state === 'gone'} what="Dự án" />;
  if (state === 'error' || !data || !project) {
    return (
      <div className="ndc-page py-16" data-ready="true">
        <StatePanel headingLevel={1} error onRetry={() => setAttempt((value) => value + 1)} />
      </div>
    );
  }

  const facts: [string, string | null][] = [
    ['Chủ đầu tư', project.developerName],
    ['Địa chỉ', project.address],
    ['Tình trạng', PROJECT_STATUS_LABELS[project.status]],
    ['Năm bàn giao dự kiến', project.handoverYear ? String(project.handoverYear) : null],
    ['Số căn', project.totalUnits > 0 ? project.totalUnits.toLocaleString('vi-VN') : null],
    ['Số tòa/khối', project.totalBlocks > 0 ? String(project.totalBlocks) : null],
    ['Giấy phép', project.legalLicenseNumber],
  ];
  return (
    <div className="ndc-page py-8 sm:py-12" data-ready="true">
      <Breadcrumbs
        items={[{ label: 'Trang chủ', to: '/' }, { label: 'Dự án', to: '/du-an' }, { label: project.name }]}
      />
      <div className="grid gap-8 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <article className="min-w-0">
          <h1 className="text-headline-lg text-on-surface">{project.name}</h1>
          {project.districtName && project.areaSlug && (
            <Link
              to={`/khu-vuc/${project.areaSlug}`}
              className="mt-2 inline-flex min-h-11 items-center gap-1 text-sm font-medium text-primary"
            >
              <MapPin className="h-4 w-4" aria-hidden="true" /> {project.districtName}, Hà Nội
            </Link>
          )}
          {project.description ? (
            <p className="mt-4 whitespace-pre-line leading-7 [overflow-wrap:anywhere] text-on-surface">{project.description}</p>
          ) : (
            <p className="mt-4 text-on-surface-variant">Chưa có mô tả dự án.</p>
          )}
          <div className="mt-6">
            <InventoryPanel inventory={data.inventory} scope={project.name} />
          </div>
        </article>
        <aside className="rounded-2xl border border-outline-variant/40 bg-white p-5" aria-label="Thông tin dự án">
          <h2 className="text-base font-semibold">Thông tin dự án</h2>
          <dl className="mt-3 space-y-3 text-sm">
            {facts
              .filter(([, value]) => value)
              .map(([label, value]) => (
                <div key={label}>
                  <dt className="text-on-surface-variant">{label}</dt>
                  <dd className="font-medium text-on-surface">{value}</dd>
                </div>
              ))}
          </dl>
          <p className="mt-4 text-xs leading-5 text-on-surface-variant">
            Nguồn: {project.infoSource ?? 'Chưa có dữ liệu'}
            {project.infoCheckedAt
              ? ` · kiểm tra ngày ${new Date(project.infoCheckedAt).toLocaleDateString('vi-VN')}`
              : ''}
          </p>
          {project.websiteUrl && (
            <a
              href={project.websiteUrl}
              rel="nofollow noopener noreferrer"
              target="_blank"
              className="mt-3 inline-flex min-h-11 items-center gap-1 text-sm font-medium text-primary"
            >
              Trang của chủ đầu tư <ExternalLink className="h-4 w-4" aria-hidden="true" />
            </a>
          )}
        </aside>
      </div>

      <section className="mt-10" aria-labelledby="amenities-heading">
        <h2 id="amenities-heading" className="text-lg font-semibold">
          Tiện ích xung quanh
        </h2>
        {data.amenities.length === 0 ? (
          <p className="mt-2 text-sm text-on-surface-variant">Chưa có tiện ích được kiểm tra nguồn.</p>
        ) : (
          <ul className="mt-3 grid gap-3 sm:grid-cols-2">
            {data.amenities.map((amenity) => (
              <li
                key={`${amenity.name}-${amenity.checkedAt}`}
                className="rounded-xl border border-outline-variant/40 bg-white p-4 text-sm"
              >
                <p className="font-medium text-on-surface">
                  {amenity.name}
                  <span className="ml-2 text-xs font-normal text-on-surface-variant">
                    {AMENITY_CATEGORIES.find((item) => item.value === amenity.category)?.label}
                  </span>
                </p>
                {amenity.distanceM != null && (
                  <p className="text-on-surface-variant">Khoảng {amenity.distanceM.toLocaleString('vi-VN')} m</p>
                )}
                <p className="mt-1 text-xs text-on-surface-variant">
                  Nguồn:{' '}
                  {amenity.sourceUrl ? (
                    <a
                      className="text-primary underline"
                      href={amenity.sourceUrl}
                      rel="nofollow noopener noreferrer"
                      target="_blank"
                    >
                      {amenity.sourceName}
                    </a>
                  ) : (
                    amenity.sourceName
                  )}{' '}
                  · kiểm tra {new Date(amenity.checkedAt).toLocaleDateString('vi-VN')}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <ListingStrip
        title="Tin đăng trong dự án"
        params={{ project: project.id }}
        moreHref={`/search?project=${project.id}`}
      />
    </div>
  );
}
