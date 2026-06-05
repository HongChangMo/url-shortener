import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '3m', target: 200 },
    { duration: '2m', target: 300 },
    { duration: '2m', target: 200 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
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

export function setup() {
  const codes = [];
  for (let i = 0; i < 20; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/data/shorten`,
      JSON.stringify({ originalUrl: `${API_SERVER}/anything/stress-${i}` }),
      { headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '10.0.0.0' } }
    );
    if (res.status === 200) {
      try {
        codes.push(JSON.parse(res.body).shortCode);
      } catch { /* skip */ }
    }
  }

  if (codes.length === 0) {
    throw new Error('setup() 실패: shortCode를 하나도 생성하지 못했습니다. 서버 상태를 확인하세요.');
  }

  return { codes };
}

export default function ({ codes }) {
  if (!codes || codes.length === 0) {
    return;
  }

  if (Math.random() < 0.9) {
    const code = codes[Math.floor(Math.random() * codes.length)];
    const res = http.get(`${BASE_URL}/api/v1/${code}`, {
      headers: headers(),
      redirects: 0,
    });
    check(res, {
      'redirect 302': (r) => r.status === 302,
      'location header exists': (r) => r.headers['Location'] !== undefined,
    });
  } else {
    const res = http.post(
      `${BASE_URL}/api/v1/data/shorten`,
      JSON.stringify({ originalUrl: `${API_SERVER}/anything/stress-${Date.now()}` }),
      { headers: headers() }
    );
    check(res, { 'shorten 200': (r) => r.status === 200 });
  }

  sleep(0.1);
}
