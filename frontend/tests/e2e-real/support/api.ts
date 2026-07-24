import type { APIRequestContext } from '@playwright/test';

// Seeded by backend/src/main/resources/db/migration/dev/V13__add_test_users.sql,
// which is only applied under the `dev` Spring profile — i.e. the CI/local
// docker-compose stack, never a real deployment running another profile.
export const SEEDED_USERS = {
  leader1: { username: 'leader1', password: 'admin123' }, // leads employee1-3
  leader2: { username: 'leader2', password: 'admin123' }, // leads employee4-5
  employee1: { username: 'employee1', password: 'admin123' },
  employee4: { username: 'employee4', password: 'admin123' }, // reports to leader2
  employee5: { username: 'employee5', password: 'admin123' }, // reports to leader2
} as const;

// V16__add_must_change_password.sql force-sets must_change_password=TRUE for
// every seeded dev user (and AdminUserSeeder always sets it for the bootstrap
// admin). JwtAuthenticationFilter then 403s every non-/api/auth/ request until
// it's cleared via POST /api/auth/change-password. We clear it by "changing"
// the password to itself — idempotent, so reruns against a non-fresh local
// volume (where a prior run already cleared the flag) work unchanged.
//
// LoginRateLimitFilter caps /api/auth/login at 5 attempts/minute/IP. On a
// fresh DB, every one of this suite's 5 distinct seeded users needs 2 logins
// (initial + the re-login the change-password dance above forces), which
// alone exceeds the budget — this is a real interaction with a real
// anti-brute-force control, not a test artifact, so we retry through it
// (bucket4j refills the full bucket once per window) rather than weakening it.
export async function loginRequest(request: APIRequestContext, user: { username: string; password: string }) {
  for (let attempt = 0; ; attempt++) {
    const res = await request.post('/api/auth/login', { data: user });
    if (res.status() !== 429) return res;
    if (attempt >= 2) return res;
    await new Promise(resolve => setTimeout(resolve, 65_000));
  }
}

// The access token (15min TTL, see application.yml) is a bearer credential,
// not tied to the APIRequestContext that fetched it — reusable across specs
// in the same worker process. This suite is short enough that expiry never
// enters into it. Caching by username sidesteps the 5-attempts/min login
// rate limit that specs used to burn through independently (each paying a
// 65s retry sleep). CI runs 2 workers (see playwright.config.ts), so the
// cache is scoped per worker, not suite-wide — it dedupes logins only for
// e2e-real spec files that happen to land on the same worker. Bounded
// impact either way: CI separately raises the login rate limit to 1000/min
// (see RATE_LIMIT_LOGIN_MAX_ATTEMPTS in main.yml), so a cache miss just
// costs one extra login, not a 65s rate-limit retry.
const tokenCache = new Map<string, Promise<string>>();

export async function loginAs(
  request: APIRequestContext,
  user: { username: string; password: string }
): Promise<string> {
  const cached = tokenCache.get(user.username);
  if (cached) return cached;

  const promise = (async () => {
    const res = await loginRequest(request, user);
    if (!res.ok()) {
      throw new Error(`Login failed for ${user.username}: ${res.status()} ${await res.text()}`);
    }
    const body = await res.json();
    let token = body.accessToken as string;

    if (body.user?.mustChangePassword) {
      const changeRes = await request.post('/api/auth/change-password', {
        headers: authHeaders(token),
        data: { oldPassword: user.password, newPassword: user.password },
      });
      if (!changeRes.ok()) {
        throw new Error(`Clearing mustChangePassword failed for ${user.username}: ${changeRes.status()} ${await changeRes.text()}`);
      }
      // The old token predates passwordChangedAt and is now rejected; re-login.
      const reloginRes = await loginRequest(request, user);
      if (!reloginRes.ok()) {
        throw new Error(`Re-login after password change failed for ${user.username}: ${reloginRes.status()} ${await reloginRes.text()}`);
      }
      token = (await reloginRes.json()).accessToken as string;
    }

    return token;
  })();

  // Don't let a failed login poison the cache for a retried run.
  promise.catch(() => tokenCache.delete(user.username));
  tokenCache.set(user.username, promise);
  return promise;
}

export function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}
