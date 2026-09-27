/**
 * Web analytics event catalog v1 (contract §5, validated server-side by backend EventCatalog). Only these names can
 * be sent from the browser; server events (lead_submitted, appointment_*, listing_favorited…) are recorded by the
 * backend. The listed properties are the only keys that leave the page. The server rejects a whole batch when one
 * event is invalid, so the client drops what the catalog would refuse (unknown keys, nulls where not allowed,
 * missing listingId). Adding an event = a new row + version on both sides; never reuse a name for new semantics.
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

interface CatalogEntry {
  version: number;
  properties: readonly string[];
  /** Properties that may be sent as null; any other null is dropped before sending. */
  nullable?: readonly string[];
  requiresListing?: boolean;
}

export const WEB_EVENT_CATALOG: { readonly [N in WebEventName]: CatalogEntry } = {
  search_performed: {
    version: 1,
    properties: ['filterHash', 'purpose', 'resultCount', 'zeroResult', 'engine', 'hasBbox', 'hasKeyword'],
    nullable: ['resultCount'],
  },
  search_results_viewed: { version: 1, properties: ['filterHash', 'listingIds', 'offset'] },
  listing_detail_viewed: { version: 1, properties: ['purpose', 'propertyType', 'district'], requiresListing: true },
  compare_opened: { version: 1, properties: ['listingIds'] },
  lead_form_opened: { version: 1, properties: ['requestType'], requiresListing: true },
  kyc_required_shown: { version: 1, properties: ['context'] },
  web_vital: { version: 1, properties: ['metric', 'value', 'rating', 'route'] },
};

/** Upper bound of ids in list properties (listingIds ≤ 48 in the contract). */
export const MAX_LISTING_IDS = 48;

export function isWebEventName(name: string): name is WebEventName {
  return Object.prototype.hasOwnProperty.call(WEB_EVENT_CATALOG, name);
}

/** Copies only the catalogued keys; nulls survive only where allowed; list properties are capped. */
export function pickCatalogProperties(name: WebEventName, properties: object): Record<string, unknown> {
  const entry = WEB_EVENT_CATALOG[name];
  const source = properties as Record<string, unknown>;
  const picked: Record<string, unknown> = {};
  for (const key of entry.properties) {
    if (!(key in source) || source[key] === undefined) continue;
    const value = source[key];
    if (value === null && !entry.nullable?.includes(key)) continue;
    picked[key] = Array.isArray(value) ? value.slice(0, MAX_LISTING_IDS) : value;
  }
  return picked;
}
