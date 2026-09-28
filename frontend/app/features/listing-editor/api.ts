import { apiClient, apiFetch } from '@/shared/api/client';
import { ApiProblemException, type ProblemDetails } from '@/shared/types/problem-details';
import type { Money } from '@/shared/format/money';

export type Purpose = 'SALE' | 'RENT';
export type Furnishing = 'NONE' | 'BASIC' | 'FULL';
export type LegalCode = 'RED_BOOK' | 'PINK_BOOK' | 'SALE_CONTRACT' | 'PENDING_CERTIFICATE' | 'OTHER';

export interface QualityItem {
  code: string;
  label: string;
  status: 'PASS' | 'WARN' | 'NO_DATA';
  hint: string | null;
}

export interface QualityReport {
  passed: number;
  total: number;
  items: QualityItem[];
}

/** Editable fields of a draft, as sent to `POST /listings` and `PUT /listings/{id}/draft`. */
export interface DraftFields {
  purpose: Purpose;
  propertyType: string;
  title: string;
  priceVnd: number | null;
  areaM2: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  floors: number | null;
  frontageM: number | null;
  roadWidthM: number | null;
  direction: string;
  legalStatusCode: LegalCode | '';
  legalStatus: string;
  furnishing: Furnishing | '';
  monthlyServiceFeeVnd: number | null;
  depositVnd: number | null;
  description: string;
  provinceCode: string;
  districtCode: string;
  wardCode: string;
  addressSummary: string;
  publicLatitude: number | null;
  publicLongitude: number | null;
  imageUrls: string[];
}

export interface DraftView extends Omit<DraftFields, 'legalStatusCode' | 'furnishing' | 'direction' | 'legalStatus'> {
  listingId: string;
  slug: string;
  listingStatus: string;
  version: number;
  revisionId: string;
  revisionNumber: number;
  revisionStatus: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  direction: string | null;
  legalStatus: string | null;
  legalStatusCode: LegalCode | null;
  furnishing: Furnishing | null;
  hasPublicVersion: boolean;
  quality: QualityReport;
}

export interface PreviewView {
  id: string;
  title: string;
  purpose: Purpose;
  propertyType: string;
  price: Money;
  unitPrice: { amount: number; per: 'M2' } | null;
  areaM2: number;
  bedrooms: number | null;
  bathrooms: number | null;
  location: { addressSummary: string | null; districtCode: string | null };
  description: string | null;
  images: Array<{ url: string }>;
  rentTerms: { monthlyServiceFee: number | null; deposit: number | null } | null;
  legal: { code: string | null; label: string | null; detail: string | null } | null;
  furnishing: Furnishing | null;
  revisionStatus: string;
  quality: QualityReport;
}

export const EMPTY_DRAFT: DraftFields = {
  purpose: 'SALE',
  propertyType: 'APARTMENT',
  title: '',
  priceVnd: null,
  areaM2: null,
  bedrooms: null,
  bathrooms: null,
  floors: null,
  frontageM: null,
  roadWidthM: null,
  direction: '',
  legalStatusCode: '',
  legalStatus: '',
  furnishing: '',
  monthlyServiceFeeVnd: null,
  depositVnd: null,
  description: '',
  provinceCode: '',
  districtCode: '',
  wardCode: '',
  addressSummary: '',
  publicLatitude: null,
  publicLongitude: null,
  imageUrls: [],
};

export function fieldsFromDraft(view: DraftView): DraftFields {
  return {
    purpose: view.purpose,
    propertyType: view.propertyType,
    title: view.title ?? '',
    priceVnd: view.priceVnd,
    areaM2: view.areaM2,
    bedrooms: view.bedrooms ?? null,
    bathrooms: view.bathrooms ?? null,
    floors: view.floors ?? null,
    frontageM: view.frontageM ?? null,
    roadWidthM: view.roadWidthM ?? null,
    direction: view.direction ?? '',
    legalStatusCode: view.legalStatusCode ?? '',
    legalStatus: view.legalStatus ?? '',
    furnishing: view.furnishing ?? '',
    monthlyServiceFeeVnd: view.monthlyServiceFeeVnd ?? null,
    depositVnd: view.depositVnd ?? null,
    description: view.description ?? '',
    provinceCode: view.provinceCode ?? '',
    districtCode: view.districtCode ?? '',
    wardCode: view.wardCode ?? '',
    addressSummary: view.addressSummary ?? '',
    publicLatitude: view.publicLatitude ?? null,
    publicLongitude: view.publicLongitude ?? null,
    imageUrls: view.imageUrls ?? [],
  };
}

/** Request body: blanks become null, rent terms are only sent for RENT (the server rejects them for SALE). */
export function draftPayload(fields: DraftFields): Record<string, unknown> {
  const blank = (value: string) => (value.trim() === '' ? null : value.trim());
  const rent = fields.purpose === 'RENT';
  return {
    purpose: fields.purpose,
    propertyType: fields.propertyType,
    title: fields.title.trim(),
    priceVnd: fields.priceVnd,
    areaM2: fields.areaM2,
    bedrooms: fields.bedrooms,
    bathrooms: fields.bathrooms,
    floors: fields.floors,
    frontageM: fields.frontageM,
    roadWidthM: fields.roadWidthM,
    direction: blank(fields.direction),
    legalStatusCode: fields.legalStatusCode || null,
    legalStatus: blank(fields.legalStatus),
    furnishing: fields.furnishing || null,
    monthlyServiceFeeVnd: rent ? fields.monthlyServiceFeeVnd : null,
    depositVnd: rent ? fields.depositVnd : null,
    description: fields.description,
    provinceCode: blank(fields.provinceCode),
    districtCode: blank(fields.districtCode),
    wardCode: blank(fields.wardCode),
    addressSummary: blank(fields.addressSummary),
    publicLatitude: fields.publicLatitude,
    publicLongitude: fields.publicLongitude,
    imageUrls: fields.imageUrls,
  };
}

export type FieldErrors = Partial<Record<keyof DraftFields | string, string>>;

/** Client-side checks mirrored from the server so errors show next to the field before a round trip. */
export function validateDraft(fields: DraftFields): FieldErrors {
  const errors: FieldErrors = {};
  const title = fields.title.trim();
  if (title.length < 10) errors.title = 'Tiêu đề cần ít nhất 10 ký tự.';
  else if (title.length > 200) errors.title = 'Tiêu đề tối đa 200 ký tự.';
  if (fields.priceVnd == null || !Number.isFinite(fields.priceVnd) || fields.priceVnd <= 0) {
    errors.priceVnd = fields.purpose === 'RENT' ? 'Nhập giá thuê mỗi tháng (VNĐ).' : 'Nhập giá bán (VNĐ).';
  }
  if (fields.areaM2 == null || !Number.isFinite(fields.areaM2) || fields.areaM2 < 1) {
    errors.areaM2 = 'Diện tích từ 1 m².';
  }
  if (fields.legalStatusCode === 'OTHER' && fields.legalStatus.trim() === '') {
    errors.legalStatus = 'Mô tả loại giấy tờ khi chọn “Khác”.';
  }
  if (fields.description.length > 5000) errors.description = 'Mô tả tối đa 5.000 ký tự.';
  for (const key of ['depositVnd', 'monthlyServiceFeeVnd'] as const) {
    const value = fields[key];
    if (value != null && (!Number.isFinite(value) || value < 0)) errors[key] = 'Không được âm.';
  }
  return errors;
}

/** Only these errors stop autosave (the server requires them for any draft). */
export const AUTOSAVE_BLOCKING = ['title', 'priceVnd', 'areaM2'] as const;

export class VersionConflictError extends Error {
  constructor(public readonly currentVersion: number | null) {
    super('VERSION_CONFLICT');
  }
}

export class OfflineError extends Error {}

function etagVersion(etag: string | null): number | null {
  const match = etag?.match(/v(\d+)/);
  return match ? Number(match[1]) : null;
}

export interface SavedDraft {
  listingId: string;
  version: number;
}

/** Creates or updates the draft; `If-Match` makes a concurrent save elsewhere a {@link VersionConflictError}. */
export async function saveDraft(
  listingId: string | null,
  version: number | null,
  fields: DraftFields,
): Promise<SavedDraft> {
  let response: Response;
  try {
    response = await apiFetch(listingId ? `/listings/${listingId}/draft` : '/listings', {
      method: listingId ? 'PUT' : 'POST',
      headers: listingId && version != null ? { 'If-Match': `"v${version}"` } : undefined,
      body: JSON.stringify(draftPayload(fields)),
    });
  } catch {
    throw new OfflineError('offline');
  }
  const body = await response.json().catch(() => null);
  if (response.status === 409 && body?.code === 'VERSION_CONFLICT') {
    throw new VersionConflictError(etagVersion(response.headers.get('ETag')));
  }
  if (!response.ok) {
    throw new ApiProblemException(
      (body as ProblemDetails) ?? { title: 'Lỗi', status: response.status, detail: 'Không lưu được bản nháp.' },
    );
  }
  return { listingId: body.listingId, version: body.version };
}

export const loadDraft = (listingId: string) => apiClient<DraftView>(`/api/v2/me/listings/${listingId}/draft`);
export const loadPreview = (listingId: string) => apiClient<PreviewView>(`/api/v2/me/listings/${listingId}/preview`);
export const submitDraft = (listingId: string) =>
  apiClient<{ status: string }>(`/listings/${listingId}/submit`, { method: 'POST' });

/** Server field errors (400) as a map keyed by field name. */
export function serverFieldErrors(error: unknown): FieldErrors {
  if (!(error instanceof ApiProblemException) || !error.problem.errors) return {};
  const result: FieldErrors = {};
  for (const item of error.problem.errors) if (item.field) result[item.field] = item.message;
  return result;
}
