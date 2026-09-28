import { apiClient } from '@/shared/api/client';
import type {
  AdminAction,
  AdminListingRow,
  AdminOrder,
  AdminOrderPage,
  AuditSample,
  BankSettings,
  BillingOrder,
  BulkItemResult,
  ClaimView,
  DecisionView,
  DuplicateCandidate,
  KycAccessLogEntry,
  KycDocumentAccess,
  ListingPreview,
  ModerationDecision,
  ModerationQueuePage,
  MyKycStatus,
  OrderEvent,
  Page,
  QueueFilter,
  ReasonOption,
  ReportEvent,
  ReportQueueItem,
  RevisionRow,
  StatusHistoryRow,
  TrustReasonOption,
  VerificationEvidence,
} from '../model/types';

const json = (body: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(body) });
const query = (params: Record<string, string | number | boolean | undefined | null>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
};

export const moderationV2Api = {
  queue: (filter: QueueFilter, page: number, size = 20) =>
    apiClient<ModerationQueuePage>(`/moderation/queue${query({ filter, page, size })}`),
  claim: (listingId: string) => apiClient<ClaimView>(`/moderation/listings/${listingId}/claim`, { method: 'POST' }),
  release: (listingId: string) => apiClient<void>(`/moderation/listings/${listingId}/claim`, { method: 'DELETE' }),
  approve: (listingId: string, revisionId: string, reasonCode: string, note?: string) =>
    apiClient<ModerationDecision>(`/moderation/listings/${listingId}/approve`, json({ revisionId, reasonCode, note })),
  reject: (listingId: string, revisionId: string, reasonCode: string, reasonDetail?: string) =>
    apiClient<ModerationDecision>(
      `/moderation/listings/${listingId}/reject`,
      json({ revisionId, reasonCode, reasonDetail }),
    ),
  bulk: (
    action: 'APPROVE' | 'REJECT',
    items: Array<{ listingId: string; revisionId: string }>,
    reasonCode: string,
    note?: string,
  ) =>
    apiClient<{ batchId: string; results: BulkItemResult[] }>(
      '/moderation/bulk',
      json({ action, items, reasonCode, note }),
    ),
  reasons: () => apiClient<{ approve: ReasonOption[]; reject: ReasonOption[] }>('/moderation/reasons'),
  decisions: (listingId: string) => apiClient<DecisionView[]>(`/moderation/listings/${listingId}/decisions`),
  duplicates: (listingId: string) => apiClient<DuplicateCandidate[]>(`/moderation/listings/${listingId}/duplicates`),
  decideDuplicate: (candidateId: string, status: 'DISMISSED' | 'CONFIRMED', note?: string) =>
    apiClient<void>(`/moderation/duplicates/${candidateId}`, json({ status, note })),
  auditSamples: (status: 'OPEN' | 'PASSED' | 'FAILED', page: number) =>
    apiClient<Page<AuditSample>>(`/moderation/audit-samples${query({ status, page, size: 20 })}`),
  reviewSample: (id: string, outcome: 'PASSED' | 'FAILED', reasonCode: string, note?: string) =>
    apiClient<void>(`/moderation/audit-samples/${id}/review`, json({ outcome, reasonCode, note })),
};

export interface AdminListingFilters {
  status?: string;
  q?: string;
  district?: string;
  source?: string;
  pendingEdit?: boolean;
  ownerId?: string;
}

export const adminListingsApi = {
  search: (filters: AdminListingFilters, page: number, size = 20) =>
    apiClient<Page<AdminListingRow>>(
      `/admin/listings${query({ ...filters, pendingEdit: filters.pendingEdit || undefined, page, size })}`,
    ),
  revisions: (id: string) => apiClient<RevisionRow[]>(`/admin/listings/${id}/revisions`),
  history: (id: string) => apiClient<StatusHistoryRow[]>(`/admin/listings/${id}/history`),
  changeStatus: (id: string, action: 'LOCK' | 'UNLOCK' | 'HIDE' | 'UNHIDE', reason: string) =>
    apiClient<{ listingId: string; fromStatus: string; toStatus: string }>(
      `/admin/listings/${id}/status`,
      json({ action, reason }),
    ),
  preview: (id: string, revisionId?: string) =>
    apiClient<ListingPreview>(`/admin/listings/${id}/preview${query({ revisionId })}`),
};

export const adminUsersApi = {
  changeRole: (id: string, role: string, reason: string) =>
    apiClient<{ userId: string; fromRole: string; toRole: string }>(`/admin/users/${id}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role, reason }),
    }),
  changeStatus: (id: string, status: 'ACTIVE' | 'SUSPENDED', reason: string) =>
    apiClient<void>(`/admin/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status, reason }) }),
  history: (id: string) => apiClient<AdminAction[]>(`/admin/users/${id}/history`),
  /** Lost authenticator: removes the second factor and signs the account out everywhere (reason required). */
  resetMfa: (id: string, reason: string) => apiClient<void>(`/admin/users/${id}/mfa/reset`, json({ reason })),
  revokeSessions: (id: string, reason: string) =>
    apiClient<{ revokedSessions: number }>(`/admin/users/${id}/sessions/revoke`, json({ reason })),
  openKycDocuments: (id: string, password: string, reason: string) =>
    apiClient<KycDocumentAccess>(`/admin/users/${id}/kyc-documents`, json({ password, reason })),
  kycAccessLog: (id: string) => apiClient<KycAccessLogEntry[]>(`/admin/users/${id}/kyc-access-log`),
};

export interface ReportQueueFilters {
  status?: string;
  severity?: string;
  breached?: boolean;
  mine?: boolean;
}

export const reportDeskApi = {
  queue: (filters: ReportQueueFilters, page: number, size = 20) =>
    apiClient<Page<ReportQueueItem>>(
      `/reports/queue${query({ ...filters, breached: filters.breached || undefined, mine: filters.mine || undefined, page, size })}`,
    ),
  claim: (id: string) => apiClient<unknown>(`/reports/${id}/claim`, { method: 'POST' }),
  release: (id: string) => apiClient<void>(`/reports/${id}/claim`, { method: 'DELETE' }),
  events: (id: string) => apiClient<ReportEvent[]>(`/reports/${id}/events`),
  note: (id: string, note: string) => apiClient<void>(`/reports/${id}/notes`, json({ note })),
  escalate: (id: string, severity: string, reason: string) =>
    apiClient<void>(`/reports/${id}/severity`, json({ severity, reason })),
  emergencyHide: (id: string, reason: string) => apiClient<unknown>(`/reports/${id}/emergency-hide`, json({ reason })),
  resolve: (id: string, resolutionNote: string, permanentlyLockListing: boolean) =>
    apiClient<unknown>(`/reports/${id}/resolve`, json({ resolutionNote, permanentlyLockListing })),
  dismiss: (id: string, dismissNote: string, resumeListing: boolean) =>
    apiClient<unknown>(`/reports/${id}/dismiss`, json({ dismissNote, resumeListing })),
};

export const trustApi = {
  reasons: () =>
    apiClient<{ approve: TrustReasonOption[]; reject: TrustReasonOption[]; revoke: TrustReasonOption[] }>(
      '/verifications/reasons',
    ),
  evidence: (id: string) => apiClient<VerificationEvidence>(`/verifications/${id}/evidence`),
  openDocuments: (id: string, password: string, reason: string) =>
    apiClient<KycDocumentAccess>(`/verifications/${id}/document-access`, json({ password, reason })),
  approveOwnership: (id: string, reasonCode: string, verifierNote?: string) =>
    apiClient<unknown>(`/verifications/${id}/approve`, json({ reasonCode, verifierNote })),
  rejectOwnership: (id: string, reasonCode: string, reason: string) =>
    apiClient<unknown>(`/verifications/${id}/reject`, json({ reasonCode, reason })),
  revokeOwnership: (id: string, reasonCode: string, reason: string) =>
    apiClient<unknown>(`/verifications/${id}/revoke`, json({ reasonCode, reason })),
  approveKyc: (id: string, reasonCode: string, reason?: string) =>
    apiClient<unknown>(`/kyc/${id}/approve`, json({ reasonCode, reason })),
  rejectKyc: (id: string, reasonCode: string, reason: string) =>
    apiClient<unknown>(`/kyc/${id}/reject`, json({ reasonCode, reason })),
  myStatus: () => apiClient<MyKycStatus>('/kyc/me/status'),
};

export const billingApi = {
  myOrders: (page: number, size = 10) => apiClient<Page<BillingOrder>>(`/billing/orders${query({ page, size })}`),
  myOrder: (id: string) => apiClient<{ order: BillingOrder; events: OrderEvent[] }>(`/billing/orders/${id}`),
  /** The key makes a double click or a retried request return the same order. */
  createOrder: (planCode: string, idempotencyKey: string) =>
    apiClient<BillingOrder>('/billing/orders', {
      ...json({ planCode }),
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  report: (id: string) => apiClient<BillingOrder>(`/billing/orders/${id}/reported`, { method: 'POST' }),
  cancel: (id: string) => apiClient<BillingOrder>(`/billing/orders/${id}/cancel`, { method: 'POST' }),
  reconciliation: (status: string, page: number, size = 20) =>
    apiClient<AdminOrderPage>(`/billing/admin/reconciliation${query({ status, page, size })}`),
  adminOrder: (id: string) => apiClient<{ order: BillingOrder; events: OrderEvent[] }>(`/billing/admin/orders/${id}`),
  receipt: (id: string, receivedAmountVnd: number, receivedReference: string, note?: string) =>
    apiClient<AdminOrder>(
      `/billing/admin/reconciliation/${id}/receipt`,
      json({ receivedAmountVnd, receivedReference, note }),
    ),
  resolve: (id: string, resolution: 'APPROVE_WITH_NOTE' | 'REJECT' | 'REFUNDED_OFFLINE', note: string) =>
    apiClient<AdminOrder>(`/billing/admin/reconciliation/${id}/resolve`, json({ resolution, note })),
  reject: (id: string, reason: string) =>
    apiClient<AdminOrder>(`/billing/admin/reconciliation/${id}/reject`, json({ reason })),
  bank: () => apiClient<BankSettings | undefined>('/billing/admin/bank'),
  saveBank: (bank: Omit<BankSettings, 'version'>, expectedVersion: number | null) =>
    apiClient<BankSettings>('/billing/admin/bank', {
      method: 'PUT',
      body: JSON.stringify({ ...bank, expectedVersion }),
    }),
};

/** Stable Idempotency-Key for one user intention (a new key per intention, the same key for its retries). */
export function newIdempotencyKey(prefix: string): string {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  return `${prefix}-${random}`.replace(/[^A-Za-z0-9._:-]/g, '');
}

export type { AdminOrder };
