import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Building2, Info, MapPin } from 'lucide-react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import type { AreaCard, Inventory, ProjectCard } from '@/entities/content/model';
import { PROJECT_STATUS_LABELS } from '@/entities/content/model';
import { FavoriteButton } from '@/features/engagement/FavoriteButton';
import { formatVndCompact } from '@/shared/format/money';
import { ListingSkeleton, StatePanel } from '@/shared/ui/Feedback';

const dateTime = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Asia/Ho_Chi_Minh',
});

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : dateTime.format(date);
}

/**
 * Asking-price statistics of an area or a project (P-06): count per purpose, the median only when the API says there
 * are enough listings, and always the method, sample size and time. Never presented as transaction prices.
 */
export function InventoryPanel({ inventory, scope }: { inventory: Inventory; scope: string }) {
  const statistics = inventory.statistics;
  return (
    <section
      aria-labelledby="inventory-heading"
      className="rounded-2xl border border-outline-variant/40 bg-white p-5 sm:p-6"
    >
      <h2 id="inventory-heading" className="text-lg font-semibold text-on-surface">
        Tin đang hiển thị tại {scope}
      </h2>
      {statistics.length === 0 ? (
        <p className="mt-3 text-sm text-on-surface-variant">Chưa có tin đang hiển thị trong phạm vi này.</p>
      ) : (
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          {statistics.map((stat) => {
            const sale = stat.purpose === 'SALE';
            return (
              <div key={stat.purpose} className="rounded-xl bg-surface-container-low p-4">
                <dt className="text-sm font-medium text-on-surface-variant">{sale ? 'Tin bán' : 'Tin cho thuê'}</dt>
                <dd className="mt-1 text-2xl font-semibold text-on-surface">
                  {stat.count.toLocaleString('vi-VN')} tin
                </dd>
                <dd className="mt-2 text-sm text-on-surface">
                  {stat.median == null ? (
                    <span className="text-on-surface-variant">Chưa đủ {stat.minSamples} tin để tính giá trung vị.</span>
                  ) : (
                    <>
                      Giá chào trung vị:{' '}
                      <strong>
                        {formatVndCompact(stat.median)}
                        {sale ? '/m²' : '/tháng'}
                      </strong>
                    </>
                  )}
                </dd>
              </div>
            );
          })}
        </dl>
      )}
      <details className="mt-4 text-sm text-on-surface-variant">
        <summary className="inline-flex min-h-11 cursor-pointer items-center gap-2 font-medium text-primary">
          <Info className="h-4 w-4" aria-hidden="true" /> Cách tính
        </summary>
        <ul className="mt-2 list-disc space-y-1 pl-5">
          {statistics.map((stat) => (
            <li key={stat.purpose}>{stat.method}</li>
          ))}
          <li>
            Chỉ hiển thị trung vị khi có ít nhất {statistics[0]?.minSamples ?? 5} tin. Số liệu tính lúc{' '}
            {formatDateTime(inventory.dataAsOf)}
            {inventory.lastListingUpdate
              ? `; tin cập nhật gần nhất ${formatDateTime(inventory.lastListingUpdate)}`
              : ''}
            .
          </li>
        </ul>
      </details>
    </section>
  );
}

/** Newest listings for a search (the public search API with the same filters as `/search`). */
export function ListingStrip({
  title,
  params,
  moreHref,
  size = 6,
}: {
  title: string;
  params: Record<string, string>;
  moreHref: string;
  size?: number;
}) {
  const key = JSON.stringify(params);
  const [items, setItems] = useState<ListingSummaryV2[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setItems(null);
    setFailed(false);
    const query = new URLSearchParams({ ...(JSON.parse(key) as Record<string, string>), size: String(size) });
    listingV2Api
      .search(query, abort.signal)
      .then((page) => setItems(page.items))
      .catch(() => {
        if (!abort.signal.aborted) setFailed(true);
      });
    return () => abort.abort();
  }, [key, size, attempt]);

  return (
    <section className="mt-10" aria-label={title}>
      <div className="ndc-section-heading">
        <div>
          <h2>{title}</h2>
        </div>
        <Link to={moreHref} className="ndc-text-link">
          Xem tất cả <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </div>
      {failed ? (
        <StatePanel error onRetry={() => setAttempt((value) => value + 1)} />
      ) : items == null ? (
        <ListingSkeleton count={Math.min(size, 3)} />
      ) : items.length === 0 ? (
        <StatePanel title="Chưa có tin trong mục này" description="Bạn có thể mở rộng khu vực hoặc đổi nhu cầu." />
      ) : (
        <div className="ndc-listing-grid">
          {items.map((listing) => (
            <ListingCard
              key={listing.id}
              listing={listing}
              actions={<FavoriteButton listingId={listing.id} title={listing.title} />}
            />
          ))}
        </div>
      )}
    </section>
  );
}

export function ProjectCardList({ projects }: { projects: ProjectCard[] }) {
  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {projects.map((project) => (
        <li key={project.slug} className="min-w-0">
          <Link
            to={`/du-an/${project.slug}`}
            className="flex h-full min-h-11 min-w-0 flex-col gap-2 rounded-2xl border border-outline-variant/40 bg-white p-5 [overflow-wrap:anywhere] transition hover:border-primary/40 hover:shadow-md"
          >
            <span className="flex items-center gap-2 text-xs font-medium text-on-surface-variant">
              <Building2 className="h-4 w-4 text-primary" aria-hidden="true" />
              {PROJECT_STATUS_LABELS[project.status] ?? 'Dự án'}
            </span>
            <span className="text-base font-semibold text-on-surface">{project.name}</span>
            <span className="text-sm text-on-surface-variant">
              {project.districtName ? `${project.districtName} · ` : ''}
              {project.activeListings > 0
                ? `${project.activeListings.toLocaleString('vi-VN')} tin đang hiển thị`
                : 'Chưa có tin'}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function AreaLinks({ areas }: { areas: AreaCard[] }) {
  return (
    <ul className="flex flex-wrap gap-2">
      {areas.map((area) => (
        <li key={area.slug}>
          <Link
            to={`/khu-vuc/${area.slug}`}
            className="inline-flex min-h-11 max-w-full items-center gap-2 rounded-full border [overflow-wrap:anywhere] border-outline-variant/40 bg-white px-4 text-sm font-medium text-on-surface hover:border-primary/40 hover:text-primary"
          >
            <MapPin className="h-4 w-4 text-primary" aria-hidden="true" />
            {area.name}
            <span className="text-on-surface-variant">
              {area.activeListings > 0 ? area.activeListings.toLocaleString('vi-VN') : 'chưa có tin'}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function Breadcrumbs({ items }: { items: { label: string; to?: string }[] }) {
  return (
    <nav aria-label="Đường dẫn" className="mb-4 text-sm text-on-surface-variant">
      <ol className="flex flex-wrap items-center gap-1">
        {items.map((item, index) => (
          <li key={item.label} className="flex items-center gap-1">
            {index > 0 && <span aria-hidden="true">/</span>}
            {item.to ? (
              <Link
                className="inline-flex min-h-11 min-w-11 items-center justify-center hover:text-primary"
                to={item.to}
              >
                {item.label}
              </Link>
            ) : (
              <span aria-current="page">{item.label}</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function NotFoundState({ gone, what }: { gone: boolean; what: string }) {
  return (
    <div className="ndc-page py-16" data-ready="true">
      <StatePanel
        headingLevel={1}
        title={gone ? `${what} không còn hiển thị` : `Không tìm thấy ${what.toLowerCase()}`}
        description={
          gone
            ? 'Nội dung này đã được gỡ khỏi trang công khai.'
            : 'Đường dẫn không đúng hoặc nội dung chưa được công khai.'
        }
        action={
          <Link to="/search" className="ndc-primary-link">
            Tìm nhà
          </Link>
        }
      />
    </div>
  );
}
