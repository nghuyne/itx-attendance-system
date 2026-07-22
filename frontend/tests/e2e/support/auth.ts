import type { Page } from '@playwright/test';

export type AuthUser = {
  id: string;
  username: string;
  fullName: string;
  role: 'ADMIN' | 'LEADER' | 'EMPLOYEE';
  mustChangePassword: boolean;
};

const DEFAULT_USERS = {
  admin: { id: 'admin-id', username: 'admin', fullName: 'System Administrator', role: 'ADMIN', mustChangePassword: false },
  employee: { id: 'emp-id', username: 'emp1', fullName: 'Nguyen Van A', role: 'EMPLOYEE', mustChangePassword: false },
  leader: { id: 'leader-id', username: 'leader1', fullName: 'Tran Thi B', role: 'LEADER', mustChangePassword: false },
} as const satisfies Record<string, AuthUser>;

async function seedAuth(page: Page, user: AuthUser) {
  await page.addInitScript(
    (storage: { key: string; value: unknown }) => {
      localStorage.setItem(storage.key, JSON.stringify(storage.value));
    },
    {
      key: 'itx-auth',
      value: { state: { user, isAuthenticated: true }, version: 0 },
    }
  );

  // ProtectedRoute performs a silent refresh on mount because the persisted
  // `isAuthenticated` flag survives reload while the in-memory accessToken
  // doesn't. Without this mock the real /auth/refresh call hits a backend
  // that isn't running in this test config, fails, and clearAuth() kicks
  // the page back to /login before any protected content renders.
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({ status: 200, json: { accessToken: 'mock-access-token' } })
  );
}

export const seedAdminAuth = (page: Page, overrides: Partial<AuthUser> = {}) =>
  seedAuth(page, { ...DEFAULT_USERS.admin, ...overrides });

export const seedEmployeeAuth = (page: Page, overrides: Partial<AuthUser> = {}) =>
  seedAuth(page, { ...DEFAULT_USERS.employee, ...overrides });

export const seedLeaderAuth = (page: Page, overrides: Partial<AuthUser> = {}) =>
  seedAuth(page, { ...DEFAULT_USERS.leader, ...overrides });
