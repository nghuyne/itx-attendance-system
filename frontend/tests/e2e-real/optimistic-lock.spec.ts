import { test, expect } from '@playwright/test';
import { SEEDED_USERS, loginAs, authHeaders } from './support/api';

// Exercises the @Version optimistic lock on AttendanceRecord (backend/.../
// AdminOverrideService.java, caught as OptimisticLockingFailureException and
// mapped to 409 CONCURRENT_MODIFICATION). Nothing — not a backend integration
// test, not a Playwright spec — previously fired two real concurrent writes at
// this path, so the conflict handling was unverified.
//
// AttendanceService.checkIn() only rejects check-in *before* the shift's open
// window; there's no upper bound, so a 00:00-23:59 shift keeps this test valid
// at any time of day. Weekend/holiday check-in requires a pre-approved OT
// request (out of scope here), so the test skips on weekends.

const TINY_JPEG_BASE64 =
  'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=';

// Attendance dates are Asia/Ho_Chi_Minh-relative (see backend TimeUtil.UTC_PLUS_7); computing
// "today" via the test runner's local/UTC clock can land on the wrong calendar day when CI
// runs in UTC during the 17:00-23:59 UTC window (VN's next calendar day has already started).
const vnToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
const vnWeekday = () => new Date(`${vnToday()}T00:00:00Z`).getUTCDay();

test.describe('Real API — Optimistic locking on admin attendance override', () => {
  test.skip(
    [0, 6].includes(vnWeekday()),
    'Weekend check-in requires a pre-approved OT request; not set up by this fixture.'
  );

  test('two concurrent overrides: one succeeds, the other gets a real 409 CONCURRENT_MODIFICATION', async ({ request }) => {
    const bootstrapPassword = process.env.ADMIN_BOOTSTRAP_PASSWORD;
    if (!bootstrapPassword) {
      throw new Error('ADMIN_BOOTSTRAP_PASSWORD must be set for real-API tests (see docker-compose.yml / CI env).');
    }
    const adminToken = await loginAs(request, { username: 'admin', password: bootstrapPassword });
    const adminAuth = authHeaders(adminToken);

    // Fixed (not Date.now()-suffixed) fixture name: reuse the same Shift row across
    // runs instead of creating a new orphan every time, mirroring the 409-tolerant
    // valid-ip fixture below and the today's-record reuse further down.
    const FIXTURE_SHIFT_NAME = 'Real-API Optimistic Lock Fixture';
    const existingShiftsRes = await request.get('/api/admin/shifts', { headers: adminAuth, params: { size: 100 } });
    expect(existingShiftsRes.status(), await existingShiftsRes.text()).toBe(200);
    const existingShift = (await existingShiftsRes.json()).content.find((s: { name: string }) => s.name === FIXTURE_SHIFT_NAME);

    let shiftId: string;
    if (existingShift) {
      shiftId = existingShift.id;
    } else {
      const shiftRes = await request.post('/api/admin/shifts', {
        headers: adminAuth,
        data: {
          name: FIXTURE_SHIFT_NAME,
          startTime: '00:00',
          endTime: '23:59',
          checkInOpenMinutes: 0,
          lateInThreshold: 999,
          earlyOutThreshold: 0,
          halfDayThreshold: 999,
          otBuffer: 30,
        },
      });
      expect(shiftRes.status(), await shiftRes.text()).toBe(201);
      shiftId = (await shiftRes.json()).id;
    }

    const employeeToken = await loginAs(request, SEEDED_USERS.employee5);

    // Capture emp-005's pre-test shift so it can be restored in `finally` — there's
    // no unassign endpoint, and Shift.delete() 409s (SHIFT_IN_USE) while assigned,
    // so the fixture shift can't be torn down until emp-005 is moved back off it.
    const employeesRes = await request.get('/api/admin/employees/details', { headers: adminAuth });
    expect(employeesRes.status(), await employeesRes.text()).toBe(200);
    const originalShiftId: string | null =
      (await employeesRes.json()).find((e: { id: string; shiftId: string | null }) => e.id === 'emp-005')?.shiftId ?? null;

    const assignRes = await request.put(`/api/admin/shifts/${shiftId}/assign/emp-005`, { headers: adminAuth });
    expect(assignRes.status(), await assignRes.text()).toBe(200);

    try {
      const ipRes = await request.post('/api/admin/valid-ips', {
        headers: adminAuth,
        data: { ipAddress: '127.0.0.1', scope: 'COMPANY', employeeId: null, description: 'Real-API test fixture' },
      });
      // A prior run against this same (non-fresh) DB may have already registered
      // 127.0.0.1 as a company IP; that's fine, it's the same fixture either way.
      if (ipRes.status() !== 409) {
        expect(ipRes.status(), await ipRes.text()).toBe(201);
      }

      // Reuse today's record if a retried run already created one for employee5.
      // There's no admin endpoint to hard-delete an attendance record (it's an
      // audit-trail entity by design, unlike Shift/valid-ip below), so reuse —
      // not teardown — is the correct isolation strategy for this piece of state.
      const todayRes = await request.get('/api/attendance/today', { headers: authHeaders(employeeToken) });
      let recordId: string;
      if (todayRes.status() === 200) {
        recordId = (await todayRes.json()).id;
      } else {
        const checkInRes = await request.post('/api/attendance/check-in', {
          headers: authHeaders(employeeToken),
          data: { lat: null, lng: null, photoBase64: TINY_JPEG_BASE64, isClientSite: false, bssid: null },
        });
        expect(checkInRes.status(), await checkInRes.text()).toBe(201);
        recordId = (await checkInRes.json()).id;
      }

      const todayDate = vnToday();
      const overridePayload = (minute: number) => ({
        checkInTime: `${todayDate}T08:${String(minute).padStart(2, '0')}:00`,
        auditReason: `Optimistic lock race test override (minute ${minute})`,
      });

      // A 2-request race is too easy to lose to scheduling luck (both requests'
      // SELECTs can land far enough apart that they never actually overlap).
      // Firing a larger batch makes it overwhelmingly likely that at least two
      // land within the same read-before-write window.
      const CONCURRENCY = 6;
      const responses = await Promise.all(
        Array.from({ length: CONCURRENCY }, (_, i) =>
          request.put(`/api/admin/attendance/${recordId}/override`, { headers: adminAuth, data: overridePayload(i + 1) })
        )
      );
      const statuses = responses.map(r => r.status());

      expect(statuses.filter(s => s === 200).length, `statuses: ${statuses}`).toBeGreaterThanOrEqual(1);
      const conflicts = responses.filter(r => r.status() === 409);
      expect(conflicts.length, `statuses: ${statuses}`).toBeGreaterThanOrEqual(1);

      const conflictBody = await conflicts[0].json();
      expect(conflictBody.error).toBe('CONCURRENT_MODIFICATION');
      expect(statuses.every(s => s === 200 || s === 409), `statuses: ${statuses}`).toBe(true);
    } finally {
      // Restore emp-005's original shift assignment (or leave the reassignment in
      // place if none existed — there's no unassign endpoint to fall back to).
      if (originalShiftId) {
        await request.put(`/api/admin/shifts/${originalShiftId}/assign/emp-005`, { headers: adminAuth });
      }

      // Now safe to delete: assignedCount is back to 0 (or was never bumped, if
      // originalShiftId was null), so Shift.delete() won't 409 SHIFT_IN_USE.
      await request.delete(`/api/admin/shifts/${shiftId}`, { headers: adminAuth });

      // Find and delete this run's valid-ip fixture (id wasn't captured above
      // since the 409-tolerant create path never returns the existing row's id).
      const ipsRes = await request.get('/api/admin/valid-ips', { headers: adminAuth, params: { size: 100 } });
      if (ipsRes.status() === 200) {
        const fixtureIp = (await ipsRes.json()).content.find(
          (ip: { id: number; ipAddress: string; description: string }) =>
            ip.ipAddress === '127.0.0.1' && ip.description === 'Real-API test fixture'
        );
        if (fixtureIp) {
          await request.delete(`/api/admin/valid-ips/${fixtureIp.id}`, { headers: adminAuth });
        }
      }
    }
  });
});
