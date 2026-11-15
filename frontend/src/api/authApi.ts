import { apiClient, initializeCsrf, refreshSession } from './client';
import type { AuthResponse } from './types';

export const authApi = {
  register: (email: string, password: string) => apiClient.post('/auth/register', { email, password }),
  login: async (email: string, password: string) => (await apiClient.post<AuthResponse>('/auth/login', { email, password })).data,
  initializeCsrf,
  refresh: refreshSession,
  logout: () => apiClient.post('/auth/logout'),
  me: () => apiClient.get<{ userId: string; role: string }>('/auth/me')
};
