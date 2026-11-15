import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthContext } from './AuthContext';
import { ProtectedRoute } from './ProtectedRoute';

test('redirects an unauthenticated visitor to login', () => {
  render(
    <AuthContext.Provider value={{ session: null, restoring: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route element={<ProtectedRoute />}><Route path="/" element={<p>Dashboard</p>} /></Route>
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
  expect(screen.getByText('Login page')).toBeInTheDocument();
});

test('does not render protected content while authentication is initializing', () => {
  render(
    <AuthContext.Provider value={{ session: null, restoring: true, login: vi.fn(), register: vi.fn(), logout: vi.fn() }}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes><Route element={<ProtectedRoute />}><Route path="/dashboard" element={<p>Dashboard</p>} /></Route></Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
  expect(screen.getByLabelText('Restoring session')).toBeInTheDocument();
  expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
});
