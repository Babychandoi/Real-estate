import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Building2, CheckCheck, MapPin, Search, SlidersHorizontal } from 'lucide-react';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';
import { listingPath } from '@/entities/listing/model/seo';
import { apiClient } from '@/shared/api/client';
import { formatMoney } from '@/shared/format/money';
import { ListingSkeleton, StatePanel } from '@/shared/ui/Feedback';
import { ResponsiveImage } from '@/shared/ui/ResponsiveImage';

export function HomePage() {
  const navigate = useNavigate();
  const [purpose, setPurpose] = useState<'SALE' | 'RENT'>('SALE');
  const [keyword, setKeyword] = useState('');
  const [listings, setListings] = useState<ListingSummaryV2[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setFailed(false);
    // Public search API v2 (contract §8); the home page shows the first page of the newest listings.
    apiClient<{ items: ListingSummaryV2[] }>(`/api/v2/listings/search?purpose=${purpose}&size=6`)
      .then((data) => {
        if (active) setListings(data.items);
      })
      .catch(() => {
        if (active) {
          setFailed(true);
          setListings([]);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [purpose, attempt]);

  const submitSearch = () => {
    // URL state of the search page (features/search/filterSchema): keyword is `q`.
    const query = new URLSearchParams({ purpose });
    if (keyword.trim()) query.set('q', keyword.trim());
    navigate(`/search?${query.toString()}`);
  };

  const featured = listings.find((item) => item.image);

  return (
    <div data-ready={loading ? 'false' : 'true'}>
      <section className="ndc-home-hero">
        <div className="ndc-page grid items-center gap-10 lg:grid-cols-[1.2fr_1fr]">
          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-[.18em] text-secondary">
              Một nơi ở. Nhiều khởi đầu.
            </p>
            <h1>
              Ngôi nhà phù hợp,
              <br />
              từ thông tin rõ ràng.
            </h1>
            <p className="mt-5 max-w-lg text-base leading-7 text-on-surface-variant">
              Tìm mua, thuê và so sánh bất động sản. Nội dung tin đăng được kiểm duyệt; trạng thái kiểm duyệt không thay
              thế thẩm định pháp lý.
            </p>
            <div className="ndc-purpose mb-3 mt-7">
              <button type="button" aria-pressed={purpose === 'SALE'} onClick={() => setPurpose('SALE')}>
                Mua nhà
              </button>
              <button type="button" aria-pressed={purpose === 'RENT'} onClick={() => setPurpose('RENT')}>
                Thuê nhà
              </button>
            </div>
            <form
              className="ndc-search-form"
              onSubmit={(event) => {
                event.preventDefault();
                submitSearch();
              }}
            >
              <label className="flex min-w-0 flex-1 items-center gap-3 px-3">
                <MapPin className="h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
                <span className="sr-only">Từ khóa hoặc địa điểm</span>
                <input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="Dự án, đường hoặc khu vực…"
                  maxLength={100}
                  className="min-w-0 flex-1 bg-transparent text-sm outline-offset-0"
                />
              </label>
              <button className="ndc-primary-link" type="submit">
                <Search className="h-4 w-4" aria-hidden="true" />
                Tìm kiếm
              </button>
            </form>
            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-on-surface-variant">
              {[
                ['APARTMENT', 'Căn hộ'],
                ['HOUSE', 'Nhà riêng'],
                ['LAND', 'Đất'],
              ].map(([value, label]) => (
                <Link
                  className="inline-flex min-h-11 items-center gap-1 hover:text-primary"
                  key={value}
                  to={`/search?purpose=${purpose}&type=${value}`}
                >
                  {label}
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              ))}
            </div>
          </div>
          <div className="relative hidden overflow-hidden rounded-[24px] bg-primary lg:block">
            {featured && !failed && !loading ? (
              <Link to={listingPath(featured)} className="block">
                <ResponsiveImage
                  image={featured.image}
                  alt={featured.title}
                  sizes="(min-width: 1024px) 40vw, 100vw"
                  aspectRatio="5 / 4"
                  loading="eager"
                  priority
                  imgClassName="h-full w-full object-cover"
                />
                <div className="bg-primary p-6 text-white">
                  <p className="mb-2 text-xs uppercase tracking-widest text-white/80">Tin vừa cập nhật</p>
                  <h2 className="line-clamp-2 text-xl font-semibold">{featured.title}</h2>
                  <p className="mt-3 text-sm">
                    {formatMoney(featured.price)} · {featured.areaM2} m²
                  </p>
                </div>
              </Link>
            ) : (
              <div className="flex min-h-[420px] flex-col justify-end p-10 text-white">
                <Building2 className="mb-12 h-24 w-24 text-white/60" aria-hidden="true" />
                <p className="text-3xl font-semibold">
                  Thêm một lựa chọn.
                  <br />
                  Gần hơn một tổ ấm.
                </p>
                <p className="mt-4 text-sm text-white/80">Khám phá tin đăng theo nhu cầu của bạn.</p>
              </div>
            )}
          </div>
        </div>
      </section>
      <section className="ndc-page py-10 sm:py-14">
        <div className="ndc-section-heading">
          <div>
            <h2>{purpose === 'SALE' ? 'Bất động sản mới đăng bán' : 'Không gian mới cho thuê'}</h2>
            <p>Nội dung đã qua kiểm duyệt; hãy xác minh pháp lý và hiện trạng trước khi quyết định.</p>
          </div>
          <Link to={`/search?purpose=${purpose}`} className="ndc-text-link">
            Xem tất cả
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
        {loading ? (
          <ListingSkeleton />
        ) : failed ? (
          <StatePanel error onRetry={() => setAttempt((value) => value + 1)} />
        ) : !listings.length ? (
          <StatePanel
            title="Chưa có tin mới trong mục này"
            description="Bạn có thể đổi nhu cầu hoặc khám phá những khu vực khác."
          />
        ) : (
          <div className="ndc-listing-grid">
            {listings.map((listing, index) => (
              <ListingCard key={listing.id} listing={listing} priority={index < 3} />
            ))}
          </div>
        )}
      </section>
      <section className="ndc-page">
        <div className="grid gap-6 rounded-2xl border border-outline-variant/40 bg-white p-6 sm:p-8 md:grid-cols-3">
          {[
            {
              icon: SlidersHorizontal,
              title: 'Tìm đúng nhu cầu',
              body: 'Lọc theo loại hình, ngân sách, diện tích và khu vực.',
            },
            {
              icon: CheckCheck,
              title: 'So sánh có cơ sở',
              body: 'Đặt các tin cạnh nhau để nhìn rõ giá và đặc điểm.',
            },
            {
              icon: MapPin,
              title: 'Chủ động kết nối',
              body: 'Gửi yêu cầu tư vấn hoặc hẹn xem khi bạn đã sẵn sàng.',
            },
          ].map(({ icon: Icon, title, body }) => (
            <div key={title}>
              <Icon className="mb-4 h-6 w-6 text-secondary" aria-hidden="true" />
              <h2 className="font-semibold">{title}</h2>
              <p className="mt-2 text-sm leading-7 text-on-surface-variant">{body}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

export default HomePage;
