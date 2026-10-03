import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

// Run only against a disposable local stack with dedicated KYC-verified actors.
const baseUrl = __ENV.BASE_URL;
if (__ENV.BDS_PERF_ISOLATED !== '1' || !/^http:\/\/(localhost|127\.0\.0\.1|host\.docker\.internal)(:\d+)?$/.test(baseUrl || '')) {
  throw new Error('An explicitly isolated, local HTTP stack is required.');
}
const runId = __ENV.PERF_RUN_ID || '';
if (!/^[a-z]{4,32}$/.test(runId)) throw new Error('PERF_RUN_ID must be a unique 4–32 lowercase-letter marker.');
const tokens = JSON.parse(__ENV.PERF_ACTOR_TOKENS_JSON || '[]');
if (!Array.isArray(tokens) || !tokens.length || tokens.some((token) => typeof token !== 'string' || !token.trim())) {
  throw new Error('Supply dedicated verified actor bearer tokens in PERF_ACTOR_TOKENS_JSON.');
}
const profile = __ENV.PROFILE || 'steady';
if (!['steady', 'burst', 'soak', 'transition'].includes(profile)) {
  throw new Error('PROFILE must be steady, burst, soak or transition.');
}
// first-page: the original diagnostic (every read is the warmed SALE first page). realistic: the mix below over the
// seeded dataset (LISTINGS public listings perf-1..perf-N, OWNERS sellers; infra/perf/seed-listings.sql).
const readMix = __ENV.READ_MIX || 'first-page';
if (!['first-page', 'realistic'].includes(readMix)) throw new Error('READ_MIX must be first-page or realistic.');
const listings = Number(__ENV.LISTINGS || 0);
const owners = Number(__ENV.OWNERS || 0);
if (readMix === 'realistic' && !(listings > 0 && owners > 0)) throw new Error('realistic READ_MIX needs LISTINGS and OWNERS.');
// 0: every search read must be served by Elasticsearch; 1: every one degraded (database engine); any: not checked
// (the breaker-opening transition, measured separately and reported without a pass/fail gate).
const expectDegraded = __ENV.EXPECT_DEGRADED || '0';
if (!['0', '1', 'any'].includes(expectDegraded)) throw new Error('EXPECT_DEGRADED must be 0, 1 or any.');
const publishEvery = Number(__ENV.PUBLISH_EVERY_S || 0);
const moderatorToken = __ENV.PERF_MODERATOR_TOKEN || '';
if (publishEvery > 0 && !moderatorToken.trim()) throw new Error('Publishing needs PERF_MODERATOR_TOKEN.');

const BURST_FACTOR = 3;
const rates = (read) => {
  const base = read ? 100 : 10;
  const shared = { timeUnit: '1s', preAllocatedVUs: read ? 150 : 20, maxVUs: read ? 600 : 100 };
  if (profile === 'burst') {
    // 3x the steady arrival rate held for 60 s, with 10 s ramps and 20 s back at the steady rate.
    return {
      executor: 'ramping-arrival-rate', startRate: base, ...shared,
      stages: [
        { duration: '10s', target: base * BURST_FACTOR },
        { duration: '60s', target: base * BURST_FACTOR },
        { duration: '10s', target: base },
        { duration: '20s', target: base },
      ],
    };
  }
  const fallback = { soak: '10m', transition: '20s' }[profile] || '5m';
  return { executor: 'constant-arrival-rate', rate: base, duration: __ENV.DURATION || fallback, ...shared };
};
const scenarioDuration = profile === 'burst' ? '100s' : rates(true).duration;

const draftsCreated = new Counter('drafts_created');
const degradedReads = new Counter('degraded_reads');
const readAttempts = new Counter('read_attempts');
const searchReads = new Counter('search_reads');
const publications = new Counter('publications');
const publicationLag = new Trend('publication_lag_ms', true);
const publicationVisible = new Rate('publication_visible');
const publishStep = new Trend('publish_step_ms', true);
const publicationDegradedPolls = new Counter('publication_degraded_polls');

const ENDPOINTS = ['search-first', 'search-filtered', 'search-page2-first', 'search-page2', 'search-keyword', 'map', 'detail',
  'seller', 'create-draft'];
const scenarios = {
  reads: { ...rates(true), exec: 'readMixed' },
  writes: { ...rates(false), exec: 'writeDraft' },
};
const thresholds = {
  'http_req_failed{scenario:reads}': ['rate<0.01'],
  'http_req_failed{scenario:writes}': ['rate<0.01'],
  'http_req_duration{scenario:reads}': ['p(95)<500', 'p(99)<1000'],
  'http_req_duration{scenario:writes}': ['p(95)<1000', 'p(99)<2000'],
  'checks{scenario:reads}': ['rate>0.99'],
  'checks{scenario:writes}': ['rate>0.99'],
  dropped_iterations: ['count==0'],
  drafts_created: ['count>0'],
  read_attempts: ['count>0'],
  degraded_reads: ['count>=0'],
  search_reads: ['count>=0'],
};
// Reporting only (always true): makes k6 keep per-endpoint latency/error sub-metrics in the summary.
for (const endpoint of ENDPOINTS) {
  thresholds[`http_req_duration{endpoint:${endpoint}}`] = ['max>=0'];
  thresholds[`http_req_failed{endpoint:${endpoint}}`] = ['rate>=0'];
}
if (publishEvery > 0) {
  scenarios.publish = {
    executor: 'constant-arrival-rate', rate: 1, timeUnit: `${publishEvery}s`, duration: scenarioDuration,
    preAllocatedVUs: 10, maxVUs: 60, exec: 'publishListing',
  };
  // F05.5: approval -> visible in search, p95 <= 10 s under the load above; every approved listing must appear.
  thresholds.publication_lag_ms = ['p(95)<10000'];
  thresholds.publication_visible = ['rate==1'];
  thresholds['http_req_failed{scenario:publish}'] = ['rate<0.01'];
  thresholds.publications = ['count>0'];
  thresholds.publication_degraded_polls = ['count==0'];
  for (const step of ['create', 'submit', 'approve', 'poll']) thresholds[`publish_step_ms{step:${step}}`] = ['max>=0'];
}
export const options = { scenarios, summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'], thresholds };

// Hà Nội search areas by popularity, as in seed-listings.sql, with approximate centres.
const DISTRICTS = [
  ['005', 105.7900, 21.0310], ['019', 105.7650, 21.0130], ['268', 105.7750, 20.9600], ['006', 105.8270, 21.0130],
  ['009', 105.8100, 20.9940], ['008', 105.8600, 20.9750], ['004', 105.8950, 21.0480], ['021', 105.7650, 21.0700],
  ['001', 105.8195, 21.0358], ['007', 105.8570, 21.0060], ['003', 105.8180, 21.0700], ['002', 105.8522, 21.0287],
  ['017', 105.8480, 21.1370], ['018', 105.9400, 21.0280], ['020', 105.8450, 20.9400], ['274', 105.7000, 21.0300],
  ['250', 105.7200, 21.1800], ['016', 105.8480, 21.2570], ['273', 105.6700, 21.0900], ['277', 105.6600, 20.9000],
  ['278', 105.7700, 20.8600], ['279', 105.8600, 20.8700], ['276', 105.5600, 21.0300], ['275', 105.6400, 20.9900],
  ['272', 105.5700, 21.1100], ['269', 105.5050, 21.1380], ['280', 105.9000, 20.7400], ['281', 105.7800, 20.7200],
  ['282', 105.7300, 20.6800], ['271', 105.4200, 21.2000],
];
const KEYWORDS = ['can ho', 'nha pho', 'biet thu', 'dat nen', 'can ho cau giay', 'nha rieng ha dong', 'chung cu tran duy hung',
  'nha pho ngo o to', 'dat nen dong anh', 'can ho full noi that', 'cho thue can ho', 'so hong chinh chu', 'landmarkrare'];
const PLACES = ['cau giay', 'nam tu liem', 'ha dong', 'dong da', 'thanh xuan', 'hoang mai', 'long bien', 'bac tu liem',
  'ba dinh', 'hai ba trung', 'tay ho', 'hoan kiem', 'dong anh', 'gia lam', 'thanh tri', 'tran duy hung', 'nguyen trai'];
const TYPES = ['APARTMENT', 'HOUSE', 'TOWNHOUSE', 'LAND', 'VILLA'];
const SORTS = ['NEWEST', 'PRICE_ASC', 'PRICE_DESC', 'AREA_DESC'];
const pick = (items) => items[Math.floor(Math.random() * items.length)];
const district = () => DISTRICTS[Math.min(29, Math.floor(30 * Math.pow(Math.random(), 2.2)))];
const purpose = () => (Math.random() < 0.72 ? 'SALE' : 'RENT');
const query = (params) => Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
const ownerId = (index) => {
  const h = crypto.md5(`perf-owner:${index}`, 'hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
};

// Wide key cardinality on purpose: continuous price/area ranges, district pairs, keyword + place combinations and
// viewports panned anywhere around a district centre, so caches only help where real traffic would repeat itself.
function filtered() {
  const p = { purpose: purpose(), size: 24 };
  if (Math.random() < 0.7) p.district = Math.random() < 0.2 ? `${district()[0]},${district()[0]}` : district()[0];
  if (Math.random() < 0.5) p.type = pick(TYPES);
  if (Math.random() < 0.4) {
    const low = p.purpose === 'SALE' ? 100000000 * (8 + Math.floor(Math.random() * 72)) : 500000 * (6 + Math.floor(Math.random() * 60));
    p.priceMin = low;
    p.priceMax = Math.round(low * (1.5 + Math.random() * 1.5));
  }
  if (Math.random() < 0.2) { p.areaMin = 30 + 5 * Math.floor(Math.random() * 19); p.areaMax = p.areaMin + 20 + 5 * Math.floor(Math.random() * 17); }
  if (Math.random() < 0.2) p.bedsMin = 1 + Math.floor(Math.random() * 4);
  if (Math.random() < 0.6) p.sort = pick(SORTS);
  return p;
}

function request() {
  if (readMix === 'first-page') return { endpoint: 'search-first', url: '/api/v2/listings/search?purpose=SALE&size=24' };
  const r = Math.random();
  if (r < 0.25) return { endpoint: 'search-first', url: `/api/v2/listings/search?${query({ purpose: purpose(), size: 24 })}` };
  if (r < 0.45) return { endpoint: 'search-filtered', url: `/api/v2/listings/search?${query(filtered())}` };
  if (r < 0.50) return { endpoint: 'search-page2-first', url: `/api/v2/listings/search?${query(filtered())}`, next: true };
  if (r < 0.60) {
    const p = { q: Math.random() < 0.6 ? `${pick(KEYWORDS)} ${pick(PLACES)}` : pick(KEYWORDS), size: 24 };
    if (Math.random() < 0.5) p.purpose = purpose();
    return { endpoint: 'search-keyword', url: `/api/v2/listings/search?${query(p)}` };
  }
  if (r < 0.70) {
    const [, lng, lat] = district();
    const zoom = pick([11, 12, 13, 14, 15]);
    const half = { 11: [0.10, 0.075], 12: [0.05, 0.035], 13: [0.02, 0.0125], 14: [0.01, 0.0065], 15: [0.005, 0.003] }[zoom];
    const cx = lng + (Math.random() - 0.5) * 4 * half[0];
    const cy = lat + (Math.random() - 0.5) * 4 * half[1];
    const bbox = [cx - half[0], cy - half[1], cx + half[0], cy + half[1]].map((v) => v.toFixed(5)).join(',');
    return { endpoint: 'map', url: `/api/v2/listings/map?${query({ purpose: purpose(), zoom, bbox })}` };
  }
  if (r < 0.95) return { endpoint: 'detail', url: `/api/v2/listings/perf-${1 + Math.floor(Math.random() * listings)}` };
  const owner = Math.floor(owners * Math.pow(Math.random(), 2));
  return { endpoint: 'seller', url: `/api/v2/public/sellers/${ownerId(owner)}/listings?size=24` };
}

function searchChecks(response, body, endpoint) {
  searchReads.add(1);
  degradedReads.add(body?.degraded === true ? 1 : 0);
  check(response, {
    'read answers 200 with the expected shape': (r) => r.status === 200 && Array.isArray(body?.items) && !!body?.pageInfo,
    'expected search engine state': () => expectDegraded === 'any'
      || (expectDegraded === '1' ? body?.degraded === true : body?.degraded === false),
  }, { endpoint });
}

export function readMixed() {
  readAttempts.add(1);
  const { endpoint, url, next } = request();
  const response = http.get(`${baseUrl}${url}`, { tags: { endpoint }, timeout: '10s' });
  let body;
  try { body = response.json(); } catch { body = null; }
  const isSearch = endpoint.startsWith('search');
  if (isSearch) {
    searchChecks(response, body, endpoint);
    // Second page through the cursor of the first (keyset paging on the engine that issued the cursor).
    const cursor = body?.pageInfo?.nextCursor;
    if (next && cursor) {
      readAttempts.add(1);
      const second = http.get(`${baseUrl}${url}&cursor=${encodeURIComponent(cursor)}`,
        { tags: { endpoint: 'search-page2' }, timeout: '10s' });
      let secondBody;
      try { secondBody = second.json(); } catch { secondBody = null; }
      searchChecks(second, secondBody, 'search-page2');
    }
    return;
  }
  check(response, {
    'read answers 200 with the expected shape': (r) => r.status === 200 && (
      endpoint === 'map' ? typeof body?.mode === 'string'
        : endpoint === 'detail' ? !!body?.id
          : Array.isArray(body?.items)),
  });
}

export function writeDraft() {
  const iteration = execution.scenario.iterationInTest;
  const title = `Perf draft ${runId} ${iteration.toString(36)}`;
  const response = http.post(`${baseUrl}/api/v1/listings`, JSON.stringify({
    title, purpose: 'SALE', propertyType: 'APARTMENT', priceVnd: 2500000000,
    areaM2: 60, description: 'Isolated performance fixture', imageUrls: [],
  }), {
    headers: { Authorization: `Bearer ${tokens[iteration % tokens.length]}`, 'Content-Type': 'application/json' },
    tags: { endpoint: 'create-draft' }, timeout: '10s',
  });
  let body;
  try { body = response.json(); } catch { body = null; }
  const success = check(response, {
    'draft persisted': (r) => r.status === 201 && body?.status === 'DRAFT' && !!body?.listingId && !!body?.revisionId,
  });
  if (success) draftsCreated.add(1);
}

const letters = (n) => {
  let out = '';
  let value = n;
  do { out = String.fromCharCode(97 + (value % 26)) + out; value = Math.floor(value / 26); } while (value > 0);
  return out;
};

// Broker creates and submits a listing, a moderator approves it; the lag is the time from the approval response until a
// search request that bypasses the first-page cache (bbox + unique keyword) returns it from Elasticsearch.
export function publishListing() {
  const iteration = execution.scenario.iterationInTest;
  const marker = `pub${runId}${letters(iteration)}`;
  const broker = { Authorization: `Bearer ${tokens[iteration % tokens.length]}`, 'Content-Type': 'application/json' };
  const moderator = { Authorization: `Bearer ${moderatorToken}`, 'Content-Type': 'application/json' };
  const created = http.post(`${baseUrl}/api/v1/listings`, JSON.stringify({
    title: `Bán căn hộ ${marker} kiểm thử tải`, purpose: 'SALE', propertyType: 'APARTMENT', priceVnd: 3200000000,
    areaM2: 72, bedrooms: 2, bathrooms: 2, description: 'Căn hộ kiểm thử xuất bản trong bài đo tải, không có thật.',
    provinceCode: '01', districtCode: '005', addressSummary: 'Trần Duy Hưng, Cầu Giấy, Hà Nội',
    publicLatitude: 21.0100, publicLongitude: 105.8000, legalStatusCode: 'PINK_BOOK', imageUrls: [],
  }), { headers: broker, tags: { endpoint: 'publish-create' }, timeout: '10s' });
  publishStep.add(created.timings.duration, { step: 'create' });
  let draft;
  try { draft = created.json(); } catch { draft = null; }
  if (!check(created, { 'publish: draft created': (r) => r.status === 201 && !!draft?.revisionId })) {
    publicationVisible.add(false);
    return;
  }
  const submitted = http.post(`${baseUrl}/api/v1/listings/${draft.listingId}/submit`, null,
    { headers: broker, tags: { endpoint: 'publish-submit' }, timeout: '10s' });
  publishStep.add(submitted.timings.duration, { step: 'submit' });
  if (!check(submitted, { 'publish: submitted': (r) => r.status === 200 })) {
    publicationVisible.add(false);
    return;
  }
  const approved = http.post(`${baseUrl}/api/v1/moderation/listings/${draft.listingId}/approve`,
    JSON.stringify({ revisionId: draft.revisionId, reasonCode: 'MEETS_STANDARDS' }),
    { headers: moderator, tags: { endpoint: 'publish-approve' }, timeout: '10s' });
  publishStep.add(approved.timings.duration, { step: 'approve' });
  if (!check(approved, { 'publish: approved': (r) => r.status === 200 })) {
    publicationVisible.add(false);
    return;
  }
  publications.add(1);
  const approvedAt = Date.now();
  const url = `${baseUrl}/api/v2/listings/search?${query({ q: marker, bbox: '105.79000,21.00000,105.81000,21.02000', size: 5 })}`;
  let visible = false;
  while (!visible && Date.now() - approvedAt < 30000) {
    const poll = http.get(url, { tags: { endpoint: 'publish-poll' }, timeout: '10s' });
    publishStep.add(poll.timings.duration, { step: 'poll' });
    let body;
    try { body = poll.json(); } catch { body = null; }
    // Only an Elasticsearch answer counts: a degraded (database) answer would show the listing at once and hide the
    // index lag that F05.5 is about.
    if (body?.degraded === true) publicationDegradedPolls.add(1);
    visible = poll.status === 200 && body?.degraded === false && Array.isArray(body?.items)
      && body.items.some((item) => item.id === draft.listingId);
    if (!visible) sleep(0.2);
  }
  publicationLag.add(Date.now() - approvedAt);
  publicationVisible.add(visible);
}

// No tokens or response bodies are written to summaries. Verify persisted draft count separately by title marker.
// The modern summary object preserves metric types and nested values for the evidence parser.
export function handleSummary(data) {
  return {
    [__ENV.PERF_SUMMARY_PATH || 'summary.json']: JSON.stringify(data),
    stdout: `${JSON.stringify(data.metrics, null, 2)}\n`,
  };
}
