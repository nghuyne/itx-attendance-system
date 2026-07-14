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
}

export const seedAdminAuth = (page: Page, overrides: Partial<AuthUser> = {}) =>
  seedAuth(page, { ...DEFAULT_USERS.admin, ...overrides });

export const seedEmployeeAuth = (page: Page, overrides: Partial<AuthUser> = {}) =>
  seedAuth(page, { ...DEFAULT_USERS.employee, ...overrides });

export const seedLeaderAuth = (page: Page, overrides: Partial<AuthUser> = {}) =>
  seedAuth(page, { ...DEFAULT_USERS.leader, ...overrides });
