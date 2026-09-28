import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Bath,
  BedDouble,
  Building,
  Compass,
  FileText,
  Flag,
  History,
  Home,
  Lock,
  MapPin,
  Maximize2,
  MessageSquare,
  Route as RouteIcon,
  Ruler,
  Sofa,
  Tag,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import { listingDocumentMeta, listingIdFromRoute, listingPath } from '@/entities/listing/model/seo';
import {
  FURNISHING_LABELS,
  formatArea,
  formatDate,
  propertyTypeLabel,
  purposeLabel,
  sellerRoleLabel,
  type GoneListingProblem,
  type ListingDetailV2,
  type ListingSummaryV2,
  type PriceHistoryV2,
} from '@/entities/listing/model/v2';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import type { UserKycProfile } from '@/entities/verification/model/types';
import { CompareToggleButton } from '@/features/compare/CompareControls';
import { compareItemFromSummary } from '@/features/compare/compareStore';
import { Gallery } from '@/features/listing-detail/Gallery';
import { LeadConsultationModal } from '@/features/lead/ui/LeadConsultationModal';
import { track } from '@/shared/analytics/track';
import { apiClient } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';
import { formatMoney, formatRentTerms } from '@/shared/format/money';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { ApiProblemException } from '@/shared/types/problem-details';
import { Avatar } from '@/shared/ui/Avatar';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Dialog } from '@/shared/ui/Dialog';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Money, UnitPriceText } from '@/shared/ui/Money';
import { Skeleton } from '@/shared/ui/Skeleton';
import { TrustBadge, TrustPanel } from '@/shared/ui/TrustBadge';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; listing: ListingDetailV2 }
  | { kind: 'gone'; slug: string; title?: string }
  | { kind: 'missing' }
  | { kind: 'error' };

/** Public listing detail on API v2 (UI-03): gallery, price with its period, facts, trust scopes, price history,
 * contact states, similar listings, 404/410 pages and route metadata. */
export const ListingDetailPage: React.FC = () => {
  const { listingId: route = '' } = useParams<{ listingId: string }>();
  const legacyId = listingIdFromRoute(route);
  const location = useLocation();
  const navigate = useNavigate();
  const fromSearch = (location.state as { fromSearch?: string } | null)?.fromSearch;
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [reload, setReload] = useState(0);
  const [history, setHistory] = useState<PriceHistoryV2 | null>(null);
  const [similar, setSimilar] = useState<ListingSummaryV2[] | null>(null);

  // A new listing starts at the top (the list page restores its own position on "back").
  useLayoutEffect(() => {
    window.scrollTo(0, 0);
  }, [route]);

  useEffect(() => {
    const abort = new AbortController();
    setState({ kind: 'loading' });
    setHistory(null);
    setSimilar(null);
    listingV2Api
      .detail(legacyId ?? route, abort.signal)
      .then((listing) => setState({ kind: 'ready', listing }))
      .catch((error: unknown) => {
        if (abort.signal.aborted) return;
        const problem = error instanceof ApiProblemException ? error.problem : null;
        if (problem?.status === 410) {
          const gone = problem as unknown as GoneListingProblem;
          setState({ kind: 'gone', slug: gone.slug, title: gone.listingTitle });
        } else if (problem?.status === 404) setState({ kind: 'missing' });
        else setState({ kind: 'error' });
      });
    return () => abort.abort();
  }, [legacyId, route, reload]);

  const listing = state.kind === 'ready' ? state.listing : null;

  useEffect(() => {
    if (!listing) return;
    const abort = new AbortController();
    listingV2Api
      .priceHistory(listing.id, abort.signal)
      .then(setHistory)
      .catch(() => !abort.signal.aborted && setHistory({ listingId: listing.id, purpose: null, points: [] }));
    listingV2Api
      .similar(listing.id, 6, abort.signal)
      .then(setSimilar)
      .catch(() => !abort.signal.aborted && setSimilar([]));
    track(
      'listing_detail_viewed',
      {
        purpose: listing.purpose,
        propertyType: listing.propertyType,
        ...(listing.location.districtCode && /^\d{3}$/.test(listing.location.districtCode)
          ? { district: listing.location.districtCode }
          : {}),
      },
      { listingId: listing.id },
    );
    // Legacy UUID links land on the canonical slug URL without another request.
    const canonical = listingPath(listing);
    if (window.location.pathname !== canonical) window.history.replaceState(window.history.state, '', canonical);
    return () => abort.abort();
  }, [listing]);

  useDocumentMeta(
    listing
      ? listingDocumentMeta(listing, window.location.origin)
      : state.kind === 'loading'
        ? null
        : {
            title: `${state.kind === 'gone' ? 'Tin không còn hiển thị' : 'Không tìm thấy bất động sản'} | Nhà Đất Chuẩn`,
            robots: 'noindex',
          },
  );

  const backLink = (
    <Link
      to={fromSearch ?? '/search'}
      onClick={(event) => {
        // Coming from the result list: go back in history so the list restores its pages and scroll position.
        if (fromSearch) {
          event.preventDefault();
          navigate(-1);
        }
      }}
      className="inline-flex min-h-11 items-center gap-1.5 text-body-sm font-semibold text-on-surface-variant hover:text-primary"
    >
      <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Quay lại danh sách
    </Link>
  );

  if (state.kind === 'loading') {
    return (
      <div
        className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-6"
        data-ready="false"
        role="status"
        aria-label="Đang tải tin đăng"
      >
        <Skeleton className="aspect-[16/9] w-full rounded-card" />
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  if (state.kind === 'missing' || state.kind === 'gone') {
    return (
      <div className="mx-auto my-16 flex max-w-md flex-col items-center gap-3 px-4 text-center" data-ready="true">
        <h1 className="text-headline-md text-on-surface">
          {state.kind === 'gone' ? 'Tin không còn hiển thị' : 'Không tìm thấy bất động sản'}
        </h1>
        {state.kind === 'gone' ? (
          <p className="text-body-sm text-on-surface-variant">
            {state.title ? `“${state.title}”` : 'Tin đăng này'} đã được ẩn, hết hạn hoặc bị gỡ nên không còn xem được.
            Bạn có thể tìm các tin tương tự đang hiển thị.
          </p>
        ) : (
          <p className="text-body-sm text-on-surface-variant">
            Đường dẫn không đúng hoặc tin chưa từng được công khai.
          </p>
        )}
        <div className="flex flex-wrap justify-center gap-3">
          <ButtonLink to="/search">Tìm tin khác</ButtonLink>
          <ButtonLink to="/" variant="ghost">
            Quay lại trang chủ
          </ButtonLink>
        </div>
      </div>
    );
  }
  if (state.kind === 'error' || !listing) {
    return (
      <div className="mx-auto my-12 max-w-md px-4" data-ready="true">
        <ErrorState
          title="Không tải được tin đăng"
          description="Kết nối đang gián đoạn. Vui lòng thử lại."
          onRetry={() => setReload((value) => value + 1)}
          headingLevel={2}
        />
      </div>
    );
  }

  return (
    <ListingDetailView
      listing={listing}
      history={history}
      similar={similar}
      backLink={backLink}
      ready={history !== null && similar !== null}
    />
  );
};

function ListingDetailView({
  listing,
  history,
  similar,
  backLink,
  ready,
}: {
  listing: ListingDetailV2;
  history: PriceHistoryV2 | null;
  similar: ListingSummaryV2[] | null;
  backLink: React.ReactNode;
  ready: boolean;
}) {
  const { user, isAuthenticated, isPoster, setIsLoginModalOpen } = useAuth();
  const [kycStatus, setKycStatus] = useState<UserKycProfile['status'] | 'NONE' | 'LOADING'>('NONE');
  const [leadOpen, setLeadOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const isOwn = Boolean(user && user.id === listing.seller.id);
  const rent = formatRentTerms(listing.rentTerms);
  const contactRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!user) {
      setKycStatus('NONE');
      return;
    }
    setKycStatus('LOADING');
    apiClient<UserKycProfile>(`/kyc/user/${user.id}`)
      .then((profile) => setKycStatus(profile.status))
      .catch(() => setKycStatus('NONE'));
  }, [user]);

  const facts: Array<{ label: string; value: string | null; icon: typeof Maximize2 }> = [
    { label: 'Diện tích', value: formatArea(listing.areaM2), icon: Maximize2 },
    {
      label: 'Phòng ngủ',
      value: listing.facts.bedrooms != null ? `${listing.facts.bedrooms} phòng` : null,
      icon: BedDouble,
    },
    {
      label: 'Phòng tắm, vệ sinh',
      value: listing.facts.bathrooms != null ? `${listing.facts.bathrooms} phòng` : null,
      icon: Bath,
    },
    { label: 'Số tầng', value: listing.facts.floors != null ? `${listing.facts.floors} tầng` : null, icon: Building },
    { label: 'Mặt tiền', value: listing.facts.frontageM != null ? `${listing.facts.frontageM} m` : null, icon: Ruler },
    {
      label: 'Đường vào',
      value: listing.facts.roadWidthM != null ? `${listing.facts.roadWidthM} m` : null,
      icon: RouteIcon,
    },
    { label: 'Hướng', value: listing.facts.direction || null, icon: Compass },
    {
      label: 'Pháp lý',
      value: listing.legal
        ? listing.legal.label + (listing.facts.legalStatusText ? ` (${listing.facts.legalStatusText})` : '')
        : listing.facts.legalStatusText || null,
      icon: FileText,
    },
    { label: 'Nội thất', value: listing.furnishing ? FURNISHING_LABELS[listing.furnishing] : null, icon: Sofa },
  ];
  const place = [listing.location.addressSummary, listing.location.districtName].filter(Boolean).join(' · ');

  const contactAction = isOwn ? null : !isAuthenticated ? (
    <Button
      className="w-full"
      onClick={() => setIsLoginModalOpen(true)}
      leftIcon={<MessageSquare className="h-4 w-4" />}
    >
      Đăng nhập để liên hệ
    </Button>
  ) : kycStatus === 'VERIFIED' ? (
    <Button className="w-full" onClick={() => setLeadOpen(true)} leftIcon={<MessageSquare className="h-4 w-4" />}>
      Hẹn xem & nhận tư vấn
    </Button>
  ) : (
    <ButtonLink to="/kyc" className="w-full" leftIcon={<Lock className="h-4 w-4" />}>
      Xác minh eKYC để liên hệ
    </ButtonLink>
  );

  return (
    <div
      className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6 pb-28 md:px-8 lg:pb-10"
      data-ready={ready ? 'true' : 'false'}
    >
      <div className="flex flex-wrap items-center gap-4">
        {backLink}
        {!isOwn && (
          <button
            type="button"
            onClick={() => setReportOpen(true)}
            className="inline-flex min-h-11 items-center gap-1.5 text-body-sm font-semibold text-error"
          >
            <Flag className="h-4 w-4" aria-hidden="true" /> Báo cáo tin vi phạm
          </button>
        )}
      </div>

      <Gallery images={listing.images} title={listing.title} />

      <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-3">
        <div className="flex min-w-0 flex-col gap-6 lg:col-span-2">
          <header className="flex flex-col gap-2">
            <p className="text-label font-semibold text-primary">
              {purposeLabel(listing.purpose)} · {propertyTypeLabel(listing.propertyType)}
              {listing.project && <> · Dự án {listing.project.name}</>}
            </p>
            <h1 className="mt-1 text-xl font-bold leading-snug text-on-surface md:text-2xl">{listing.title}</h1>
            <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <Money price={listing.price} className="text-3xl font-extrabold tracking-tight text-primary" />
              <UnitPriceText
                unitPrice={listing.unitPrice}
                className="text-body-sm font-semibold text-on-surface-variant"
              />
              {listing.priceChange && (
                <span className="inline-flex items-center gap-1 text-body-sm font-semibold text-on-surface-variant">
                  {listing.priceChange.direction === 'DOWN' ? (
                    <TrendingDown className="h-4 w-4 text-success" aria-hidden="true" />
                  ) : (
                    <TrendingUp className="h-4 w-4 text-warning" aria-hidden="true" />
                  )}
                  {listing.priceChange.direction === 'DOWN' ? 'Giảm' : 'Tăng'} từ{' '}
                  {formatMoney({ ...listing.price, amount: listing.priceChange.previousAmount })} ngày{' '}
                  {formatDate(listing.priceChange.changedAt)}
                </span>
              )}
            </p>
            {listing.purpose === 'RENT' && (rent.monthlyServiceFee || rent.deposit) && (
              <dl className="flex flex-wrap gap-x-6 gap-y-1 text-body-sm text-on-surface">
                {rent.monthlyServiceFee && (
                  <div className="flex gap-1">
                    <dt className="text-on-surface-variant">Phí dịch vụ:</dt>
                    <dd className="font-semibold">{rent.monthlyServiceFee}</dd>
                  </div>
                )}
                {rent.deposit && (
                  <div className="flex gap-1">
                    <dt className="text-on-surface-variant">Đặt cọc:</dt>
                    <dd className="font-semibold">{rent.deposit}</dd>
                  </div>
                )}
              </dl>
            )}
            {place && (
              <p className="flex items-center gap-1.5 text-body-sm text-on-surface-variant">
                <MapPin className="h-4 w-4 shrink-0 text-outline" aria-hidden="true" /> {place}
                <span className="text-label">(vị trí gần đúng)</span>
              </p>
            )}
            <p className="text-label font-normal text-on-surface-variant">
              Đăng ngày {formatDate(listing.freshness.publishedAt)} · Cập nhật {formatDate(listing.freshness.updatedAt)}
              {listing.freshness.availabilityConfirmedAt && (
                <> · Người đăng xác nhận còn hàng ngày {formatDate(listing.freshness.availabilityConfirmedAt)}</>
              )}
            </p>
          </header>

          <div className="grid grid-cols-3 gap-2 sm:gap-3">
            <Card className="flex flex-col items-center p-3 text-center">
              <Maximize2 className="mb-1 h-5 w-5 text-primary" aria-hidden="true" />
              <span className="text-xs text-on-surface-variant">Diện tích</span>
              <span className="text-sm font-bold text-on-surface">{formatArea(listing.areaM2)}</span>
            </Card>
            <Card className="flex flex-col items-center p-3 text-center">
              <Home className="mb-1 h-5 w-5 text-primary" aria-hidden="true" />
              <span className="text-xs text-on-surface-variant">Loại hình</span>
              <span className="text-sm font-bold text-on-surface">{propertyTypeLabel(listing.propertyType)}</span>
            </Card>
            <Card className="flex flex-col items-center p-3 text-center">
              <Tag className="mb-1 h-5 w-5 text-primary" aria-hidden="true" />
              <span className="text-xs text-on-surface-variant">Nhu cầu</span>
              <span className="text-sm font-bold text-on-surface">{purposeLabel(listing.purpose)}</span>
            </Card>
          </div>

          <section aria-labelledby="facts-heading" className="border-t border-outline-variant/40 pt-6">
            <h2 id="facts-heading" className="text-lg font-bold text-on-surface">
              Đặc điểm bất động sản
            </h2>
            <dl className="mt-3 grid grid-cols-1 gap-x-8 sm:grid-cols-2">
              {facts
                .filter((fact) => fact.value)
                .map(({ label, value, icon: Icon }) => (
                  <div key={label} className="flex min-h-12 items-center gap-3 border-b border-outline-variant/60 py-3">
                    <Icon className="h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
                    <dt className="text-body-sm text-on-surface-variant">{label}</dt>
                    <dd className="ml-auto text-right text-body-sm font-semibold text-on-surface">{value}</dd>
                  </div>
                ))}
            </dl>
          </section>

          <section aria-labelledby="description-heading" className="flex flex-col gap-3">
            <h2 id="description-heading" className="text-lg font-bold text-on-surface">
              Mô tả bất động sản
            </h2>
            <div className="whitespace-pre-line rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-5 text-sm leading-relaxed text-on-surface">
              {listing.description?.trim() || 'Người đăng chưa cung cấp mô tả chi tiết.'}
            </div>
          </section>

          <TrustPanel trust={listing.trust} />

          <PriceHistorySection history={history} />

          <section aria-labelledby="seller-heading" className="border-t border-outline-variant/40 pt-6">
            <h2 id="seller-heading" className="text-lg font-bold text-on-surface">
              Thông tin người đăng
            </h2>
            <div className="mt-3 flex items-start gap-4 rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-4">
              <Link
                to={`/nguoi-dang/${listing.seller.id}`}
                aria-label={`Xem trang người đăng ${listing.seller.name || ''}`.trim()}
                className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <Avatar name={listing.seller.name} src={listing.seller.avatarUrl} size="lg" />
              </Link>
              <div className="flex min-w-0 flex-col gap-2">
                <p className="font-semibold text-on-surface">
                  <Link to={`/nguoi-dang/${listing.seller.id}`} className="hover:text-primary hover:underline">
                    {listing.seller.name || 'Người đăng'}
                  </Link>{' '}
                  <span className="text-body-sm font-normal text-on-surface-variant">
                    · {sellerRoleLabel(listing.seller.role)}
                  </span>
                </p>
                <TrustBadge
                  kind="identity"
                  status={listing.trust.identity.status}
                  checkedAt={listing.trust.identity.checkedAt}
                  expiresAt={listing.trust.identity.expiresAt}
                />
                <p className="text-label font-normal text-on-surface-variant">
                  Xác minh danh tính áp dụng cho người đăng; việc đối chiếu giấy tờ được xét riêng cho từng tin.
                </p>
              </div>
            </div>
          </section>
        </div>

        <aside ref={contactRef} aria-labelledby="contact-heading" className="flex flex-col gap-4 lg:sticky lg:top-24">
          <Card className="border border-primary/20 p-5 shadow-lg shadow-primary/5">
            <div className="flex items-center gap-3 border-b border-outline-variant/40 pb-4">
              <Avatar
                name={isOwn ? user?.name : listing.seller.name}
                src={isOwn ? user?.avatarMediaUrl : listing.seller.avatarUrl}
                size="md"
              />
              <div className="min-w-0">
                <h2 id="contact-heading" className="text-sm font-bold text-on-surface">
                  {isOwn ? 'Đây là tin của bạn' : `Liên hệ ${listing.seller.name ?? 'người đăng'}`}
                </h2>
                {isOwn ? (
                  <p className="text-xs text-on-surface-variant">Khách quan tâm sẽ gửi yêu cầu liên hệ tới bạn.</p>
                ) : (
                  <Link
                    to={`/nguoi-dang/${listing.seller.id}`}
                    className="text-xs font-semibold text-primary hover:underline"
                  >
                    Xem trang người đăng
                  </Link>
                )}
              </div>
            </div>
            {isOwn ? (
              <div className="mt-4 flex flex-col gap-2.5">
                {isPoster && (
                  <>
                    <ButtonLink to={`/listings/new?edit=${listing.id}`} variant="outline" className="w-full">
                      Chỉnh sửa tin
                    </ButtonLink>
                    <ButtonLink to="/my-leads" variant="outline" className="w-full">
                      Xem khách quan tâm
                    </ButtonLink>
                    <ButtonLink to="/my-listings" variant="outline" className="w-full">
                      Quản lý kho tin
                    </ButtonLink>
                  </>
                )}
                <ButtonLink to={`/nguoi-dang/${listing.seller.id}`} variant="ghost" className="w-full">
                  Xem trang người đăng công khai
                </ButtonLink>
              </div>
            ) : (
              <div className="mt-4 flex flex-col gap-3">
                <p className="text-sm text-on-surface-variant">
                  {!isAuthenticated
                    ? 'Đăng nhập để gửi yêu cầu hẹn xem. Số điện thoại chỉ được chia sẻ qua yêu cầu liên hệ.'
                    : kycStatus === 'VERIFIED'
                      ? 'Gửi một yêu cầu ngắn để hẹn thời gian xem nhà; bạn sẽ thấy yêu cầu trong mục “Tin đã liên hệ”.'
                      : kycStatus === 'LOADING'
                        ? 'Đang kiểm tra trạng thái xác minh của tài khoản…'
                        : 'Tài khoản cần được xác minh eKYC trước khi gửi yêu cầu liên hệ.'}
                </p>
                {contactAction}
                <CompareToggleButton variant="inline" listing={compareItemFromSummary(listing)} />
                <div className="mt-2 flex items-start gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50 p-3">
                  <Lock className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" aria-hidden="true" />
                  <p className="text-xs">
                    <span className="block font-bold text-emerald-900">Liên hệ và trao đổi trực tiếp</span>
                    <span className="mt-0.5 block leading-snug text-emerald-800">
                      Số điện thoại và email của người đăng không hiển thị công khai. Nhà Đất Chuẩn không nhận tiền cọc
                      và không ký hợp đồng thay bạn.
                    </span>
                  </p>
                </div>
              </div>
            )}
          </Card>
        </aside>
      </div>

      <section
        aria-labelledby="similar-heading"
        className="flex flex-col gap-4 border-t border-outline-variant/40 pt-6"
      >
        <h2 id="similar-heading" className="text-lg font-bold text-on-surface">
          Tin tương tự
        </h2>
        {similar === null ? (
          <Skeleton className="h-40 w-full rounded-card" />
        ) : similar.length ? (
          <ul className="ndc-listing-grid">
            {similar.map((item) => (
              <li key={item.id}>
                <ListingCard listing={item} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-body-sm text-on-surface-variant">Chưa có tin tương tự đang hiển thị.</p>
        )}
      </section>

      {!isOwn && (
        <div className="fixed inset-x-0 bottom-0 z-header flex items-center gap-3 border-t border-outline-variant bg-surface-container-lowest/95 px-4 py-3 backdrop-blur lg:hidden">
          <div className="min-w-0 flex-1">
            <Money price={listing.price} className="block truncate text-body font-bold text-primary" />
            <p className="truncate text-label font-normal text-on-surface-variant">{formatArea(listing.areaM2)}</p>
          </div>
          <div className="shrink-0">{contactAction}</div>
        </div>
      )}

      <LeadConsultationModal
        isOpen={leadOpen}
        onClose={() => setLeadOpen(false)}
        listing={{
          id: listing.id,
          title: listing.title,
          priceVnd: listing.price.amount,
          price: listing.price,
          areaM2: listing.areaM2,
          address: place,
          imageUrl: listing.images[0]?.url ?? '',
        }}
      />
      <ReportDialog listingId={listing.id} open={reportOpen} onClose={() => setReportOpen(false)} />
    </div>
  );
}

function PriceHistorySection({ history }: { history: PriceHistoryV2 | null }) {
  if (!history || history.points.length < 2) return null;
  return (
    <section aria-labelledby="price-history-heading" className="border-t border-outline-variant pt-6">
      <h2 id="price-history-heading" className="flex items-center gap-2 text-headline-sm text-on-surface">
        <History className="h-5 w-5 text-primary" aria-hidden="true" /> Lịch sử giá
      </h2>
      <p className="mt-1 text-label font-normal text-on-surface-variant">
        Theo các phiên bản tin đã được duyệt công khai.
      </p>
      <ol className="mt-3 flex flex-col divide-y divide-outline-variant/60">
        {[...history.points].reverse().map((point, index) => (
          <li key={`${point.changedAt}-${index}`} className="flex justify-between gap-4 py-2 text-body-sm">
            <span className="text-on-surface-variant">{formatDate(point.changedAt) || 'Không rõ ngày'}</span>
            <Money price={point.price} className="font-semibold text-on-surface" />
          </li>
        ))}
      </ol>
    </section>
  );
}

function ReportDialog({ listingId, open, onClose }: { listingId: string; open: boolean; onClose: () => void }) {
  const [category, setCategory] = useState('OTHER');
  const [description, setDescription] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (description.trim().length < 10) return;
    setBusy(true);
    setFeedback(null);
    try {
      await apiClient('/public/reports', {
        method: 'POST',
        body: JSON.stringify({
          listingId,
          category,
          severity: 'MEDIUM',
          description: description.trim(),
          evidenceUrls: '',
          reporterPhone: '',
        }),
      });
      setFeedback('Báo cáo đã được ghi nhận trên hệ thống.');
      setDescription('');
    } catch {
      setFeedback('Không thể gửi báo cáo. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Báo cáo tin vi phạm"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Đóng
          </Button>
          <Button
            type="submit"
            form="listing-report-form"
            isLoading={busy}
            disabled={description.trim().length < 10}
            variant="danger"
          >
            Gửi báo cáo
          </Button>
        </>
      }
    >
      <form id="listing-report-form" onSubmit={submit} className="space-y-4">
        <label className="block text-body-sm font-semibold">
          Loại vi phạm
          <select
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="mt-1 min-h-11 w-full rounded-input border border-outline px-3"
          >
            <option value="SCAM_DEPOSIT">Có dấu hiệu lừa cọc</option>
            <option value="FAKE_SOLD">Tin không còn đúng hiện trạng</option>
            <option value="INCORRECT_PRICE">Giá không chính xác</option>
            <option value="OTHER">Khác</option>
          </select>
        </label>
        <label className="block text-body-sm font-semibold">
          Mô tả (ít nhất 10 ký tự)
          <textarea
            required
            minLength={10}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={4}
            className="mt-1 w-full rounded-input border border-outline p-3"
          />
        </label>
        {feedback && (
          <p role="status" className="text-body-sm">
            {feedback}
          </p>
        )}
      </form>
    </Dialog>
  );
}
