import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { createMemoryRouter, MemoryRouter, Route, RouterProvider, Routes, useLocation } from 'react-router-dom';
import { AuthContext } from '../auth/AuthContext';
import { AppLayout } from '../components/AppLayout';
import { LoginPage } from './LoginPage';

function Location() {
  return <p data-testid="location">{useLocation().pathname}</p>;
}

afterEach(cleanup);

test('successful login replaces the login route with the dashboard route', async () => {
  const login = vi.fn().mockResolvedValue(undefined);
  render(
    <AuthContext.Provider value={{ session: null, restoring: false, login, register: vi.fn(), logout: vi.fn() }}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<Location />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
  await userEvent.type(screen.getByLabelText('Email'), 'customer@example.com');
  await userEvent.type(screen.getByLabelText('Password'), 'StrongPassword123!');
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await waitFor(() => expect(login).toHaveBeenCalledWith('customer@example.com', 'StrongPassword123!'));
  expect(screen.getByTestId('location')).toHaveTextContent('/dashboard');
});

test('keeps real browser history available after login route replacement', async () => {
  const login = vi.fn().mockResolvedValue(undefined);
  const router = createMemoryRouter([
    { path: '/previous', element: <p>Previous browser page</p> },
    { path: '/login', element: <LoginPage /> },
    { path: '/dashboard', element: <Location /> }
  ], { initialEntries: ['/previous', '/login'], initialIndex: 1 });
  render(
    <AuthContext.Provider value={{ session: null, restoring: false, login, register: vi.fn(), logout: vi.fn() }}>
      <RouterProvider router={router} />
    </AuthContext.Provider>
  );
  await userEvent.type(screen.getByLabelText('Email'), 'customer@example.com');
  await userEvent.type(screen.getByLabelText('Password'), 'StrongPassword123!');
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByTestId('location');
  await router.navigate(-1);
  await waitFor(() => expect(screen.getByText('Previous browser page')).toBeInTheDocument());
});

test('redirects an already authenticated visitor from login to the dashboard', () => {
  render(
    <AuthContext.Provider value={{
      session: { accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' },
      restoring: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn()
    }}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<Location />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
  expect(screen.getByTestId('location')).toHaveTextContent('/dashboard');
});

test('logout clears the client session through the context and replaces the route', async () => {
  const logout = vi.fn().mockResolvedValue(undefined);
  render(
    <AuthContext.Provider value={{
      session: { accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' },
      restoring: false,
      login: vi.fn(),
      register: vi.fn(),
      logout
    }}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/dashboard" element={<AppLayout><p>Dashboard</p></AppLayout>} />
          <Route path="/login" element={<Location />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
  await userEvent.click(screen.getByRole('button', { name: /sign out/i }));
  await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
  expect(screen.getByTestId('location')).toHaveTextContent('/login');
});
