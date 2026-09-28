import { formatMoney } from '@/shared/format/money';

export interface Listing {
  id: string;
  slug: string;
  title: string;
  purpose: 'SALE' | 'RENT';
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

export interface ListingDetail extends Omit<Listing, 'primaryImageUrl' | 'publishedAt'> {
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
  APARTMENT: 'Căn hộ',
  HOUSE: 'Nhà riêng',
  VILLA: 'Biệt thự',
  TOWNHOUSE: 'Nhà phố',
  LAND: 'Đất',
};

export function formatPropertyType(propertyType: string): string {
  return PROPERTY_TYPE_LABELS[propertyType] ?? 'Bất động sản';
}

const LISTING_STATUS_LABELS: Record<string, string> = {
  PAUSED: 'Tạm ẩn',
  EXPIRED: 'Hết hạn',
  LOCKED: 'Đã khóa',
  DRAFT: 'Bản nháp',
  PENDING_REVIEW: 'Chờ duyệt',
  ACTIVE: 'Đang hiển thị',
  REJECTED: 'Bị từ chối',
  ARCHIVED: 'Đã lưu trữ',
  SUSPENDED: 'Tạm dừng',
};

export function formatListingStatus(status: string): string {
  return LISTING_STATUS_LABELS[status] ?? 'Chưa xác định';
}

export interface ListingSearchParams {
  purpose?: 'SALE' | 'RENT';
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
  sortBy?: 'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'AREA_DESC';
  page?: number;
  size?: number;
}

/**
 * Compact VND price ("3,95 tỷ", "850 triệu") with the vi-VN decimal comma.
 * Purpose-agnostic legacy helper: prefer `formatMoney(moneyFromLegacy(priceVnd, purpose))` from
 * `@/shared/format/money`, which also adds "/tháng" to RENT prices.
 */
export function formatPriceVnd(price: number): string {
  return formatMoney({ amount: price, currency: 'VND' });
}

const UNIT_PRICE_MILLIONS = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });

/** Legacy card/detail unit price ("~48,2 tr/m²"); the v2 API returns `unitPrice` for SALE listings instead. */
export function calculateUnitPrice(price: number, area: number): string {
  if (!area || area <= 0) return '';
  const pricePerM2 = price / area;
  if (pricePerM2 >= 1_000_000) {
    return `~${UNIT_PRICE_MILLIONS.format(Math.round(pricePerM2 / 100_000) / 10)} tr/m²`;
  }
  return '';
}

export function formatListingPrice(price: number, purpose: string): string {
  return formatPriceVnd(price) + (purpose === 'RENT' ? '/tháng' : '');
}
