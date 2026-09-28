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
