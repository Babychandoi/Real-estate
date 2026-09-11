import { apiClient } from '../../../shared/api/client';
import type {
  ApproveListingPayload,
  ListingDiff,
  ModerationQueueItem,
  RejectListingPayload,
  StandardReason,
} from '../model/types';

export const moderationApi = {
  getQueue: () => apiClient<ModerationQueueItem[]>('/moderation/queue'),

  getDiff: (listingId: string) => apiClient<ListingDiff>(`/moderation/listings/${listingId}/diff`),

  approve: (listingId: string, payload: ApproveListingPayload) =>
    apiClient<{ success: boolean; listingId: string; status: string }>(
      `/moderation/listings/${listingId}/approve`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    ),

  reject: (listingId: string, payload: RejectListingPayload) =>
    apiClient<{ success: boolean; listingId: string; status: string }>(
      `/moderation/listings/${listingId}/reject`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    ),

  getRejectionReasons: () => apiClient<StandardReason[]>('/moderation/rejection-reasons'),
};
