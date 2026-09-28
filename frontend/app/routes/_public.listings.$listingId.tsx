import { ListingGallery } from '@/entities/listing/ui/ListingGallery';
import { StatePanel } from '@/shared/ui/Feedback';
import { Dialog } from '@/shared/ui/Dialog';
import { ApiProblemException } from '@/shared/types/problem-details';
import React, { useState, useEffect, useLayoutEffect } from 'react';
import { CompareToggleButton } from '@/features/compare/CompareControls';
import { Avatar } from '@/shared/ui/Avatar';
import { useParams, Link } from 'react-router-dom';
import { ShieldCheck, MapPin, Maximize2, Home, ArrowLeft, Lock, MessageSquare, Flag, Tag, BedDouble, Bath, Building, Ruler, Route, Compass, FileText } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';
import { apiClient } from '@/shared/api/client';
import { type ListingDetail, type PublicSellerProfile, formatListingPrice, calculateUnitPrice, formatPropertyType } from '@/entities/listing/model/types';
import { LeadConsultationModal } from '@/features/lead/ui/LeadConsultationModal';
import { useAuth } from '@/shared/auth/AuthContext';
import type { UserKycProfile } from '@/entities/verification/model/types';
import { listingIdFromRoute, listingPath } from '@/entities/listing/model/seo';

export const ListingDetailPage: React.FC = () => {
  const { listingId: listingRoute } = useParams<{ listingId: string }>();
  const legacyListingId = listingIdFromRoute(listingRoute);
  const { user, isAuthenticated, setIsLoginModalOpen } = useAuth();
  const [kycStatus, setKycStatus] = useState<UserKycProfile['status'] | 'NONE'>('NONE');
  const [listing, setListing] = useState<ListingDetail | null>(null);
  const [seller, setSeller] = useState<PublicSellerProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [isConsultationModalOpen, setIsConsultationModalOpen] = useState(false);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [reportCategory, setReportCategory] = useState('OTHER');
  const [reportDescription, setReportDescription] = useState('');
  const [reportFeedback, setReportFeedback] = useState<string | null>(null);
  const [reportBusy, setReportBusy] = useState(false);

  // Không giữ vị trí cuộn của trang danh sách khi người dùng mở một tin mới.
  useLayoutEffect(() => {
    window.scrollTo(0, 0);
  }, [listingRoute]);

  useEffect(() => {
    if (!user) { setKycStatus('NONE'); return; }
    apiClient<UserKycProfile>(`/kyc/user/${user.id}`)
      .then(profile => setKycStatus(profile.status))
      .catch(() => setKycStatus('NONE'));
  }, [user]);

  const submitReport = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!listing?.id || reportDescription.trim().length < 10) return;
    setReportBusy(true);
    setReportFeedback(null);
    try {
      await apiClient('/public/reports', { method: 'POST', body: JSON.stringify({ listingId: listing.id, category: reportCategory, severity: 'MEDIUM', description: reportDescription.trim(), evidenceUrls: '', reporterPhone: '' }) });
      setReportFeedback('Báo cáo đã được ghi nhận trên hệ thống.');
      setReportDescription('');
    } catch {
      setReportFeedback('Không thể gửi báo cáo. Vui lòng thử lại.');
    } finally { setReportBusy(false); }
  };

  useEffect(() => {
    let active = true;
    setIsLoading(true); setListing(null); setSeller(null); setLoadError(false);
    const endpoint = legacyListingId ? `/listings/${legacyListingId}` : `/listings/by-slug/${encodeURIComponent(listingRoute || '')}`;
    apiClient<ListingDetail>(endpoint).then(data => { if (active) setListing(data); }).catch((error: unknown) => {
      if (active) setLoadError(!(error instanceof ApiProblemException && error.problem.status === 404));
    }).finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
  }, [legacyListingId, listingRoute, attempt]);

  useEffect(() => {
    if (!listing?.ownerId) return;
    apiClient<PublicSellerProfile>(`/public/profiles/${listing.ownerId}`).then(setSeller).catch(() => setSeller(null));
  }, [listing?.ownerId]);

  useEffect(() => {
    if (!listing) return;
    const canonicalPath = listingPath(listing);
    const canonicalUrl = `${window.location.origin}${canonicalPath}`;
    if (window.location.pathname !== canonicalPath) window.history.replaceState(null, '', canonicalPath);

    document.title = `${listing.title} | Nhà Đất Chuẩn`;
    const description = `${formatPropertyType(listing.propertyType)} ${listing.purpose === 'SALE' ? 'cần bán' : 'cho thuê'} tại ${listing.addressSummary}, diện tích ${listing.areaM2} m², giá ${formatListingPrice(listing.priceVnd, listing.purpose)}.`;
    const upsertMeta = (selector: string, attributes: Record<string, string>) => {
      let element = document.head.querySelector<HTMLMetaElement>(selector);
      if (!element) { element = document.createElement('meta'); document.head.appendChild(element); }
      Object.entries(attributes).forEach(([key, value]) => element!.setAttribute(key, value));
    };
    upsertMeta('meta[name="description"]', { name: 'description', content: description });
    upsertMeta('meta[property="og:title"]', { property: 'og:title', content: listing.title });
    upsertMeta('meta[property="og:description"]', { property: 'og:description', content: description });
    upsertMeta('meta[property="og:type"]', { property: 'og:type', content: 'product' });
    upsertMeta('meta[property="og:url"]', { property: 'og:url', content: canonicalUrl });
    if (listing.imageUrls[0]) upsertMeta('meta[property="og:image"]', { property: 'og:image', content: listing.imageUrls[0] });
    upsertMeta('meta[name="twitter:card"]', { name: 'twitter:card', content: 'summary_large_image' });

    let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!canonical) { canonical = document.createElement('link'); canonical.rel = 'canonical'; document.head.appendChild(canonical); }
    canonical.href = canonicalUrl;

    document.getElementById('listing-structured-data')?.remove();
    const schema = document.createElement('script');
    schema.id = 'listing-structured-data';
    schema.type = 'application/ld+json';
    schema.text = JSON.stringify({
      '@context': 'https://schema.org', '@type': 'Product', name: listing.title, category: 'Bất động sản',
      description: listing.description, url: canonicalUrl, image: listing.imageUrls,
      datePosted: listing.createdAt,
      address: { '@type': 'PostalAddress', streetAddress: listing.addressSummary, addressCountry: 'VN' },
      offers: { '@type': 'Offer', price: listing.priceVnd, priceCurrency: 'VND', availability: 'https://schema.org/InStock' },
    });
    document.head.appendChild(schema);
    return () => { document.getElementById('listing-structured-data')?.remove(); };
  }, [listing]);

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-12">
        <div className="h-96 bg-surface-container rounded-2xl animate-pulse"></div>
      </div>
    );
  }

  if (loadError) return <div className="ndc-page py-10"><StatePanel error onRetry={() => setAttempt(value => value + 1)} /></div>;

  if (!listing) {
    return (
      <div className="max-w-md mx-auto my-16 text-center">
        <h2 className="text-xl font-bold">Không tìm thấy bất động sản</h2>
        <Link to="/" className="text-primary mt-4 inline-block font-semibold">
          Quay lại trang chủ
        </Link>
      </div>
    );
  }


  const isOwnListing = Boolean(user && user.id === listing.ownerId);
  return (
    <div className="max-w-6xl mx-auto px-4 md:px-8 py-6 flex flex-col gap-6">
      {/* Nút quay lại */}
      <div>
        <Link
          to="/search"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant hover:text-primary transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Quay lại danh sách
        </Link>
        {!isOwnListing && <button type="button" onClick={() => setIsReportOpen(true)} className="ml-4 inline-flex min-h-11 items-center gap-1.5 text-xs font-semibold text-rose-700"><Flag className="h-4 w-4" /> Báo cáo tin vi phạm</button>}
      </div>

      <ListingGallery key={listing.id} images={listing.imageUrls} title={listing.title} />
      {listing.isVerified && <div><Badge variant="verified" icon={<ShieldCheck className="h-4 w-4" aria-hidden="true" />}>Tin đã xác thực</Badge></div>}

      {/* Chi tiết nội dung và Form liên hệ Lead */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
        {/* Cột trái: Thông số & Mô tả */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          <div>
            <div className="flex items-baseline gap-3 flex-wrap">
              <span className="text-3xl font-extrabold text-primary tracking-tight">
                {formatListingPrice(listing.priceVnd, listing.purpose)}
              </span>
              <span className="text-sm font-semibold text-on-surface-variant">
                {listing.purpose === 'SALE' ? calculateUnitPrice(listing.priceVnd, listing.areaM2) : 'Giá thuê mỗi tháng'}
              </span>
            </div>
            <h1 className="text-xl md:text-2xl font-bold text-on-surface mt-2 leading-snug">
              {listing.title}
            </h1>
            <p className="flex items-center gap-1.5 text-sm text-on-surface-variant mt-2">
              <MapPin className="w-4 h-4 text-outline" /> {listing.addressSummary}
            </p>
          </div>

          {/* Ma trận thông số kỹ thuật */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Card className="flex flex-col items-center text-center p-3">
              <Maximize2 className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Diện tích</span>
              <span className="text-sm font-bold text-on-surface">{listing.areaM2} m²</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <Home className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Loại hình</span>
              <span className="text-sm font-bold text-on-surface">{formatPropertyType(listing.propertyType)}</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <Tag className="w-5 h-5 text-primary mb-1" aria-hidden="true" />
              <span className="text-xs text-on-surface-variant">Nhu cầu</span>
              <span className="text-sm font-bold text-on-surface">{listing.purpose === 'SALE' ? 'Cần bán' : 'Cho thuê'}</span>
            </Card>
          </div>

          {/* Mô tả chi tiết */}
          <section className="border-t border-outline-variant/40 pt-6">
            <h2 className="text-lg font-bold text-on-surface">Đặc điểm bất động sản</h2>
            <dl className="mt-3 grid grid-cols-1 gap-x-8 sm:grid-cols-2">
              {[
                { label: 'Diện tích', value: `${listing.areaM2} m²`, icon: Maximize2 },
                { label: 'Số phòng ngủ', value: listing.bedrooms != null ? `${listing.bedrooms} phòng` : null, icon: BedDouble },
                { label: 'Số phòng tắm, vệ sinh', value: listing.bathrooms != null ? `${listing.bathrooms} phòng` : null, icon: Bath },
                { label: 'Số tầng', value: listing.floors != null ? `${listing.floors} tầng` : null, icon: Building },
                { label: 'Mặt tiền', value: listing.frontageM != null ? `${listing.frontageM} m` : null, icon: Ruler },
                { label: 'Đường vào', value: listing.roadWidthM != null ? `${listing.roadWidthM} m` : null, icon: Route },
                { label: 'Hướng nhà', value: listing.direction || null, icon: Compass },
                { label: 'Pháp lý', value: listing.legalStatus || null, icon: FileText },
              ].filter((item) => item.value).map(({ label, value, icon: Icon }) => (
                <div key={label} className="flex min-h-12 items-center gap-3 border-b border-outline-variant/30 py-3">
                  <Icon className="h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
                  <dt className="text-sm text-on-surface-variant">{label}</dt>
                  <dd className="ml-auto text-right text-sm font-semibold text-on-surface">{value}</dd>
                </div>
              ))}
            </dl>
          </section>

          <div className="flex flex-col gap-3">
            <h2 className="text-lg font-bold text-on-surface">Mô tả bất động sản</h2>
            <div className="text-sm text-on-surface leading-relaxed whitespace-pre-line bg-surface-container-lowest p-5 rounded-xl border border-outline-variant/40">
              {listing.description?.trim() || 'Người đăng chưa cung cấp mô tả chi tiết.'}
            </div>
          </div>

          <section className="border-t border-outline-variant/40 pt-6">
            <h2 className="text-lg font-bold text-on-surface">Thông tin người đăng</h2>
            {seller ? <div className="mt-3 flex items-start gap-4 rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-4">
              <Link to={`/nguoi-dang/${listing.ownerId}`} aria-label={`Xem trang cá nhân của ${seller.displayName}`} className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"><Avatar name={seller.displayName} src={seller.avatarMediaUrl} size="lg" /></Link>
              <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="font-bold text-on-surface"><Link to={`/nguoi-dang/${listing.ownerId}`} className="hover:text-primary hover:underline">{seller.displayName}</Link></h3>{seller.identityVerified && <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-800"><ShieldCheck className="h-4 w-4" />Đã xác minh danh tính</span>}</div><p className="mt-1 text-sm text-on-surface-variant">Đang có {seller.activeListingCount} tin hiển thị · Tham gia từ {new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(new Date(seller.memberSince))}</p><p className="mt-2 text-xs text-on-surface-variant">Thông tin liên hệ chỉ mở cho tài khoản đã xác minh eKYC khi gửi yêu cầu liên hệ.</p></div>
            </div> : <p className="mt-3 text-sm text-on-surface-variant">Thông tin người đăng đang được cập nhật.</p>}
          </section>
        </div>

        {/* Cột phải: tin của chính mình → khối quản lý thay cho form liên hệ */}
        {isOwnListing ? (
        <div className="lg:col-span-1 lg:sticky lg:top-24">
          <Card className="p-5 border border-primary/20 shadow-lg shadow-primary/5">
            <div className="flex items-center gap-3 pb-4 border-b border-outline-variant/40">
              <Avatar name={user?.name} src={user?.avatarMediaUrl} size="md" />
              <div className="min-w-0">
                <h3 className="font-bold text-sm text-on-surface">Đây là tin của bạn</h3>
                <p className="text-xs text-on-surface-variant">Khách quan tâm sẽ gửi yêu cầu liên hệ tới bạn.</p>
              </div>
            </div>
            <div className="mt-4 flex flex-col gap-2.5">
              {(user?.role === 'BROKER' || user?.role === 'ADMIN') && <>
                <Link to={`/listings/new?edit=${listing.id}`}><Button type="button" variant="outline" className="w-full min-h-11 font-bold">Chỉnh sửa tin</Button></Link>
                <Link to="/my-leads"><Button type="button" variant="outline" className="w-full min-h-11 font-bold">Xem khách quan tâm</Button></Link>
                <Link to="/my-listings"><Button type="button" variant="ghost" className="w-full min-h-11">Quản lý kho tin</Button></Link>
              </>}
              <Link to={`/nguoi-dang/${listing.ownerId}`}><Button type="button" variant="ghost" className="w-full min-h-11">Xem trang cá nhân công khai</Button></Link>
            </div>
          </Card>
        </div>
        ) : (
        <div className="lg:col-span-1 lg:sticky lg:top-24">
          <Card className="p-5 border border-primary/20 shadow-lg shadow-primary/5">
            <div className="flex items-center gap-2 pb-4 border-b border-outline-variant/40">
              <Link to={`/nguoi-dang/${listing.ownerId}`} aria-label={seller ? `Xem trang cá nhân của ${seller.displayName}` : 'Xem trang cá nhân người đăng'} className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
                <Avatar name={seller?.displayName} src={seller?.avatarMediaUrl} size="md" />
              </Link>
              <div className="min-w-0">
                <h3 className="font-bold text-sm text-on-surface">Liên hệ {seller?.displayName ?? 'người đăng'}</h3>
                <Link to={`/nguoi-dang/${listing.ownerId}`} className="text-xs font-semibold text-primary hover:underline">Xem trang cá nhân</Link>
              </div>
            </div>

            <div className="mt-4 flex flex-col gap-3">
                <p className="text-sm text-on-surface-variant">Gửi một yêu cầu ngắn để hẹn thời gian xem nhà. Bạn có thể theo dõi yêu cầu đã gửi trong tài khoản.</p>
                <Button
                  type="button"
                  variant="outline"
                  size="md"
                  onClick={() => isAuthenticated ? setIsConsultationModalOpen(true) : setIsLoginModalOpen(true)}
                  leftIcon={<MessageSquare className="w-4 h-4 text-primary" />}
                  className="w-full border-primary/30 text-primary hover:bg-primary/10 font-bold"
                >
                  {isAuthenticated ? 'Hẹn xem & nhận tư vấn' : 'Đăng nhập để liên hệ'}
                </Button>
                <CompareToggleButton variant="inline" listing={{ id: listing.id, slug: listing.slug, title: listing.title, purpose: listing.purpose, priceVnd: listing.priceVnd, areaM2: listing.areaM2, addressSummary: listing.addressSummary, primaryImageUrl: listing.imageUrls[0] ?? '' }} />
                {isAuthenticated && kycStatus !== 'VERIFIED' && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Tài khoản cần được duyệt eKYC trước khi gửi yêu cầu. <Link to="/kyc" className="font-bold underline underline-offset-4">Mở hồ sơ eKYC</Link></p>}
            </div>

            {/* Khối Giao dịch Đặt cọc Trực tuyến Bảo đảm Escrow (FR28, FR30, UC05) */}
            <div className="mt-6 pt-5 border-t border-outline-variant/30 flex flex-col gap-3">
              <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-start gap-2.5">
                <Lock className="w-4 h-4 text-emerald-700 shrink-0 mt-0.5" />
                <div className="text-xs">
                  <span className="font-bold text-emerald-900 block">Liên hệ và trao đổi trực tiếp</span>
                  <span className="text-emerald-700 leading-snug block mt-0.5">
                    Chỉ tài khoản đã eKYC mới được gửi và nhận yêu cầu liên hệ. Nhà Đất Chuẩn không nhận tiền cọc hoặc ký hợp đồng thay bạn.
                  </span>
                </div>
              </div>

            </div>
          </Card>
        </div>
        )}
      </div>

      {/* Modal Đăng Ký Tư Vấn & Xác Minh OTP Khách Hàng (FR18, FR20, UC04) */}
      {listing && (
        <LeadConsultationModal
          isOpen={isConsultationModalOpen}
          onClose={() => setIsConsultationModalOpen(false)}
          listing={{
            id: listing.id,
            title: listing.title,
            priceVnd: listing.priceVnd,
            areaM2: listing.areaM2,
            address: listing.addressSummary,
            imageUrl: listing.imageUrls[0] || '',
          }}
        />
      )}
      {isReportOpen && (
        <Dialog open={isReportOpen} onClose={() => setIsReportOpen(false)} title="Báo cáo tin vi phạm">
          <form onSubmit={submitReport} className="w-full max-w-lg space-y-4 rounded-2xl bg-white p-6">
            <label className="block text-sm font-semibold">Loại vi phạm<select value={reportCategory} onChange={(event) => setReportCategory(event.target.value)} className="mt-1 min-h-11 w-full rounded-lg border px-3"><option value="SCAM_DEPOSIT">Có dấu hiệu lừa cọc</option><option value="FAKE_SOLD">Tin không còn đúng hiện trạng</option><option value="INCORRECT_PRICE">Giá không chính xác</option><option value="OTHER">Khác</option></select></label>
            <label className="block text-sm font-semibold">Mô tả<textarea required minLength={10} value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} rows={4} className="mt-1 w-full rounded-lg border p-3" /></label>
            {reportFeedback && <p role="status" className="text-sm">{reportFeedback}</p>}
            <div className="flex justify-end gap-2"><button type="button" onClick={() => setIsReportOpen(false)} className="min-h-11 rounded-lg border px-4">Đóng</button><button disabled={reportBusy || reportDescription.trim().length < 10} className="min-h-11 rounded-lg bg-rose-700 px-4 font-bold text-white disabled:opacity-50">{reportBusy ? 'Đang gửi…' : 'Gửi báo cáo'}</button></div>
          </form>
        </Dialog>
      )}
    </div>
  );
};

