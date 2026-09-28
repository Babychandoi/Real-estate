import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { LayoutGrid, Map as MapIcon, MapPin, Search, X } from "lucide-react";
import { listingApi } from "@/entities/listing/api/listingApi";
import type { Listing } from "@/entities/listing/model/types";
import { ComparableListingCard } from "@/features/compare/ComparableListingCard";
import {
  priceOptions,
  propertyOptions,
  readSearchState,
  SEARCH_PAGE_SIZE,
  sortOptions,
} from "@/features/search/model/searchState";
import { geocodePlaces, type GeocodePlace } from "@/shared/api/geocodingApi";
import type { MapBounds } from "@/shared/map/ListingMap";
import { ListingSkeleton, StatePanel } from "@/shared/ui/Feedback";
const ListingMap = lazy(() =>
  import("@/shared/map/ListingMap").then((module) => ({
    default: module.ListingMap,
  })),
);
export function SearchAndMapPage() {
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => readSearchState(params), [params]);
  const [keyword, setKeyword] = useState(state.keyword);
  const [items, setItems] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [places, setPlaces] = useState<GeocodePlace[]>([]);
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const queryKey = JSON.stringify(state.query);
  useEffect(() => {
    setKeyword(state.keyword);
  }, [state.keyword]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setFailed(false);
    listingApi
      .searchListings(state.query)
      .then((data) => {
        if (active) setItems(data);
      })
      .catch(() => {
        if (active) {
          setItems([]);
          setFailed(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [queryKey, attempt]);
  useEffect(() => {
    if (keyword.trim().length < 3 || !suggestOpen) {
      setPlaces([]);
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      geocodePlaces(keyword.trim(), controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) setPlaces(result);
        })
        .catch(() => {
          if (!controller.signal.aborted) setPlaces([]);
        });
    }, 450);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [keyword, suggestOpen]);
  const update = (
    values: Record<string, string | undefined>,
    resetPage = true,
  ) => {
    const next = new URLSearchParams(params);
    if (resetPage) next.delete("page");
    Object.entries(values).forEach(([key, value]) =>
      value ? next.set(key, value) : next.delete(key),
    );
    setParams(next);
  };
  const clearArea = {
    place: undefined,
    minLat: undefined,
    maxLat: undefined,
    minLng: undefined,
    maxLng: undefined,
  };
  const searchKeyword = () => {
    setSuggestOpen(false);
    setHighlight(-1);
    update({ keyword: keyword.trim(), ...clearArea });
  };
  const searchArea = (bounds: MapBounds, place = "Vùng bản đồ đã chọn") => {
    setSuggestOpen(false);
    setKeyword("");
    update({
      keyword: undefined,
      place,
      view: "map",
      ...Object.fromEntries(
        Object.entries(bounds).map(([key, value]) => [key, value.toFixed(6)]),
      ),
    });
  };
  const choosePlace = (place: GeocodePlace) => {
    const box = place.bbox || [
      place.lat - 0.01,
      place.lat + 0.01,
      place.lon - 0.01,
      place.lon + 0.01,
    ];
    searchArea(
      { minLat: box[0], maxLat: box[1], minLng: box[2], maxLng: box[3] },
      place.label,
    );
  };
  const focus = state.bounds
    ? {
        label: state.place,
        lat: (state.bounds.minLat + state.bounds.maxLat) / 2,
        lon: (state.bounds.minLng + state.bounds.maxLng) / 2,
        type: "district",
        bbox: [
          state.bounds.minLat,
          state.bounds.maxLat,
          state.bounds.minLng,
          state.bounds.maxLng,
        ] as [number, number, number, number],
        key: JSON.stringify(state.bounds),
      }
    : null;
  const visible = state.verified
    ? items.filter((item) => item.isVerified)
    : items;
  const reset = () => {
    setKeyword("");
    setParams({ purpose: state.purpose });
  };
  return (
    <div className="ndc-page py-8 sm:py-10">
      <div className="ndc-section-heading">
        <div>
          <p className="!mt-0 text-xs font-semibold uppercase tracking-widest">
            Khám phá bất động sản
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">
            {state.purpose === "RENT"
              ? "Tìm nơi thuê phù hợp"
              : "Tìm ngôi nhà tiếp theo"}
          </h1>
          <p>Chọn nhu cầu, thu hẹp khu vực và so sánh trước khi liên hệ.</p>
        </div>
      </div>
      <section className="ndc-search-toolbar" aria-label="Bộ lọc tìm kiếm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="ndc-purpose border">
            <button
              type="button"
              aria-pressed={state.purpose === "SALE"}
              onClick={() => update({ purpose: "SALE", priceRange: undefined })}
            >
              Mua nhà
            </button>
            <button
              type="button"
              aria-pressed={state.purpose === "RENT"}
              onClick={() => update({ purpose: "RENT", priceRange: undefined })}
            >
              Thuê nhà
            </button>
          </div>
          <button className="ndc-text-link" type="button" onClick={reset}>
            Đặt lại bộ lọc
          </button>
        </div>
        <form
          className="relative flex flex-col gap-2 sm:flex-row"
          onSubmit={(event) => {
            event.preventDefault();
            if (highlight >= 0 && places[highlight])
              choosePlace(places[highlight]);
            else searchKeyword();
          }}
        >
          <label className="ndc-field flex-1" htmlFor="search-keyword">
            <span>Từ khóa hoặc địa điểm</span>
            <input
              id="search-keyword"
              placeholder="Nhập dự án, đường, phường…"
              value={keyword}
              maxLength={200}
              autoComplete="off"
              role="combobox"
              aria-expanded={suggestOpen && places.length > 0}
              aria-controls="place-suggestions"
              aria-autocomplete="list"
              aria-activedescendant={
                highlight >= 0 ? `place-${highlight}` : undefined
              }
              onChange={(event) => {
                setKeyword(event.target.value);
                setSuggestOpen(true);
                setHighlight(-1);
              }}
              onFocus={() => setSuggestOpen(true)}
              onBlur={(event) => {
                if (!event.currentTarget.form?.contains(event.relatedTarget)) {
                  setSuggestOpen(false);
                  setHighlight(-1);
                }
              }}
              onKeyDown={(event) => {
                if (event.key === "Escape") {
                  setSuggestOpen(false);
                  setHighlight(-1);
                }
                if (
                  suggestOpen &&
                  places.length &&
                  ["ArrowDown", "ArrowUp"].includes(event.key)
                ) {
                  event.preventDefault();
                  setHighlight((index) =>
                    event.key === "ArrowDown"
                      ? (index + 1) % places.length
                      : index <= 0
                        ? places.length - 1
                        : index - 1,
                  );
                }
              }}
            />
          </label>
          <button type="submit" className="ndc-primary-link sm:self-end">
            <Search className="h-4 w-4" aria-hidden="true" />
            Tìm kiếm
          </button>
          {suggestOpen && places.length > 0 && (
            <ul
              id="place-suggestions"
              role="listbox"
              aria-label="Địa điểm gợi ý"
              className="left-0 right-0 top-[75px] z-40 rounded-xl border bg-white p-2 shadow-xl sm:absolute sm:right-32"
            >
              {places.map((place, index) => (
                <li
                  id={`place-${index}`}
                  key={`${place.lat}-${place.lon}`}
                  role="option"
                  aria-selected={highlight === index}
                >
                  <button
                    type="button"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => choosePlace(place)}
                    className={`flex min-h-11 w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm ${highlight === index ? "bg-surface-container" : "hover:bg-surface-container-low"}`}
                  >
                    <MapPin className="h-4 w-4 shrink-0" aria-hidden="true" />
                    {place.label}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </form>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="ndc-field">
            Loại bất động sản
            <select
              value={state.propertyType}
              onChange={(event) => update({ propertyType: event.target.value })}
            >
              <option value="">Tất cả loại hình</option>
              {propertyOptions.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="ndc-field">
            {state.purpose === "RENT" ? "Giá thuê mỗi tháng" : "Khoảng giá"}
            <select
              value={state.priceRange}
              onChange={(event) => update({ priceRange: event.target.value })}
            >
              <option value="">Tất cả mức giá</option>
              {priceOptions(state.purpose).map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <label className="ndc-field">
            Diện tích
            <select
              value={state.area}
              onChange={(event) => update({ area: event.target.value })}
            >
              <option value="">Tất cả diện tích</option>
              <option value="SMALL">Đến 50 m²</option>
              <option value="MEDIUM">50–100 m²</option>
              <option value="LARGE">Từ 100 m²</option>
            </select>
          </label>
          <label className="ndc-field">
            Sắp xếp
            <select
              value={state.sortBy}
              onChange={(event) => update({ sortBy: event.target.value })}
            >
              {sortOptions.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {state.bounds && (
          <div className="flex items-center gap-2 rounded-lg bg-surface-container-low px-3 py-1 text-sm">
            <MapPin className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span className="min-w-0 flex-1">{state.place}</span>
            <button
              type="button"
              className="ndc-icon-button"
              aria-label="Bỏ giới hạn khu vực"
              onClick={() => update(clearArea)}
            >
              <X aria-hidden="true" />
            </button>
          </div>
        )}
      </section>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="font-semibold">
            {loading
              ? "Đang tìm bất động sản…"
              : `${visible.length} tin trên trang ${state.page}`}
          </h2>
          <label className="mt-1 inline-flex min-h-11 items-center gap-2 text-sm text-on-surface-variant">
            <input
              type="checkbox"
              checked={state.verified}
              onChange={(event) =>
                update(
                  { verified: event.target.checked ? "page" : undefined },
                  false,
                )
              }
            />
            Chỉ tin đã xác thực trong trang này
          </label>
        </div>
        <div
          className="flex gap-1 rounded-lg border bg-white p-1"
          aria-label="Chế độ xem"
        >
          <button
            className={`ndc-nav-link ${state.view === "list" ? "is-active" : ""}`}
            type="button"
            aria-pressed={state.view === "list"}
            onClick={() => update({ view: undefined }, false)}
          >
            <LayoutGrid className="mr-2 h-4 w-4" aria-hidden="true" />
            Danh sách
          </button>
          <button
            className={`ndc-nav-link ${state.view === "map" ? "is-active" : ""}`}
            type="button"
            aria-pressed={state.view === "map"}
            onClick={() => update({ view: "map" }, false)}
          >
            <MapIcon className="mr-2 h-4 w-4" aria-hidden="true" />
            Bản đồ
          </button>
        </div>
      </div>
      <div className="ndc-search-layout" data-view={state.view}>
        <div className="min-w-0" aria-busy={loading}>
          {loading ? (
            <ListingSkeleton />
          ) : failed ? (
            <StatePanel
              error
              onRetry={() => setAttempt((value) => value + 1)}
            />
          ) : !visible.length ? (
            <StatePanel
              title={
                state.verified && items.length
                  ? "Trang này chưa có tin đã xác thực"
                  : "Chưa tìm thấy tin phù hợp"
              }
              description="Thử mở rộng khoảng giá, diện tích hoặc chọn khu vực khác."
              action={
                <button
                  type="button"
                  className="ndc-primary-link"
                  onClick={reset}
                >
                  Xóa bộ lọc
                </button>
              }
            />
          ) : (
            <div className="ndc-listing-grid">
              {visible.map((item) => (
                <ComparableListingCard key={item.id} listing={item} />
              ))}
            </div>
          )}
          <nav className="ndc-pagination" aria-label="Phân trang kết quả">
            <button
              type="button"
              className="ndc-nav-link border disabled:opacity-40"
              disabled={state.page <= 1 || loading}
              onClick={() => update({ page: String(state.page - 1) }, false)}
            >
              Trang trước
            </button>
            <span aria-live="polite">Trang {state.page}</span>
            <button
              type="button"
              className="ndc-nav-link border disabled:opacity-40"
              disabled={loading || failed || items.length < SEARCH_PAGE_SIZE}
              onClick={() => update({ page: String(state.page + 1) }, false)}
            >
              Trang tiếp
            </button>
          </nav>
        </div>
        {state.view === "map" && (
          <section
            className="order-first min-w-0 lg:sticky lg:top-24 lg:order-none lg:self-start"
            aria-label="Bản đồ kết quả"
          >
            <p className="mb-2 text-sm text-on-surface-variant">
              Bản đồ các tin trên trang này. Đổi trang để xem thêm vị trí.
            </p>
            <div className="relative h-[420px] overflow-hidden rounded-2xl border bg-surface-container lg:h-[640px]">
              <Suspense
                fallback={
                  <div className="p-6" role="status">
                    Đang tải bản đồ…
                  </div>
                }
              >
                <ListingMap
                  listings={loading || failed ? [] : visible}
                  focus={focus}
                  onSearchArea={searchArea}
                />
              </Suspense>
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
