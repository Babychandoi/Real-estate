import { useEffect, useState, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingApi } from '../entities/listing/api/listingApi';
import type { Listing, ListingSearchParams } from '../entities/listing/model/types';
import { formatPriceVnd, calculateUnitPrice } from '../entities/listing/model/types';

interface MapPin {
  id: string;
  listingId: string;
  title: string;
  priceLabel: string;
  priceVnd: number;
  latitude: number;
  longitude: number;
  topPercent: number;
  leftPercent: number;
  propertyType: string;
  isVerified: boolean;
}

export function SearchAndMapPage() {
  const [searchParams] = useSearchParams();
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
  const [searchAsMoveMap, setSearchAsMoveMap] = useState(true);
  const [mapZoom, setMapZoom] = useState(1);

  // Load listings from API
  useEffect(() => {
    fetchListings();
  }, [purpose, propertyType, priceRange, sortBy]);

  const fetchListings = async () => {
    setLoading(true);
    try {
      const params: ListingSearchParams = {
        purpose,
        sortBy,
      };
      if (propertyType) params.propertyType = propertyType;
      if (keyword.trim()) params.keyword = keyword.trim();

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
    fetchListings();
  };

  // Convert listings coordinates to map pin percentages
  // Hanoi Pilot Area Center: Lat ~21.01, Lng ~105.77 (Bounds: 20.98 - 21.05 Lat, 105.72 - 105.82 Lng)
  const mapPins: MapPin[] = useMemo(() => {
    const minLat = 20.98;
    const maxLat = 21.06;
    const minLng = 105.72;
    const maxLng = 105.82;

    return listings.map((item, index) => {
      const lat = item.publicLatitude || (21.00 + (index % 4) * 0.015);
      const lng = item.publicLongitude || (105.74 + (index % 3) * 0.025);

      // Normalize to 10% - 90% space
      const top = Math.max(12, Math.min(88, 100 - ((lat - minLat) / (maxLat - minLat)) * 100));
      const left = Math.max(12, Math.min(88, ((lng - minLng) / (maxLng - minLng)) * 100));

      return {
        id: item.id,
        listingId: item.id,
        title: item.title,
        priceLabel: formatPriceVnd(item.priceVnd),
        priceVnd: item.priceVnd,
        latitude: lat,
        longitude: lng,
        topPercent: top,
        leftPercent: left,
        propertyType: item.propertyType,
        isVerified: item.isVerified,
      };
    });
  }, [listings]);

  const activeListing = listings.find((l) => l.id === (hoveredId || selectedId)) || listings[0];
  const activePin = mapPins.find((p) => p.id === (hoveredId || selectedId));

  return (
    <div className="w-full flex flex-col xl:flex-row h-[calc(100vh-4rem)] overflow-hidden bg-surface text-on-surface">
      {/* ========================================================= */}
      {/* LEFT PANEL: Filter Toolbar & Scrollable Listing Feed (45%) */}
      {/* ========================================================= */}
      <section className="w-full xl:w-[46%] flex flex-col h-full bg-surface z-10 shadow-[4px_0_24px_rgba(11,28,48,0.06)] relative border-r border-outline-variant/30">
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
                  <strong className="text-on-surface font-semibold">Bảo mật chuẩn BDS WF:</strong> Tọa độ ghim được làm mờ bán kính ~200m bảo vệ quyền riêng tư người bán (FR13).
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
            (onlyVerified ? listings.filter(l => l.isVerified) : listings).map((item) => {
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
                      Đang hiển thị trên tâm bản đồ
                    </div>
                  )}

                  {/* Thumbnail Image */}
                  <div className="relative w-full sm:w-48 h-40 sm:h-36 rounded-xl overflow-hidden shrink-0 bg-surface-container">
                    <img
                      src={item.primaryImageUrl || 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80'}
                      alt={item.title}
                      className="w-full h-full object-cover transition-transform duration-500 hover:scale-105"
                    />
                    <div className="absolute top-2 left-2 flex flex-col gap-1">
                      <span className="px-2 py-0.5 rounded-md bg-primary/90 text-white font-bold text-[10px] backdrop-blur-sm">
                        {item.purpose === 'SALE' ? 'Bán' : 'Cho thuê'}
                      </span>
                      {item.isVerified && (
                        <span className="px-2 py-0.5 rounded-md bg-emerald-700/95 text-white font-bold text-[10px] flex items-center gap-1 shadow-sm border border-emerald-400/40">
                          <span className="material-symbols-outlined text-[12px]">verified</span>
                          Sổ hồng chính chủ • eKYC
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
                          {item.propertyType}
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
                      <div className="flex items-center gap-3 py-1.5 px-2 mt-2 bg-surface-container-low rounded-lg text-xs font-semibold text-on-surface">
                        <span>📐 {item.areaM2} m²</span>
                        <span className="w-1 h-1 rounded-full bg-outline-variant"></span>
                        <span>🛏️ 2 PN</span>
                        <span className="w-1 h-1 rounded-full bg-outline-variant"></span>
                        <span>🚿 2 WC</span>
                      </div>
                    </div>

                    {/* Action Bar */}
                    <div className="flex items-center justify-between pt-2 mt-1 border-t border-outline-variant/20">
                      <div className="flex items-center gap-1.5">
                        <div className="w-6 h-6 rounded-full bg-primary/20 text-primary font-bold text-[10px] flex items-center justify-center">
                          WF
                        </div>
                        <span className="text-[11px] font-semibold text-on-surface-variant">Môi giới WF Pro</span>
                      </div>

                      <Link
                        to={`/listings/${item.id}`}
                        className="px-3 py-1 rounded-lg bg-primary hover:bg-primary/90 text-white text-xs font-semibold transition flex items-center gap-1"
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
      <section className="hidden xl:flex xl:w-[54%] relative h-full bg-slate-900 overflow-hidden select-none">
        {/* Map Grid / Satellite Background Simulation */}
        <div
          className="absolute inset-0 w-full h-full bg-cover bg-center opacity-90 transition-transform duration-300"
          style={{
            backgroundImage: `url('https://lh3.googleusercontent.com/aida-public/AB6AXuA4PLQ772cbzQkyaewfX451p7muH9GeujoDu8znVg-E30WZy2A8BAjM1ExFML0Yf3hvktFnoSXPgVxof2yxoEupgxBzlozAfjMZINPoBieF7sqnQcy9Vcc020TOCkTbg4eGEhK56HITGc4b0gfU0BPPjJMVNfbnzzE3j4E_Rym6OMqmbJcerLGHjbOuyoa36sD9QHPG63psWj55wwh01tva2pkwuhQxnHSYT5mnzzgMAuv71t6DL4c')`,
            transform: `scale(${mapZoom})`,
          }}
        />

        {/* GIS Map Grid Overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-transparent to-slate-950/30 pointer-events-none" />

        {/* FR13 Privacy Halo around Active Pin */}
        {activePin && (
          <div
            className="absolute -translate-x-1/2 -translate-y-1/2 rounded-full bg-sky-500/20 backdrop-blur-[1px] pointer-events-none border border-sky-400/40 flex items-center justify-center animate-pulse transition-all duration-300"
            style={{
              top: `${activePin.topPercent}%`,
              left: `${activePin.leftPercent}%`,
              width: '180px',
              height: '180px',
            }}
          >
            <span className="text-[9px] font-bold tracking-widest uppercase bg-slate-950/80 text-sky-300 px-2 py-0.5 rounded shadow-sm border border-sky-500/30">
              Vùng bảo mật ~200m
            </span>
          </div>
        )}

        {/* GIS MAP PINS LAYER */}
        {mapPins.map((pin) => {
          const isActive = pin.id === (hoveredId || selectedId);
          return (
            <div
              key={pin.id}
              onClick={() => setSelectedId(pin.id)}
              onMouseEnter={() => setHoveredId(pin.id)}
              onMouseLeave={() => setHoveredId(null)}
              className="absolute -translate-x-1/2 -translate-y-1/2 group cursor-pointer z-30 transition-transform duration-200 hover:scale-110"
              style={{
                top: `${pin.topPercent}%`,
                left: `${pin.leftPercent}%`,
              }}
            >
              <div
                className={`px-3 py-1.5 rounded-full font-bold text-xs shadow-xl flex items-center gap-1.5 transition-all border ${
                  isActive
                    ? 'bg-primary text-white border-white ring-4 ring-primary/40 scale-110'
                    : 'bg-white text-slate-900 border-slate-300 hover:bg-primary hover:text-white'
                }`}
              >
                <span className={`w-2 h-2 rounded-full ${isActive ? 'bg-emerald-400 animate-ping' : 'bg-secondary'}`}></span>
                <span>{pin.priceLabel}</span>
              </div>
            </div>
          );
        })}

        {/* Cluster Badges Simulation (Cầu Giấy & Hà Đông) */}
        <div className="absolute top-[22%] left-[28%] -translate-x-1/2 -translate-y-1/2 z-20 pointer-events-none">
          <div className="w-12 h-12 rounded-full bg-primary/90 text-white flex flex-col items-center justify-center shadow-lg ring-4 ring-primary-fixed/40">
            <span className="text-xs font-extrabold leading-none">24</span>
            <span className="text-[8px] uppercase tracking-tighter text-sky-200">Cầu Giấy</span>
          </div>
        </div>

        <div className="absolute bottom-[28%] left-[32%] -translate-x-1/2 -translate-y-1/2 z-20 pointer-events-none">
          <div className="w-12 h-12 rounded-full bg-secondary/90 text-white flex flex-col items-center justify-center shadow-lg ring-4 ring-secondary-container/50">
            <span className="text-xs font-extrabold leading-none">12</span>
            <span className="text-[8px] uppercase tracking-tighter text-emerald-200">Hà Đông</span>
          </div>
        </div>

        {/* Floating Mini Card for Active Listing */}
        {activeListing && (
          <div className="absolute bottom-6 left-6 z-40 bg-slate-950/95 border border-slate-800 rounded-2xl p-3 shadow-2xl backdrop-blur-md max-w-sm flex gap-3 text-white">
            <img
              src={activeListing.primaryImageUrl || 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=400&q=80'}
              alt={activeListing.title}
              className="w-20 h-20 rounded-xl object-cover shrink-0"
            />
            <div className="flex flex-col justify-between min-w-0">
              <div>
                <span className="text-xs font-extrabold text-emerald-400">
                  {formatPriceVnd(activeListing.priceVnd)}
                </span>
                <h4 className="text-xs font-bold text-slate-100 line-clamp-1 mt-0.5">
                  {activeListing.title}
                </h4>
                <p className="text-[11px] text-slate-400 truncate mt-0.5">
                  {activeListing.addressSummary}
                </p>
              </div>
              <Link
                to={`/listings/${activeListing.id}`}
                className="text-[11px] font-semibold text-sky-400 hover:text-sky-300 flex items-center gap-1 mt-1"
              >
                Xem chi tiết bất động sản ➔
              </Link>
            </div>
          </div>
        )}

        {/* Map Floating Controls */}
        <div className="absolute top-6 right-6 z-40 flex flex-col gap-2">
          {/* Zoom controls */}
          <div className="flex flex-col rounded-xl bg-slate-950/90 border border-slate-800 shadow-xl overflow-hidden backdrop-blur">
            <button
              onClick={() => setMapZoom((z) => Math.min(1.4, z + 0.1))}
              className="w-9 h-9 flex items-center justify-center text-slate-200 hover:bg-slate-800 font-bold text-base transition border-b border-slate-800"
              title="Phóng to"
            >
              +
            </button>
            <button
              onClick={() => setMapZoom((z) => Math.max(0.8, z - 0.1))}
              className="w-9 h-9 flex items-center justify-center text-slate-200 hover:bg-slate-800 font-bold text-base transition"
              title="Thu nhỏ"
            >
              −
            </button>
          </div>

          {/* Re-center button */}
          <button
            onClick={() => setMapZoom(1)}
            className="w-9 h-9 rounded-xl bg-slate-950/90 border border-slate-800 text-slate-200 hover:bg-slate-800 flex items-center justify-center shadow-xl backdrop-blur transition"
            title="Định vị tâm Hà Nội"
          >
            <svg className="w-4 h-4 text-sky-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </button>
        </div>

        {/* Bottom Map Status Bar */}
        <div className="absolute bottom-6 right-6 z-40 bg-slate-950/90 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-300 shadow-xl backdrop-blur flex items-center gap-3">
          <label className="flex items-center gap-1.5 cursor-pointer">
            <input
              type="checkbox"
              checked={searchAsMoveMap}
              onChange={(e) => setSearchAsMoveMap(e.target.checked)}
              className="rounded bg-slate-800 border-slate-700 text-sky-500 focus:ring-0 w-3.5 h-3.5"
            />
            <span>Tìm kiếm khi di chuyển bản đồ</span>
          </label>
          <span className="w-1 h-1 rounded-full bg-slate-700"></span>
          <span className="text-slate-400 text-[11px]">Hà Nội WGS84 • PostGIS Ready</span>
        </div>
      </section>
    </div>
  );
}

export default SearchAndMapPage;
