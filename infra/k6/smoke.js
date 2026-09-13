import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: { search: { executor: 'constant-vus', vus: 10, duration: '30s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
export default function () {
  const response = http.get(`${baseUrl}/api/v1/listings/search?page=0&size=20`);
  check(response, { 'search 200': (r) => r.status === 200 });
  sleep(1);
}

