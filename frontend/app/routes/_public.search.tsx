import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingApi } from '../entities/listing/api/listingApi';
import type { Listing, ListingSearchParams } from '../entities/listing/model/types';
import { ListingMap, type MapBounds } from '@/shared/map/ListingMap';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { LayoutGrid, Map as MapIcon } from 'lucide-react';

export function SearchAndMapPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [listings, setListings] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'list' | 'map'>('list');

  // Filters state
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [purpose, setPurpose] = useState<'SALE' | 'RENT'>(
    (searchParams.get('purpose') as 'SALE' | 'RENT') || 'SALE'
  );
  const [propertyType, setPropertyType] = useState<string>(searchParams.get('propertyType') || '');
  const [priceRange, setPriceRange] = useState<string>('ALL'); // ALL, <3B, 3-5B, >5B
  const [sortBy, setSortBy] = useState<'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'AREA_DESC'>('LATEST');
  const [onlyVerified, setOnlyVerified] = useState(false);

  // Load listings from API
  useEffect(() => {
    fetchListings();
  }, [purpose, propertyType, priceRange, sortBy]);

  const fetchListings = async (bounds?: MapBounds, overrides?: Partial<ListingSearchParams>) => {
    setLoading(true);
    setLoadError(null);
    try {
      const params: ListingSearchParams = {
        purpose,
        sortBy,
      };
      const effectivePropertyType = overrides && 'propertyType' in overrides ? overrides.propertyType : propertyType;
      const effectiveKeyword = overrides && 'keyword' in overrides ? overrides.keyword : keyword;
      if (effectivePropertyType) params.propertyType = effectivePropertyType;
      if (effectiveKeyword?.trim()) params.keyword = effectiveKeyword.trim();
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
      setListings([]);
      setSelectedId(null);
      setLoadError('Không thể tải dữ liệu tìm kiếm. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  const visibleListings = onlyVerified ? listings.filter((listing) => listing.isVerified) : listings;

  const clearKeyword = () => {
    setKeyword('');
    const next = new URLSearchParams({ purpose, sortBy });
    if (propertyType) next.set('propertyType', propertyType);
    setSearchParams(next);
    void fetchListings(undefined, { keyword: undefined });
  };

  const resetFilters = () => {
    setKeyword('');
    setPropertyType('');
    setPriceRange('ALL');
    setOnlyVerified(false);
    setSearchParams({ purpose, sortBy });
    void fetchListings(undefined, { keyword: undefined, propertyType: undefined });
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
    <div className="min-h-[calc(100vh-4rem)] bg-surface text-on-surface">
      <section className="w-full bg-surface relative">
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
                  onClick={clearKeyword}
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

            {/* Filter Chính chủ eKYC */}
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
              So sánh bất động sản
            </Link>
          </div>

        </div>

        {/* Feed Summary & Sorting Toolbar */}
        <div className="px-4 py-2.5 flex items-center justify-between bg-surface-container-low/50 border-b border-outline-variant/20 flex-shrink-0 text-xs">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span className="font-bold text-primary text-sm">{visibleListings.length}</span>
            <span className="text-on-surface-variant font-medium">bất động sản trong vùng quét</span>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex rounded-lg bg-surface-container-low p-0.5 border border-outline-variant/30" role="group" aria-label="Chế độ hiển thị kết quả">
              <button type="button" onClick={() => setViewMode('list')} aria-pressed={viewMode === 'list'} className={`min-h-9 px-2 sm:px-3 rounded-md inline-flex items-center gap-1.5 font-semibold transition ${viewMode === 'list' ? 'bg-primary text-white shadow-sm' : 'text-on-surface-variant hover:text-primary'}`}><LayoutGrid className="w-4 h-4" /><span className="hidden sm:inline">Danh sách</span></button>
              <button type="button" onClick={() => setViewMode('map')} aria-pressed={viewMode === 'map'} className={`min-h-9 px-2 sm:px-3 rounded-md inline-flex items-center gap-1.5 font-semibold transition ${viewMode === 'map' ? 'bg-primary text-white shadow-sm' : 'text-on-surface-variant hover:text-primary'}`}><MapIcon className="w-4 h-4" /><span className="hidden sm:inline">Bản đồ</span></button>
            </div>
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

        <div className={viewMode === 'map' ? 'h-[calc(100vh-12.5rem)] min-h-[34rem] relative bg-slate-900' : 'max-w-7xl mx-auto p-4 md:p-6'}>
          {loading ? (
            <div className="text-center py-16 text-on-surface-variant text-sm">
              <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
              Đang tải danh sách bất động sản và quét toạ độ GIS...
            </div>
          ) : loadError ? (
            <div className="py-16 text-center" role="alert"><p className="text-rose-700">{loadError}</p><button type="button" onClick={() => void fetchListings()} className="mt-4 min-h-11 rounded-xl bg-primary px-5 font-bold text-white">Thử lại</button></div>
          ) : visibleListings.length === 0 ? (
            <div className="text-center py-16 text-on-surface-variant text-sm">
              <p>Không tìm thấy bất động sản nào khớp với bộ lọc hiện tại.</p>
              <button
                onClick={resetFilters}
                className="mt-3 px-4 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold"
              >
                Xóa bộ lọc
              </button>
            </div>
          ) : (
            viewMode === 'list' ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 md:gap-5">
                {visibleListings.map((item) => <ListingCard key={item.id} listing={item} />)}
              </div>
            ) : (
              <ListingMap listings={visibleListings} selectedId={selectedId} onSearchArea={(bounds) => fetchListings(bounds)} />
            )
          )}
        </div>
      </section>
    </div>
  );
}

export default SearchAndMapPage;
