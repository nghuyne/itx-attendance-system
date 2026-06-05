import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  // Story 1.5 will attach accessToken from authStore here
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // Story 1.5 will implement 401 refresh flow here
    return Promise.reject(error);
  }
);

export default api;
