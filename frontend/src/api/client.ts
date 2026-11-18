import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, AuthResponse } from './types';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse> | null = null;
let csrfInitializationInFlight: Promise<void> | null = null;
let onSessionRefreshed: ((response: AuthResponse) => void) | null = null;
let onSessionExpired: (() => void) | null = null;

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

export const apiClient = axios.create({ baseURL, withCredentials: true });

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function setSessionCallbacks(callbacks: {
  onRefreshed: (response: AuthResponse) => void;
  onExpired: () => void;
}) {
  onSessionRefreshed = callbacks.onRefreshed;
  onSessionExpired = callbacks.onExpired;
}

/**
 * The only browser-side path that may call the rotating refresh endpoint.
 * Bootstrap and Axios 401 recovery share this promise so they cannot submit
 * the same HttpOnly cookie twice while a request is already in flight.
 */
export function refreshSession(): Promise<AuthResponse> {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const doRefresh = () => apiClient.post<AuthResponse>('/auth/refresh').then((response) => response.data);
  const pending = typeof navigator !== 'undefined' && navigator.locks
    ? navigator.locks.request('bmc-website-refresh-cookie', { mode: 'exclusive' }, doRefresh)
    : doRefresh();
  refreshInFlight = pending;
  void pending.then(
    () => { if (refreshInFlight === pending) refreshInFlight = null; },
    () => { if (refreshInFlight === pending) refreshInFlight = null; }
  );
  return pending;
}

/**
 * Ensures the readable CSRF cookie exists before the first cookie-authenticated
 * POST. This prevents bootstrap refresh from relying on a rejected request to
 * create its CSRF token.
 */
export function initializeCsrf(): Promise<void> {
  if (csrfInitializationInFlight) {
    return csrfInitializationInFlight;
  }

  const pending = apiClient.get('/auth/csrf').then(() => undefined);
  csrfInitializationInFlight = pending;
  void pending.then(
    () => { if (csrfInitializationInFlight === pending) csrfInitializationInFlight = null; },
    () => { if (csrfInitializationInFlight === pending) csrfInitializationInFlight = null; }
  );
  return pending;
}

export function csrfToken() {
  return document.cookie.split(';').map((value) => value.trim())
    .find((value) => value.startsWith('XSRF-TOKEN='))?.slice('XSRF-TOKEN='.length);
}

apiClient.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  if (config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
    const csrf = csrfToken();
    if (csrf) config.headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const config = error.config as RetriableRequestConfig | undefined;
    const url = config?.url ?? '';
    const authRequest = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'].some((path) => url.includes(path));
    if (error.response?.status !== 401 || !config || config._retried || authRequest) throw error;

    config._retried = true;
    try {
      const refreshed = await refreshSession();
      accessToken = refreshed.accessToken;
      onSessionRefreshed?.(refreshed);
      return apiClient(config);
    } catch (refreshError) {
      accessToken = null;
      onSessionExpired?.();
      throw refreshError;
    } finally {
      refreshInFlight = null;
    }
  }
);

export function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<ApiError>(error)) {
    const payload = error.response?.data;
    if (payload?.fieldErrors) return Object.values(payload.fieldErrors)[0] ?? fallback;
    if (error.response?.status === 409 && payload?.code === 'refresh_cooldown') return 'This product was refreshed recently. Please try again later.';
    if (error.response?.status === 409 && payload?.code === 'enrichment_in_progress') return 'Metadata retrieval is already in progress.';
    return payload?.message ?? fallback;
  }
  return fallback;
}

export function authenticationErrorMessage(error: unknown, operation: 'login' | 'register') {
  if (!axios.isAxiosError<ApiError>(error)) return 'Unable to connect to the server. Please try again.';
  const status = error.response?.status;
  const code = error.response?.data?.code;
  const fields = error.response?.data?.fieldErrors ?? {};
  if (operation === 'login') {
    if (fields.email || code === 'validation_failed' && status === 400) return 'Please enter a valid email address.';
    if (fields.password) return 'Please enter your password.';
    if (status === 401 || status === 429) return 'Invalid email or password. Please try again.';
    if (!error.response) return 'Unable to connect to the server. Please try again.';
    return 'Something went wrong. Please try again shortly.';
  }
  if (status === 409 && code === 'email_in_use') return 'An account with this email already exists. Please sign in.';
  if (fields.email || status === 400 && code === 'validation_failed') return fields.password ?? 'Please enter a valid email address.';
  if (!error.response) return 'Unable to connect to the server. Please try again.';
  if (status !== undefined && status >= 500) return 'Something went wrong. Please try again shortly.';
  return fields.password ?? fields.email ?? 'Please check the information and try again.';
}
