import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingApi } from '../entities/listing/api/listingApi';
import type { Listing, ListingSearchParams } from '../entities/listing/model/types';
import { formatPriceVnd, calculateUnitPrice, formatPropertyType } from '../entities/listing/model/types';
import { ListingMap, type MapBounds } from '@/shared/map/ListingMap';

export function SearchAndMapPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [listings, setListings] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [hoveredId, setHoveredId] = useState<string | null>(null);

  // Filters state
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [purpose, setPurpose] = useState<'SALE' | 'RENT'>(
    (searchParams.get('purpose') as 'SALE' | 'RENT') || 'SALE'
  );
  const [propertyType, setPropertyType] = useState<string>(searchParams.get('propertyType') || '');
  const [priceRange, setPriceRange] = useState<string>('ALL'); // ALL, <3B, 3-5B, >5B
  const [sortBy, setSortBy] = useState<'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'AREA_DESC'>('LATEST');
  const [onlyVerified, setOnlyVerified] = useState(false);
  const [showPrivacyBanner, setShowPrivacyBanner] = useState(true);

  // Load listings from API
  useEffect(() => {
    fetchListings();
  }, [purpose, propertyType, priceRange, sortBy]);

  const fetchListings = async (bounds?: MapBounds) => {
    setLoading(true);
    try {
      const params: ListingSearchParams = {
        purpose,
        sortBy,
      };
      if (propertyType) params.propertyType = propertyType;
      if (keyword.trim()) params.keyword = keyword.trim();
      if (bounds) Object.assign(params, bounds);

      if (priceRange === '<3B') {
        params.maxPrice = 3_000_000_000;
      } else if (priceRange === '3-5B') {
        params.minPrice = 3_000_000_000;
        params.maxPrice = 5_000_000_000;
      } else if (priceRange === '>5B') {
        params.minPrice = 5_000_000_000;
      }

      const data = await listingApi.searchListings(params);
      setListings(data);
      if (data.length > 0 && !selectedId) {
        setSelectedId(data[0].id);
      }
    } catch (err) {
      console.error('Lỗi tải danh sách tìm kiếm:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const next = new URLSearchParams({ purpose, sortBy });
    if (keyword.trim()) next.set('keyword', keyword.trim());
    if (propertyType) next.set('propertyType', propertyType);
    if (priceRange !== 'ALL') next.set('priceRange', priceRange);
    setSearchParams(next);
    fetchListings();
  };

  return (
    <div className="w-full flex flex-col xl:flex-row h-[calc(100vh-4rem)] overflow-hidden bg-surface text-on-surface">
      {/* ========================================================= */}
      {/* LEFT PANEL: Filter Toolbar & Scrollable Listing Feed (45%) */}
      {/* ========================================================= */}
      <section className="w-full flex flex-col h-full bg-surface z-10 relative">
        {/* Top Query & Smart Filters */}
        <div className="p-4 bg-surface-container-lowest flex flex-col gap-3 shadow-sm border-b border-outline-variant/20 flex-shrink-0">
          {/* Search Input Bar */}
          <form onSubmit={handleSearchSubmit} className="flex items-center gap-2">
            <div className="relative flex-1 flex items-center bg-surface-container-low rounded-xl px-3 py-2 transition-all focus-within:ring-2 focus-within:ring-primary/20">
              <svg className="w-5 h-5 text-primary mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="text-[10px] font-bold text-on-surface-variant uppercase tracking-wider leading-none">
                  Khu vực tìm kiếm Hà Nội Pilot
                </span>
                <input
                  type="text"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="Nhập tên dự án, đường phố, quận huyện..."
                  className="bg-transparent text-sm font-semibold text-on-surface focus:outline-none w-full truncate pt-0.5"
                />
              </div>
              {keyword && (
                <button
                  type="button"
                  onClick={() => { setKeyword(''); fetchListings(); }}
                  className="text-outline hover:text-on-surface ml-1 text-xs"
                >
                  ✕
                </button>
              )}
            </div>

            <button
              type="submit"
              aria-label="Tìm kiếm"
              className="h-11 px-4 rounded-xl bg-primary hover:bg-primary/90 text-white font-semibold text-sm flex items-center gap-1.5 shadow-sm transition"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <span className="hidden sm:inline">Tìm kiếm</span>
            </button>
          </form>

          {/* Quick Filter Pills */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs no-scrollbar font-medium">
            {/* Purpose Toggle */}
            <div className="flex rounded-lg bg-surface-container-low p-0.5 border border-outline-variant/30 shrink-0">
              <button
                onClick={() => setPurpose('SALE')}
                className={`px-3 py-1 rounded-md transition ${purpose === 'SALE' ? 'bg-primary text-white font-bold shadow-sm' : 'text-on-surface-variant'}`}
              >
                Cần bán
              </button>
              <button
                onClick={() => setPurpose('RENT')}
                className={`px-3 py-1 rounded-md transition ${purpose === 'RENT' ? 'bg-primary text-white font-bold shadow-sm' : 'text-on-surface-variant'}`}
              >
                Cho thuê
              </button>
            </div>

            {/* Property Type Pills */}
            <button
              onClick={() => setPropertyType(propertyType === 'APARTMENT' ? '' : 'APARTMENT')}
              className={`px-3 py-1.5 rounded-full border transition shrink-0 ${
                propertyType === 'APARTMENT'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              🏢 Căn hộ
            </button>

            <button
              onClick={() => setPropertyType(propertyType === 'HOUSE' ? '' : 'HOUSE')}
              className={`px-3 py-1.5 rounded-full border transition shrink-0 ${
                propertyType === 'HOUSE'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              🏠 Nhà phố
            </button>

            {/* Price Range Pills */}
            <button
              onClick={() => setPriceRange(priceRange === '<3B' ? 'ALL' : '<3B')}
              className={`px-3 py-1.5 rounded-full border transition shrink-0 ${
                priceRange === '<3B'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              Dưới 3 tỷ
            </button>

            <button
              onClick={() => setPriceRange(priceRange === '3-5B' ? 'ALL' : '3-5B')}
              className={`px-3 py-1.5 rounded-full border transition shrink-0 ${
                priceRange === '3-5B'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              3 - 5 tỷ
            </button>

            <button
              onClick={() => setPriceRange(priceRange === '>5B' ? 'ALL' : '>5B')}
              className={`px-3 py-1.5 rounded-full border transition shrink-0 ${
                priceRange === '>5B'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              Trên 5 tỷ
            </button>

            {/* Filter Chính chủ eKYC (FR01/FR03) */}
            <button
              onClick={() => setOnlyVerified(!onlyVerified)}
              className={`px-3 py-1.5 rounded-full border transition flex items-center gap-1.5 shrink-0 ${
                onlyVerified
                  ? 'bg-emerald-100 border-emerald-500 text-emerald-800 font-bold shadow-xs'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              <span className="w-2 h-2 rounded-full bg-emerald-600"></span>
              Chính chủ eKYC
            </button>

            <Link
              to="/compare"
              className="px-3 py-1.5 rounded-full border border-blue-300 bg-blue-50 text-blue-800 hover:bg-blue-100 font-bold transition flex items-center gap-1.5 shrink-0 text-xs shadow-xs"
            >
              <span>⚖️</span>
              So sánh Đối chiếu (FR17)
            </Link>
          </div>

          {/* Privacy Protocol Banner (FR13 / Privacy Law 91/2025) */}
          {showPrivacyBanner && (
            <div className="flex items-center justify-between gap-2 p-2.5 rounded-xl bg-surface-container-low text-xs border border-outline-variant/30">
              <div className="flex items-center gap-2">
                <svg className="w-4 h-4 text-emerald-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
                <p className="text-on-surface-variant leading-tight">
                  <strong className="text-on-surface font-semibold">Bảo vệ vị trí riêng tư:</strong> Tọa độ ghim công khai được làm mờ trong bán kính khoảng 200 m.
                </p>
              </div>
              <button
                onClick={() => setShowPrivacyBanner(false)}
                className="text-outline hover:text-on-surface text-xs shrink-0 p-1"
              >
                ✕
              </button>
            </div>
          )}
        </div>

        {/* Feed Summary & Sorting Toolbar */}
        <div className="px-4 py-2.5 flex items-center justify-between bg-surface-container-low/50 border-b border-outline-variant/20 flex-shrink-0 text-xs">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span className="font-bold text-primary text-sm">{listings.length}</span>
            <span className="text-on-surface-variant font-medium">bất động sản trong vùng quét</span>
          </div>

          <div className="flex items-center gap-1.5">
            <span className="text-on-surface-variant">Sắp xếp:</span>
            <select
              aria-label="Sắp xếp kết quả"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              className="bg-surface-container-lowest font-semibold text-primary px-2 py-1 rounded-lg border border-outline-variant/30 focus:outline-none"
            >
              <option value="LATEST">Mới niêm yết</option>
              <option value="PRICE_ASC">Giá: Thấp đến cao</option>
              <option value="PRICE_DESC">Giá: Cao đến thấp</option>
              <option value="AREA_DESC">Diện tích lớn nhất</option>
            </select>
          </div>
        </div>

        {/* Scrollable Listing Cards Feed */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {loading ? (
            <div className="text-center py-16 text-on-surface-variant text-sm">
              <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
              Đang tải danh sách bất động sản và quét toạ độ GIS...
            </div>
          ) : listings.length === 0 ? (
            <div className="text-center py-16 text-on-surface-variant text-sm">
              <p>Không tìm thấy bất động sản nào khớp với bộ lọc hiện tại.</p>
              <button
                onClick={() => { setKeyword(''); setPropertyType(''); setPriceRange('ALL'); }}
                className="mt-3 px-4 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold"
              >
                Xóa bộ lọc
              </button>
            </div>
          ) : (
            (onlyVerified ? listings.filter(l => l.isVerified) : listings).map((item, index) => {
              const isSelected = item.id === (hoveredId || selectedId);
              const unitPrice = calculateUnitPrice(item.priceVnd, item.areaM2);

              return (
                <article
                  key={item.id}
                  onMouseEnter={() => setHoveredId(item.id)}
                  onMouseLeave={() => setHoveredId(null)}
                  onClick={() => setSelectedId(item.id)}
                  className={`bg-surface-container-lowest rounded-2xl p-3.5 border transition-all cursor-pointer flex flex-col sm:flex-row gap-3.5 relative ${
                    isSelected
                      ? 'border-primary shadow-lg ring-2 ring-primary/30'
                      : 'border-outline-variant/40 hover:border-outline shadow-sm'
                  }`}
                >
                  {/* Indicator if active */}
                  {isSelected && (
                    <div className="absolute -top-2.5 left-4 px-2.5 py-0.5 bg-primary text-white text-[10px] font-bold rounded-full shadow-sm flex items-center gap-1 z-20">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping"></span>
                      Đang hiển thị trên bản đồ
                    </div>
                  )}

                  {/* Thumbnail Image */}
                  <div className="relative w-full sm:w-48 h-40 sm:h-36 rounded-xl overflow-hidden shrink-0 bg-surface-container">
                    {item.primaryImageUrl ? <img
                      src={item.primaryImageUrl}
                      alt={item.title}
                      loading={index === 0 ? 'eager' : 'lazy'}
                      decoding="async"
                      fetchPriority={index === 0 ? 'high' : 'auto'}
                      className="w-full h-full object-cover transition-transform duration-500 hover:scale-105"
                    /> : <div className="grid h-full place-items-center bg-surface-container-high text-on-surface-variant" role="img" aria-label="Tin đăng chưa có ảnh">
                      <span className="material-symbols-outlined text-5xl" aria-hidden="true">apartment</span>
                    </div>}
                    <div className="absolute top-2 left-2 flex flex-col gap-1">
                      <span className="px-2 py-0.5 rounded-md bg-primary/90 text-white font-bold text-[10px] backdrop-blur-sm">
                        {item.purpose === 'SALE' ? 'Bán' : 'Cho thuê'}
                      </span>
                      {item.isVerified && (
                        <span className="px-2 py-0.5 rounded-md bg-emerald-700/95 text-white font-bold text-[10px] flex items-center gap-1 shadow-sm border border-emerald-400/40">
                          <span className="material-symbols-outlined text-[12px]">verified</span>
                          Trạng thái kiểm duyệt nội bộ
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Card Content */}
                  <div className="flex-1 flex flex-col justify-between min-w-0">
                    <div>
                      {/* Price & Unit Price */}
                      <div className="flex items-baseline justify-between gap-1">
                        <div className="flex items-baseline gap-1.5">
                          <span className="text-lg font-extrabold text-primary">
                            {formatPriceVnd(item.priceVnd)}
                          </span>
                          {unitPrice && (
                            <span className="text-xs text-on-surface-variant font-medium">
                              {unitPrice}
                            </span>
                          )}
                        </div>
                        <span className="text-[10px] font-semibold px-2 py-0.5 rounded bg-surface-container text-on-surface-variant uppercase">
                          {formatPropertyType(item.propertyType)}
                        </span>
                      </div>

                      {/* Title */}
                      <h3 className="font-bold text-sm text-on-surface line-clamp-2 mt-1 leading-snug hover:text-primary transition-colors">
                        {item.title}
                      </h3>

                      {/* Address */}
                      <p className="text-xs text-on-surface-variant flex items-center gap-1 mt-1 truncate">
                        <svg className="w-3.5 h-3.5 text-secondary shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                        </svg>
                        <span className="truncate">{item.addressSummary}</span>
                      </p>

                      {/* Specs */}
                      <div className="flex items-center py-1.5 px-2 mt-2 bg-surface-container-low rounded-lg text-xs font-semibold text-on-surface">
                        <span>Diện tích: {item.areaM2} m²</span>
                      </div>
                    </div>

                    {/* Action Bar */}
                    <div className="flex items-center justify-between pt-2 mt-1 border-t border-outline-variant/20">
                      <span className="text-[11px] font-semibold text-on-surface-variant">Thông tin đã qua kiểm duyệt</span>

                      <Link
                        to={`/listings/${item.id}`}
                        onClick={(event) => event.stopPropagation()}
                        className="relative z-20 min-h-9 px-4 rounded-lg bg-primary hover:bg-primary/90 text-white text-xs font-semibold transition inline-flex items-center gap-1"
                      >
                        Chi tiết
                        <span>→</span>
                      </Link>
                    </div>
                  </div>
                </article>
              );
            })
          )}
        </div>
      </section>

      {/* ========================================================= */}
      {/* RIGHT PANEL: Full-Height Interactive GIS Map Studio (54%) */}
      {/* ========================================================= */}
      <section className="hidden xl:block w-[54%] h-full relative bg-slate-900 overflow-hidden border-l border-outline-variant/30">
        <ListingMap listings={listings} onSearchArea={(bounds) => fetchListings(bounds)} />
      </section>
    </div>
  );
}

export default SearchAndMapPage;
