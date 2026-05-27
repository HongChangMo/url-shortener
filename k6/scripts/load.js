import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://shortener:8080';

export function setup() {
  const codes = [];
  for (let i = 0; i < 10; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/data/shorten`,
      JSON.stringify({ originalUrl: `https://example.com/preload-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    codes.push(JSON.parse(res.body).shortCode);
  }
  return { codes };
}

export default function ({ codes }) {
  // 80% read (redirect), 20% write (shorten)
  if (Math.random() < 0.8) {
    const code = codes[Math.floor(Math.random() * codes.length)];
    const res = http.get(`${BASE_URL}/api/v1/${code}`, { redirects: 0 });
    check(res, { 'redirect 302': (r) => r.status === 302 });
  } else {
    const res = http.post(
      `${BASE_URL}/api/v1/data/shorten`,
      JSON.stringify({ originalUrl: `https://example.com/load-${Date.now()}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { 'shorten 200': (r) => r.status === 200 });
  }

  sleep(0.5);
}
