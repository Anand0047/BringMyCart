import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ShoppingBag } from 'lucide-react';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/useAuth';

export function LoginPage() {
  const { login, session, restoring } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  if (restoring) return <AuthInitializing />;
  if (session) return <Navigate to="/dashboard" replace />;

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(null); setSubmitting(true);
    try { await login(email, password); navigate('/dashboard', { replace: true }); }
    catch (exception) { setError(errorMessage(exception, 'Unable to sign in. Check your credentials and try again.')); }
    finally { setSubmitting(false); }
  }

  return <AuthShell title="Welcome back" subtitle="Your saved Amazon India products are waiting.">
    <form onSubmit={submit} className="space-y-4">
      <Field label="Email" value={email} onChange={setEmail} type="email" autoComplete="email" />
      <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete="current-password" />
      {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
      <button disabled={submitting} className="w-full rounded-xl bg-sky-600 px-4 py-3 font-bold text-white hover:bg-sky-700 disabled:bg-slate-300">{submitting ? 'Signing in…' : 'Sign in'}</button>
    </form>
    <p className="mt-6 text-center text-sm text-slate-600">New to BringMyCart? <Link className="font-semibold text-sky-700 hover:underline" to="/register">Create an account</Link></p>
  </AuthShell>;
}

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return <div className="grid min-h-screen place-items-center bg-gradient-to-br from-sky-50 via-white to-indigo-50 p-4">
    <section className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-7 shadow-card sm:p-9">
      <div className="mb-7 flex items-center gap-3"><span className="grid h-11 w-11 place-items-center rounded-xl bg-sky-600 text-white"><ShoppingBag /></span><span className="font-bold">BringMyCart</span></div>
      <h1 className="text-2xl font-bold text-slate-950">{title}</h1><p className="mt-2 text-sm text-slate-600">{subtitle}</p>
      <div className="mt-7">{children}</div>
    </section>
  </div>;
}

export function AuthInitializing() {
  return <div className="grid min-h-screen place-items-center bg-slate-50"><p className="text-sm font-medium text-slate-600" role="status">Checking your session…</p></div>;
}

export function Field({ label, value, onChange, type, autoComplete }: { label: string; value: string; onChange(value: string): void; type: string; autoComplete: string }) {
  return <label className="block text-sm font-semibold text-slate-700">{label}
    <input required type={type} autoComplete={autoComplete} value={value} onChange={(event) => onChange(event.target.value)} className="mt-1.5 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal outline-none transition focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
  </label>;
}
