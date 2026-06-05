import axios from 'axios';
import { useAuthStore } from '../store/authStore';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

// Attach access token from store to every request
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
});

// 401 → refresh token → retry with queue pattern to avoid parallel refresh races
let isRefreshing = false;
type Subscriber = { onToken: (token: string) => void; onError: (err: unknown) => void };
let refreshSubscribers: Subscriber[] = [];

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          refreshSubscribers.push({
            onToken: (token: string) => {
              originalRequest.headers['Authorization'] = `Bearer ${token}`;
              resolve(api(originalRequest));
            },
            onError: reject,
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const response = await api.post<{ accessToken: string }>('/auth/refresh');
        const { accessToken } = response.data;
        useAuthStore.getState().setAccessToken(accessToken);
        refreshSubscribers.forEach(({ onToken }) => onToken(accessToken));
        refreshSubscribers = [];
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        useAuthStore.getState().clearAuth();
        refreshSubscribers.forEach(({ onError }) => onError(refreshError));
        refreshSubscribers = [];
        window.location.replace('/login');
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
