import http from 'k6/http';
import { check } from 'k6';

// S10 read-only baseline. An isolated stack is mandatory; the 10 RPS write scenario remains a separate task.
const baseUrl = __ENV.BASE_URL;
if (__ENV.BDS_PERF_ISOLATED !== '1' || !baseUrl || !/^http:\/\/(127\.0\.0\.1|localhost|host\.docker\.internal)(:\d+)?$/.test(baseUrl)) {
  throw new Error('Set BDS_PERF_ISOLATED=1 and BASE_URL to the isolated local test stack (http://localhost:<port>).');
}

export const options = {
  scenarios: {
    search_reads: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: 100,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/v2/listings/search?purpose=SALE&size=24`, {
    headers: { Accept: 'application/json' },
    tags: { endpoint: 'search-v2' },
    timeout: '10s',
  });
  check(response, {
    'search returns 200': (r) => r.status === 200,
    'search includes a result page': (r) => {
      if (r.status !== 200) return false;
      try {
        const body = r.json();
        return Array.isArray(body.items) && body.pageInfo !== undefined;
      } catch {
        return false;
      }
    },
  });
}
