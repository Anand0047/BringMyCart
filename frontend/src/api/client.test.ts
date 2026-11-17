import { AxiosError } from 'axios';
import { afterEach, expect, test, vi } from 'vitest';
import { apiClient, authenticationErrorMessage, errorMessage, initializeCsrf, refreshSession } from './client';

afterEach(() => vi.restoreAllMocks());

test('translates refresh conflicts without exposing backend implementation details', () => {
  const error = new AxiosError('Conflict', undefined, undefined, undefined, {
    data: { status: 409, code: 'refresh_cooldown', message: 'internal provider detail' },
    status: 409,
    statusText: 'Conflict',
    headers: {},
    config: { headers: {} }
  } as never);
  expect(errorMessage(error, 'Fallback')).toBe('This product was refreshed recently. Please try again later.');
});

test('uses a generic login message for invalid credentials to prevent account enumeration', () => {
  const error = new AxiosError('Unauthorized', undefined, undefined, undefined, {
    data: { status: 401, code: 'invalid_credentials', message: 'Invalid email or password' }, status: 401,
    statusText: 'Unauthorized', headers: {}, config: { headers: {} }
  } as never);
  expect(authenticationErrorMessage(error, 'login')).toBe('Invalid email or password. Please try again.');
});

test('maps registration conflicts and network failures to actionable safe messages', () => {
  const conflict = new AxiosError('Conflict', undefined, undefined, undefined, {
    data: { status: 409, code: 'email_in_use', message: 'An account already exists' }, status: 409,
    statusText: 'Conflict', headers: {}, config: { headers: {} }
  } as never);
  expect(authenticationErrorMessage(conflict, 'register')).toBe('An account with this email already exists. Please sign in.');
  expect(authenticationErrorMessage(new AxiosError('Network Error'), 'register')).toBe('Unable to connect to the server. Please try again.');
});

test('shares one refresh-token rotation across concurrent bootstrap and recovery callers', async () => {
  const response = { accessToken: 'rotated-token', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' as const };
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: response } as never);

  const [bootstrap, recovery] = await Promise.all([refreshSession(), refreshSession()]);

  expect(post).toHaveBeenCalledTimes(1);
  expect(bootstrap).toEqual(response);
  expect(recovery).toEqual(response);

  await refreshSession();
  expect(post).toHaveBeenCalledTimes(2);
});

test('releases the shared refresh operation after an invalid session response', async () => {
  const post = vi.spyOn(apiClient, 'post')
    .mockRejectedValueOnce(new Error('invalid refresh token'))
    .mockResolvedValueOnce({ data: { accessToken: 'new-token', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' } } as never);

  await expect(refreshSession()).rejects.toThrow('invalid refresh token');
  await expect(refreshSession()).resolves.toMatchObject({ accessToken: 'new-token' });
  expect(post).toHaveBeenCalledTimes(2);
});

test('shares CSRF initialization and completes it before bootstrap refresh', async () => {
  const get = vi.spyOn(apiClient, 'get').mockResolvedValue({} as never);

  await Promise.all([initializeCsrf(), initializeCsrf()]);

  expect(get).toHaveBeenCalledTimes(1);
  expect(get).toHaveBeenCalledWith('/auth/csrf');
});
