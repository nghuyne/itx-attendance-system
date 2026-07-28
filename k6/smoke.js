// k6 smoke test — initializes the perf-test level (R-005 mitigation
// groundwork, framework doc "TEA Test Framework" step).
//
// P0-012 acceptance criteria (PRD §8): API SLA p95<=2s @<=200 concurrent
// (history_smoke, VUS-scalable) + check-in flow <30s (checkin_smoke, added
// per TA/Bước 4 — see automation-summary.md). Full 200-concurrent load
// validation is the separate "weekly load run" per test-design-qa.md
// Execution Strategy — raise K6_VUS for that; this script's own default
// stays a deliberately light smoke.
//
// Run: k6 run k6/smoke.js
// Target: CI docker-compose stack (interim, per test-design-architecture.md
// R-005 mitigation) — start it first with `docker compose up -d`.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.K6_USERNAME || 'employee1';
const PASSWORD = __ENV.K6_PASSWORD || 'admin123';
// Only needed to provision a shift for checkinSmoke (AttendanceService.checkIn
// 400s with NO_SHIFT_ASSIGNED otherwise — seed data leaves every employee
// shift-less by design, see V3__shifts.sql). CI's perf-smoke job already
// exports ADMIN_BOOTSTRAP_PASSWORD into this step's env, so it lines up with
// AdminUserSeeder's actual seeded password without a separate secret.
const ADMIN_USERNAME = __ENV.K6_ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = __ENV.K6_ADMIN_PASSWORD || __ENV.ADMIN_BOOTSTRAP_PASSWORD || 'admin123';
const SMOKE_SHIFT_NAME = 'k6-smoke-shift';
// Smoke default is intentionally well under the PRD's 200-concurrent
// target — this stage just proves the harness works and gets a first
// reading. Raise K6_SMOKE_VUS for the real load run once a staging target exists.
//
// Deliberately NOT named K6_VUS/K6_DURATION: those are k6's own reserved
// global-option env vars (equivalent to --vus/--duration). Setting them
// with `scenarios` already defined makes k6 hard-fail with "executor
// default: function 'default' not found in exports" instead of scaling
// the scenario — confirmed while executing the real 200-VU P0-012 run.
const VUS = Number(__ENV.K6_SMOKE_VUS || 20);
const DURATION = __ENV.K6_SMOKE_DURATION || '30s';

// Tiny valid base64 JPEG — same payload shape backend integration tests use
// (PhotoService.decodeBase64Photo just needs a decodable, <512KB body).
const FAKE_PHOTO = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2Q==';

export const options = {
  scenarios: {
    history_smoke: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'historySmoke',
    },
    // Single-shot: the check-in endpoint enforces one check-in per
    // employee per day, so this cannot run as a multi-iteration/multi-VU
    // scenario against the shared smoke account without colliding with
    // itself (or with history_smoke's login) — one measured call is
    // exactly what "check-in flow <30s" (a single-request SLA, not a
    // concurrency target) asks for.
    checkin_smoke: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      exec: 'checkinSmoke',
    },
  },
  thresholds: {
    // PRD §8: API SLA p95 <= 2s @ <= 200 concurrent.
    'http_req_duration{scenario:history_smoke}': ['p(95)<2000'],
    'http_req_failed{scenario:history_smoke}': ['rate<0.01'],
    // PRD §8: check-in flow < 30s. Generous vs. history's 2s budget on
    // purpose — check-in does synchronous IP/GPS/shift validation plus
    // decoding the photo payload before the (fire-and-forget) upload.
    'http_req_duration{scenario:checkin_smoke}': ['p(100)<30000'],
  },
};

// setup() runs once (not per-VU/iteration) — logging in per-request would
// blow through LoginRateLimitFilter's 5/min/IP budget the moment VUs > 5,
// since every VU shares this runner's IP.
export function setup() {
  const employeeToken = ensureUsablePassword(USERNAME, PASSWORD);
  ensureShiftAssigned();
  return { token: employeeToken };
}

// V16__add_must_change_password.sql force-flags every seeded emp*/leader*/
// admin account (this smoke account included) with must_change_password=TRUE.
// JwtAuthenticationFilter 403s every non-/api/auth/* request until that's
// cleared, so a fresh login's token can only be used to clear it — not to
// call any other endpoint. Change-password also stamps passwordChangedAt,
// which invalidates that same token for every subsequent request (see
// isIssuedBeforePasswordChange), so a second login is required afterward.
// Returns a token guaranteed usable against non-auth endpoints.
function ensureUsablePassword(username, password) {
  const res = login(username, password);
  if (res.status !== 200) {
    throw new Error(`k6 setup login failed for ${username}: ${res.status} ${res.body}`);
  }
  const body = res.json();
  if (!body.user.mustChangePassword) {
    return body.accessToken;
  }
  const changeRes = http.post(
    `${BASE_URL}/api/auth/change-password`,
    JSON.stringify({ oldPassword: password, newPassword: password }),
    { headers: { Authorization: `Bearer ${body.accessToken}`, 'Content-Type': 'application/json' } }
  );
  if (changeRes.status !== 200) {
    throw new Error(`k6 setup change-password failed for ${username}: ${changeRes.status} ${changeRes.body}`);
  }
  const relogin = login(username, password);
  if (relogin.status !== 200) {
    throw new Error(`k6 setup re-login failed for ${username}: ${relogin.status} ${relogin.body}`);
  }
  return relogin.json('accessToken');
}

function login(username, password) {
  return http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
}

// checkinSmoke needs employeeToken's account to have a shift (seed data
// leaves every employee shift-less — see V3__shifts.sql) with a check-in
// window open right now, whatever time of day CI happens to run. startTime
// 00:00 with checkInOpenMinutes 0 makes checkInOpensAt == 00:00, which is
// never after any valid time-of-day, so the window is always open.
// Idempotent: reuses SMOKE_SHIFT_NAME across runs instead of creating a
// duplicate (shifts.name is unique) and re-assigning is a harmless no-op
// PUT if this account already has it.
function ensureShiftAssigned() {
  const adminToken = ensureUsablePassword(ADMIN_USERNAME, ADMIN_PASSWORD);

  const employeesRes = http.get(`${BASE_URL}/api/admin/employees`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (employeesRes.status !== 200) {
    throw new Error(`k6 setup could not list employees: ${employeesRes.status} ${employeesRes.body}`);
  }
  const employee = (employeesRes.json() || []).find(e => e.username === USERNAME);
  if (!employee) {
    throw new Error(`k6 setup could not find employee ${USERNAME} via /api/admin/employees`);
  }
  const employeeId = employee.id;

  const listRes = http.get(`${BASE_URL}/api/admin/shifts?page=0&size=100`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (listRes.status !== 200) {
    throw new Error(`k6 setup could not list shifts: ${listRes.status} ${listRes.body}`);
  }
  const existing = (listRes.json('content') || []).find(s => s.name === SMOKE_SHIFT_NAME);

  let shiftId = existing && existing.id;
  if (!shiftId) {
    const createRes = http.post(
      `${BASE_URL}/api/admin/shifts`,
      JSON.stringify({
        name: SMOKE_SHIFT_NAME,
        startTime: '00:00',
        endTime: '23:59',
        checkInOpenMinutes: 0,
        lateInThreshold: 15,
        earlyOutThreshold: 15,
        halfDayThreshold: 120,
        otBuffer: 30,
      }),
      { headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' } }
    );
    if (createRes.status !== 201) {
      throw new Error(`k6 setup could not create shift: ${createRes.status} ${createRes.body}`);
    }
    shiftId = createRes.json('id');
  }

  const assignRes = http.put(
    `${BASE_URL}/api/admin/shifts/${shiftId}/assign/${employeeId}`,
    null,
    { headers: { Authorization: `Bearer ${adminToken}` } }
  );
  if (assignRes.status !== 200) {
    throw new Error(`k6 setup could not assign shift: ${assignRes.status} ${assignRes.body}`);
  }
}

export function historySmoke(data) {
  const today = new Date().toISOString().slice(0, 10);
  const res = http.get(
    `${BASE_URL}/api/attendance/history?from=${today}&to=${today}&page=0&size=20`,
    { headers: { Authorization: `Bearer ${data.token}` } }
  );
  check(res, { 'status is 200': r => r.status === 200 });
}

export function checkinSmoke(data) {
  const res = http.post(
    `${BASE_URL}/api/attendance/check-in`,
    JSON.stringify({ lat: null, lng: null, photoBase64: FAKE_PHOTO, isClientSite: false, bssid: null }),
    { headers: { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' } }
  );
  // 201 on a fresh stack; 409 ALREADY_CHECKED_IN if this account already
  // checked in today (e.g. script re-run against a non-fresh environment).
  // Either way the server did the full validation+work the SLA measures —
  // only an unexpected status (auth/5xx/etc.) fails the check.
  check(res, { 'status is 201 or 409': r => r.status === 201 || r.status === 409 });
}
