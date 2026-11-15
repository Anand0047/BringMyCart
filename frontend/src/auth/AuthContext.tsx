import { createContext, useCallback, useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { authApi } from '../api/authApi';
import { setAccessToken, setSessionCallbacks } from '../api/client';
import type { AuthResponse } from '../api/types';

interface AuthContextValue {
  session: AuthResponse | null;
  restoring: boolean;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<AuthResponse | null>(null);
  const [restoring, setRestoring] = useState(true);

  const applySession = useCallback((next: AuthResponse) => {
    setAccessToken(next.accessToken);
    setSession(next);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setSession(null);
  }, []);

  useEffect(() => {
    let active = true;
    setSessionCallbacks({ onRefreshed: applySession, onExpired: clearSession });
    authApi.initializeCsrf()
      .then(() => authApi.refresh())
      .then((response) => { if (active) applySession(response); })
      .catch(() => { if (active) clearSession(); })
      .finally(() => { if (active) setRestoring(false); });
    return () => { active = false; };
  }, [applySession, clearSession]);

  const value = useMemo<AuthContextValue>(() => ({
    session,
    restoring,
    login: async (email, password) => applySession(await authApi.login(email, password)),
    register: async (email, password) => { await authApi.register(email, password); },
    logout: async () => {
      try { await authApi.logout(); } finally { clearSession(); }
    }
  }), [applySession, clearSession, restoring, session]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
