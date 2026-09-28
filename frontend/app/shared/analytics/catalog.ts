/**
 * Web analytics event catalog v1 (contract §5), mirroring the backend `EventCatalog`/`EventValidation` exactly:
 * same property names, same required/nullable/optional rules and the same value patterns (hex hash, district code,
 * context code, route pattern, UUIDs). Only these names can be sent from the browser; server events
 * (lead_submitted, appointment_*, listing_favorited…) are recorded by the backend.
 *
 * The server validates a batch all-or-nothing: one event with an unknown key, a missing required property or a
 * value that fails its pattern makes it reject the *whole* batch (up to 50 events). `sanitizeCatalogProperties`
 * mirrors those rules client-side so a bad event never takes the rest of the batch down with it: unknown keys are
 * dropped silently (as before), an invalid *optional* property is dropped on its own, and an invalid or missing
 * *required* property invalidates just that one event (the caller drops it, per m2).
 *
 * Adding an event = a new row + version on both sides; never reuse a name for new semantics.
 */
export type PropertyType = 'APARTMENT' | 'HOUSE' | 'VILLA' | 'TOWNHOUSE' | 'LAND';
export type RequestType = 'VIEWING' | 'CONSULTATION';

export interface WebEventProperties {
  search_performed: {
    /** First 32 hex chars of SHA-256 over the canonical filter JSON (contract §7). */
    filterHash: string;
    purpose: 'SALE' | 'RENT';
    resultCount: number | null;
    zeroResult?: boolean;
    engine?: 'search' | 'database';
    hasBbox?: boolean;
    hasKeyword?: boolean;
  };
  search_results_viewed: { filterHash: string; listingIds: string[]; offset?: number };
  listing_detail_viewed: { purpose?: 'SALE' | 'RENT'; propertyType?: PropertyType; district?: string };
  compare_opened: { listingIds: string[] };
  lead_form_opened: { requestType?: RequestType };
  /** `context` is a lower-case code, e.g. "lead_form". */
  kyc_required_shown: { context: string };
  web_vital: {
    metric: 'LCP' | 'INP' | 'CLS' | 'TTFB';
    value: number;
    rating?: 'good' | 'needs-improvement' | 'poor';
    /** Route pattern, never the concrete URL: "/listings/:slug". */
    route?: string;
  };
}

export type WebEventName = keyof WebEventProperties;

/** Events about one listing: they must carry `listingId` in the envelope. */
export type ListingEventName = 'listing_detail_viewed' | 'lead_form_opened';

/** Upper bound of ids in list properties (listingIds ≤ 48 in the contract; also the server's `uuidArray` cap). */
export const MAX_LISTING_IDS = 48;

// ---------------------------------------------------------------------------------------------- value patterns
// Same character classes as the backend's `EventCatalog` (java.util.regex.Pattern, matched against the whole
// string) so a value either both sides accept or both sides reject.
const HASH = /^[0-9a-f]{32}$/;
const DISTRICT = /^[0-9]{1,5}$/;
const CONTEXT = /^[a-z][a-z0-9_-]{0,39}$/;
const ROUTE = /^\/[A-Za-z0-9/:_.-]{0,99}$/;
/** Same shape check as the server's `isUuid` (36 chars, standard hyphenated form); any version, case-insensitive. */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const isString = (value: unknown): value is string => typeof value === 'string';
const matches =
  (pattern: RegExp) =>
  (value: unknown): value is string =>
    isString(value) && pattern.test(value);
const oneOf =
  <T extends string>(...values: readonly T[]) =>
  (value: unknown): value is T =>
    isString(value) && (values as readonly string[]).includes(value);
const isBoolean = (value: unknown): value is boolean => typeof value === 'boolean';
const isNonNegativeInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value >= 0;
const isFiniteNonNegativeNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0;
const isUuidString = (value: unknown): value is string => isString(value) && value.length === 36 && UUID.test(value);
const isUuidArray =
  (max: number) =>
  (value: unknown): value is string[] =>
    Array.isArray(value) && value.length <= max && value.every(isUuidString);

interface PropertyRule {
  required: boolean;
  /** May be sent as `null` even though it's otherwise required (mirrors the server's `nullable` flag). */
  nullable: boolean;
  accepts: (value: unknown) => boolean;
  /** Arrays are capped to this length (same as the server's `uuidArray(max)`) before being validated. */
  maxArrayLength?: number;
}

function required(accepts: (value: unknown) => boolean, maxArrayLength?: number): PropertyRule {
  return { required: true, nullable: false, accepts, maxArrayLength };
}
function optional(accepts: (value: unknown) => boolean): PropertyRule {
  return { required: false, nullable: false, accepts };
}
function nullableProperty(accepts: (value: unknown) => boolean): PropertyRule {
  return { required: false, nullable: true, accepts };
}

interface CatalogEntry {
  version: number;
  requiresListing?: boolean;
  properties: Record<string, PropertyRule>;
}

export const WEB_EVENT_CATALOG: { readonly [N in WebEventName]: CatalogEntry } = {
  search_performed: {
    version: 1,
    properties: {
      filterHash: required(matches(HASH)),
      purpose: required(oneOf('SALE', 'RENT')),
      resultCount: nullableProperty(isNonNegativeInteger),
      zeroResult: optional(isBoolean),
      engine: optional(oneOf('search', 'database')),
      hasBbox: optional(isBoolean),
      hasKeyword: optional(isBoolean),
    },
  },
  search_results_viewed: {
    version: 1,
    properties: {
      filterHash: required(matches(HASH)),
      listingIds: required(isUuidArray(MAX_LISTING_IDS), MAX_LISTING_IDS),
      offset: optional(isNonNegativeInteger),
    },
  },
  listing_detail_viewed: {
    version: 1,
    requiresListing: true,
    properties: {
      purpose: optional(oneOf('SALE', 'RENT')),
      propertyType: optional(oneOf('APARTMENT', 'HOUSE', 'VILLA', 'TOWNHOUSE', 'LAND')),
      district: optional(matches(DISTRICT)),
    },
  },
  compare_opened: {
    version: 1,
    properties: { listingIds: required(isUuidArray(MAX_LISTING_IDS), MAX_LISTING_IDS) },
  },
  lead_form_opened: {
    version: 1,
    requiresListing: true,
    properties: { requestType: optional(oneOf('VIEWING', 'CONSULTATION')) },
  },
  kyc_required_shown: {
    version: 1,
    properties: { context: required(matches(CONTEXT)) },
  },
  web_vital: {
    version: 1,
    properties: {
      metric: required(oneOf('LCP', 'INP', 'CLS', 'TTFB')),
      value: required(isFiniteNonNegativeNumber),
      rating: optional(oneOf('good', 'needs-improvement', 'poor')),
      route: optional(matches(ROUTE)),
    },
  },
};

export function isWebEventName(name: string): name is WebEventName {
  return Object.prototype.hasOwnProperty.call(WEB_EVENT_CATALOG, name);
}

/**
 * Validates and copies only the catalogued keys of `input` for `name`, mirroring the server's per-property rules.
 * An invalid *optional* property is dropped on its own (the rest of the event is still sent); a missing or
 * invalid *required* property makes the whole event unsendable, so this returns `null` and the caller drops the
 * event (m2/m5). Never throws: non-object input is treated as `{}`.
 */
export function sanitizeCatalogProperties(name: WebEventName, input: unknown): Record<string, unknown> | null {
  const entry = WEB_EVENT_CATALOG[name];
  const source = input != null && typeof input === 'object' ? (input as Record<string, unknown>) : {};
  const result: Record<string, unknown> = {};
  for (const [key, rule] of Object.entries(entry.properties)) {
    if (!(key in source) || source[key] === undefined) {
      if (rule.required) return null;
      continue;
    }
    const raw = source[key];
    if (raw === null) {
      if (rule.nullable) {
        result[key] = null;
        continue;
      }
      if (rule.required) return null;
      continue;
    }
    const value = rule.maxArrayLength != null && Array.isArray(raw) ? raw.slice(0, rule.maxArrayLength) : raw;
    if (!rule.accepts(value)) {
      if (rule.required) return null;
      continue;
    }
    result[key] = value;
  }
  return result;
}
