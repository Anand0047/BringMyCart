import { useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { authApi } from '../api/authApi';
import { authenticationErrorMessage } from '../api/client';
import { AuthInitializing, AuthShell } from './LoginPage';

export function ExtensionAuthorizationPage() {
  const { session, restoring } = useAuth();
  const location = useLocation();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  if (restoring) return <AuthInitializing />;
  const returnTo = `${location.pathname}${location.search}`;
  if (!session) return <AuthShell title="Sign in to authorize" subtitle="Your BringMyCart website session can authorize the extension without sharing your website tokens.">
    <Link to="/login" state={{ returnTo }} className="block rounded-xl bg-sky-600 px-4 py-3 text-center font-bold text-white">Sign in to BringMyCart</Link>
  </AuthShell>;

  const clientId = params.get('client_id') ?? '';
  const redirectUri = params.get('redirect_uri') ?? '';
  const state = params.get('state') ?? '';
  const codeChallenge = params.get('code_challenge') ?? '';
  const validRequest = clientId.length > 0 && redirectUri.startsWith('https://') && state.length >= 32 && codeChallenge.length === 43;
  if (!validRequest) return <AuthShell title="Authorization request unavailable" subtitle="Restart sign-in from the BringMyCart extension."><p className="text-sm text-slate-600">No valid extension authorization request was supplied.</p></AuthShell>;

  async function approve() {
    setBusy(true); setError(null);
    try {
      const result = await authApi.authorizeExtension({ clientId, redirectUri, state, codeChallenge });
      const callback = new URL(result.data.redirectUrl);
      const requested = new URL(redirectUri);
      if (callback.origin !== requested.origin || callback.pathname !== requested.pathname
        || callback.searchParams.get('state') !== state || !callback.searchParams.get('code')) {
        throw new Error('The extension callback did not match this authorization request.');
      }
      window.location.replace(callback.href);
    } catch (failure) {
      setError(failure instanceof Error && failure.message.startsWith('The extension callback')
        ? failure.message : authenticationErrorMessage(failure, 'login'));
      setBusy(false);
    }
  }

  return <AuthShell title="Authorize BringMyCart extension" subtitle={`Signed in as ${session.email}. Your extension will receive a separate, revocable session.`}>
    <p className="text-sm leading-6 text-slate-600">The extension can save supported Amazon India product links to your BringMyCart account. It will not access your Amazon account or checkout.</p>
    {error && <p role="alert" className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    <button onClick={() => void approve()} disabled={busy} className="mt-6 w-full rounded-xl bg-sky-600 px-4 py-3 font-bold text-white disabled:bg-slate-300">{busy ? 'Authorizing…' : 'Authorize extension'}</button>
  </AuthShell>;
}
