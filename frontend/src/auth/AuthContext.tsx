import { createContext, useCallback, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react';
import { authApi } from '../api/authApi';
import { notifyExtensionAccountEvent } from '../api/extensionBridge';
import { setAccessToken, setSessionCallbacks } from '../api/client';
import type { AuthResponse } from '../api/types';

interface AuthContextValue {
  session: AuthResponse | null;
  restoring: boolean;
  login(email: string, password: string): Promise<void>;
  register(fullName: string, email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<AuthResponse | null>(null);
  const [restoring, setRestoring] = useState(true);
  const channelRef = useRef<BroadcastChannel | null>(null);
  const crossTabRestoreRef = useRef<Promise<void> | null>(null);

  const applySession = useCallback((next: AuthResponse) => {
    setAccessToken(next.accessToken);
    setSession(next);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setSession(null);
  }, []);

  const expireSession = useCallback(async () => {
    clearSession();
    channelRef.current?.postMessage({ type: 'logout' });
    await notifyExtensionAccountEvent('logout');
  }, [clearSession]);

  useEffect(() => {
    let active = true;
    let channel: BroadcastChannel | null = null;
    setSessionCallbacks({ onRefreshed: applySession, onExpired: expireSession });
    authApi.initializeCsrf()
      .then(() => authApi.refresh())
      .then((response) => { if (active) applySession(response); })
      .catch(async () => {
        if (active) {
          await expireSession();
        }
      })
      .finally(() => { if (active) setRestoring(false); });
    if ('BroadcastChannel' in window) {
      const activeChannel = new BroadcastChannel('bmc-website-account');
      channel = activeChannel;
      channelRef.current = activeChannel;
      activeChannel.onmessage = (event: MessageEvent<{ type?: string }>) => {
        if (event.data?.type === 'logout') {
          clearSession();
          setRestoring(false);
          return;
        }
        if (event.data?.type === 'login' && !crossTabRestoreRef.current) {
          clearSession();
          setRestoring(true);
          crossTabRestoreRef.current = authApi.initializeCsrf().then(() => authApi.refresh())
            .then((response) => { if (active) applySession(response); })
            .catch(async () => { if (active) await expireSession(); })
            .finally(() => {
              crossTabRestoreRef.current = null;
              if (active) setRestoring(false);
            });
        }
      };
    }
    return () => {
      active = false;
      channel?.close();
      if (channelRef.current === channel) channelRef.current = null;
    };
  }, [applySession, clearSession, expireSession]);

  const value = useMemo<AuthContextValue>(() => ({
    session,
    restoring,
    login: async (email, password) => {
      applySession(await authApi.login(email, password));
      channelRef.current?.postMessage({ type: 'login' });
      await notifyExtensionAccountEvent('login');
    },
    register: async (fullName, email, password) => { await authApi.register(fullName, email, password); },
    logout: async () => {
      try { await authApi.logout(); } finally {
        clearSession();
        channelRef.current?.postMessage({ type: 'logout' });
        await notifyExtensionAccountEvent('logout');
      }
    }
  }), [applySession, clearSession, restoring, session]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
