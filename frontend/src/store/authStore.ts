import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserRole } from '../types/domain';

interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  role: UserRole;
  mustChangePassword: boolean;
}

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: AuthUser) => void;
  setAccessToken: (token: string) => void;
  clearAuth: () => void;
  clearMustChangePassword: () => void;
}

// Zustand v5 curried form required with middleware
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      setAuth: (token, user) => set({ accessToken: token, user, isAuthenticated: true }),
      setAccessToken: (token) => set({ accessToken: token }),
      clearAuth: () => set({ accessToken: null, user: null, isAuthenticated: false }),
      clearMustChangePassword: () =>
        set((state) => ({
          user: state.user ? { ...state.user, mustChangePassword: false } : null,
        })),
    }),
    {
      name: 'itx-auth',
      partialize: (s) => ({ user: s.user, isAuthenticated: s.isAuthenticated }),
    }
  )
);
