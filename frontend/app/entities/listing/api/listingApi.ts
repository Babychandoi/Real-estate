import { apiClient } from '../../../shared/api/client';
import type { Listing, ListingSearchParams } from '../model/types';

export const listingApi = {
  searchListings: (params: ListingSearchParams = {}): Promise<Listing[]> => {
    const query = new URLSearchParams();
    if (params.purpose) query.set('purpose', params.purpose);
    if (params.propertyType) query.set('propertyType', params.propertyType);
    if (params.minPrice !== undefined) query.set('minPrice', params.minPrice.toString());
    if (params.maxPrice !== undefined) query.set('maxPrice', params.maxPrice.toString());
    if (params.minArea !== undefined) query.set('minArea', params.minArea.toString());
    if (params.maxArea !== undefined) query.set('maxArea', params.maxArea.toString());
    if (params.keyword) query.set('keyword', params.keyword);
    if (params.minLat !== undefined) query.set('minLat', params.minLat.toString());
    if (params.maxLat !== undefined) query.set('maxLat', params.maxLat.toString());
    if (params.minLng !== undefined) query.set('minLng', params.minLng.toString());
    if (params.maxLng !== undefined) query.set('maxLng', params.maxLng.toString());
    if (params.sortBy) query.set('sortBy', params.sortBy);
    if (params.page !== undefined) query.set('page', params.page.toString());
    if (params.size !== undefined) query.set('size', params.size.toString());

    const queryString = query.toString();
    return apiClient<Listing[]>(`/listings/search${queryString ? `?${queryString}` : ''}`);
  },

  getListingDetail: (id: string) => apiClient<any>(`/listings/${id}`),

  getMyListings: () => apiClient<Listing[]>('/listings/my-listings'),
};
