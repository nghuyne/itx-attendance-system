// Ephemeral test-user factory (R-002 mitigation) — replaces the hardcoded
// V13__add_test_users.sql accounts with faker-generated users created via
// the real POST /api/admin/users endpoint, so specs can run with
// `fullyParallel` instead of sharing 6 fixed rows under `workers:1`.
// Sign-off: _bmad-output/test-artifacts/test-design-architecture.md (R-002 blocker, resolved 2026-07-17).
//
// Known gap: User.leader (leader_id) has no admin API to set it — it's only
// ever populated by the V13 migration today. Specs that need a pre-wired
// leader/employee relationship (e.g. cross-team-authorization.spec.ts) must
// keep using SEEDED_USERS until such an endpoint exists.
import type { APIRequestContext } from '@playwright/test';
import { faker } from '@faker-js/faker';
import { authHeaders, loginRequest } from './api';
import { waitForTempPassword } from './mailhog';

export type Role = 'EMPLOYEE' | 'LEADER' | 'ADMIN';

export type FactoryUser = {
  id: string;
  username: string;
  password: string;
  email: string;
  fullName: string;
  role: Role;
  token: string;
};

const BOOTSTRAP_ADMIN_USERNAME = 'admin';

// Fixed, known password every factory user is switched to after its
// admin-issued temp password is retrieved from MailHog.
const FACTORY_USER_PASSWORD = 'Factory123!';

let cachedAdminToken: string | null = null;

async function getAdminToken(request: APIRequestContext): Promise<string> {
  if (cachedAdminToken) return cachedAdminToken;
  const bootstrapPassword = process.env.ADMIN_BOOTSTRAP_PASSWORD;
  if (!bootstrapPassword) {
    throw new Error('ADMIN_BOOTSTRAP_PASSWORD is not set — required to bootstrap the admin token for e2e-real factories');
  }
  const res = await loginRequest(request, { username: BOOTSTRAP_ADMIN_USERNAME, password: bootstrapPassword });
  if (!res.ok()) {
    throw new Error(`Factory bootstrap admin login failed: ${res.status()} ${await res.text()}`);
  }
  cachedAdminToken = (await res.json()).accessToken as string;
  return cachedAdminToken;
}

export type CreateUserOverrides = {
  fullName?: string;
  departmentId?: number;
  shiftId?: string;
};

async function createUser(
  request: APIRequestContext,
  role: Role,
  overrides: CreateUserOverrides = {}
): Promise<FactoryUser> {
  const adminToken = await getAdminToken(request);

  const username = `${role.toLowerCase()}-${faker.string.alphanumeric(10).toLowerCase()}`;
  const email = `${username}@itx-e2e.test`;
  const fullName = overrides.fullName ?? faker.person.fullName();

  const createRes = await request.post('/api/admin/users', {
    headers: authHeaders(adminToken),
    data: {
      username,
      email,
      fullName,
      role,
      departmentId: overrides.departmentId,
      shiftId: overrides.shiftId,
    },
  });
  if (!createRes.ok()) {
    throw new Error(`Factory createUser(${role}) failed: ${createRes.status()} ${await createRes.text()}`);
  }
  const created = await createRes.json();

  const tempPassword = await waitForTempPassword(email);

  const tempLoginRes = await loginRequest(request, { username, password: tempPassword });
  if (!tempLoginRes.ok()) {
    throw new Error(`Factory temp-password login failed for ${username}: ${tempLoginRes.status()} ${await tempLoginRes.text()}`);
  }
  const tempToken = (await tempLoginRes.json()).accessToken as string;

  const changeRes = await request.post('/api/auth/change-password', {
    headers: authHeaders(tempToken),
    data: { oldPassword: tempPassword, newPassword: FACTORY_USER_PASSWORD },
  });
  if (!changeRes.ok()) {
    throw new Error(`Factory password change failed for ${username}: ${changeRes.status()} ${await changeRes.text()}`);
  }

  // The pre-change token is now stale (issued before passwordChangedAt).
  const finalLoginRes = await loginRequest(request, { username, password: FACTORY_USER_PASSWORD });
  if (!finalLoginRes.ok()) {
    throw new Error(`Factory final login failed for ${username}: ${finalLoginRes.status()} ${await finalLoginRes.text()}`);
  }
  const token = (await finalLoginRes.json()).accessToken as string;

  return { id: created.id, username, password: FACTORY_USER_PASSWORD, email, fullName, role, token };
}

export const createEmployee = (request: APIRequestContext, overrides?: CreateUserOverrides) =>
  createUser(request, 'EMPLOYEE', overrides);

export const createLeader = (request: APIRequestContext, overrides?: CreateUserOverrides) =>
  createUser(request, 'LEADER', overrides);

export const createAdmin = (request: APIRequestContext, overrides?: CreateUserOverrides) =>
  createUser(request, 'ADMIN', overrides);

// Best-effort teardown: there is no DELETE /api/admin/users/{id} endpoint
// today, so cleanup deactivates the account rather than removing the row.
// Call from a fixture/afterEach. The faker-suffixed username/email keeps
// re-runs collision-free regardless of whether teardown ran.
export async function deactivateFactoryUser(request: APIRequestContext, userId: string): Promise<void> {
  const adminToken = await getAdminToken(request);
  const res = await request.put(`/api/admin/users/${userId}/deactivate`, {
    headers: authHeaders(adminToken),
  });
  if (!res.ok() && res.status() !== 404) {
    throw new Error(`Factory teardown failed for user ${userId}: ${res.status()} ${await res.text()}`);
  }
}
