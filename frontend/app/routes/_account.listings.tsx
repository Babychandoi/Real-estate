import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlarmClock, Eye, EyeOff, FileUp, ImageOff, Pencil, PlusCircle, RefreshCw, Send, Users } from 'lucide-react';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Badge, type BadgeVariant } from '@/shared/ui/Badge';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Money } from '@/shared/ui/Money';
import { Pagination } from '@/shared/ui/Pagination';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Tabs } from '@/shared/ui/Tabs';
import { useToast } from '@/shared/ui/Toast';
import { useAuth } from '@/shared/auth/AuthContext';
import { ROLES } from '@/shared/auth/roles';
import { validationMessage } from '@/shared/types/problem-details';
import { listingPath } from '@/entities/listing/model/seo';
import {
  confirmAvailability,
  fetchMyListings,
  renewListing,
  setHidden,
  submitListing,
  type MyListingItem,
  type MyListingsPage as Page,
  type StatusTab,
  type VersionSummary,
} from '@/features/my-listings/api';
import { ImportDialog } from '@/features/my-listings/ImportDialog';

const TABS: Array<{ id: StatusTab; label: string }> = [
  { id: 'ALL', label: 'Tất cả' },
  { id: 'ACTIVE', label: 'Đang hiển thị' },
  { id: 'PENDING_REVIEW', label: 'Chờ duyệt' },
  { id: 'DRAFT', label: 'Nháp' },
  { id: 'REJECTED', label: 'Bị từ chối' },
  { id: 'EXPIRED', label: 'Hết hạn' },
  { id: 'PAUSED', label: 'Đã ẩn' },
  { id: 'LOCKED', label: 'Bị khóa' },
];
const STATUS_BADGE: Record<string, { label: string; variant: BadgeVariant }> = {
  ACTIVE: { label: 'Đang hiển thị', variant: 'success' },
  PENDING_REVIEW: { label: 'Chờ duyệt', variant: 'info' },
  DRAFT: { label: 'Nháp', variant: 'neutral' },
  REJECTED: { label: 'Bị từ chối', variant: 'error' },
  EXPIRED: { label: 'Hết hạn', variant: 'warning' },
  PAUSED: { label: 'Đã ẩn', variant: 'neutral' },
  LOCKED: { label: 'Bị khóa', variant: 'error' },
};
const EDIT_LABEL: Record<VersionSummary['status'], string> = {
  DRAFT: 'Bản nháp chưa gửi',
  SUBMITTED: 'Bản sửa chờ duyệt',
  APPROVED: 'Bản đã duyệt',
  REJECTED: 'Bản sửa bị từ chối',
};
const PAGE_SIZE = 10;
const date = (value: string | null) => (value ? new Date(value).toLocaleDateString('vi-VN') : '');

function isTab(value: string | null): value is StatusTab {
  return TABS.some((tab) => tab.id === value);
}

function ListingRow({
  item,
  onAction,
}: {
  item: MyListingItem;
  onAction: (run: () => Promise<unknown>, done: string) => void;
}) {
  const shown = item.publicVersion ?? item.pendingEdit;
  const badge = STATUS_BADGE[item.status];
  const f = item.freshness;
  const title = shown?.title || 'Tin chưa có tiêu đề';
  return (
    <li
      className="rounded-xl border border-outline-variant bg-surface p-4"
      data-testid="my-listing"
      data-listing-id={item.id}
    >
      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="aspect-video w-full shrink-0 overflow-hidden rounded-lg bg-surface-container sm:w-40">
          {item.thumbnailUrl ? (
            <img src={item.thumbnailUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
          ) : (
            <div className="flex h-full items-center justify-center text-on-surface-variant">
              <ImageOff className="h-6 w-6" aria-hidden="true" />
              <span className="sr-only">Chưa có ảnh</span>
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={badge.variant}>{badge.label}</Badge>
            {item.source === 'IMPORT' && <Badge variant="neutral">Nhập từ tệp</Badge>}
            <span className="text-label text-on-surface-variant">Tạo {date(item.createdAt)}</span>
          </div>
          <h3 className="mt-1 truncate text-body font-semibold text-on-surface">
            {item.publicVersion && item.status === 'ACTIVE' ? (
              <Link to={listingPath({ slug: item.slug, title })}>{title}</Link>
            ) : (
              title
            )}
          </h3>

          {item.publicVersion && (
            <p className="mt-1 text-body-sm text-on-surface-variant">
              <span className="font-medium text-on-surface">Bản đang hiển thị:</span>{' '}
              <Money price={item.publicVersion.price} /> · {item.publicVersion.areaM2} m² · duyệt{' '}
              {date(item.publicVersion.moderatedAt)}
            </p>
          )}
          {item.pendingEdit && (
            <div
              className={`mt-2 rounded-lg p-2 text-body-sm ${item.pendingEdit.status === 'REJECTED' ? 'bg-error-container' : 'bg-surface-container'}`}
            >
              <p>
                <span className="font-medium">
                  {item.publicVersion ? EDIT_LABEL[item.pendingEdit.status] : 'Nội dung'}:
                </span>{' '}
                {item.pendingEdit.title} · <Money price={item.pendingEdit.price} />
              </p>
              {item.pendingEdit.rejectionReason && (
                <p className="mt-1">Lý do từ chối: {item.pendingEdit.rejectionReason}</p>
              )}
            </div>
          )}

          {f.soldCheckDueAt && (
            <p className="mt-2 flex items-center gap-1 text-body-sm text-error" role="note">
              <AlarmClock className="h-4 w-4" aria-hidden="true" /> Có báo cáo đã bán: xác nhận còn hàng trước{' '}
              {new Date(f.soldCheckDueAt).toLocaleString('vi-VN')} để tin không bị tạm ẩn.
            </p>
          )}
          {item.status === 'ACTIVE' && f.expiresAt && (
            <p
              className={`mt-2 text-body-sm ${f.expiringSoon ? 'text-warning-on-container' : 'text-on-surface-variant'}`}
            >
              {f.expiringSoon ? 'Sắp hết hạn: ' : 'Hiển thị đến '}
              {date(f.expiresAt)}
              {f.daysUntilExpiry != null && ` (còn ${f.daysUntilExpiry} ngày)`}
            </p>
          )}
          {item.status === 'EXPIRED' && (
            <p className="mt-2 text-body-sm text-on-surface-variant">
              {f.renewable
                ? 'Gia hạn trong 30 ngày sau khi hết hạn để hiển thị lại ngay, không cần duyệt lại.'
                : 'Tin cần kiểm tra lại nội dung và gửi duyệt để hiển thị lại.'}
            </p>
          )}
          <p className="mt-2 text-label text-on-surface-variant">
            Chất lượng {item.quality.passed}/{item.quality.total} · {item.leadCount} khách quan tâm
          </p>

          <div className="mt-3 flex flex-wrap gap-2">
            {item.status === 'ACTIVE' && (
              <Button
                size="sm"
                leftIcon={<RefreshCw className="h-4 w-4" />}
                onClick={() => onAction(() => confirmAvailability(item.id), 'Đã xác nhận còn hàng thêm 45 ngày.')}
              >
                Xác nhận còn hàng
              </Button>
            )}
            {item.status === 'EXPIRED' && f.renewable && (
              <Button
                size="sm"
                leftIcon={<RefreshCw className="h-4 w-4" />}
                onClick={() => onAction(() => renewListing(item.id), 'Đã gia hạn tin.')}
              >
                Gia hạn
              </Button>
            )}
            {(item.status === 'DRAFT' ||
              (item.status === 'REJECTED' && item.pendingEdit?.status === 'DRAFT') ||
              (item.pendingEdit?.status === 'DRAFT' && item.publicVersion)) && (
              <Button
                size="sm"
                variant="outline"
                leftIcon={<Send className="h-4 w-4" />}
                onClick={() => onAction(() => submitListing(item.id), 'Đã gửi duyệt.')}
              >
                Gửi duyệt
              </Button>
            )}
            {item.status !== 'LOCKED' && (
              <ButtonLink
                size="sm"
                variant="outline"
                to={`/listings/new?edit=${item.id}`}
                leftIcon={<Pencil className="h-4 w-4" />}
              >
                Sửa
              </ButtonLink>
            )}
            {item.status === 'ACTIVE' && (
              <Button
                size="sm"
                variant="ghost"
                leftIcon={<EyeOff className="h-4 w-4" />}
                onClick={() => onAction(() => setHidden(item.id, true), 'Đã ẩn tin.')}
              >
                Ẩn tin
              </Button>
            )}
            {item.status === 'PAUSED' && (
              <Button
                size="sm"
                variant="ghost"
                leftIcon={<Eye className="h-4 w-4" />}
                onClick={() => onAction(() => setHidden(item.id, false), 'Đã hiện lại tin.')}
              >
                Hiện tin
              </Button>
            )}
            {item.leadCount > 0 && (
              <ButtonLink
                size="sm"
                variant="ghost"
                to={`/my-leads?listingId=${item.id}`}
                leftIcon={<Users className="h-4 w-4" />}
              >
                Xem khách quan tâm
              </ButtonLink>
            )}
          </div>
        </div>
      </div>
    </li>
  );
}

export const MyListingsPage: React.FC = () => {
  const { user } = useAuth();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const tab: StatusTab = isTab(params.get('status')) ? (params.get('status') as StatusTab) : 'ALL';
  const page = Math.max(0, Number(params.get('page') ?? '0') || 0);
  const [data, setData] = useState<Page | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  const requestSeq = useRef(0);
  const load = useCallback(async () => {
    // Only the latest request may update the page (tab/page clicks can overtake each other).
    const seq = ++requestSeq.current;
    setLoading(true);
    setFailed(false);
    try {
      const result = await fetchMyListings(tab, page, PAGE_SIZE);
      if (seq === requestSeq.current) setData(result);
    } catch {
      if (seq === requestSeq.current) setFailed(true);
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, [tab, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const navigate = (nextTab: StatusTab, nextPage: number) => {
    const next = new URLSearchParams();
    if (nextTab !== 'ALL') next.set('status', nextTab);
    if (nextPage > 0) next.set('page', String(nextPage));
    setParams(next);
  };

  const onAction = (run: () => Promise<unknown>, done: string) => {
    run()
      .then(() => {
        toast.show({ kind: 'success', title: done });
        return load();
      })
      .catch((error) =>
        toast.show({ kind: 'error', title: validationMessage(error, 'Chưa thực hiện được, vui lòng thử lại.') }),
      );
  };

  const isOwner = user?.role === ROLES.OWNER;
  const expiring =
    data?.items.filter((item) => item.freshness.expiringSoon || item.freshness.soldCheckDueAt).length ?? 0;

  const list = failed ? (
    <ErrorState title="Không tải được danh sách tin" onRetry={() => void load()} />
  ) : loading && !data ? (
    <div className="mt-4 flex flex-col gap-3" role="status" aria-label="Đang tải">
      {[0, 1, 2].map((key) => (
        <Skeleton key={key} className="h-32 w-full" />
      ))}
    </div>
  ) : data && data.items.length === 0 ? (
    <EmptyState
      className="mt-6"
      title={tab === 'ALL' ? 'Bạn chưa có tin đăng' : 'Không có tin ở trạng thái này'}
      description={
        tab === 'ALL' && isOwner ? (
          <ol className="mt-2 list-decimal pl-5 text-left">
            <li>Đăng tin với ảnh thật và giá đúng; tin được lưu nháp tự động.</li>
            <li>Tin được kiểm duyệt trước khi hiển thị.</li>
            <li>Mỗi 45 ngày, xác nhận còn hàng để tin tiếp tục hiển thị.</li>
            <li>Khách quan tâm gửi yêu cầu trong mục Khách quan tâm, không cần công khai số điện thoại.</li>
          </ol>
        ) : undefined
      }
      actions={
        tab === 'ALL' ? (
          <ButtonLink to="/listings/new" leftIcon={<PlusCircle className="h-4 w-4" />}>
            Đăng tin đầu tiên
          </ButtonLink>
        ) : undefined
      }
    />
  ) : (
    data && (
      <>
        <ul className="mt-4 flex flex-col gap-3" aria-busy={loading || undefined}>
          {data.items.map((item) => (
            <ListingRow key={item.id} item={item} onAction={onAction} />
          ))}
        </ul>
        {data.totalPages > 1 && (
          <Pagination
            className="mt-6"
            page={page + 1}
            pageCount={data.totalPages}
            onPageChange={(next) => navigate(tab, next - 1)}
            label="Trang tin đăng"
          />
        )}
      </>
    )
  );

  return (
    <div className="ndc-page py-8" data-ready={!loading ? 'true' : undefined}>
      <div className="ndc-section-heading">
        <div>
          <h1 className="text-3xl font-semibold">Tin đăng của tôi</h1>
          <p>
            {isOwner ? 'Chủ nhà tự đăng' : 'Quản lý tin'} · {data ? `${data.counts.ALL} tin` : 'đang tải'} · Theo dõi
            kiểm duyệt, cập nhật nội dung và quản lý hiển thị.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" leftIcon={<FileUp className="h-4 w-4" />} onClick={() => setImportOpen(true)}>
            Nhập từ CSV
          </Button>
          <ButtonLink to="/listings/new" leftIcon={<PlusCircle className="h-4 w-4" />}>
            Đăng tin
          </ButtonLink>
        </div>
      </div>
      <div className="mb-6 mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {(
          [
            ['Tổng tin', 'ALL'],
            ['Đang hiển thị', 'ACTIVE'],
            ['Chờ duyệt', 'PENDING_REVIEW'],
            ['Bản nháp', 'DRAFT'],
          ] as const
        ).map(([name, key]) => (
          <div key={key} className="rounded-xl border bg-white p-5">
            <p className="text-sm text-on-surface-variant">{name}</p>
            <strong className="mt-2 block text-3xl text-primary">{data ? (data.counts[key] ?? 0) : '—'}</strong>
          </div>
        ))}
      </div>
      {expiring > 0 && (
        <p className="mt-4 rounded-lg bg-warning-container p-3 text-body-sm text-warning-on-container" role="note">
          {expiring} tin trên trang này cần bạn xác nhận còn hàng.
        </p>
      )}
      <Tabs
        className="mt-4"
        label="Lọc theo trạng thái"
        value={tab}
        onChange={(next) => navigate(next, 0)}
        items={TABS.map((item) => ({
          id: item.id,
          label: item.label,
          count: data?.counts[item.id],
          content: item.id === tab ? list : null,
        }))}
      />
      <ImportDialog open={importOpen} onClose={() => setImportOpen(false)} onImported={() => void load()} />
    </div>
  );
};

export default MyListingsPage;
