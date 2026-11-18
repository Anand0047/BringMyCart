import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ContextType } from 'react';
import { afterEach, expect, test, vi } from 'vitest';
import { AuthContext } from '../auth/AuthContext';
import { ExtensionAuthorizationPage } from './ExtensionAuthorizationPage';

const session = { accessToken: 'memory-only', tokenType: 'Bearer', expiresAt: 1, userId: 'id', email: 'customer@example.com', role: 'CUSTOMER' as const };
afterEach(cleanup);
function page(value: ContextType<typeof AuthContext>, url = '/extension/authorize?client_id=bmc-chrome-dev&redirect_uri=https%3A%2F%2Fmlogpmpajiokfmjnlmehmpgphbbflpem.chromiumapp.org%2Fbmc-auth&state=12345678901234567890123456789012&code_challenge=1234567890123456789012345678901234567890123') {
  return render(<AuthContext.Provider value={value}><MemoryRouter initialEntries={[url]}><Routes><Route path="/extension/authorize" element={<ExtensionAuthorizationPage />} /><Route path="/login" element={<p>Website login</p>} /></Routes></MemoryRouter></AuthContext.Provider>);
}

test('shows the website-session consent page without exposing extension tokens', () => {
  page({ session, restoring: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() });
  expect(screen.getByRole('heading', { name: 'Authorize BringMyCart extension' })).toBeInTheDocument();
  expect(screen.getByText(/Signed in as customer@example.com/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Authorize extension' })).toBeInTheDocument();
  expect(screen.queryByText(/accessToken|refreshToken/i)).not.toBeInTheDocument();
});

test('preserves the authorization request when website sign-in is needed', () => {
  page({ session: null, restoring: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() });
  expect(screen.getByRole('link', { name: 'Sign in to BringMyCart' })).toHaveAttribute('href', '/login');
});

test('does not offer consent before authentication initialization completes', () => {
  page({ session: null, restoring: true, login: vi.fn(), register: vi.fn(), logout: vi.fn() });
  expect(screen.getByRole('status')).toHaveTextContent('Checking your session');
  expect(screen.queryByRole('button', { name: 'Authorize extension' })).not.toBeInTheDocument();
});
