import type { ListingSearchParams } from "@/entities/listing/model/types";
export const SEARCH_PAGE_SIZE = 24;
export const propertyOptions = [
  ["APARTMENT", "Căn hộ"],
  ["HOUSE", "Nhà riêng"],
  ["VILLA", "Biệt thự"],
  ["TOWNHOUSE", "Nhà phố"],
  ["LAND", "Đất"],
] as const;
export const sortOptions = [
  ["LATEST", "Mới nhất"],
  ["PRICE_ASC", "Giá tăng dần"],
  ["PRICE_DESC", "Giá giảm dần"],
  ["AREA_DESC", "Diện tích lớn nhất"],
] as const;
export function priceOptions(purpose: "SALE" | "RENT") {
  return purpose === "RENT"
    ? [
        { value: "LOW", label: "Đến 10 triệu/tháng", max: 10_000_000 },
        {
          value: "MID",
          label: "10–20 triệu/tháng",
          min: 10_000_000,
          max: 20_000_000,
        },
        { value: "HIGH", label: "Từ 20 triệu/tháng", min: 20_000_000 },
      ]
    : [
        { value: "LOW", label: "Đến 3 tỷ", max: 3_000_000_000 },
        {
          value: "MID",
          label: "3–5 tỷ",
          min: 3_000_000_000,
          max: 5_000_000_000,
        },
        { value: "HIGH", label: "Từ 5 tỷ", min: 5_000_000_000 },
      ];
}
const coordinate = (
  params: URLSearchParams,
  key: string,
  min: number,
  max: number,
) => {
  const raw = params.get(key);
  if (raw === null || raw.trim() === "") return undefined;
  const value = Number(raw);
  return Number.isFinite(value) && value >= min && value <= max
    ? value
    : undefined;
};
export function readSearchState(params: URLSearchParams) {
  const purpose = params.get("purpose") === "RENT" ? "RENT" : "SALE";
  const propertyType =
    propertyOptions.find(
      ([value]) => value === params.get("propertyType"),
    )?.[0] || "";
  const sortBy =
    sortOptions.find(([value]) => value === params.get("sortBy"))?.[0] ||
    "LATEST";
  const pageValue = Number(params.get("page") || "1");
  const page =
    Number.isSafeInteger(pageValue) && pageValue > 0 && pageValue <= 10000
      ? pageValue
      : 1;
  const range = priceOptions(purpose).find(
    (item) => item.value === params.get("priceRange"),
  );
  const minLat = coordinate(params, "minLat", -90, 90),
    maxLat = coordinate(params, "maxLat", -90, 90),
    minLng = coordinate(params, "minLng", -180, 180),
    maxLng = coordinate(params, "maxLng", -180, 180);
  const bounds =
    minLat !== undefined &&
    maxLat !== undefined &&
    minLng !== undefined &&
    maxLng !== undefined &&
    minLat < maxLat &&
    minLng < maxLng
      ? { minLat, maxLat, minLng, maxLng }
      : undefined;
  const area =
    params.get("area") === "SMALL"
      ? { maxArea: 50 }
      : params.get("area") === "MEDIUM"
        ? { minArea: 50, maxArea: 100 }
        : params.get("area") === "LARGE"
          ? { minArea: 100 }
          : {};
  const keyword = params.get("keyword")?.trim().slice(0, 200) || "";
  const query: ListingSearchParams = {
    purpose,
    propertyType: propertyType || undefined,
    sortBy,
    page: page - 1,
    size: SEARCH_PAGE_SIZE,
    keyword: keyword || undefined,
    minPrice: range?.min,
    maxPrice: range?.max,
    ...bounds,
    ...area,
  };
  return {
    purpose,
    propertyType,
    sortBy,
    page,
    keyword,
    bounds,
    query,
    priceRange: range?.value || "",
    area: Object.keys(area).length ? params.get("area") || "" : "",
    view: params.get("view") === "map" ? "map" : "list",
    verified: params.get("verified") === "page",
    place: bounds ? params.get("place") || "Khu vực đã chọn" : "",
  } as const;
}
