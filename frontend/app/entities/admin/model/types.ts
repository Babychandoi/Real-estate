/** Types of the S4 admin APIs (moderation v2, admin listings/users, reports, trust, billing). */

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ReasonOption {
  code: string;
  vietnameseLabel: string;
  category: string;
}

// ---------------------------------------------------------------------------------------------- moderation

export type QueueFilter = 'ALL' | 'FIRST_SUBMISSION' | 'EDIT' | 'SLA_BREACH' | 'DUPLICATES' | 'MINE' | 'UNCLAIMED';

export interface ClaimView {
  moderatorId: string;
  moderatorName: string;
  expiresAt: string;
  mine: boolean;
}

export interface ModerationQueueItem {
  listingId: string;
  revisionId: string;
  revisionNumber: number;
  title: string;
  ownerId: string;
  ownerName: string;
  priceVnd: number;
  areaM2: number;
  purpose: 'SALE' | 'RENT';
  propertyType: string;
  addressSummary: string | null;
  districtCode: string | null;
  submittedAt: string | null;
  ageMinutes: number | null;
  slaDueAt: string | null;
  slaBreached: boolean;
  kind: 'FIRST_SUBMISSION' | 'EDIT';
  listingStatus: string;
  mediaCount: number;
  openDuplicates: number;
  claim: ClaimView | null;
}

export interface ModerationQueuePage extends Page<ModerationQueueItem> {
  stats: { total: number; slaBreached: number; oldestSubmittedAt: string | null };
}

export interface ModerationDecision {
  success: boolean;
  id: string;
  listingId: string;
  revisionId: string;
  decision: string;
  reasonCode: string;
  status: string;
  moderatorId: string;
}

export interface DecisionView {
  id: string;
  revisionId: string;
  revisionNumber: number;
  decision: 'APPROVED' | 'REJECTED' | 'AUDIT_PASSED' | 'AUDIT_FAILED';
  reasonCode: string;
  note: string | null;
  moderatorId: string;
  moderatorName: string | null;
  bulkBatchId: string | null;
  createdAt: string;
}

export interface BulkItemResult {
  listingId: string;
  revisionId: string;
  outcome: string;
  message: string | null;
}

export interface DuplicateCandidate {
  id: string;
  otherListingId: string;
  score: number;
  reasons: string[];
  status: 'OPEN' | 'DISMISSED' | 'CONFIRMED';
  note: string | null;
  otherStatus: string;
  otherSlug: string | null;
  otherTitle: string | null;
  otherPriceVnd: number | null;
  otherAreaM2: number | null;
  otherAddress: string | null;
  otherOwnerId: string;
  otherAssetId: string | null;
}

export interface AuditSample {
  id: string;
  weekStart: string;
  listingId: string;
  revisionId: string;
  revisionNumber: number;
  title: string;
  addressSummary: string | null;
  priceVnd: number;
  status: 'OPEN' | 'PASSED' | 'FAILED';
  note: string | null;
  reviewedAt: string | null;
  reviewerName: string | null;
  originalModeratorName: string | null;
  originalReasonCode: string | null;
  approvedAt: string | null;
  listingStatus: string;
}

// ---------------------------------------------------------------------------------------------- admin listings

export interface AdminListingRow {
  id: string;
  slug: string;
  status: string;
  source: string;
  ownerId: string;
  ownerName: string;
  title: string;
  purpose: string;
  propertyType: string;
  priceVnd: number;
  areaM2: number;
  districtCode: string | null;
  addressSummary: string | null;
  hasPublicRevision: boolean;
  latestRevisionNumber: number;
  hasPendingEdit: boolean;
  propertyAssetId: string | null;
  createdAt: string;
  updatedAt: string;
  expiresAt: string | null;
}

export interface RevisionRow {
  id: string;
  revisionNumber: number;
  status: string;
  title: string;
  priceVnd: number;
  areaM2: number;
  createdAt: string;
  submittedAt: string | null;
  moderatedAt: string | null;
  moderationNote: string | null;
  isPublic: boolean;
  mediaCount: number;
}

export interface StatusHistoryRow {
  id: string;
  fromStatus: string | null;
  toStatus: string;
  action: string;
  reason: string;
  actorId: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface ListingPreview {
  listingId: string;
  slug: string;
  listingStatus: string;
  revisionId: string;
  revisionNumber: number;
  revisionStatus: string;
  title: string;
  description: string | null;
  purpose: string;
  propertyType: string;
  priceVnd: number;
  areaM2: number;
  bedrooms: number | null;
  bathrooms: number | null;
  floors: number | null;
  legalStatus: string | null;
  districtCode: string | null;
  addressSummary: string | null;
  submittedAt: string | null;
  moderatedAt: string | null;
  moderationNote: string | null;
  isPublic: boolean;
  mediaUrls: string[];
}

// ---------------------------------------------------------------------------------------------- users

export interface AdminAction {
  id: string;
  action: 'ROLE_CHANGE' | 'LOCK' | 'UNLOCK' | 'MFA_RESET' | 'SESSIONS_REVOKE';
  fromValue: string | null;
  toValue: string | null;
  reason: string;
  actorId: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface KycDocumentAccess {
  token: string;
  expiresAt: string;
  identity: { idCardFrontUrl: string | null; idCardBackUrl: string | null; selfieUrl: string | null } | null;
  ownershipDocumentUrls: string[];
}

export interface KycAccessLogEntry {
  id: string;
  actorId: string;
  actorName: string | null;
  reason: string;
  createdAt: string;
  grantExpiresAt: string;
}

// ---------------------------------------------------------------------------------------------- reports

export interface ReportQueueItem {
  id: string;
  caseNumber: string;
  listingId: string;
  listingTitle: string | null;
  listingSlug: string | null;
  listingStatus: string | null;
  ownerId: string | null;
  category: 'SCAM_DEPOSIT' | 'FAKE_SOLD' | 'INCORRECT_PRICE' | 'OTHER';
  severity: 'P0_EMERGENCY' | 'HIGH' | 'MEDIUM' | 'LOW';
  status: 'PENDING' | 'WAITING_REPLY' | 'APPEALED' | 'RESOLVED' | 'DISMISSED';
  description: string;
  reporterPhoneMasked: string | null;
  createdAt: string;
  slaDueAt: string;
  slaBreached: boolean;
  minutesToDue: number;
  claim: { staffId: string; staffName: string; expiresAt: string; mine: boolean } | null;
  ownerOutcome: 'OWNER_RESPONSE' | 'AUTO_PAUSED' | null;
  resolutionNote: string | null;
  resolvedAt: string | null;
}

export interface ReportEvent {
  id: string;
  type: string;
  actorId: string | null;
  actorName: string | null;
  note: string | null;
  data: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------------------------------------- trust

export interface TrustDecision {
  id: string;
  decision: 'APPROVED' | 'REJECTED' | 'REVOKED' | 'EXPIRED';
  reasonCode: string;
  reasonLabel: string;
  note: string | null;
  expiresAt: string | null;
  actorId: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface EvidenceComparison {
  field: 'OWNER_NAME' | 'CERTIFICATE_NUMBER' | 'ADDRESS';
  label: string;
  identityValue: string | null;
  documentValue: string | null;
  result: 'MATCH' | 'MISMATCH' | 'MISSING' | 'UNIQUE' | 'USED_ELSEWHERE' | 'SIMILAR' | 'DIFFERENT';
}

export interface VerificationEvidence {
  id: string;
  listingId: string;
  listingTitle: string | null;
  listingSlug: string | null;
  listingStatus: string | null;
  listingAddress: string | null;
  districtCode: string | null;
  listingOwnerId: string | null;
  verificationType: string;
  status: 'PENDING' | 'VERIFIED_OWNER' | 'REJECTED' | 'REVOKED';
  certificateNumber: string | null;
  ownerNameOnDoc: string;
  documentUrls: string[];
  submittedAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  verifierNote: string | null;
  identity: {
    kycId: string | null;
    status: string;
    fullName: string | null;
    verifiedAt: string | null;
    expiresAt: string | null;
  };
  comparisons: EvidenceComparison[];
  history: TrustDecision[];
}

export interface TrustReasonOption {
  code: string;
  label: string;
}

export interface MyKycStatus {
  status: 'NOT_SUBMITTED' | 'PENDING' | 'VERIFIED' | 'REJECTED' | 'EXPIRED';
  submittedAt: string | null;
  decidedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  rejectionReason: string | null;
  canSubmit: boolean;
  timeline: TrustDecision[];
}

// ---------------------------------------------------------------------------------------------- billing

export type OrderStatus =
  'CREATED' | 'TRANSFER_REPORTED' | 'EXCEPTION' | 'APPROVED' | 'REJECTED' | 'REFUNDED' | 'CANCELLED';

export interface BillingOrder {
  id: string;
  userId: string;
  planCode: string;
  amountVnd: number;
  reference: string;
  status: OrderStatus;
  createdAt: string;
  qrUrl: string | null;
  planName: string;
  quota: number;
  durationDays: number;
  bankBinSnapshot: string | null;
  accountNumberSnapshot: string | null;
  accountNameSnapshot: string | null;
  reportedAt: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  exceptionReason: string | null;
  resolution: string | null;
  updatedAt: string | null;
  invoiceNumber: string | null;
}

export interface OrderEvent {
  id: string;
  type: string;
  fromStatus: string | null;
  toStatus: string | null;
  actorId: string | null;
  actorName: string | null;
  note: string | null;
  data: string | null;
  createdAt: string;
}

export interface AdminOrder {
  id: string;
  userId: string;
  customerName: string;
  customerEmail: string | null;
  planCode: string;
  planName: string;
  amountVnd: number;
  reference: string;
  status: OrderStatus;
  createdAt: string;
  reportedAt: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  receivedAmountVnd: number | null;
  receivedReference: string | null;
  exceptionReason: string | null;
  resolution: string | null;
  reviewerName: string | null;
}

export interface AdminOrderPage extends Page<AdminOrder> {
  counts: Partial<Record<OrderStatus, number>>;
}

export interface BankSettings {
  bankBin: string;
  bankName: string;
  accountNumber: string;
  accountName: string;
  adminEmail: string | null;
  version: number;
}
