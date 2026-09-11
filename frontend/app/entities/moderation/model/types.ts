export interface ModerationQueueItem {
  listingId: string;
  revisionId: string;
  revisionNumber: number;
  title: string;
  ownerId: string;
  priceVnd: number;
  areaM2: number;
  purpose: 'SALE' | 'RENT';
  propertyType: string;
  addressSummary: string;
  submittedAt: string;
  mediaCount: number;
  isFirstSubmission: boolean;
}

export interface FieldDiff {
  fieldName: string;
  fieldLabel: string;
  oldValue: string;
  newValue: string;
  isChanged: boolean;
}

export interface ListingDiff {
  listingId: string;
  currentRevisionId: string;
  currentRevisionNumber: number;
  previousRevisionId?: string;
  previousRevisionNumber?: number;
  isFirstSubmission: boolean;
  changedCount: number;
  diffs: FieldDiff[];
}

export interface StandardReason {
  code: string;
  vietnameseLabel: string;
  category: string;
}

export interface ApproveListingPayload {
  revisionId: string;
  note?: string;
}

export interface RejectListingPayload {
  revisionId: string;
  reasonCode: string;
  reasonDetail?: string;
}
