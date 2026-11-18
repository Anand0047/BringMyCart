import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

const authApi = vi.hoisted(() => ({ initializeCsrf: vi.fn(), refresh: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() }));
const extensionBridge = vi.hoisted(() => ({ notifyExtensionAccountEvent: vi.fn().mockResolvedValue(undefined) }));
vi.mock('../api/authApi', () => ({ authApi }));
vi.mock('../api/client', () => ({ setAccessToken: vi.fn(), setSessionCallbacks: vi.fn() }));
vi.mock('../api/extensionBridge', () => extensionBridge);

import { AuthProvider } from './AuthContext';
import { ProtectedRoute } from './ProtectedRoute';
import { useAuth } from './useAuth';

function SessionState() {
  const { session, restoring } = useAuth();
  return <p>{restoring ? 'restoring' : session?.email ?? 'signed-out'}</p>;
}

function SessionControls() {
  const { login, logout, restoring } = useAuth();
  return <><button disabled={restoring} onClick={() => void login('customer@example.com', 'StrongPassword123!')}>Login action</button>
    <button disabled={restoring} onClick={() => void logout()}>Logout action</button></>;
}

function RestoredDashboard() {
  return <MemoryRouter initialEntries={['/dashboard']}>
    <Routes>
      <Route element={<ProtectedRoute />}><Route path="/dashboard" element={<p>Dashboard</p>} /></Route>
      <Route path="/login" element={<p>Login page</p>} />
    </Routes>
  </MemoryRouter>;
}

beforeEach(() => {
  vi.clearAllMocks();
  authApi.initializeCsrf.mockResolvedValue(undefined);
  extensionBridge.notifyExtensionAccountEvent.mockResolvedValue(undefined);
});

test('notifies the extension on successful website login and logout without sending identity credentials', async () => {
  authApi.refresh.mockRejectedValue(new Error('signed out'));
  authApi.login.mockResolvedValue({ accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'user-a', email: 'customer@example.com', role: 'CUSTOMER' });
  authApi.logout.mockResolvedValue(undefined);
  render(<AuthProvider><SessionControls /></AuthProvider>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Login action' })).not.toBeDisabled());
  extensionBridge.notifyExtensionAccountEvent.mockClear();
  await userEvent.click(screen.getByRole('button', { name: 'Login action' }));
  await waitFor(() => expect(extensionBridge.notifyExtensionAccountEvent).toHaveBeenCalledWith('login'));
  await userEvent.click(screen.getByRole('button', { name: 'Logout action' }));
  await waitFor(() => expect(extensionBridge.notifyExtensionAccountEvent).toHaveBeenCalledWith('logout'));
  expect(extensionBridge.notifyExtensionAccountEvent.mock.calls).toEqual([[ 'login' ], [ 'logout' ]]);
});
afterEach(cleanup);

test('waits for the shared refresh operation across Strict Mode initialization effects', async () => {
  authApi.refresh.mockResolvedValue({ accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' });
  render(<StrictMode><AuthProvider><SessionState /></AuthProvider></StrictMode>);
  await waitFor(() => expect(screen.getByText('customer@example.com')).toBeInTheDocument());
  expect(authApi.initializeCsrf).toHaveBeenCalledTimes(2);
  expect(authApi.refresh).toHaveBeenCalledTimes(2);
});

test('keeps an initialization state until a valid refresh session resolves', async () => {
  let resolveRefresh!: (value: { accessToken: string; tokenType: string; expiresAt: number; userId: string; email: string; role: string }) => void;
  authApi.refresh.mockReturnValue(new Promise((resolve) => { resolveRefresh = resolve; }));
  render(<AuthProvider><SessionState /></AuthProvider>);
  expect(screen.getByText('restoring')).toBeInTheDocument();
  resolveRefresh({ accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' });
  await waitFor(() => expect(screen.getByText('customer@example.com')).toBeInTheDocument());
});

test('does not attempt refresh until CSRF initialization has completed', async () => {
  let resolveCsrf!: () => void;
  authApi.initializeCsrf.mockReturnValue(new Promise<void>((resolve) => { resolveCsrf = resolve; }));
  authApi.refresh.mockResolvedValue({ accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' });

  render(<AuthProvider><SessionState /></AuthProvider>);
  expect(screen.getByText('restoring')).toBeInTheDocument();
  expect(authApi.refresh).not.toHaveBeenCalled();

  resolveCsrf();
  await waitFor(() => expect(screen.getByText('customer@example.com')).toBeInTheDocument());
  expect(authApi.refresh).toHaveBeenCalledTimes(1);
});

test('clears the in-memory session when refresh is invalid or expired', async () => {
  authApi.refresh.mockRejectedValue(new Error('invalid refresh token'));
  render(<AuthProvider><RestoredDashboard /></AuthProvider>);
  await waitFor(() => expect(screen.getByText('Login page')).toBeInTheDocument());
});

test('redirects to login when CSRF initialization cannot complete', async () => {
  authApi.initializeCsrf.mockRejectedValue(new Error('CSRF initialization failed'));
  render(<AuthProvider><RestoredDashboard /></AuthProvider>);
  await waitFor(() => expect(screen.getByText('Login page')).toBeInTheDocument());
  expect(authApi.refresh).not.toHaveBeenCalled();
});

test('restores a valid refresh session before rendering the protected dashboard', async () => {
  authApi.refresh.mockResolvedValue({ accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' });
  render(<AuthProvider><RestoredDashboard /></AuthProvider>);
  expect(screen.getByLabelText('Restoring session')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('Dashboard')).toBeInTheDocument());
  expect(authApi.refresh).toHaveBeenCalledTimes(1);
});

test('uses a newly rotated session response on each later application bootstrap', async () => {
  authApi.refresh
    .mockResolvedValueOnce({ accessToken: 'rotation-one', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' })
    .mockResolvedValueOnce({ accessToken: 'rotation-two', tokenType: 'Bearer', expiresAt: 2, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' });

  const first = render(<AuthProvider><RestoredDashboard /></AuthProvider>);
  await screen.findByText('Dashboard');
  first.unmount();

  render(<AuthProvider><RestoredDashboard /></AuthProvider>);
  await screen.findByText('Dashboard');
  expect(authApi.refresh).toHaveBeenCalledTimes(2);
});
