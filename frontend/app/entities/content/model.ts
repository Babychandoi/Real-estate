/** Public CMS, project, area and site-info shapes (backend S7: `/api/v2/public/*`, `/api/v1/public/site-info`). */

export type ArticleCategory = 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';

export const ARTICLE_CATEGORIES: { value: ArticleCategory; label: string }[] = [
  { value: 'LEGAL_POLICY', label: 'Chính sách & pháp lý' },
  { value: 'KNOWLEDGE', label: 'Kiến thức' },
  { value: 'MARKET_INSIGHTS', label: 'Thị trường' },
];

export function articleCategoryLabel(category: ArticleCategory): string {
  return ARTICLE_CATEGORIES.find((item) => item.value === category)?.label ?? 'Bài viết';
}

export const REVISION_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Bản nháp',
  SUBMITTED: 'Chờ duyệt',
  SCHEDULED: 'Đã hẹn giờ xuất bản',
  PUBLISHED: 'Đang công khai',
  SUPERSEDED: 'Bản cũ',
  REJECTED: 'Bị từ chối',
  ARCHIVED: 'Đã gỡ',
};

export interface PublicRevision {
  id: string;
  revisionNumber: number;
  title: string;
  summary: string | null;
  contentHtml: string;
  coverImageUrl: string | null;
  authorName: string;
  legalReference: string | null;
  metaDescription: string | null;
  sourceName: string | null;
  sourceUrl: string | null;
  reviewedAt: string | null;
}

export interface PublicArticle {
  id: string;
  slug: string;
  category: ArticleCategory;
  categoryLabel: string;
  status: string;
  path: string;
  publishedAt: string | null;
  firstPublishedAt: string | null;
  updatedAt: string | null;
  currentRevision: PublicRevision;
}

export interface ArticlePage {
  items: PublicArticle[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
}

export interface Statistic {
  purpose: 'SALE' | 'RENT';
  count: number;
  /** null below `minSamples` listings. SALE: VND per m²; RENT: VND per month. */
  median: number | null;
  unit: 'VND_PER_M2' | 'VND_PER_MONTH';
  minSamples: number;
  method: string;
}

export interface Inventory {
  total: number;
  statistics: Statistic[];
  types: { purpose: 'SALE' | 'RENT'; propertyType: string; count: number }[];
  lastListingUpdate: string | null;
  dataAsOf: string;
}

export interface ProjectDetail {
  id: string;
  slug: string;
  name: string;
  developerName: string;
  provinceCode: string;
  districtCode: string;
  districtName: string | null;
  areaSlug: string | null;
  address: string;
  totalAreaM2: number | null;
  totalBlocks: number;
  totalUnits: number;
  handoverYear: number | null;
  legalLicenseNumber: string;
  status: 'ACTIVE' | 'PLANNING' | 'UNDER_CONSTRUCTION' | 'COMPLETED' | 'LOCKED';
  description: string | null;
  websiteUrl: string | null;
  infoSource: string | null;
  infoCheckedAt: string | null;
  updatedAt: string | null;
}

export type AmenityCategory = 'EDUCATION' | 'HEALTH' | 'TRANSPORT' | 'SHOPPING' | 'PARK' | 'SPORT' | 'OTHER';

export const AMENITY_CATEGORIES: { value: AmenityCategory; label: string }[] = [
  { value: 'EDUCATION', label: 'Giáo dục' },
  { value: 'HEALTH', label: 'Y tế' },
  { value: 'TRANSPORT', label: 'Giao thông' },
  { value: 'SHOPPING', label: 'Mua sắm' },
  { value: 'PARK', label: 'Công viên' },
  { value: 'SPORT', label: 'Thể thao' },
  { value: 'OTHER', label: 'Khác' },
];

export interface Amenity {
  id: string | null;
  name: string;
  category: AmenityCategory;
  distanceM: number | null;
  sourceName: string;
  sourceUrl: string | null;
  checkedAt: string;
}

export interface ProjectPageData {
  project: ProjectDetail;
  amenities: Amenity[];
  inventory: Inventory;
}

export interface ProjectCard {
  slug: string;
  name: string;
  districtName: string | null;
  areaSlug: string | null;
  status: ProjectDetail['status'];
  activeListings: number;
}

export interface ProjectList {
  items: ProjectCard[];
  total: number;
  page: number;
  size: number;
}

export interface AreaCard {
  slug: string;
  name: string;
  provinceName: string;
  districtCode: string;
  activeListings: number;
}

export interface AreaPageData {
  area: { provinceCode: string; districtCode: string; provinceName: string; name: string; slug: string };
  inventory: Inventory;
  projects: ProjectCard[];
}

export interface HomeData {
  areas: AreaCard[];
  projects: ProjectCard[];
  dataAsOf: string;
}

export interface SiteInfo {
  operator: {
    siteName: string;
    legalName: string | null;
    businessRegistration: string | null;
    taxCode: string | null;
    address: string | null;
    representative: string | null;
    email: string | null;
    phone: string | null;
    hotlineHours: string | null;
    complete: boolean;
  };
  policies: { title: string; path: string; summary: string | null; publishedAt: string | null }[];
}

export const PROJECT_STATUS_LABELS: Record<ProjectDetail['status'], string> = {
  ACTIVE: 'Đang mở bán/cho thuê',
  PLANNING: 'Đang chuẩn bị',
  UNDER_CONSTRUCTION: 'Đang xây dựng',
  COMPLETED: 'Đã hoàn thành',
  LOCKED: 'Tạm ẩn',
};
