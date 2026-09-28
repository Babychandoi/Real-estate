import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, BadgeCheck, Building2, CalendarDays, MessageSquareReply, ShieldQuestion } from 'lucide-react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import {
  sellerRoleLabel,
  type ListingSummaryV2,
  type SearchResponseV2,
  type SellerProfileV2,
} from '@/entities/listing/model/v2';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { FavoriteButton } from '@/features/engagement/FavoriteButton';
import { useAuth } from '@/shared/auth/AuthContext';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { ApiProblemException } from '@/shared/types/problem-details';
import { Avatar } from '@/shared/ui/Avatar';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LoadMore, type ResultTotal } from '@/shared/ui/Pagination';
import { Skeleton } from '@/shared/ui/Skeleton';
import { TrustBadge } from '@/shared/ui/TrustBadge';

const monthYear = new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' });

/**
 * Public seller page (UI-04, F08.5): who posts (role, identity check with its scope and dates) kept apart from what
 * was checked per listing (ownership documents), and the whole inventory with cursor paging (no 60-listing cap).
 * Response statistics are shown only when the API has enough answered requests to publish them.
 */
export function SellerProfilePage() {
  const { sellerId = '' } = useParams<{ sellerId: string }>();
  const { user } = useAuth();
  const [profile, setProfile] = useState<SellerProfileV2 | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading');
  const [items, setItems] = useState<ListingSummaryV2[]>([]);
  const [page, setPage] = useState<Pick<SearchResponseV2, 'pageInfo'> & { total: ResultTotal | null }>({
    pageInfo: { hasNext: false, nextCursor: null, size: 24 },
    total: null,
  });
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<string | null>(null);

  useEffect(() => {
    const abort = new AbortController();
    setState('loading');
    setItems([]);
    Promise.all([
      listingV2Api.seller(sellerId, abort.signal),
      listingV2Api.sellerListings(sellerId, null, 24, abort.signal),
    ])
      .then(([nextProfile, first]) => {
        setProfile(nextProfile);
        setItems(first.items);
        setPage({ pageInfo: first.pageInfo, total: first.total });
        setState('ready');
      })
      .catch((error: unknown) => {
        if (abort.signal.aborted) return;
        const status = error instanceof ApiProblemException ? error.problem.status : 0;
        setState(status === 404 || status === 400 ? 'missing' : 'error');
      });
    return () => abort.abort();
  }, [sellerId]);

  const loadMore = useCallback(async () => {
    if (!page.pageInfo.nextCursor) return;
    setLoadingMore(true);
    setMoreError(null);
    try {
      const next = await listingV2Api.sellerListings(sellerId, page.pageInfo.nextCursor);
      setItems((previous) => [
        ...previous,
        ...next.items.filter((item) => !previous.some((known) => known.id === item.id)),
      ]);
      setPage((previous) => ({ pageInfo: next.pageInfo, total: previous.total }));
    } catch {
      setMoreError('Không tải thêm được. Vui lòng thử lại.');
    } finally {
      setLoadingMore(false);
    }
  }, [page.pageInfo.nextCursor, sellerId]);

  useDocumentMeta(
    profile
      ? {
          title: `${profile.name || 'Người đăng'} – ${sellerRoleLabel(profile.role)} | Nhà Đất Chuẩn`,
          description: `Các tin đang hiển thị của ${profile.name || 'người đăng'} trên Nhà Đất Chuẩn.`,
        }
      : state === 'missing'
        ? { title: 'Không tìm thấy người đăng | Nhà Đất Chuẩn', robots: 'noindex' }
        : null,
  );

  if (state === 'loading') {
    return (
      <div
        className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-10"
        data-ready="false"
        role="status"
        aria-label="Đang tải"
      >
        <Skeleton className="h-40 rounded-card" />
        <Skeleton className="h-72 rounded-card" />
      </div>
    );
  }
  if (state !== 'ready' || !profile) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16 text-center" data-ready="true">
        <ShieldQuestion className="mx-auto h-12 w-12 text-outline" aria-hidden="true" />
        <h1 className="mt-3 text-xl font-bold text-on-surface">
          {state === 'missing' ? 'Không tìm thấy người đăng' : 'Không tải được trang người đăng'}
        </h1>
        <p className="mt-2 text-sm text-on-surface-variant">
          {state === 'missing'
            ? 'Tài khoản này không tồn tại hoặc đã ngừng hoạt động.'
            : 'Vui lòng thử lại sau ít phút.'}
        </p>
        <Link to="/search" className="mt-5 inline-flex min-h-11 items-center gap-2 font-semibold text-primary">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Quay lại tìm kiếm
        </Link>
      </div>
    );
  }

  const isMe = user?.id === profile.id;
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 md:px-6" data-ready="true">
      <button
        type="button"
        onClick={() => (window.history.length > 1 ? window.history.back() : undefined)}
        className="inline-flex min-h-11 items-center gap-2 self-start text-sm font-medium text-on-surface-variant hover:text-on-surface"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Quay lại
      </button>

      <section className="flex flex-col gap-5 rounded-2xl border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-sm sm:flex-row sm:items-start">
        <Avatar name={profile.name} src={profile.avatarUrl} size="xl" className="ring-4 ring-primary/10" />
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <div>
            <h1 className="text-2xl font-bold text-on-surface">{profile.name || 'Người đăng'}</h1>
            <p className="text-sm font-semibold text-primary">{sellerRoleLabel(profile.role)}</p>
          </div>
          <div className="flex flex-col gap-1">
            <TrustBadge
              kind="identity"
              status={profile.identity.status}
              checkedAt={profile.identity.checkedAt}
              expiresAt={profile.identity.expiresAt}
              detailed
            />
          </div>
          <dl className="grid gap-x-6 gap-y-2 text-sm text-on-surface-variant sm:grid-cols-2">
            <div className="flex items-center gap-2">
              <Building2 className="h-4 w-4" aria-hidden="true" />
              <dt className="sr-only">Tin đang hiển thị</dt>
              <dd>
                <strong className="text-on-surface">{profile.activeListingCount.toLocaleString('vi-VN')}</strong> tin
                đang hiển thị
              </dd>
            </div>
            <div className="flex items-center gap-2">
              <BadgeCheck className="h-4 w-4 text-success" aria-hidden="true" />
              <dt className="sr-only">Tin đã đối chiếu giấy tờ</dt>
              <dd>
                {profile.ownershipVerifiedListingCount.toLocaleString('vi-VN')} tin đã đối chiếu giấy tờ chủ sở hữu (xét
                riêng từng tin)
              </dd>
            </div>
            <div className="flex items-center gap-2">
              <CalendarDays className="h-4 w-4" aria-hidden="true" />
              <dt className="sr-only">Tham gia</dt>
              <dd data-volatile="date">Tham gia từ {monthYear.format(new Date(profile.memberSince))}</dd>
            </div>
            {profile.responseStats && (
              <div className="flex items-center gap-2">
                <MessageSquareReply className="h-4 w-4" aria-hidden="true" />
                <dt className="sr-only">Thời gian phản hồi</dt>
                <dd>
                  Phản hồi yêu cầu đầu tiên sau khoảng {Math.round(profile.responseStats.medianFirstResponseMinutes)}{' '}
                  phút (trung vị của {profile.responseStats.sampleSize} yêu cầu)
                </dd>
              </div>
            )}
          </dl>
          <p className="text-xs text-on-surface-variant">
            Số điện thoại và email không hiển thị công khai. Hãy mở một tin đăng và gửi yêu cầu liên hệ để trao đổi trực
            tiếp.
          </p>
          {isMe && (
            <p className="text-xs text-on-surface-variant">
              Đây là trang công khai của bạn; khách xem thấy đúng nội dung này.
            </p>
          )}
        </div>
        {isMe && (
          <Link
            to="/account"
            className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-lg border border-primary/30 px-4 text-sm font-bold text-primary hover:bg-primary/5"
          >
            Chỉnh sửa trang cá nhân
          </Link>
        )}
      </section>

      <section aria-labelledby="seller-listings-heading" className="flex flex-col gap-4">
        <h2 id="seller-listings-heading" className="text-lg font-bold text-on-surface">
          Tin đang hiển thị
        </h2>
        {items.length === 0 ? (
          <EmptyState title="Người đăng chưa có tin đang hiển thị" headingLevel={3} />
        ) : (
          <>
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:gap-5 xl:grid-cols-3">
              {items.map((item) => (
                <li key={item.id}>
                  <ListingCard listing={item} actions={<FavoriteButton listingId={item.id} title={item.title} />} />
                </li>
              ))}
            </ul>
            <LoadMore
              loadedCount={items.length}
              hasNext={page.pageInfo.hasNext}
              loading={loadingMore}
              onLoadMore={loadMore}
              total={page.total}
              noun="tin"
              error={moreError}
            />
          </>
        )}
      </section>
    </div>
  );
}
