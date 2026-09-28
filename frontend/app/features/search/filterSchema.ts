/**
 * The search filter schema of contract §7 — the only place that parses, validates and serializes search filters
 * for the URL, the API and saved searches (F03.1). Mirrors the backend `SearchFilterParser`/`SearchFilter`:
 * same parameter names, value sets, ranges, keyword normalisation and canonical form (hence the same `filterHash`).
 *
 * - The URL is the source of truth of the search page; `view` and `place` are UI-only, `size`/`cursor` API-only.
 * - Invalid URL values are dropped by `parseSearchParams` and reported in `errors` (the page tells the user).
 * - Changing `purpose` clears the price range (SALE and RENT amounts are never comparable).
 */
import type { Furnishing, LegalCode, PropertyType, Purpose } from '@/entities/listing/model/v2';

export type VerifiedScope = 'IDENTITY' | 'OWNERSHIP';
export type SearchSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'AREA_DESC' | 'RELEVANCE';
export type SearchView = 'list' | 'map' | 'split';
/** WGS84 `[minLng, minLat, maxLng, maxLat]`, rounded to 5 decimals. */
export type BBox = [number, number, number, number];

export interface SearchFilters {
  purpose: Purpose;
  types: PropertyType[];
  priceMin?: number;
  priceMax?: number;
  areaMin?: number;
  areaMax?: number;
  bedsMin?: number;
  legal: LegalCode[];
  furnishing: Furnishing[];
  verified?: VerifiedScope;
  districts: string[];
  project?: string;
  /** Keyword as typed (≤ 100 chars); the API and the hash use its normalised form. */
  q?: string;
  bbox?: BBox;
  /** Explicit sort; absent = RELEVANCE with a keyword, NEWEST otherwise. */
  sort?: SearchSort;
  view: SearchView;
  /** Label of the chosen place, paired with `bbox`. */
  place?: string;
}

export interface FilterError {
  param: string;
  message: string;
}

export const PURPOSES: readonly Purpose[] = ['SALE', 'RENT'];
export const PROPERTY_TYPES: readonly PropertyType[] = ['APARTMENT', 'HOUSE', 'VILLA', 'TOWNHOUSE', 'LAND'];
export const LEGAL_CODES: readonly LegalCode[] = [
  'RED_BOOK',
  'PINK_BOOK',
  'SALE_CONTRACT',
  'PENDING_CERTIFICATE',
  'OTHER',
];
export const FURNISHINGS: readonly Furnishing[] = ['NONE', 'BASIC', 'FULL'];
export const VERIFIED_SCOPES: readonly VerifiedScope[] = ['IDENTITY', 'OWNERSHIP'];
export const SORTS: readonly SearchSort[] = ['NEWEST', 'PRICE_ASC', 'PRICE_DESC', 'AREA_DESC', 'RELEVANCE'];
export const VIEWS: readonly SearchView[] = ['list', 'map', 'split'];
export const MAX_KEYWORD_LENGTH = 100;
export const MAX_BBOX_SPAN = 3;
export const MAX_PRICE_VND = 1_000_000_000_000_000;
export const MAX_AREA_M2 = 1_000_000;
export const MAX_DISTRICTS = 30;
export const PAGE_SIZE = 24;

export const DEFAULT_FILTERS: SearchFilters = {
  purpose: 'SALE',
  types: [],
  legal: [],
  furnishing: [],
  districts: [],
  view: 'list',
};

/** Parameters the page understands; anything else in the URL is ignored and reported. */
const KNOWN = new Set([
  'purpose',
  'type',
  'priceMin',
  'priceMax',
  'areaMin',
  'areaMax',
  'bedsMin',
  'legal',
  'furnishing',
  'verified',
  'district',
  'project',
  'q',
  'bbox',
  'sort',
  'view',
  'place',
]);

// ------------------------------------------------------------------------------------------------ normalisation

/**
 * Keyword normalisation v1, identical to backend `VietnameseNormalizer` and SQL `bds_search_normalize`: NFD, drop
 * combining marks, đ→d, lower case, runs of characters outside [a-z0-9] become one space, trimmed; `null` if empty.
 */
export function normalizeKeyword(text: string | null | undefined): string | null {
  if (text == null) return null;
  const folded = text
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
  return folded || null;
}

const round5 = (value: number) => Math.round(value * 100_000) / 100_000;
const plainNumber = (value: number, digits: number) => String(Number(value.toFixed(digits)));

export function canonicalBbox(bbox: BBox): string {
  return bbox.map((value) => plainNumber(value, 5)).join(',');
}

// ------------------------------------------------------------------------------------------------ parsing

function csv(value: string | null): string[] {
  if (!value) return [];
  return value
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean);
}

function uniqueSorted<T extends string>(values: T[]): T[] {
  return [...new Set(values)].sort();
}

function parseInteger(value: string): number | null {
  if (!/^\d{1,19}$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed <= MAX_PRICE_VND ? parsed : null;
}

function parseArea(value: string): number | null {
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(value)) return null;
  const parsed = Number(value);
  return parsed > 0 && parsed <= MAX_AREA_M2 ? parsed : null;
}

export function parseBbox(value: string | null | undefined): BBox | null {
  if (!value) return null;
  const parts = value.split(',').map((part) => Number(part.trim()));
  if (parts.length !== 4 || parts.some((part) => !Number.isFinite(part))) return null;
  const [minLng, minLat, maxLng, maxLat] = parts.map(round5);
  if (minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90 || minLng >= maxLng || minLat >= maxLat) return null;
  if (maxLng - minLng > MAX_BBOX_SPAN || maxLat - minLat > MAX_BBOX_SPAN) return null;
  return [minLng, minLat, maxLng, maxLat];
}

/** URL → filters. Invalid values are dropped (never guessed) and listed in `errors`. */
export function parseSearchParams(params: URLSearchParams): { filters: SearchFilters; errors: FilterError[] } {
  const errors: FilterError[] = [];
  const error = (param: string, message: string) => errors.push({ param, message });
  const filters: SearchFilters = { ...DEFAULT_FILTERS, types: [], legal: [], furnishing: [], districts: [] };

  for (const key of new Set(params.keys())) {
    if (!KNOWN.has(key)) error(key, 'Tham số không được hỗ trợ.');
  }
  const purpose = params.get('purpose');
  if (purpose) {
    if ((PURPOSES as readonly string[]).includes(purpose)) filters.purpose = purpose as Purpose;
    else error('purpose', 'Giá trị phải là SALE hoặc RENT.');
  }
  const pick = <T extends string>(param: string, allowed: readonly T[]): T[] => {
    const values = params.getAll(param).flatMap(csv);
    const good = values.filter((value): value is T => (allowed as readonly string[]).includes(value));
    if (good.length !== values.length) error(param, 'Có giá trị không hợp lệ.');
    return uniqueSorted(good);
  };
  filters.types = pick('type', PROPERTY_TYPES);
  filters.legal = pick('legal', LEGAL_CODES);
  filters.furnishing = pick('furnishing', FURNISHINGS);

  for (const param of ['priceMin', 'priceMax'] as const) {
    const raw = params.get(param);
    if (!raw) continue;
    const parsed = parseInteger(raw);
    if (parsed == null) error(param, 'Giá phải là số nguyên VND không âm.');
    else filters[param] = parsed;
  }
  for (const param of ['areaMin', 'areaMax'] as const) {
    const raw = params.get(param);
    if (!raw) continue;
    const parsed = parseArea(raw);
    if (parsed == null) error(param, 'Diện tích phải là số dương, tối đa 2 chữ số thập phân.');
    else filters[param] = parsed;
  }
  const beds = params.get('bedsMin');
  if (beds) {
    const parsed = Number(beds);
    if (/^\d{1,2}$/.test(beds) && parsed >= 1 && parsed <= 10) filters.bedsMin = parsed;
    else error('bedsMin', 'Số phòng ngủ tối thiểu phải từ 1 đến 10.');
  }
  const verified = params.get('verified');
  if (verified) {
    if ((VERIFIED_SCOPES as readonly string[]).includes(verified)) filters.verified = verified as VerifiedScope;
    else error('verified', 'Giá trị phải là IDENTITY hoặc OWNERSHIP.');
  }
  const districts = params.getAll('district').flatMap(csv);
  filters.districts = uniqueSorted(districts.filter((code) => /^\d{3}$/.test(code))).slice(0, MAX_DISTRICTS);
  if (filters.districts.length !== new Set(districts).size) error('district', 'Mã khu vực phải gồm 3 chữ số.');
  const project = params.get('project');
  if (project) {
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(project)) filters.project = project;
    else error('project', 'Mã dự án không hợp lệ.');
  }
  const q = params.get('q');
  if (q && q.trim()) {
    if (q.length > MAX_KEYWORD_LENGTH) error('q', `Từ khóa tối đa ${MAX_KEYWORD_LENGTH} ký tự.`);
    else if (!normalizeKeyword(q)) error('q', 'Từ khóa không có chữ hoặc số để tìm.');
    else filters.q = q.trim();
  }
  const bboxText = params.get('bbox');
  if (bboxText) {
    const bbox = parseBbox(bboxText);
    if (bbox) filters.bbox = bbox;
    else error('bbox', 'Khung bản đồ không hợp lệ hoặc rộng quá 3 độ.');
  }
  const sort = params.get('sort');
  if (sort) {
    if ((SORTS as readonly string[]).includes(sort) && (sort !== 'RELEVANCE' || filters.q)) {
      filters.sort = sort as SearchSort;
    } else error('sort', 'Cách sắp xếp không hợp lệ.');
  }
  const view = params.get('view');
  if (view) {
    if ((VIEWS as readonly string[]).includes(view)) filters.view = view as SearchView;
    else error('view', 'Chế độ xem không hợp lệ.');
  }
  const place = params.get('place');
  if (place && filters.bbox) filters.place = place.slice(0, 200);

  for (const message of validateFilters(filters)) {
    error(message.param, message.message);
    if (message.param === 'priceMax') delete filters.priceMax;
    if (message.param === 'areaMax') delete filters.areaMax;
  }
  return { filters, errors };
}

/** Cross-field rules (the backend answers 400 INVALID_FILTER for the same cases). */
export function validateFilters(filters: SearchFilters): FilterError[] {
  const errors: FilterError[] = [];
  if (filters.priceMin != null && filters.priceMax != null && filters.priceMin > filters.priceMax) {
    errors.push({ param: 'priceMax', message: 'Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.' });
  }
  if (filters.areaMin != null && filters.areaMax != null && filters.areaMin > filters.areaMax) {
    errors.push({ param: 'areaMax', message: 'Diện tích tối đa phải lớn hơn hoặc bằng diện tích tối thiểu.' });
  }
  return errors;
}

// ------------------------------------------------------------------------------------------------ serializing

export function effectiveSort(filters: SearchFilters): SearchSort {
  return filters.sort ?? (filters.q && normalizeKeyword(filters.q) ? 'RELEVANCE' : 'NEWEST');
}

function filterEntries(filters: SearchFilters): Array<[string, string]> {
  const entries: Array<[string, string]> = [['purpose', filters.purpose]];
  if (filters.types.length) entries.push(['type', uniqueSorted(filters.types).join(',')]);
  if (filters.priceMin != null) entries.push(['priceMin', String(filters.priceMin)]);
  if (filters.priceMax != null) entries.push(['priceMax', String(filters.priceMax)]);
  if (filters.areaMin != null) entries.push(['areaMin', plainNumber(filters.areaMin, 2)]);
  if (filters.areaMax != null) entries.push(['areaMax', plainNumber(filters.areaMax, 2)]);
  if (filters.bedsMin != null) entries.push(['bedsMin', String(filters.bedsMin)]);
  if (filters.legal.length) entries.push(['legal', uniqueSorted(filters.legal).join(',')]);
  if (filters.furnishing.length) entries.push(['furnishing', uniqueSorted(filters.furnishing).join(',')]);
  if (filters.verified) entries.push(['verified', filters.verified]);
  if (filters.districts.length) entries.push(['district', uniqueSorted(filters.districts).join(',')]);
  if (filters.project) entries.push(['project', filters.project]);
  if (filters.q && normalizeKeyword(filters.q)) entries.push(['q', filters.q.trim()]);
  if (filters.bbox) entries.push(['bbox', canonicalBbox(filters.bbox)]);
  return entries;
}

/** Filters → shareable URL query (no cursor/size; `view` only when not the default, `place` only with a bbox). */
export function serializeFilters(filters: SearchFilters): URLSearchParams {
  const params = new URLSearchParams(filterEntries(filters));
  if (filters.sort) params.set('sort', filters.sort);
  if (filters.view !== 'list') params.set('view', filters.view);
  if (filters.place && filters.bbox) params.set('place', filters.place);
  return params;
}

/** Filters → API query of `/api/v2/listings/search` (and `/map`); UI-only params are left out. */
export function toApiParams(
  filters: SearchFilters,
  paging: { size?: number; cursor?: string | null } = {},
): URLSearchParams {
  const params = new URLSearchParams(filterEntries(filters));
  if (filters.sort) params.set('sort', filters.sort);
  if (paging.size) params.set('size', String(paging.size));
  if (paging.cursor) params.set('cursor', paging.cursor);
  return params;
}

/**
 * Canonical parameters (contract §7): every set filter as a string, CSV values sorted, keyword normalised, plus the
 * resolved `purpose` and `sort`; never cursor/size/view/place. Keys sorted.
 */
export function canonicalParams(filters: SearchFilters): Record<string, string> {
  const map = new Map<string, string>(filterEntries(filters));
  const keyword = normalizeKeyword(filters.q);
  if (keyword) map.set('q', keyword);
  map.set('sort', effectiveSort(filters));
  return Object.fromEntries([...map.entries()].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
}

export function canonicalJson(filters: SearchFilters): string {
  return JSON.stringify(canonicalParams(filters));
}

/** First 32 hex chars of SHA-256 over the canonical JSON — equal to the backend `SearchFilter.filterHash()`. */
export async function filterHash(filters: SearchFilters): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalJson(filters)));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, 32);
}

/** Same filters (ignoring view/place), e.g. to decide whether a URL change needs a new API request. */
export function sameQuery(a: SearchFilters, b: SearchFilters): boolean {
  return toApiParams(a).toString() === toApiParams(b).toString();
}

// ------------------------------------------------------------------------------------------------ edits

/** New purpose: price bounds are cleared (a sale price is no rent), price sorts stay meaningful. */
export function withPurpose(filters: SearchFilters, purpose: Purpose): SearchFilters {
  if (purpose === filters.purpose) return filters;
  const next = { ...filters, purpose };
  delete next.priceMin;
  delete next.priceMax;
  return next;
}

/** Removes the given API parameters (zero-result suggestions send the list to drop). */
export function withoutParams(filters: SearchFilters, drop: readonly string[]): SearchFilters {
  const next: SearchFilters = { ...filters };
  for (const param of drop) {
    switch (param) {
      case 'type':
        next.types = [];
        break;
      case 'legal':
        next.legal = [];
        break;
      case 'furnishing':
        next.furnishing = [];
        break;
      case 'district':
        next.districts = [];
        break;
      case 'q':
        delete next.q;
        if (next.sort === 'RELEVANCE') delete next.sort;
        break;
      case 'bbox':
        delete next.bbox;
        delete next.place;
        break;
      case 'priceMin':
      case 'priceMax':
      case 'areaMin':
      case 'areaMax':
      case 'bedsMin':
      case 'verified':
      case 'project':
        delete next[param];
        break;
      default:
        break;
    }
  }
  return next;
}

/** Number of active filters shown on the "Bộ lọc" button (purpose, keyword, place and sort are shown elsewhere). */
export function activeFilterCount(filters: SearchFilters): number {
  return (
    (filters.types.length ? 1 : 0) +
    (filters.priceMin != null || filters.priceMax != null ? 1 : 0) +
    (filters.areaMin != null || filters.areaMax != null ? 1 : 0) +
    (filters.bedsMin != null ? 1 : 0) +
    (filters.legal.length ? 1 : 0) +
    (filters.furnishing.length ? 1 : 0) +
    (filters.verified ? 1 : 0) +
    (filters.districts.length ? 1 : 0)
  );
}

// ------------------------------------------------------------------------------------------------ price presets

export interface PricePreset {
  label: string;
  min?: number;
  max?: number;
}

const TY = 1_000_000_000;
const TRIEU = 1_000_000;

/** Adjacent bands without overlap: "a – dưới b" includes a and excludes b (max = b − 1 đồng, bounds are inclusive). */
function bands(unit: number, unitLabel: string, edges: number[]): PricePreset[] {
  const presets: PricePreset[] = [{ label: `Dưới ${edges[0]} ${unitLabel}`, max: edges[0] * unit - 1 }];
  for (let i = 0; i < edges.length - 1; i++) {
    presets.push({
      label: `${edges[i]} – dưới ${edges[i + 1]} ${unitLabel}`,
      min: edges[i] * unit,
      max: edges[i + 1] * unit - 1,
    });
  }
  presets.push({ label: `Từ ${edges[edges.length - 1]} ${unitLabel}`, min: edges[edges.length - 1] * unit });
  return presets;
}

/**
 * Quick price bands by purpose and property type (F04.2): RENT bands are per month, SALE bands in tỷ; the segment
 * (apartment vs house/villa vs land) changes the edges. They are shortcuts only: any custom range can be typed.
 */
export function pricePresets(purpose: Purpose, types: readonly PropertyType[]): PricePreset[] {
  const only = (...allowed: PropertyType[]) => types.length > 0 && types.every((type) => allowed.includes(type));
  if (purpose === 'RENT') {
    if (only('APARTMENT')) return bands(TRIEU, 'triệu/tháng', [8, 15, 25]);
    if (only('HOUSE', 'TOWNHOUSE', 'VILLA')) return bands(TRIEU, 'triệu/tháng', [15, 30, 60]);
    return bands(TRIEU, 'triệu/tháng', [5, 10, 20, 50]);
  }
  if (only('APARTMENT')) return bands(TY, 'tỷ', [2, 4, 7]);
  if (only('VILLA', 'TOWNHOUSE')) return bands(TY, 'tỷ', [10, 20, 40]);
  return bands(TY, 'tỷ', [2, 5, 10]);
}

export function presetMatches(preset: PricePreset, filters: SearchFilters): boolean {
  return preset.min === filters.priceMin && preset.max === filters.priceMax;
}

/** Hà Nội search areas (pre-2025 district codes the listings carry; see V033 search_locations). */
export const DISTRICTS: ReadonlyArray<{ code: string; name: string }> = [
  { code: '001', name: 'Ba Đình' },
  { code: '002', name: 'Hoàn Kiếm' },
  { code: '003', name: 'Tây Hồ' },
  { code: '004', name: 'Long Biên' },
  { code: '005', name: 'Cầu Giấy' },
  { code: '006', name: 'Đống Đa' },
  { code: '007', name: 'Hai Bà Trưng' },
  { code: '008', name: 'Hoàng Mai' },
  { code: '009', name: 'Thanh Xuân' },
  { code: '016', name: 'Sóc Sơn' },
  { code: '017', name: 'Đông Anh' },
  { code: '018', name: 'Gia Lâm' },
  { code: '019', name: 'Nam Từ Liêm' },
  { code: '020', name: 'Thanh Trì' },
  { code: '021', name: 'Bắc Từ Liêm' },
  { code: '250', name: 'Mê Linh' },
  { code: '268', name: 'Hà Đông' },
  { code: '269', name: 'Sơn Tây' },
  { code: '271', name: 'Ba Vì' },
  { code: '272', name: 'Phúc Thọ' },
  { code: '273', name: 'Đan Phượng' },
  { code: '274', name: 'Hoài Đức' },
  { code: '275', name: 'Quốc Oai' },
  { code: '276', name: 'Thạch Thất' },
  { code: '277', name: 'Chương Mỹ' },
  { code: '278', name: 'Thanh Oai' },
  { code: '279', name: 'Thường Tín' },
  { code: '280', name: 'Phú Xuyên' },
  { code: '281', name: 'Ứng Hòa' },
  { code: '282', name: 'Mỹ Đức' },
];

export const SORT_LABELS: Record<SearchSort, string> = {
  NEWEST: 'Mới nhất',
  PRICE_ASC: 'Giá thấp đến cao',
  PRICE_DESC: 'Giá cao đến thấp',
  AREA_DESC: 'Diện tích lớn nhất',
  RELEVANCE: 'Phù hợp nhất',
};
