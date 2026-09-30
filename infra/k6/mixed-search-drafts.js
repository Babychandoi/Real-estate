import http from 'k6/http';
import { check } from 'k6';
import execution from 'k6/execution';
import { Counter } from 'k6/metrics';

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
if (!['steady', 'burst', 'soak'].includes(profile)) throw new Error('PROFILE must be steady, burst or soak.');
const rates = (read) => ({
  executor: profile === 'burst' ? 'ramping-arrival-rate' : 'constant-arrival-rate',
  ...(profile === 'burst' ? {
    startRate: read ? 100 : 10,
    stages: [
      { duration: '1m', target: read ? 100 : 10 },
      { duration: '30s', target: read ? 200 : 20 },
      { duration: '1m', target: read ? 200 : 20 },
      { duration: '30s', target: read ? 100 : 10 },
    ],
  } : { rate: read ? 100 : 10, duration: __ENV.DURATION || (profile === 'soak' ? '30m' : '5m') }),
  timeUnit: '1s',
  preAllocatedVUs: read ? 100 : 20,
  maxVUs: read ? 300 : 60,
});
const draftsCreated = new Counter('drafts_created');
const degradedReads = new Counter('degraded_reads');
export const options = {
  scenarios: {
    reads: { ...rates(true), exec: 'readSearch' },
    writes: { ...rates(false), exec: 'writeDraft' },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{scenario:reads}': ['rate<0.01'],
    'http_req_failed{scenario:writes}': ['rate<0.01'],
    'http_req_duration{scenario:reads}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{scenario:writes}': ['p(95)<1000', 'p(99)<2000'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count==0'],
    drafts_created: ['count>0'],
  },
};
export function readSearch() {
  const response = http.get(`${baseUrl}/api/v2/listings/search?purpose=SALE&size=24`, {
    tags: { endpoint: 'search-v2' }, timeout: '10s',
  });
  let body;
  try { body = response.json(); } catch { body = null; }
  if (body?.degraded) degradedReads.add(1);
  check(response, {
    'read succeeds with a v2 page': (r) => r.status === 200 && Array.isArray(body?.items) && !!body?.pageInfo,
    'expected search engine state': () => __ENV.EXPECT_DEGRADED === '1' ? body?.degraded === true : body?.degraded === false,
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
// No tokens or response bodies are written to summaries. Verify persisted draft count separately by title marker.

// The modern summary object preserves metric types and nested values for the evidence parser.
export function handleSummary(data) {
  return {
    [__ENV.PERF_SUMMARY_PATH || 'summary.json']: JSON.stringify(data),
    stdout: `${JSON.stringify(data.metrics, null, 2)}\n`,
  };
}
