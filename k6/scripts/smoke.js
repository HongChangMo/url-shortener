import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_SERVER = __ENV.API_SERVER || 'http://localhost:9090';

function vuIp() {
  return `10.0.${Math.floor(__VU / 256)}.${__VU % 256}`;
}

function headers() {
  return { 'Content-Type': 'application/json', 'X-Forwarded-For': vuIp() };
}

export default function () {
  // 1. 단축 URL 생성 (api-server 엔드포인트를 대상으로)
  const shortenRes = http.post(
    `${BASE_URL}/api/v1/data/shorten`,
    JSON.stringify({ originalUrl: `${API_SERVER}/anything/smoke-test` }),
    { headers: headers() }
  );

  check(shortenRes, {
    'shorten status 200': (r) => r.status === 200,
    'shortCode present': (r) => JSON.parse(r.body).shortCode !== undefined,
  });

  const shortCode = JSON.parse(shortenRes.body).shortCode;

  // 2. 302 redirect 확인
  const redirectRes = http.get(`${BASE_URL}/api/v1/${shortCode}`, {
    headers: headers(),
    redirects: 0,
  });

  check(redirectRes, {
    'redirect status 302': (r) => r.status === 302,
    'location header exists': (r) => r.headers['Location'] !== undefined,
  });

  // 3. 실제 api-server 최종 응답 확인 (리다이렉트 따라가기)
  const finalRes = http.get(`${BASE_URL}/api/v1/${shortCode}`, {
    headers: headers(),
    redirects: 5,
  });

  check(finalRes, {
    'final response 200': (r) => r.status === 200,
  });

  sleep(1);
}
