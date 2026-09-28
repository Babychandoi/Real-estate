import type { Money, RentTerms, UnitPrice } from '@/shared/format/money';
import type { ImageDto } from '@/shared/ui/ResponsiveImage';
import type { Trust } from '@/shared/ui/TrustBadge';
import type { ResultTotal } from '@/shared/ui/Pagination';

/** Public listing read API v2 (contract §8, docs/audit-2026-09-27/02_CONTRACTS.md). */

export type Purpose = 'SALE' | 'RENT';
export type PropertyType = 'APARTMENT' | 'HOUSE' | 'VILLA' | 'TOWNHOUSE' | 'LAND';
export type SellerRole = 'BROKER' | 'OWNER' | 'ADMIN' | 'MODERATOR' | 'USER';
export type Furnishing = 'NONE' | 'BASIC' | 'FULL';
export type LegalCode = 'RED_BOOK' | 'PINK_BOOK' | 'SALE_CONTRACT' | 'PENDING_CERTIFICATE' | 'OTHER';
export type SearchEngineName = 'search' | 'database';

export interface ListingLocation {
  districtCode?: string | null;
  districtName?: string | null;
  wardName?: string | null;
  addressSummary?: string | null;
  lat?: number | null;
  lng?: number | null;
  precision: 'APPROXIMATE';
}

export interface ListingSeller {
  id: string;
  name?: string | null;
  avatarUrl?: string | null;
  role: SellerRole;
}

export interface ListingSummaryV2 {
  id: string;
  slug: string;
  title: string;
  purpose: Purpose;
  propertyType: PropertyType;
  price: Money;
  unitPrice: UnitPrice | null;
  areaM2: number;
  bedrooms?: number | null;
  bathrooms?: number | null;
  location: ListingLocation;
  image: ImageDto | null;
  imageCount: number;
  trust: Trust;
  freshness: { publishedAt: string; updatedAt: string; availabilityConfirmedAt?: string | null };
  seller: ListingSeller;
  project: { id: string; slug: string; name: string } | null;
  priceChange: { previousAmount: number; changedAt: string; direction: 'DOWN' | 'UP' } | null;
}

export interface ListingDetailV2 extends ListingSummaryV2 {
  description?: string | null;
  images: ImageDto[];
  facts: {
    bedrooms?: number | null;
    bathrooms?: number | null;
    floors?: number | null;
    frontageM?: number | null;
    roadWidthM?: number | null;
    direction?: string | null;
    legalStatusText?: string | null;
  };
  rentTerms: RentTerms | null;
  legal: { code: LegalCode; label: string } | null;
  furnishing: Furnishing | null;
  revisionNumber: number;
}

export interface SearchSuggestion {
  type: string;
  drop: string[];
  total: ResultTotal;
}

export interface SearchResponseV2 {
  items: ListingSummaryV2[];
  pageInfo: { hasNext: boolean; nextCursor: string | null; size: number };
  total: ResultTotal | null;
  queryVersion: 'v2';
  dataAsOf: string;
  engine: SearchEngineName;
  degraded: boolean;
  notices: string[];
  suggestions: SearchSuggestion[];
}

export interface MapPoint {
  id: string;
  slug: string;
  lat: number;
  lng: number;
  price: Money;
  propertyType: PropertyType;
}

export interface MapCluster {
  lat: number;
  lng: number;
  count: number;
  /** [minLng, minLat, maxLng, maxLat] of the listings in the cluster. */
  bbox: [number, number, number, number];
}

export interface MapResponseV2 {
  mode: 'points' | 'clusters';
  points: MapPoint[];
  clusters: MapCluster[];
  total: ResultTotal | null;
  engine: SearchEngineName;
  dataAsOf: string;
}

export interface PriceHistoryV2 {
  listingId: string;
  purpose: Purpose | null;
  points: Array<{ price: Money; changedAt: string | null }>;
}

export interface SellerProfileV2 {
  id: string;
  name?: string | null;
  avatarUrl?: string | null;
  role: SellerRole;
  memberSince: string;
  identity: Trust['identity'];
  activeListingCount: number;
  ownershipVerifiedListingCount: number;
  responseStats: { sampleSize: number; medianFirstResponseMinutes: number } | null;
}

/** Body of `410 LISTING_GONE`: only what the "tin không còn hiển thị" page needs. `title` is the generic problem
 * title; `listingTitle` (the last public title) is absent for moderation-locked listings and banned sellers. */
export interface GoneListingProblem {
  code: 'LISTING_GONE';
  slug: string;
  listingTitle?: string;
}

const PROPERTY_TYPE_LABELS: Record<PropertyType, string> = {
  APARTMENT: 'Căn hộ',
  HOUSE: 'Nhà riêng',
  VILLA: 'Biệt thự',
  TOWNHOUSE: 'Nhà phố',
  LAND: 'Đất',
};

export const PROPERTY_TYPES = Object.keys(PROPERTY_TYPE_LABELS) as PropertyType[];

export function propertyTypeLabel(type: string): string {
  return PROPERTY_TYPE_LABELS[type as PropertyType] ?? 'Bất động sản';
}

export function purposeLabel(purpose: Purpose): string {
  return purpose === 'RENT' ? 'Cho thuê' : 'Bán';
}

const SELLER_ROLE_LABELS: Partial<Record<SellerRole, string>> = {
  BROKER: 'Môi giới',
  OWNER: 'Chủ nhà',
  ADMIN: 'Quản trị viên',
};

/** "Môi giới" / "Chủ nhà"; generic "Người đăng" for roles without a public label. */
export function sellerRoleLabel(role: SellerRole | string | null | undefined): string {
  return SELLER_ROLE_LABELS[role as SellerRole] ?? 'Người đăng';
}

export const FURNISHING_LABELS: Record<Furnishing, string> = {
  NONE: 'Không nội thất',
  BASIC: 'Nội thất cơ bản',
  FULL: 'Đầy đủ nội thất',
};

export const LEGAL_LABELS: Record<LegalCode, string> = {
  RED_BOOK: 'Sổ đỏ',
  PINK_BOOK: 'Sổ hồng',
  SALE_CONTRACT: 'Hợp đồng mua bán',
  PENDING_CERTIFICATE: 'Đang chờ sổ',
  OTHER: 'Giấy tờ khác',
};

const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'Asia/Ho_Chi_Minh',
});

/** "27/09/2026"; empty string for a missing/invalid date. */
export function formatDate(value?: string | null): string {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : dateFormat.format(date);
}

const areaFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });

/** Lengths in metres with the Vietnamese decimal comma ("4,6 m", never "4.6 m"). */
export function formatMetres(metres: number | null | undefined): string {
  return metres == null ? '' : `${areaFormat.format(metres)} m`;
}

export function formatArea(areaM2: number | null | undefined): string {
  return areaM2 == null ? '' : `${areaFormat.format(areaM2)} m²`;
}
