import type { ListingReport, LeadItem, FunnelAnalytics, LeadStatus, ReportSeverity, ReportStatus } from '../model/types';
import { apiClient } from '@/shared/api/client';

export function fetchReports(status?: ReportStatus, severity?: ReportSeverity): Promise<ListingReport[]> {
  const params = new URLSearchParams(); if (status) params.set('status', status); if (severity) params.set('severity', severity);
  return apiClient<ListingReport[]>(`/reports${params.size ? `?${params}` : ''}`);
}
export const emergencyHideListing = (reportId: string, reason?: string) => apiClient<ListingReport>(`/reports/${reportId}/emergency-hide`, { method: 'POST', body: JSON.stringify({ reason: reason || 'Tạm ẩn để kiểm tra dấu hiệu lừa đảo' }) });
export const resolveReport = (reportId: string, note: string, permanentlyLock: boolean) => apiClient<ListingReport>(`/reports/${reportId}/resolve`, { method: 'POST', body: JSON.stringify({ resolutionNote: note, permanentlyLockListing: permanentlyLock }) });
export const dismissReport = (reportId: string, note: string, resumeListing: boolean) => apiClient<ListingReport>(`/reports/${reportId}/dismiss`, { method: 'POST', body: JSON.stringify({ dismissNote: note, resumeListing }) });
export function fetchLeads(listingId?: string, brokerId?: string): Promise<LeadItem[]> {
  const params = new URLSearchParams(); if (listingId) params.set('listingId', listingId); if (brokerId) params.set('brokerId', brokerId);
  return apiClient<LeadItem[]>(`/leads${params.size ? `?${params}` : ''}`);
}
export const updateLeadStatus = (leadId: string, status: LeadStatus) => apiClient<LeadItem>(`/leads/${leadId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
export const fetchFunnelAnalytics = () => apiClient<FunnelAnalytics>('/analytics/funnel');
