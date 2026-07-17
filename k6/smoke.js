// k6 smoke test — initializes the perf-test level (R-005 mitigation
// groundwork, framework doc "TEA Test Framework" step). Full P0-012
// (check-in flow <30s, 200-concurrent target per PRD §8) is Bước 3/4 work;
// this script only proves the tool is wired up and gives a first p95 signal
// against a real endpoint (GET /api/attendance/history).
//
// Run: k6 run k6/smoke.js
// Target: CI docker-compose stack (interim, per test-design-architecture.md
// R-005 mitigation) — start it first with `docker compose up -d`.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.K6_USERNAME || 'employee1';
const PASSWORD = __ENV.K6_PASSWORD || 'admin123';
// Smoke default is intentionally well under the PRD's 200-concurrent
// target — this stage just proves the harness works and gets a first
// reading. Raise K6_VUS for the real load run once a staging target exists.
const VUS = Number(__ENV.K6_VUS || 20);
const DURATION = __ENV.K6_DURATION || '30s';

export const options = {
  scenarios: {
    history_smoke: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    // PRD §8: API SLA p95 <= 2s @ <= 200 concurrent.
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

// setup() runs once (not per-VU/iteration) — logging in per-request would
// blow through LoginRateLimitFilter's 5/min/IP budget the moment VUs > 5,
// since every VU shares this runner's IP.
export function setup() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    throw new Error(`k6 setup login failed: ${res.status} ${res.body}`);
  }
  return { token: res.json('accessToken') };
}

export default function (data) {
  const today = new Date().toISOString().slice(0, 10);
  const res = http.get(
    `${BASE_URL}/api/attendance/history?from=${today}&to=${today}&page=0&size=20`,
    { headers: { Authorization: `Bearer ${data.token}` } }
  );
  check(res, { 'status is 200': r => r.status === 200 });
}
