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

/** WITHDRAWN: the requester withdrew the request (contract §2.4); only the requester can set it. */
export type LeadStatus = 'NEW' | 'CONTACTED' | 'APPOINTED' | 'CLOSED' | 'SPAM' | 'WITHDRAWN';
export type LeadRequestType = 'VIEWING' | 'CONSULTATION';
export type LeadQualification = 'QUALIFIED' | 'UNQUALIFIED';
export type AppointmentStatus = 'PROPOSED' | 'CONFIRMED' | 'RESCHEDULED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';
export type PartySide = 'REQUESTER' | 'OWNER_SIDE';

export interface AppointmentSummary {
  id: string;
  status: AppointmentStatus;
  startsAt: string | null;
  endsAt: string | null;
  proposedBySide: PartySide;
  version: number;
}

/** Owner-side / staff view of a lead (phone masked; never the seller's contact). */
export interface LeadItem {
  id: string;
  listingId: string;
  fullName: string;
  maskedPhone: string;
  requestType: LeadRequestType;
  note?: string | null;
  consentPolicy: boolean;
  status: LeadStatus;
  createdAt: string;
  version: number;
  updatedAt: string;
  firstResponseAt: string | null;
  responseDueAt: string;
  overdue: boolean;
  qualification: LeadQualification | null;
  qualificationReason: string | null;
  qualificationNote: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  withdrawnAt: string | null;
  listingTitle: string;
  listingSlug?: string | null;
  listingAddress?: string | null;
  listingImageUrl?: string | null;
  openAppointment: AppointmentSummary | null;
}

export interface LeadPage<T = LeadItem> {
  items: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
  statusCounts: Partial<Record<LeadStatus, number>>;
}

/** Requester view: no owner-internal data. */
export interface InquiryItem {
  id: string;
  listingId: string;
  requestType: LeadRequestType;
  note?: string | null;
  status: LeadStatus;
  createdAt: string;
  version: number;
  updatedAt: string;
  firstResponseAt: string | null;
  withdrawnAt: string | null;
  listingTitle: string;
  listingSlug?: string | null;
  listingAddress?: string | null;
  listingImageUrl?: string | null;
  listingAvailable: boolean;
  openAppointment: AppointmentSummary | null;
}

export interface AppointmentSlot {
  id: string;
  startsAt: string;
  endsAt: string;
}

export interface AppointmentView {
  id: string;
  leadId: string;
  listingId: string;
  status: AppointmentStatus;
  proposedBySide: PartySide;
  startsAt: string | null;
  endsAt: string | null;
  confirmedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  outcomeAt: string | null;
  noShowParty: PartySide | null;
  note: string | null;
  version: number;
  createdAt: string;
  slots: AppointmentSlot[];
  awaitingMe: boolean;
}

export interface LeadHistoryEntry {
  id: string;
  type: string;
  actorSide: 'REQUESTER' | 'OWNER_SIDE' | 'STAFF' | 'SYSTEM';
  fromStatus: LeadStatus | null;
  toStatus: LeadStatus | null;
  note: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface LeadEligibility {
  listingAcceptsLeads: boolean;
  ownListing: boolean;
  requesterKycVerified: boolean;
  ownerKycVerified: boolean;
  openLeadId: string | null;
  openLeadStatus: LeadStatus | null;
}

export interface LeadReport {
  from: string;
  to: string;
  targetMinutes: number;
  leads: number;
  viewingRequests: number;
  measuredResponses: number;
  medianFirstResponseMinutes: number | null;
  withinTargetPercent: number | null;
  qualified: number;
  unqualified: number;
  unassessed: number;
  qualifiedPercentOfAssessed: number | null;
  withdrawn: number;
  closed: number;
  leadsWithConfirmedAppointment: number;
  appointmentRatePercent: number | null;
  appointmentsCompleted: number;
  appointmentsNoShow: number;
  spendVnd: number | null;
  approvedOrders: number;
  costPerLeadVnd: number | null;
  costPerQualifiedLeadVnd: number | null;
  byListing: Array<{
    listingId: string;
    slug: string;
    title: string;
    leads: number;
    qualified: number;
    withAppointment: number;
  }>;
}

export interface WorkspaceTask {
  leadId: string;
  leadName: string;
  requestType: LeadRequestType;
  createdAt: string;
  listingTitle: string;
  dueAt?: string;
  overdue?: boolean;
  appointmentId?: string;
  appointmentStatus?: AppointmentStatus;
  appointmentVersion?: number;
  startsAt?: string | null;
  endsAt?: string | null;
  proposedAt?: string;
}

export interface TeamMember {
  memberId: string;
  name: string;
  email: string | null;
  addedAt: string;
  openLeads: number;
}

export interface BrokerWorkspace {
  generatedAt: string;
  listingStats: { listings: number; active: number; pending: number };
  sla: { firstResponseMinutes: number; reminderEnabled: boolean; dailyDigestEnabled: boolean; configured: boolean };
  slaMetrics: {
    windowDays: number;
    targetMinutes: number;
    leads: number;
    measuredResponses: number;
    medianFirstResponseMinutes: number | null;
    p90FirstResponseMinutes: number | null;
    withinTargetPercent: number | null;
    openBreaches: number;
    openNew: number;
  };
  tasks: {
    respond: WorkspaceTask[];
    appointmentsToday: WorkspaceTask[];
    proposalsAwaitingMe: WorkspaceTask[];
    outcomesToRecord: WorkspaceTask[];
  };
  intakeHistory: Array<{
    id: string;
    type: string;
    actorSide: string;
    actorName: string | null;
    fromStatus: LeadStatus | null;
    toStatus: LeadStatus | null;
    createdAt: string;
    leadId: string;
    leadName: string;
    listingTitle: string;
  }>;
  team: TeamMember[];
  memberOf: Array<{ ownerId: string; ownerName: string }>;
}

export interface LeadListingItem {
  listingId: string;
  title: string;
  slug: string;
  address?: string;
  imageUrl?: string;
  totalLeads: number;
  newLeads: number;
  activeLeads: number;
  closedLeads: number;
  lastLeadAt: string;
}

export interface LeadListingPage {
  items: LeadListingItem[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
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
