/**
 * Compile-time contract check (audit F22.4): every field a hand-written API type reads must exist in the backend's
 * OpenAPI schema of the same name (app/shared/api/generated/openapi.ts, generated from the committed snapshot). A
 * backend change that drops or renames a field fails `tsc` here instead of at run time.
 *
 * Maintained by hand: when a feature adds or renames a hand-written view type that mirrors a backend response 1:1,
 * add an `Expect<UnknownKeys<Type, Schemas['SchemaName']>>` row below (the schema name is what
 * `app/shared/api/generated/openapi.ts` calls it — several backend DTOs share a simple class name across modules and
 * are disambiguated there, e.g. `TeamMember` → `LeadTeamMember`).
 *
 * Left out on purpose (name collides with an unrelated schema of the same simple name, or the type is composed
 * client-side from more than one response): `KycDocumentAccess` (entities/admin), `LeadItem` (entities/lead),
 * `TeamMember` (entities/lead), `ListingLocation` (entities/listing/model/v2).
 */
import type { Schemas } from './schema';

import type {
  AdminAction,
  AdminOrder,
  AdminOrderPage,
  BankSettings,
  BulkItemResult,
  ClaimView,
  DecisionView,
  ModerationDecision,
  ModerationQueueItem,
  ModerationQueuePage,
  MyKycStatus,
  OrderEvent,
  RevisionRow,
  StatusHistoryRow,
} from '@/entities/admin/model/types';
import type { Breakdown, Funnel, FunnelStep, Metric, NamedMetric, TrendDay, VitalRow } from '@/entities/analytics/api';
import type {
  Amenity,
  AreaCard,
  Inventory,
  ProjectCard,
  ProjectList,
  PublicRevision,
  Statistic,
} from '@/entities/content/model';
import type { AppointmentSummary, AppointmentView } from '@/entities/lead/model/types';
import type {
  ListingDetailV2,
  ListingSummaryV2,
  MapResponseV2,
  SearchResponseV2,
  SellerProfileV2,
} from '@/entities/listing/model/v2';
import type { FieldDiff } from '@/entities/moderation/model/types';
import type {
  NotificationItem,
  PublicShortlist,
  SavedListingItem,
  SavedListingPage,
  ShortlistDetail,
  Unavailable,
} from '@/features/engagement/api';
import type { DraftView, PreviewView } from '@/features/listing-editor/api';
import type { ImportReport, MyListingItem, MyListingsPage, VersionSummary } from '@/features/my-listings/api';
import type { AuthResult } from '@/shared/auth/AuthContext';
import type { SessionView } from '@/shared/auth/securityApi';
import type { ProblemDetails } from '@/shared/types/problem-details';
import type { ImageDto } from '@/shared/ui/ResponsiveImage';
import type { Trust } from '@/shared/ui/TrustBadge';

/** Keys of `T` the backend schema `S` does not have; must be `never`. */
type UnknownKeys<T, S> = Exclude<keyof T, keyof S>;
type Expect<T extends never> = T;

export type ContractChecks = [
  Expect<UnknownKeys<AdminAction, Schemas['AdminAction']>>,
  Expect<UnknownKeys<AdminOrder, Schemas['AdminOrder']>>,
  Expect<UnknownKeys<AdminOrderPage, Schemas['AdminOrderPage']>>,
  Expect<UnknownKeys<BankSettings, Schemas['BankSettings']>>,
  Expect<UnknownKeys<BulkItemResult, Schemas['BulkItemResult']>>,
  Expect<UnknownKeys<ClaimView, Schemas['ClaimView']>>,
  Expect<UnknownKeys<DecisionView, Schemas['DecisionView']>>,
  Expect<UnknownKeys<ModerationDecision, Schemas['ModerationDecision']>>,
  Expect<UnknownKeys<ModerationQueueItem, Schemas['ModerationQueueItem']>>,
  Expect<UnknownKeys<ModerationQueuePage, Schemas['ModerationQueuePage']>>,
  Expect<UnknownKeys<MyKycStatus, Schemas['MyKycStatus']>>,
  Expect<UnknownKeys<OrderEvent, Schemas['OrderEvent']>>,
  Expect<UnknownKeys<RevisionRow, Schemas['RevisionRow']>>,
  Expect<UnknownKeys<StatusHistoryRow, Schemas['StatusHistoryRow']>>,
  Expect<UnknownKeys<Breakdown, Schemas['Breakdown']>>,
  Expect<UnknownKeys<Funnel, Schemas['Funnel']>>,
  Expect<UnknownKeys<FunnelStep, Schemas['FunnelStep']>>,
  Expect<UnknownKeys<Metric, Schemas['Metric']>>,
  Expect<UnknownKeys<NamedMetric, Schemas['NamedMetric']>>,
  Expect<UnknownKeys<TrendDay, Schemas['TrendDay']>>,
  Expect<UnknownKeys<VitalRow, Schemas['VitalRow']>>,
  Expect<UnknownKeys<Amenity, Schemas['Amenity']>>,
  Expect<UnknownKeys<AreaCard, Schemas['AreaCard']>>,
  Expect<UnknownKeys<Inventory, Schemas['Inventory']>>,
  Expect<UnknownKeys<ProjectCard, Schemas['ProjectCard']>>,
  Expect<UnknownKeys<ProjectList, Schemas['ProjectList']>>,
  Expect<UnknownKeys<PublicRevision, Schemas['PublicRevision']>>,
  Expect<UnknownKeys<Statistic, Schemas['Statistic']>>,
  Expect<UnknownKeys<AppointmentSummary, Schemas['AppointmentSummary']>>,
  Expect<UnknownKeys<AppointmentView, Schemas['AppointmentView']>>,
  Expect<UnknownKeys<ListingDetailV2, Schemas['ListingDetailV2']>>,
  Expect<UnknownKeys<ListingSummaryV2, Schemas['ListingSummaryV2']>>,
  Expect<UnknownKeys<MapResponseV2, Schemas['MapResponseV2']>>,
  Expect<UnknownKeys<SearchResponseV2, Schemas['SearchResponseV2']>>,
  Expect<UnknownKeys<SellerProfileV2, Schemas['SellerProfileV2']>>,
  Expect<UnknownKeys<FieldDiff, Schemas['FieldDiff']>>,
  Expect<UnknownKeys<NotificationItem, Schemas['NotificationItem']>>,
  Expect<UnknownKeys<PublicShortlist, Schemas['PublicShortlist']>>,
  Expect<UnknownKeys<SavedListingItem, Schemas['SavedListingItem']>>,
  Expect<UnknownKeys<SavedListingPage, Schemas['SavedListingPage']>>,
  Expect<UnknownKeys<ShortlistDetail, Schemas['ShortlistDetail']>>,
  Expect<UnknownKeys<Unavailable, Schemas['Unavailable']>>,
  Expect<UnknownKeys<DraftView, Schemas['DraftView']>>,
  Expect<UnknownKeys<PreviewView, Schemas['PreviewView']>>,
  Expect<UnknownKeys<ImportReport, Schemas['ImportReport']>>,
  Expect<UnknownKeys<MyListingItem, Schemas['MyListingItem']>>,
  Expect<UnknownKeys<MyListingsPage, Schemas['MyListingsPage']>>,
  Expect<UnknownKeys<VersionSummary, Schemas['VersionSummary']>>,
  Expect<UnknownKeys<AuthResult, Schemas['AuthResult']>>,
  Expect<UnknownKeys<SessionView, Schemas['SessionView']>>,
  Expect<UnknownKeys<ProblemDetails, Schemas['ProblemDetails']>>,
  Expect<UnknownKeys<ImageDto, Schemas['ImageDto']>>,
  Expect<UnknownKeys<Trust, Schemas['Trust']>>,
];
