export type ReportCategory = 'SCAM_DEPOSIT' | 'FAKE_SOLD' | 'INCORRECT_PRICE' | 'OTHER';

export type ReportSeverity = 'P0_EMERGENCY' | 'HIGH' | 'MEDIUM' | 'LOW';

export type ReportStatus = 'PENDING' | 'WAITING_REPLY' | 'RESOLVED' | 'DISMISSED' | 'APPEALED';

export interface ListingReport {
  id: string;
  listingId: string;
  caseNumber: string;
  reporterType: string;
  reporterPhone?: string;
  category: ReportCategory;
  severity: ReportSeverity;
  status: ReportStatus;
  description: string;
  evidenceUrls?: string;
  resolutionNote?: string;
  createdAt: string;
  resolvedAt?: string;
}

export type LeadStatus = 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM';

export interface LeadItem {
  id: string;
  listingId: string;
  fullName: string;
  maskedPhone: string;
  note?: string;
  consentPolicy: boolean;
  status: LeadStatus;
  createdAt: string;
}

export interface FunnelStepMetric {
  stepIndex: number;
  stepName: string;
  count: number;
  percentage: number;
}

export interface FunnelAnalytics {
  impressions: number;
  detailViews: number;
  leadsSubmitted: number;
  contactedCount: number;
  dealsClosed: number;
  conversionRatePercent: number;
  steps: FunnelStepMetric[];
}
