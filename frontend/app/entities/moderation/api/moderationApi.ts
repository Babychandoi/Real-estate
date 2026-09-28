import { apiClient } from '../../../shared/api/client';
import type { ListingDiff, StandardReason } from '../model/types';

/** Diff and reason catalogue; queue, claims and decisions are in `entities/admin/api/adminApi.ts` (moderation v2). */
export const moderationApi = {
  getDiff: (listingId: string) => apiClient<ListingDiff>(`/moderation/listings/${listingId}/diff`),

  getRejectionReasons: () => apiClient<StandardReason[]>('/moderation/rejection-reasons'),
};
