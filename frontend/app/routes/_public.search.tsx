import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listingApi } from '../entities/listing/api/listingApi';
import type { Listing, ListingSearchParams } from '../entities/listing/model/types';
import { ListingMap, type MapBounds, type MapFocus } from '@/shared/map/ListingMap';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { geocodePlaces, type GeocodePlace } from '@/shared/api/geocodingApi';
import {
  Building2,
  Home,
  LayoutGrid,
  Loader2,
  Map as MapIcon,
  MapPin,
  Navigation,
  Scale,
  Search,
  X,
} from 'lucide-react';

type Suggestion = { kind: 'keyword'; text: string } | { kind: 'place'; place: GeocodePlace };

export function SearchAndMapPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [listings, setListings] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'list' | 'map'>(searchParams.get('view') === 'map' ? 'map' : 'list');

  // Filters state
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [purpose, setPurpose] = useState<'SALE' | 'RENT'>((searchParams.get('purpose') as 'SALE' | 'RENT') || 'SALE');
  const [propertyType, setPropertyType] = useState<string>(searchParams.get('propertyType') || '');
  const [priceRange, setPriceRange] = useState<string>('ALL'); // ALL, <3B, 3-5B, >5B
  const [sortBy, setSortBy] = useState<'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'AREA_DESC'>('LATEST');
  const [onlyVerified, setOnlyVerified] = useState(false);

  // Place search: a chosen place moves the map and replaces the keyword filter with its visible area.
  const [focus, setFocus] = useState<MapFocus | null>(null);
  const [activePlace, setActivePlace] = useState<GeocodePlace | null>(null);
  const areaRef = useRef<MapBounds | undefined>();
  const [places, setPlaces] = useState<GeocodePlace[]>([]);
  const [placesLoading, setPlacesLoading] = useState(false);
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const [emptyKeywordPlace, setEmptyKeywordPlace] = useState<GeocodePlace | null>(null);
  const requestSeq = useRef(0);

  // Load listings from API
  useEffect(() => {
    fetchListings(areaRef.current);
    // Refetch when a server-side filter changes; fetchListings reads the latest state itself.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [purpose, propertyType, priceRange, sortBy]);

  const fetchListings = async (bounds?: MapBounds, overrides?: Partial<ListingSearchParams>) => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setLoadError(null);
    try {
      // The map needs every pin in view; the API caps a page at 100.
      const params: ListingSearchParams = {
        purpose,
        sortBy,
        size: 100,
      };
      const effectivePropertyType = overrides && 'propertyType' in overrides ? overrides.propertyType : propertyType;
      const effectiveKeyword =
        overrides && 'keyword' in overrides ? overrides.keyword : activePlace ? undefined : keyword;
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
      if (seq !== requestSeq.current) return data;
      setListings(data);
      return data;
    } catch (err) {
      if (seq !== requestSeq.current) return [];
      console.error('Lỗi tải danh sách tìm kiếm:', err);
      setListings([]);
      setLoadError('Không thể tải dữ liệu tìm kiếm. Vui lòng thử lại.');
      return [];
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  };

  // Debounced place suggestions while typing.
  useEffect(() => {
    const text = keyword.trim();
    if (activePlace && text === activePlace.label) return;
    if (text.length < 3) {
      setPlaces([]);
      setPlacesLoading(false);
      return;
    }
    const controller = new AbortController();
    setPlacesLoading(true);
    const timer = window.setTimeout(() => {
      geocodePlaces(text, controller.signal)
        .then((result) => setPlaces(result))
        .catch(() => {
          if (!controller.signal.aborted) setPlaces([]);
        })
        .finally(() => {
          if (!controller.signal.aborted) setPlacesLoading(false);
        });
    }, 450);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
    // Suggestions follow typing only; choosing a place must not trigger a new lookup.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword]);

  const visibleListings = onlyVerified ? listings.filter((listing) => listing.isVerified) : listings;
  const suggestions: Suggestion[] = keyword.trim()
    ? [{ kind: 'keyword', text: keyword.trim() }, ...places.map((place) => ({ kind: 'place' as const, place }))]
    : [];

  const syncUrl = (extra: Record<string, string> = {}) => {
    const next = new URLSearchParams({ purpose, sortBy, ...extra });
    if (propertyType) next.set('propertyType', propertyType);
    if (priceRange !== 'ALL') next.set('priceRange', priceRange);
    setSearchParams(next, { replace: true });
  };

  const goToPlace = (place: GeocodePlace) => {
    setActivePlace(place);
    setKeyword(place.label);
    setEmptyKeywordPlace(null);
    setSuggestOpen(false);
    setHighlight(-1);
    setViewMode('map');
    setFocus({ ...place, key: Date.now() });
    syncUrl({ view: 'map' });
  };

  const runKeywordSearch = async (text: string) => {
    setActivePlace(null);
    setFocus(null);
    areaRef.current = undefined;
    setSuggestOpen(false);
    setHighlight(-1);
    syncUrl(text ? { keyword: text } : {});
    const data = await fetchListings(undefined, { keyword: text || undefined });
    // Nothing matches the words: offer to look at that place on the map instead.
    setEmptyKeywordPlace(text && data.length === 0 && places.length ? places[0] : null);
  };

  const clearKeyword = () => {
    setKeyword('');
    setPlaces([]);
    void runKeywordSearch('');
  };

  const resetFilters = () => {
    setKeyword('');
    setPlaces([]);
    setPropertyType('');
    setPriceRange('ALL');
    setOnlyVerified(false);
    setActivePlace(null);
    setFocus(null);
    setEmptyKeywordPlace(null);
    areaRef.current = undefined;
    setSearchParams({ purpose, sortBy });
    void fetchListings(undefined, { keyword: undefined, propertyType: undefined });
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const chosen = highlight >= 0 ? suggestions[highlight] : undefined;
    if (chosen?.kind === 'place') goToPlace(chosen.place);
    else void runKeywordSearch(keyword.trim());
  };

  const handleSearchArea = (bounds: MapBounds) => {
    areaRef.current = bounds;
    void fetchListings(bounds);
  };

  const onInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (!suggestOpen || !suggestions.length) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlight((index) => (index + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlight((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
    } else if (event.key === 'Escape') {
      setSuggestOpen(false);
      setHighlight(-1);
    }
  };

  const summary = activePlace ? (
    <>
      trong khu vực <span className="font-bold text-on-surface">{activePlace.label.split(',')[0]}</span>
    </>
  ) : areaRef.current ? (
    'trong vùng bản đồ đang xem'
  ) : (
    'phù hợp bộ lọc'
  );

  const emptyState = (
    <div className="text-center py-16 text-on-surface-variant text-sm">
      <p>
        {activePlace
          ? 'Chưa có tin đăng trong khu vực này.'
          : 'Không tìm thấy bất động sản nào khớp với bộ lọc hiện tại.'}
      </p>
      {emptyKeywordPlace && (
        <button
          type="button"
          onClick={() => goToPlace(emptyKeywordPlace)}
          className="mt-3 inline-flex min-h-11 items-center gap-2 rounded-lg border border-primary/30 bg-primary/5 px-4 font-semibold text-primary"
        >
          <Navigation className="h-4 w-4" /> Xem “{emptyKeywordPlace.label.split(',')[0]}” trên bản đồ
        </button>
      )}
      <div>
        <button
          onClick={resetFilters}
          className="mt-3 px-4 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold"
        >
          Xóa bộ lọc
        </button>
      </div>
    </div>
  );

  return (
    <div className="min-h-[calc(100vh-4rem)] bg-surface text-on-surface">
      <section className="w-full bg-surface relative">
        {/* Top Query & Smart Filters */}
        <div className="p-4 bg-surface-container-lowest flex flex-col gap-3 shadow-sm border-b border-outline-variant/20 flex-shrink-0 relative z-40">
          {/* Search Input Bar */}
          <form onSubmit={handleSearchSubmit} className="flex items-center gap-2">
            <div className="relative flex-1">
              <div className="flex items-center bg-surface-container-low rounded-xl px-3 py-2 transition-all focus-within:ring-2 focus-within:ring-primary/20">
                <MapPin className="w-5 h-5 text-primary mr-2 flex-shrink-0" aria-hidden="true" />
                <div className="flex flex-col flex-1 min-w-0">
                  <label
                    htmlFor="search-keyword"
                    className="text-xs font-bold text-on-surface-variant uppercase tracking-wider leading-none"
                  >
                    Từ khóa hoặc địa điểm · Hà Nội
                  </label>
                  <input
                    id="search-keyword"
                    type="text"
                    role="combobox"
                    aria-expanded={suggestOpen && suggestions.length > 0}
                    aria-controls="search-suggestions"
                    aria-autocomplete="list"
                    aria-activedescendant={highlight >= 0 ? `suggestion-${highlight}` : undefined}
                    autoComplete="off"
                    value={keyword}
                    onChange={(e) => {
                      setKeyword(e.target.value);
                      setActivePlace(null);
                      setSuggestOpen(true);
                      setHighlight(-1);
                    }}
                    onFocus={() => setSuggestOpen(true)}
                    onBlur={() => window.setTimeout(() => setSuggestOpen(false), 150)}
                    onKeyDown={onInputKeyDown}
                    placeholder="Nhập dự án, đường, phường, quận… (VD: Cầu Giấy, Times City)"
                    className="bg-transparent text-sm font-semibold text-on-surface focus:outline-none w-full truncate pt-0.5"
                  />
                </div>
                {placesLoading && (
                  <Loader2 className="h-4 w-4 animate-spin text-outline" aria-label="Đang tìm địa điểm" />
                )}
                {keyword && (
                  <button
                    type="button"
                    onClick={clearKeyword}
                    aria-label="Xóa từ khóa"
                    className="text-outline hover:text-on-surface ml-1 grid h-8 w-8 place-items-center text-xs"
                  >
                    <X className="h-4 w-4" aria-hidden="true" />
                  </button>
                )}
              </div>

              {suggestOpen && suggestions.length > 0 && (
                <ul
                  id="search-suggestions"
                  role="listbox"
                  className="absolute inset-x-0 top-full z-50 mt-1 max-h-80 overflow-y-auto rounded-xl border border-outline-variant/40 bg-white py-1 shadow-xl"
                >
                  {suggestions.map((suggestion, index) => {
                    const active = index === highlight;
                    const base = `flex w-full items-start gap-3 px-3 py-2.5 text-left text-sm ${active ? 'bg-primary/10' : 'hover:bg-surface-container-low'}`;
                    return suggestion.kind === 'keyword' ? (
                      <li key="keyword" id={`suggestion-${index}`} role="option" aria-selected={active}>
                        <button
                          type="button"
                          onMouseDown={(event) => event.preventDefault()}
                          onClick={() => void runKeywordSearch(suggestion.text)}
                          className={base}
                        >
                          <Search className="mt-0.5 h-4 w-4 shrink-0 text-outline" aria-hidden="true" />
                          <span>
                            Tìm tin đăng có từ khóa <strong>“{suggestion.text}”</strong>
                          </span>
                        </button>
                      </li>
                    ) : (
                      <li
                        key={`${suggestion.place.lat},${suggestion.place.lon},${index}`}
                        id={`suggestion-${index}`}
                        role="option"
                        aria-selected={active}
                      >
                        {index === 1 && (
                          <p className="px-3 pb-1 pt-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">
                            Đi tới địa điểm trên bản đồ
                          </p>
                        )}
                        <button
                          type="button"
                          onMouseDown={(event) => event.preventDefault()}
                          onClick={() => goToPlace(suggestion.place)}
                          className={base}
                        >
                          <Navigation className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                          <span className="min-w-0">
                            <span className="block truncate font-semibold text-on-surface">
                              {suggestion.place.label.split(',')[0]}
                            </span>
                            <span className="block truncate text-xs text-on-surface-variant">
                              {suggestion.place.label.split(',').slice(1).join(',').trim()}
                            </span>
                          </span>
                        </button>
                      </li>
                    );
                  })}
                  {placesLoading && <li className="px-3 py-2 text-xs text-on-surface-variant">Đang tìm địa điểm…</li>}
                </ul>
              )}
            </div>

            <button
              type="submit"
              aria-label="Tìm kiếm"
              className="h-11 px-4 rounded-xl bg-primary hover:bg-primary/90 text-white font-semibold text-sm flex items-center gap-1.5 shadow-sm transition"
            >
              <Search className="w-4 h-4" aria-hidden="true" />
              <span className="hidden sm:inline">Tìm kiếm</span>
            </button>
          </form>

          {/* Quick Filter Pills */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs no-scrollbar font-medium">
            {/* Purpose Toggle */}
            <div className="flex rounded-lg bg-surface-container-low p-0.5 border border-outline-variant/30 shrink-0">
              <button
                onClick={() => setPurpose('SALE')}
                aria-pressed={purpose === 'SALE'}
                className={`px-3 py-1 rounded-md transition ${purpose === 'SALE' ? 'bg-primary text-white font-bold shadow-sm' : 'text-on-surface-variant'}`}
              >
                Cần bán
              </button>
              <button
                onClick={() => setPurpose('RENT')}
                aria-pressed={purpose === 'RENT'}
                className={`px-3 py-1 rounded-md transition ${purpose === 'RENT' ? 'bg-primary text-white font-bold shadow-sm' : 'text-on-surface-variant'}`}
              >
                Cho thuê
              </button>
            </div>

            {/* Property Type Pills */}
            <button
              onClick={() => setPropertyType(propertyType === 'APARTMENT' ? '' : 'APARTMENT')}
              aria-pressed={propertyType === 'APARTMENT'}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border transition shrink-0 ${
                propertyType === 'APARTMENT'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              <Building2 className="h-4 w-4" aria-hidden="true" />
              Căn hộ
            </button>

            <button
              onClick={() => setPropertyType(propertyType === 'HOUSE' ? '' : 'HOUSE')}
              aria-pressed={propertyType === 'HOUSE'}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border transition shrink-0 ${
                propertyType === 'HOUSE'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/40 text-on-surface-variant hover:border-outline'
              }`}
            >
              <Home className="h-4 w-4" aria-hidden="true" />
              Nhà phố
            </button>

            {/* Price Range Pills */}
            <button
              onClick={() => setPriceRange(priceRange === '<3B' ? 'ALL' : '<3B')}
              aria-pressed={priceRange === '<3B'}
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
              aria-pressed={priceRange === '3-5B'}
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
              aria-pressed={priceRange === '>5B'}
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
              aria-pressed={onlyVerified}
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
              <Scale className="h-4 w-4" aria-hidden="true" />
              So sánh bất động sản
            </Link>
          </div>
        </div>

        {/* Feed Summary & Sorting Toolbar */}
        <div className="px-4 py-2.5 flex items-center justify-between bg-surface-container-low/50 border-b border-outline-variant/20 flex-shrink-0 text-xs">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span className="font-bold text-primary text-sm">{visibleListings.length}</span>
            <span className="text-on-surface-variant font-medium">bất động sản {summary}</span>
          </div>

          <div className="flex items-center gap-2">
            <div
              className="flex rounded-lg bg-surface-container-low p-0.5 border border-outline-variant/30"
              role="group"
              aria-label="Chế độ hiển thị kết quả"
            >
              <button
                type="button"
                onClick={() => setViewMode('list')}
                aria-pressed={viewMode === 'list'}
                className={`min-h-9 px-2 sm:px-3 rounded-md inline-flex items-center gap-1.5 font-semibold transition ${viewMode === 'list' ? 'bg-primary text-white shadow-sm' : 'text-on-surface-variant hover:text-primary'}`}
              >
                <LayoutGrid className="w-4 h-4" />
                <span className="hidden sm:inline">Danh sách</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode('map')}
                aria-pressed={viewMode === 'map'}
                className={`min-h-9 px-2 sm:px-3 rounded-md inline-flex items-center gap-1.5 font-semibold transition ${viewMode === 'map' ? 'bg-primary text-white shadow-sm' : 'text-on-surface-variant hover:text-primary'}`}
              >
                <MapIcon className="w-4 h-4" />
                <span className="hidden sm:inline">Bản đồ</span>
              </button>
            </div>
            <span className="text-on-surface-variant">Sắp xếp:</span>
            <select
              aria-label="Sắp xếp kết quả"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
              className="bg-surface-container-lowest font-semibold text-primary px-2 py-1 rounded-lg border border-outline-variant/30 focus:outline-none"
            >
              <option value="LATEST">Mới niêm yết</option>
              <option value="PRICE_ASC">Giá: Thấp đến cao</option>
              <option value="PRICE_DESC">Giá: Cao đến thấp</option>
              <option value="AREA_DESC">Diện tích lớn nhất</option>
            </select>
          </div>
        </div>

        {viewMode === 'map' ? (
          // The map stays mounted while results reload so the view never jumps back to the default area.
          <div className="h-[calc(100vh-12.5rem)] min-h-[34rem] relative bg-slate-900">
            <ListingMap listings={visibleListings} focus={focus} onSearchArea={handleSearchArea} />
            <div className="pointer-events-none absolute inset-x-0 bottom-6 z-40 flex justify-center px-4">
              {loading ? (
                <span className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-lg">
                  <Loader2 className="h-4 w-4 animate-spin" /> Đang tải tin đăng…
                </span>
              ) : loadError ? (
                <span
                  role="alert"
                  className="pointer-events-auto inline-flex items-center gap-3 rounded-full bg-white px-4 py-2 text-sm font-semibold text-rose-700 shadow-lg"
                >
                  {loadError}
                  <button type="button" onClick={() => void fetchListings(areaRef.current)} className="underline">
                    Thử lại
                  </button>
                </span>
              ) : visibleListings.length === 0 ? (
                <span className="rounded-full bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-lg">
                  Chưa có tin đăng trong vùng này — kéo hoặc thu nhỏ bản đồ rồi bấm “Tìm trong khu vực này”.
                </span>
              ) : null}
            </div>
          </div>
        ) : (
          <div className="max-w-7xl mx-auto p-4 md:p-6">
            {loading ? (
              <div className="text-center py-16 text-on-surface-variant text-sm">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                Đang tải danh sách bất động sản…
              </div>
            ) : loadError ? (
              <div className="py-16 text-center" role="alert">
                <p className="text-rose-700">{loadError}</p>
                <button
                  type="button"
                  onClick={() => void fetchListings(areaRef.current)}
                  className="mt-4 min-h-11 rounded-xl bg-primary px-5 font-bold text-white"
                >
                  Thử lại
                </button>
              </div>
            ) : visibleListings.length === 0 ? (
              emptyState
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 md:gap-5">
                {visibleListings.map((item) => (
                  <ListingCard key={item.id} listing={item} />
                ))}
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

export default SearchAndMapPage;
