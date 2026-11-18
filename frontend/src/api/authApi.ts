import { apiClient, initializeCsrf, refreshSession } from './client';
import type { AuthResponse } from './types';

export const authApi = {
  register: (fullName: string, email: string, password: string) => apiClient.post('/auth/register', { fullName, email, password }),
  login: async (email: string, password: string) => (await apiClient.post<AuthResponse>('/auth/login', { email, password })).data,
  initializeCsrf,
  refresh: refreshSession,
  logout: () => apiClient.post('/auth/logout'),
  me: () => apiClient.get<{ userId: string; email: string; fullName: string | null; role: string }>('/identity/me'),
  authorizeExtension: (request: { clientId: string; redirectUri: string; state: string; codeChallenge: string }) =>
    apiClient.post<{ redirectUrl: string }>('/extension-auth/authorize', request)
};
