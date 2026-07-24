import { test, expect } from '@playwright/test';
import { SEEDED_USERS, loginAs, authHeaders } from './support/api';

// Exercises RequestService.checkLeaderAuthorization (backend/.../RequestService.java)
// against the real backend. This path is unit/integration-tested server-side but was
// never driven end-to-end: every existing Playwright spec mocks page.route() so the
// actual 403 never left the backend. leader1 only manages employee1-3; employee4
// reports to leader2 (both seeded by V13__add_test_users.sql), so leader1 approving
// employee4's request must be rejected regardless of what the UI would show.

// Returns the next weekday (Mon-Fri) that is at least `minDaysOut` days from today,
// as an ISO date string.
function nextWeekdayAtLeast(minDaysOut: number): string {
  const d = new Date();
  d.setDate(d.getDate() + minDaysOut);
  while (d.getDay() === 0 || d.getDay() === 6) {
    d.setDate(d.getDate() + 1);
  }
  return d.toISOString().slice(0, 10);
}

test.describe('Real API — Leader cross-team authorization', () => {
  test('leader from a different team cannot approve or reject a leave request', async ({ request }, testInfo) => {
    // Checked up front (not in the finally below) so a missing env var fails
    // fast instead of throwing inside a finally, which would mask whatever
    // the try block's own assertions found (no-unsafe-finally).
    const bootstrapPassword = process.env.ADMIN_BOOTSTRAP_PASSWORD;
    if (!bootstrapPassword) {
      throw new Error('ADMIN_BOOTSTRAP_PASSWORD must be set for real-API tests (see docker-compose.yml / CI env).');
    }

    const employeeToken = await loginAs(request, SEEDED_USERS.employee4);
    // Deterministic per attempt (was an unseeded Math.random(), making failures
    // impossible to reproduce). 30 is just an arbitrary "far enough out" buffer —
    // no server-side minimum-notice rule to honor. Offsetting by `testInfo.retry`
    // keeps CI's configured retries (playwright.config.ts) self-healing: if the
    // admin-DELETE cleanup in the `finally` block below didn't run for a prior
    // attempt, a retry still gets a fresh date instead of repeating the same
    // fixture and deterministically 409-ing every time.
    const leaveDate = nextWeekdayAtLeast(30 + testInfo.retry * 5);

    const createRes = await request.post('/api/requests/leave', {
      headers: authHeaders(employeeToken),
      data: {
        leaveType: 'SICK',
        startDate: leaveDate,
        endDate: leaveDate,
        reason: 'Real-API cross-team authorization test fixture',
      },
    });
    expect(createRes.status(), await createRes.text()).toBe(201);
    const { id: requestId } = await createRes.json();

    try {
      const wrongLeaderToken = await loginAs(request, SEEDED_USERS.leader1);
      const forbiddenApprove = await request.put(`/api/requests/${requestId}/approve`, {
        headers: authHeaders(wrongLeaderToken),
      });
      expect(forbiddenApprove.status()).toBe(403);
      const forbiddenBody = await forbiddenApprove.json();
      expect(forbiddenBody.error).toBe('FORBIDDEN');

      const forbiddenReject = await request.put(`/api/requests/${requestId}/reject`, {
        headers: authHeaders(wrongLeaderToken),
        data: { reason: 'Should not be allowed' },
      });
      expect(forbiddenReject.status()).toBe(403);

      const correctLeaderToken = await loginAs(request, SEEDED_USERS.leader2);
      const approveRes = await request.put(`/api/requests/${requestId}/approve`, {
        headers: authHeaders(correctLeaderToken),
      });
      expect(approveRes.status(), await approveRes.text()).toBe(200);
      const approved = await approveRes.json();
      expect(approved.status).toBe('APPROVED');
    } finally {
      // Hard-delete the fixture leave request (and reverse its leave-balance
      // effect) via DELETE /api/admin/requests/{id} so this real-DB run doesn't
      // leave a permanent APPROVED row behind for employee4.
      const adminToken = await loginAs(request, { username: 'admin', password: bootstrapPassword });
      await request.delete(`/api/admin/requests/${requestId}`, { headers: authHeaders(adminToken) });
    }
  });
});
