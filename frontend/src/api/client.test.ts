import { AxiosError } from 'axios';
import { afterEach, expect, test, vi } from 'vitest';
import { apiClient, errorMessage, initializeCsrf, refreshSession } from './client';

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
