import { apiClient, apiFetch } from '@/shared/api/client';
import type { Money } from '@/shared/format/money';
import type { QualityReport } from '@/features/listing-editor/api';

export type ListingStatus = 'DRAFT' | 'PENDING_REVIEW' | 'ACTIVE' | 'PAUSED' | 'EXPIRED' | 'REJECTED' | 'LOCKED';
export type StatusTab = 'ALL' | ListingStatus;

export interface VersionSummary {
  revisionId: string;
  revisionNumber: number;
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  title: string;
  purpose: 'SALE' | 'RENT';
  propertyType: string;
  price: Money;
  areaM2: number;
  submittedAt: string | null;
  moderatedAt: string | null;
  rejectionReason: string | null;
}

export interface Freshness {
  availabilityConfirmedAt: string | null;
  expiresAt: string | null;
  daysUntilExpiry: number | null;
  expiringSoon: boolean;
  soldCheckDueAt: string | null;
  renewable: boolean;
}

export interface MyListingItem {
  id: string;
  slug: string;
  status: ListingStatus;
  source: 'DIRECT' | 'IMPORT' | 'SEED';
  version: number;
  createdAt: string;
  updatedAt: string;
  thumbnailUrl: string | null;
  leadCount: number;
  publicVersion: VersionSummary | null;
  pendingEdit: VersionSummary | null;
  freshness: Freshness;
  quality: QualityReport;
}

export interface MyListingsPage {
  items: MyListingItem[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  counts: Record<StatusTab, number>;
}

export const fetchMyListings = (status: StatusTab, page: number, size = 10) =>
  apiClient<MyListingsPage>(`/api/v2/me/listings?status=${status}&page=${page}&size=${size}`);

export const confirmAvailability = (id: string) =>
  apiClient(`/api/v2/me/listings/${id}/confirm-availability`, { method: 'POST' });
export const renewListing = (id: string) => apiClient(`/api/v2/me/listings/${id}/renew`, { method: 'POST' });
export const setHidden = (id: string, hidden: boolean) =>
  apiClient(`/listings/${id}/visibility`, { method: 'POST', body: JSON.stringify({ hidden }) });
export const submitListing = (id: string) => apiClient(`/listings/${id}/submit`, { method: 'POST' });

export interface ImportIssue {
  field: string | null;
  message: string;
}
export interface ImportRow {
  line: number;
  status: 'VALID' | 'INVALID';
  title: string | null;
  errors: ImportIssue[];
  warnings: string[];
  listingId: string | null;
}
export interface ImportReport {
  fileSha256: string;
  dryRun: boolean;
  committed: boolean;
  duplicate: boolean;
  batchId: string | null;
  totalRows: number;
  validRows: number;
  quotaRemaining: number | null;
  fileErrors: ImportIssue[];
  rows: ImportRow[];
}

/** Dry run or commit; 422 (a commit with row errors) still carries the report. */
export async function importCsv(file: File, dryRun: boolean): Promise<ImportReport> {
  const form = new FormData();
  form.append('file', file);
  const response = await apiFetch(`/api/v2/me/listings/import?dryRun=${dryRun}`, { method: 'POST', body: form });
  const body = await response.json().catch(() => null);
  if (body && Array.isArray(body.rows)) return body as ImportReport;
  throw new Error(body?.detail || 'Không đọc được tệp. Kiểm tra định dạng CSV (UTF-8) và thử lại.');
}

/** Downloads the CSV template with the session token (a plain link would not carry it). */
export async function downloadTemplate() {
  const response = await apiFetch('/api/v2/me/listings/import/template', { headers: { Accept: 'text/csv' } });
  if (!response.ok) throw new Error('template');
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = 'mau-nhap-tin-dang.csv';
  link.click();
  URL.revokeObjectURL(url);
}
