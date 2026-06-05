import { create } from 'zustand';
import type { UserRole } from '../types/domain';

interface AuthState {
  accessToken: string | null;
  user: {
    id: number;
    email: string;
    fullName: string;
    role: UserRole;
  } | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: AuthState['user']) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  setAuth: (token, user) => set({ accessToken: token, user, isAuthenticated: true }),
  clearAuth: () => set({ accessToken: null, user: null, isAuthenticated: false }),
}));
