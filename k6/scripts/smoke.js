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

function headers(ip) {
  return { 'Content-Type': 'application/json', 'X-Forwarded-For': ip || vuIp() };
}

export default function () {
  // 1. 단축 URL 생성
  const shortenRes = http.post(
    `${BASE_URL}/api/v1/data/shorten`,
    JSON.stringify({ originalUrl: `${API_SERVER}/anything/smoke-test` }),
    { headers: headers() }
  );

  check(shortenRes, {
    'shorten status 200': (r) => r.status === 200,
    'shortCode present': (r) => {
      try { return JSON.parse(r.body).shortCode !== undefined; }
      catch { return false; }
    },
  });

  if (shortenRes.status !== 200) {
    return;
  }

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

  // 3. 최종 응답 확인 (리다이렉트 따라가기)
  const finalRes = http.get(`${BASE_URL}/api/v1/${shortCode}`, {
    headers: headers(),
    redirects: 5,
  });

  check(finalRes, {
    'final response 200': (r) => r.status === 200,
  });

  // 4. 존재하지 않는 코드 → 404
  const notFoundRes = http.get(`${BASE_URL}/api/v1/nonexistent000`, {
    headers: headers(),
    redirects: 0,
  });

  check(notFoundRes, {
    '404 for unknown code': (r) => r.status === 404,
  });

  // 5. Rate Limiting 검증 — 같은 IP로 shorten을 capacity(10) 초과해서 요청
  //    capacity=10, refill=5/s 이므로 빠르게 11번 이상 보내면 429가 발생해야 함
  const rlIp = '192.168.99.1';
  let rateLimited = false;
  for (let i = 0; i < 15; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/data/shorten`,
      JSON.stringify({ originalUrl: `${API_SERVER}/anything/rl-${i}` }),
      { headers: headers(rlIp) }
    );
    if (res.status === 429) {
      rateLimited = true;
      break;
    }
  }

  check({ rateLimited }, {
    'rate limit 429 triggered': (s) => s.rateLimited === true,
  });

  sleep(1);
}
