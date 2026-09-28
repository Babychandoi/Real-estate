import { apiClient } from '@/shared/api/client';
import type {
  ListingDetailV2,
  ListingSummaryV2,
  MapResponseV2,
  PriceHistoryV2,
  SearchResponseV2,
  SellerProfileV2,
} from '../model/v2';

const V2 = '/api/v2';

/** Public read API v2 (search, map, detail, price history, similar, sellers). */
export const listingV2Api = {
  search: (params: URLSearchParams, signal?: AbortSignal) =>
    apiClient<SearchResponseV2>(`${V2}/listings/search?${params.toString()}`, { signal }),

  map: (params: URLSearchParams, signal?: AbortSignal) =>
    apiClient<MapResponseV2>(`${V2}/listings/map?${params.toString()}`, { signal }),

  detail: (slugOrId: string, signal?: AbortSignal) =>
    apiClient<ListingDetailV2>(`${V2}/listings/${encodeURIComponent(slugOrId)}`, { signal }),

  priceHistory: (slugOrId: string, signal?: AbortSignal) =>
    apiClient<PriceHistoryV2>(`${V2}/listings/${encodeURIComponent(slugOrId)}/price-history`, { signal }),

  similar: (slugOrId: string, size = 6, signal?: AbortSignal) =>
    apiClient<ListingSummaryV2[]>(`${V2}/listings/${encodeURIComponent(slugOrId)}/similar?size=${size}`, { signal }),

  seller: (sellerId: string, signal?: AbortSignal) =>
    apiClient<SellerProfileV2>(`${V2}/public/sellers/${encodeURIComponent(sellerId)}`, { signal }),

  sellerListings: (sellerId: string, cursor?: string | null, size = 24, signal?: AbortSignal) => {
    const params = new URLSearchParams({ size: String(size) });
    if (cursor) params.set('cursor', cursor);
    return apiClient<SearchResponseV2>(`${V2}/public/sellers/${encodeURIComponent(sellerId)}/listings?${params}`, {
      signal,
    });
  },
};
