export interface Listing {
  id: string;
  slug: string;
  title: string;
  purpose: "SALE" | "RENT";
  propertyType: string;
  priceVnd: number;
  areaM2: number;
  bedrooms?: number;
  bathrooms?: number;
  floors?: number;
  frontageM?: number;
  roadWidthM?: number;
  direction?: string;
  legalStatus?: string;
  description?: string;
  addressSummary: string;
  publicLatitude?: number;
  publicLongitude?: number;
  isVerified: boolean;
  isShowcase?: boolean;
  primaryImageUrl: string;
  publishedAt?: string;
  sellerId?: string;
  sellerName?: string;
  sellerAvatarUrl?: string;
}

export interface ListingDetail
  extends Omit<Listing, "primaryImageUrl" | "publishedAt"> {
  ownerId: string;
  status: string;
  revisionNumber: number;
  revisionStatus: string;
  imageUrls: string[];
  createdAt: string;
  updatedAt: string;
}

export interface PublicSellerProfile {
  displayName: string;
  avatarMediaUrl?: string;
  identityVerified: boolean;
  activeListingCount: number;
  memberSince: string;
}

const PROPERTY_TYPE_LABELS: Record<string, string> = {
  APARTMENT: "Căn hộ",
  HOUSE: "Nhà riêng",
  VILLA: "Biệt thự",
  TOWNHOUSE: "Nhà phố",
  LAND: "Đất",
};

export function formatPropertyType(propertyType: string): string {
  return PROPERTY_TYPE_LABELS[propertyType] ?? "Bất động sản";
}

const LISTING_STATUS_LABELS: Record<string, string> = {
  PAUSED: "Tạm ẩn",
  EXPIRED: "Hết hạn",
  LOCKED: "Đã khóa",
  DRAFT: "Bản nháp",
  PENDING_REVIEW: "Chờ duyệt",
  ACTIVE: "Đang hiển thị",
  REJECTED: "Bị từ chối",
  ARCHIVED: "Đã lưu trữ",
  SUSPENDED: "Tạm dừng",
};

export function formatListingStatus(status: string): string {
  return LISTING_STATUS_LABELS[status] ?? "Chưa xác định";
}

export interface ListingSearchParams {
  purpose?: "SALE" | "RENT";
  propertyType?: string;
  minPrice?: number;
  maxPrice?: number;
  minArea?: number;
  maxArea?: number;
  keyword?: string;
  minLat?: number;
  maxLat?: number;
  minLng?: number;
  maxLng?: number;
  sortBy?: "LATEST" | "PRICE_ASC" | "PRICE_DESC" | "AREA_DESC";
  page?: number;
  size?: number;
}

export function formatPriceVnd(price: number): string {
  if (price >= 1_000_000_000) {
    const billions = price / 1_000_000_000;
    return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(billions)} tỷ`;
  }
  if (price >= 1_000_000) {
    const millions = price / 1_000_000;
    return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(millions)} triệu`;
  }
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
  }).format(price);
}

export function calculateUnitPrice(price: number, area: number): string {
  if (!area || area <= 0) return "";
  const pricePerM2 = price / area;
  if (pricePerM2 >= 1_000_000) {
    return `~${(pricePerM2 / 1_000_000).toFixed(1)} tr/m²`;
  }
  return "";
}

export function formatListingPrice(price: number, purpose: string): string {
  return formatPriceVnd(price) + (purpose === "RENT" ? "/tháng" : "");
}
