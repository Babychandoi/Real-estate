import http from 'k6/http';
import { check, sleep } from 'k6';

const profile = __ENV.PROFILE || 'load';
const profiles = {
  smoke: [{ duration: '30s', target: 5 }],
  load: [{ duration: '2m', target: 25 }, { duration: '5m', target: 25 }, { duration: '1m', target: 0 }],
  soak: [{ duration: '5m', target: 20 }, { duration: __ENV.SOAK_DURATION || '2h', target: 20 }, { duration: '5m', target: 0 }],
};

export const options = {
  stages: profiles[profile] || profiles.load,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:3000').replace(/\/$/, '');

export default function () {
  const response = http.get(`${baseUrl}/api/v1/listings/search?page=0&size=20`, {
    headers: { Accept: 'application/json' },
    tags: { endpoint: 'listing-search' },
  });
  check(response, {
    'search returns 200': (r) => r.status === 200,
    'search response is JSON': (r) => (r.headers['Content-Type'] || '').includes('application/json'),
  });
  sleep(Number(__ENV.THINK_TIME_SECONDS || 1));
}
