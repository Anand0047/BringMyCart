import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { createMemoryRouter, MemoryRouter, Route, RouterProvider, Routes, useLocation } from 'react-router-dom';
import { AuthContext } from '../auth/AuthContext';
import { AppLayout } from '../components/AppLayout';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';

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

test('sign-in password can be shown and hidden without submitting the form', async () => {
  render(
    <AuthContext.Provider value={{ session: null, restoring: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }}>
      <MemoryRouter initialEntries={['/login']}><Routes><Route path="/login" element={<LoginPage />} /></Routes></MemoryRouter>
    </AuthContext.Provider>
  );
  const password = screen.getByLabelText('Password');
  expect(password).toHaveAttribute('type', 'password');
  await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
  expect(password).toHaveAttribute('type', 'text');
  expect(screen.getByRole('button', { name: 'Hide password' })).toHaveAttribute('type', 'button');
  await userEvent.click(screen.getByRole('button', { name: 'Hide password' }));
  expect(password).toHaveAttribute('type', 'password');
});

test('registration requires a name and matching passwords and submits only the password once', async () => {
  const register = vi.fn().mockResolvedValue(undefined);
  render(
    <AuthContext.Provider value={{ session: null, restoring: false, login: vi.fn(), register, logout: vi.fn() }}>
      <MemoryRouter initialEntries={['/register']}><Routes><Route path="/register" element={<RegisterPage />} /></Routes></MemoryRouter>
    </AuthContext.Provider>
  );
  await userEvent.type(screen.getByLabelText('Full Name'), '  Anand Raj  ');
  await userEvent.type(screen.getByLabelText('Email'), 'anand@example.com');
  const password = screen.getByLabelText('Password');
  const confirm = screen.getByLabelText('Confirm Password');
  await userEvent.type(password, 'StrongPassword123!');
  await userEvent.type(confirm, 'DifferentPassword123!');
  expect(screen.getByText('Passwords do not match.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled();
  expect(password).toHaveAttribute('type', 'password');
  expect(confirm).toHaveAttribute('type', 'password');
  await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
  expect(password).toHaveAttribute('type', 'text');
  expect(confirm).toHaveAttribute('type', 'password');
  await userEvent.click(screen.getByRole('button', { name: 'Show confirm password' }));
  expect(confirm).toHaveAttribute('type', 'text');
  await userEvent.clear(confirm);
  await userEvent.type(confirm, 'StrongPassword123!');
  expect(screen.queryByText('Passwords do not match.')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
  await waitFor(() => expect(register).toHaveBeenCalledWith('Anand Raj', 'anand@example.com', 'StrongPassword123!'));
  expect(register.mock.calls[0]).toHaveLength(3);
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
