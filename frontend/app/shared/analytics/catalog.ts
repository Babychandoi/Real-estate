/**
 * Web analytics event catalog v1 (contract §5). Only these names can be sent from the browser; server events
 * (lead_submitted, appointment_*, listing_favorited…) are recorded by the backend. The listed properties are the
 * only keys that leave the page. Adding an event = a new row + version; never reuse a name for new semantics.
 */
export interface WebEventProperties {
  search_performed: {
    filterHash: string;
    purpose: 'SALE' | 'RENT';
    resultCount: number | null;
    zeroResult: boolean;
    engine: 'search' | 'database';
    hasBbox: boolean;
    hasKeyword: boolean;
  };
  search_results_viewed: { filterHash: string; listingIds: string[]; offset: number };
  listing_detail_viewed: { purpose: 'SALE' | 'RENT'; propertyType: string; district?: string | null };
  compare_opened: { listingIds: string[] };
  lead_form_opened: { requestType: string };
  kyc_required_shown: { context: string };
  web_vital: {
    metric: 'LCP' | 'INP' | 'CLS' | 'TTFB';
    value: number;
    rating: 'good' | 'needs-improvement' | 'poor';
    route: string;
  };
}

export type WebEventName = keyof WebEventProperties;

interface CatalogEntry {
  version: number;
  properties: readonly string[];
}

export const WEB_EVENT_CATALOG: { readonly [N in WebEventName]: CatalogEntry } = {
  search_performed: {
    version: 1,
    properties: ['filterHash', 'purpose', 'resultCount', 'zeroResult', 'engine', 'hasBbox', 'hasKeyword'],
  },
  search_results_viewed: { version: 1, properties: ['filterHash', 'listingIds', 'offset'] },
  listing_detail_viewed: { version: 1, properties: ['purpose', 'propertyType', 'district'] },
  compare_opened: { version: 1, properties: ['listingIds'] },
  lead_form_opened: { version: 1, properties: ['requestType'] },
  kyc_required_shown: { version: 1, properties: ['context'] },
  web_vital: { version: 1, properties: ['metric', 'value', 'rating', 'route'] },
};

/** Upper bound of ids in list properties (search_results_viewed.listingIds ≤ 48 in the contract). */
export const MAX_LISTING_IDS = 48;

export function isWebEventName(name: string): name is WebEventName {
  return Object.prototype.hasOwnProperty.call(WEB_EVENT_CATALOG, name);
}

/** Copies only the catalogued keys; list properties are capped so an event stays small. */
export function pickCatalogProperties(name: WebEventName, properties: object): Record<string, unknown> {
  const source = properties as Record<string, unknown>;
  const picked: Record<string, unknown> = {};
  for (const key of WEB_EVENT_CATALOG[name].properties) {
    if (!(key in source) || source[key] === undefined) continue;
    const value = source[key];
    picked[key] = Array.isArray(value) ? value.slice(0, MAX_LISTING_IDS) : value;
  }
  return picked;
}
