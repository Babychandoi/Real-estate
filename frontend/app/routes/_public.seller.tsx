import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Building2, CalendarDays, ShieldCheck, ShieldQuestion } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { Avatar } from '@/shared/ui/Avatar';
import { ComparableListingCard } from '@/features/compare/ComparableListingCard';
import type { Listing, PublicSellerProfile } from '@/entities/listing/model/types';
import { useAuth } from '@/shared/auth/AuthContext';

export function SellerProfilePage() {
  const { sellerId = '' } = useParams<{ sellerId: string }>();
  const { user } = useAuth();
  const [profile, setProfile] = useState<PublicSellerProfile | null>(null);
  const [listings, setListings] = useState<Listing[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading');
  const [purpose, setPurpose] = useState<'ALL' | 'SALE' | 'RENT'>('ALL');

  useEffect(() => {
    let active = true;
    setState('loading');
    Promise.all([
      apiClient<PublicSellerProfile>(`/public/profiles/${sellerId}`),
      apiClient<Listing[]>(`/public/profiles/${sellerId}/listings`),
    ]).then(([nextProfile, nextListings]) => {
      if (!active) return;
      setProfile(nextProfile);
      // Cards on this page all belong to the same person, so the seller row would only repeat the header.
      setListings((nextListings ?? []).map((item) => ({ ...item, sellerName: undefined })));
      setState('ready');
    }).catch((error: unknown) => {
      if (!active) return;
      setState((error as { problem?: { status?: number } })?.problem?.status === 404 ? 'missing' : 'error');
    });
    return () => { active = false; };
  }, [sellerId]);

  if (state === 'loading') {
    return <section className="mx-auto max-w-6xl px-4 py-10"><div className="h-40 animate-pulse rounded-2xl bg-surface-container" /></section>;
  }
  if (state !== 'ready' || !profile) {
    return (
      <section className="mx-auto max-w-xl px-4 py-16 text-center">
        <ShieldQuestion className="mx-auto h-12 w-12 text-outline" aria-hidden="true" />
        <h1 className="mt-3 text-xl font-bold text-on-surface">{state === 'missing' ? 'Không tìm thấy người đăng' : 'Không tải được trang cá nhân'}</h1>
        <p className="mt-2 text-sm text-on-surface-variant">{state === 'missing' ? 'Tài khoản này không tồn tại hoặc đã ngừng hoạt động.' : 'Vui lòng thử lại sau ít phút.'}</p>
        <Link to="/search" className="mt-5 inline-flex min-h-11 items-center rounded-lg bg-primary px-5 text-sm font-bold text-white">Xem tin đăng khác</Link>
      </section>
    );
  }

  const isMe = user?.id === sellerId;
  const shown = purpose === 'ALL' ? listings : listings.filter((item) => item.purpose === purpose);
  const saleCount = listings.filter((item) => item.purpose === 'SALE').length;
  const memberSince = new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(new Date(profile.memberSince));

  return (
    <section className="mx-auto max-w-6xl px-4 py-8 md:px-6">
      <button type="button" onClick={() => window.history.length > 1 ? window.history.back() : undefined} className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-on-surface-variant hover:text-on-surface">
        <ArrowLeft className="h-4 w-4" /> Quay lại
      </button>

      <section className="flex flex-col gap-5 rounded-2xl border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-sm sm:flex-row sm:items-center">
        <Avatar name={profile.displayName} src={profile.avatarMediaUrl} size="xl" className="ring-4 ring-primary/10" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold text-on-surface">{profile.displayName}</h1>
            {profile.identityVerified
              ? <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-bold text-emerald-800"><ShieldCheck className="h-3.5 w-3.5" /> Đã xác minh danh tính</span>
              : <span className="rounded-full bg-surface-container px-2.5 py-1 text-xs font-semibold text-on-surface-variant">Chưa xác minh danh tính</span>}
          </div>
          <dl className="mt-3 flex flex-wrap gap-x-6 gap-y-2 text-sm text-on-surface-variant">
            <div className="flex items-center gap-1.5"><Building2 className="h-4 w-4" aria-hidden="true" /><dt className="sr-only">Tin đang hiển thị</dt><dd><strong className="text-on-surface">{profile.activeListingCount}</strong> tin đang hiển thị</dd></div>
            <div className="flex items-center gap-1.5"><CalendarDays className="h-4 w-4" aria-hidden="true" /><dt className="sr-only">Ngày tham gia</dt><dd>Tham gia từ {memberSince}</dd></div>
          </dl>
          <p className="mt-3 text-xs text-on-surface-variant">Số điện thoại và email không hiển thị công khai. Hãy mở một tin đăng và gửi yêu cầu liên hệ để trao đổi trực tiếp.</p>
        </div>
        {isMe && <Link to="/account" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-lg border border-primary/30 px-4 text-sm font-bold text-primary hover:bg-primary/5">Chỉnh sửa trang cá nhân</Link>}
      </section>

      <section className="mt-8">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-bold text-on-surface">Tin đang đăng</h2>
          <div className="inline-flex rounded-lg border border-outline-variant/40 bg-surface-container-low p-0.5 text-xs" role="group" aria-label="Lọc theo nhu cầu">
            {([['ALL', `Tất cả (${listings.length})`], ['SALE', `Bán (${saleCount})`], ['RENT', `Cho thuê (${listings.length - saleCount})`]] as const).map(([value, label]) => (
              <button key={value} type="button" onClick={() => setPurpose(value)} aria-pressed={purpose === value}
                className={`min-h-11 rounded-md px-3 font-semibold ${purpose === value ? 'bg-primary text-white' : 'text-on-surface-variant hover:text-primary'}`}>{label}</button>
            ))}
          </div>
        </div>
        {shown.length === 0
          ? <p className="rounded-2xl border border-dashed border-outline-variant p-10 text-center text-sm text-on-surface-variant">Chưa có tin nào đang hiển thị.</p>
          : <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3 md:gap-5">{shown.map((item) => <ComparableListingCard key={item.id} listing={item} />)}</div>}
      </section>
    </section>
  );
}

export default SellerProfilePage;

