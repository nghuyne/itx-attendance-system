import { create } from 'zustand';
import type { ToastType } from '../types/designTokens';

export interface ToastMessage {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

interface UiState {
  isLoading: boolean;
  toasts: ToastMessage[];
  isSidebarOpen: boolean;
  notificationPanelOpen: boolean;
  currentPage: string;

  setLoading: (loading: boolean) => void;
  showToast: (payload: Omit<ToastMessage, 'id'>) => void;
  dismissToast: (id: string) => void;
  setSidebarOpen: (open: boolean) => void;
  setNotificationPanelOpen: (open: boolean) => void;
  setCurrentPage: (page: string) => void;
}

export const useUiStore = create<UiState>((set) => ({
  isLoading: false,
  toasts: [],
  isSidebarOpen: false,
  notificationPanelOpen: false,
  currentPage: '',

  setLoading: (loading) => set({ isLoading: loading }),

  showToast: (payload) =>
    set((state) => ({
      toasts: [
        ...state.toasts,
        { ...payload, id: `toast-${Date.now()}-${Math.random().toString(36).slice(2)}` },
      ],
    })),

  dismissToast: (id) =>
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),

  setSidebarOpen: (open) => set({ isSidebarOpen: open }),
  setNotificationPanelOpen: (open) => set({ notificationPanelOpen: open }),
  setCurrentPage: (page) => set({ currentPage: page }),
}));
