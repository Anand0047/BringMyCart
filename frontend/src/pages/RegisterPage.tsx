import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { authenticationErrorMessage } from '../api/client';
import { useAuth } from '../auth/useAuth';
import { PasswordField } from '../components/PasswordField';
import { AuthInitializing, AuthShell, Field } from './LoginPage';

export function RegisterPage() {
  const { register, session, restoring } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  if (restoring) return <AuthInitializing />;
  if (session) return <Navigate to="/dashboard" replace />;
  const passwordMismatch = confirmPassword.length > 0 && password !== confirmPassword;

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(null);
    if (password !== confirmPassword) { setError('Passwords do not match.'); return; }
    setSubmitting(true);
    try { await register(fullName.trim(), email, password); navigate('/login', { state: { registered: true }, replace: true }); }
    catch (exception) { setError(authenticationErrorMessage(exception, 'register')); }
    finally { setSubmitting(false); }
  }

  return <AuthShell title="Create your account" subtitle="Save Amazon India products in one focused wishlist.">
    <form onSubmit={submit} className="space-y-4">
      <Field label="Full Name" value={fullName} onChange={setFullName} type="text" autoComplete="name" />
      <Field label="Email" value={email} onChange={setEmail} type="email" autoComplete="email" />
      <PasswordField label="Password" value={password} onChange={setPassword} autoComplete="new-password" />
      <p className="text-xs leading-5 text-slate-500">Use at least 12 characters with upper and lower case letters, a number, and a symbol.</p>
      <PasswordField label="Confirm Password" value={confirmPassword} onChange={setConfirmPassword} autoComplete="new-password" />
      {passwordMismatch && <p className="text-sm text-rose-700" role="alert">Passwords do not match.</p>}
      {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
      <button disabled={submitting || passwordMismatch || !confirmPassword} className="w-full rounded-xl bg-sky-600 px-4 py-3 font-bold text-white hover:bg-sky-700 disabled:bg-slate-300">{submitting ? 'Creating account…' : 'Create account'}</button>
    </form>
    <p className="mt-6 text-center text-sm text-slate-600">Already registered? <Link className="font-semibold text-sky-700 hover:underline" to="/login">Sign in</Link></p>
  </AuthShell>;
}
