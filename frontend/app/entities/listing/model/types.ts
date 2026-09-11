export interface Listing {
  id: string;
  title: string;
  purpose: 'SALE' | 'RENT';
  propertyType: string;
  priceVnd: number;
  areaM2: number;
  addressSummary: string;
  publicLatitude?: number;
  publicLongitude?: number;
  isVerified: boolean;
  primaryImageUrl: string;
  publishedAt?: string;
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

export function formatPriceVnd(price: number): string {
  if (price >= 1_000_000_000) {
    const billions = price / 1_000_000_000;
    return `${billions.toFixed(billions % 1 === 0 ? 0 : 2)} tỷ`;
  }
  if (price >= 1_000_000) {
    const millions = price / 1_000_000;
    return `${millions.toFixed(millions % 1 === 0 ? 0 : 1)} triệu`;
  }
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
}

export function calculateUnitPrice(price: number, area: number): string {
  if (!area || area <= 0) return '';
  const pricePerM2 = price / area;
  if (pricePerM2 >= 1_000_000) {
    return `~${(pricePerM2 / 1_000_000).toFixed(1)} tr/m²`;
  }
  return '';
}
