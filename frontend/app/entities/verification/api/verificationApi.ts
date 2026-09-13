import type { ListingVerification, VerificationStatus } from '../model/types';
import { apiClient } from '@/shared/api/client';

export async function fetchVerificationQueue(status?: VerificationStatus): Promise<ListingVerification[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiClient<ListingVerification[]>(`/verifications${query}`);
}
export const fetchVerificationDetail = (id: string) => apiClient<ListingVerification>(`/verifications/${id}`);
export const approveVerification = (id: string, note?: string) => apiClient<ListingVerification>(`/verifications/${id}/approve`, {
  method: 'POST', body: JSON.stringify({ verifierNote: note || 'Đã đối soát hồ sơ theo quy trình kiểm duyệt.' }),
});
export const rejectVerification = (id: string, reason: string) => apiClient<ListingVerification>(`/verifications/${id}/reject`, {
  method: 'POST', body: JSON.stringify({ reason }),
});
