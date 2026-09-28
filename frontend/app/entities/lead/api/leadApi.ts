import type {
  AppointmentView,
  BrokerWorkspace,
  FunnelAnalytics,
  InquiryItem,
  LeadEligibility,
  LeadHistoryEntry,
  LeadItem,
  LeadListingPage,
  LeadPage,
  LeadQualification,
  LeadReport,
  LeadRequestType,
  LeadStatus,
  ListingReport,
  PartySide,
  ReportSeverity,
  ReportStatus,
  TeamMember,
} from '../model/types';
import { apiClient } from '@/shared/api/client';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export function fetchReports(status?: ReportStatus, severity?: ReportSeverity): Promise<ListingReport[]> {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (severity) params.set('severity', severity);
  return apiClient<ListingReport[]>(`/reports${params.size ? `?${params}` : ''}`);
}
export const emergencyHideListing = (reportId: string, reason?: string) =>
  apiClient<ListingReport>(
    `/reports/${reportId}/emergency-hide`,
    json('POST', { reason: reason || 'Tạm ẩn để kiểm tra dấu hiệu lừa đảo' }),
  );
export const resolveReport = (reportId: string, note: string, permanentlyLock: boolean) =>
  apiClient<ListingReport>(
    `/reports/${reportId}/resolve`,
    json('POST', { resolutionNote: note, permanentlyLockListing: permanentlyLock }),
  );
export const dismissReport = (reportId: string, note: string, resumeListing: boolean) =>
  apiClient<ListingReport>(`/reports/${reportId}/dismiss`, json('POST', { dismissNote: note, resumeListing }));

export interface InboxFilters {
  listingId?: string;
  status?: LeadStatus | '';
  requestType?: LeadRequestType | '';
  qualification?: LeadQualification | 'UNSET' | '';
  overdue?: boolean;
  q?: string;
}

/** Server-filtered inbox page (owner side: own listings + leads assigned to me; staff: all). */
export function fetchInbox(filters: InboxFilters, page: number, size: number): Promise<LeadPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.listingId) params.set('listingId', filters.listingId);
  if (filters.status) params.set('status', filters.status);
  if (filters.requestType) params.set('requestType', filters.requestType);
  if (filters.qualification) params.set('qualification', filters.qualification);
  if (filters.overdue) params.set('overdue', 'true');
  if (filters.q) params.set('q', filters.q);
  return apiClient<LeadPage>(`/leads/inbox?${params}`);
}
/** Older name used by the admin oversight page. */
export function searchLeads(listingId: string, page: number, size: number, query = '', status = ''): Promise<LeadPage> {
  return fetchInbox({ listingId, q: query, status: status as LeadStatus | '' }, page, size);
}
export function fetchLeadListings(page: number, size: number, query = ''): Promise<LeadListingPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size), q: query });
  return apiClient<LeadListingPage>(`/leads/listings?${params}`);
}
export const fetchLead = (leadId: string) => apiClient<LeadItem>(`/leads/${leadId}`);
export const fetchLeadHistory = (leadId: string) => apiClient<LeadHistoryEntry[]>(`/leads/${leadId}/history`);
/** Compare-and-set: a stale `expectedVersion` answers 409 LEAD_VERSION_CONFLICT. */
export const updateLeadStatus = (leadId: string, status: LeadStatus, expectedVersion: number, note?: string) =>
  apiClient<LeadItem>(`/leads/${leadId}/status`, json('PATCH', { status, expectedVersion, note }));
export const qualifyLead = (
  leadId: string,
  qualification: LeadQualification | null,
  reason: string | null,
  note: string,
  expectedVersion: number,
) =>
  apiClient<LeadItem>(
    `/leads/${leadId}/qualification`,
    json('PATCH', { qualification, reason, note, expectedVersion }),
  );
export const assignLead = (leadId: string, assigneeId: string | null, expectedVersion: number) =>
  apiClient<LeadItem>(`/leads/${leadId}/assignee`, json('PATCH', { assigneeId, expectedVersion }));
export const revealLeadContact = (leadId: string) => apiClient<{ phone: string }>(`/leads/${leadId}/contact`);
export const fetchLeadReport = (from?: string, to?: string) => {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return apiClient<LeadReport>(`/leads/report${params.size ? `?${params}` : ''}`);
};
export const fetchFunnelAnalytics = () => apiClient<FunnelAnalytics>('/analytics/funnel');

// Requester side (UI-10)
export const fetchInquiries = (page: number, size: number) =>
  apiClient<LeadPage<InquiryItem>>(`/me/inquiries?page=${page}&size=${size}`);
export const fetchInquiryHistory = (leadId: string) => apiClient<LeadHistoryEntry[]>(`/me/inquiries/${leadId}/history`);
export const withdrawInquiry = (leadId: string, reason: string, expectedVersion: number) =>
  apiClient<InquiryItem>(`/me/inquiries/${leadId}/withdraw`, json('POST', { reason, expectedVersion }));
export const fetchEligibility = (listingId: string) =>
  apiClient<LeadEligibility>(`/me/inquiries/eligibility?listingId=${encodeURIComponent(listingId)}`);

// Appointments (P-03): the path prefix depends on the side of the actor.
const appointmentBase = (side: PartySide, leadId: string) =>
  side === 'OWNER_SIDE' ? `/leads/${leadId}/appointments` : `/me/inquiries/${leadId}/appointments`;
export const fetchAppointments = (side: PartySide, leadId: string) =>
  apiClient<AppointmentView[]>(appointmentBase(side, leadId));
export const proposeAppointment = (
  side: PartySide,
  leadId: string,
  slots: Array<{ startsAt: string; endsAt: string }>,
  note: string,
  replacesVersion: number | null,
) => apiClient<AppointmentView>(appointmentBase(side, leadId), json('POST', { slots, note, replacesVersion }));
export const confirmAppointment = (id: string, slotId: string, expectedVersion: number) =>
  apiClient<AppointmentView>(`/appointments/${id}/confirm`, json('POST', { slotId, expectedVersion }));
export const cancelAppointment = (id: string, reason: string, expectedVersion: number) =>
  apiClient<AppointmentView>(`/appointments/${id}/cancel`, json('POST', { reason, expectedVersion }));
export const recordAppointmentOutcome = (
  id: string,
  outcome: 'COMPLETED' | 'NO_SHOW',
  noShowParty: PartySide | null,
  note: string,
  expectedVersion: number,
) =>
  apiClient<AppointmentView>(
    `/appointments/${id}/outcome`,
    json('POST', { outcome, noShowParty, note, expectedVersion }),
  );

// Broker workspace (UI-08)
export const fetchWorkspace = () => apiClient<BrokerWorkspace>('/broker/workspace');
export const saveSla = (sla: BrokerWorkspace['sla']) =>
  apiClient<BrokerWorkspace>('/broker/workspace/sla', json('PUT', sla));
export const fetchTeam = () => apiClient<TeamMember[]>('/broker/team');
export const addTeamMember = (email: string) => apiClient<TeamMember[]>('/broker/team', json('POST', { email }));
export const removeTeamMember = (memberId: string) =>
  apiClient<TeamMember[]>(`/broker/team/${memberId}`, { method: 'DELETE' });
