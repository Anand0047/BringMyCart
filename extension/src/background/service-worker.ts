import { parseRetailerProductUrl } from '../amazon/product-url';
import { profileMatchesSession, saveAllowed, websiteEventStatus, type SyncStatus } from '../auth/account-sync';
import { callbackMatches, callbackResponseIsValid, createCodeChallenge, randomUrlSafe } from '../auth/pkce';
import type { ExtensionMessage, ExtensionMessageResponse, ExtensionUser } from '../shared/messages';

interface ExtensionSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  userId: string;
}
interface TokenResponse extends ExtensionSession { email: string; role: 'CUSTOMER' | 'ADMIN' }
interface ApiErrorBody { code?: string }

const webUrl = (import.meta.env.VITE_BMC_WEB_URL ?? 'http://localhost:5173').replace(/\/$/, '');
const webOrigin = new URL(webUrl).origin;
const apiUrl = (import.meta.env.VITE_BMC_API_URL ?? 'http://localhost:8080/api/v1').replace(/\/$/, '');
const clientId = import.meta.env.VITE_BMC_EXTENSION_CLIENT_ID ?? 'bmc-chrome-dev';
const registeredCallback = import.meta.env.VITE_EXTENSION_CALLBACK_URI
  ?? 'https://mlogpmpajiokfmjnlmehmpgphbbflpem.chromiumapp.org/bmc-auth';
const sessionKey = 'bmc-extension-session';
const statusKey = 'bmc-extension-sync-status';
const profileKey = 'bmc-extension-profile';
let refreshInFlight: Promise<ExtensionSession | null> | null = null;
let authFlowInFlight: Promise<boolean> | null = null;
let authGeneration = 0;
const saving = new Map<string, AbortController>();

void chrome.storage.session.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' });

async function currentSession(): Promise<ExtensionSession | null> {
  const stored = await chrome.storage.session.get(sessionKey);
  return (stored[sessionKey] as ExtensionSession | undefined) ?? null;
}

async function currentSyncStatus(): Promise<SyncStatus> {
  const values = await chrome.storage.session.get([statusKey, sessionKey]);
  return (values[statusKey] as SyncStatus | undefined) ?? (values[sessionKey] ? 'ready' : 'signed_out');
}

async function setSyncStatus(status: SyncStatus): Promise<void> {
  await chrome.storage.session.set({ [statusKey]: status });
}

async function setSession(session: ExtensionSession | null): Promise<void> {
  if (session) await chrome.storage.session.set({ [sessionKey]: session });
  else await chrome.storage.session.remove(sessionKey);
}

async function clearVerifiedProfile(): Promise<void> {
  await chrome.storage.session.remove(profileKey);
}

async function refreshSession(): Promise<ExtensionSession | null> {
  if (refreshInFlight) return refreshInFlight;
  const generation = authGeneration;
  const pending = (async () => {
    if (!saveAllowed(await currentSyncStatus())) return null;
    const session = await currentSession();
    if (!session) return null;
    try {
      const response = await fetch(`${apiUrl}/extension-auth/refresh`, {
        method: 'POST', credentials: 'omit', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken })
      });
      if (generation !== authGeneration) return null;
      if (!response.ok) {
        await setSession(null); await clearVerifiedProfile(); await setSyncStatus('signed_out'); await notifyTabs('signed_out'); return null;
      }
      const rotated = await response.json() as TokenResponse;
      if (!rotated.userId || rotated.userId !== session.userId || !rotated.accessToken || !rotated.refreshToken) {
        await revokeSession(session);
        await setSession(null); await clearVerifiedProfile(); await setSyncStatus('sync_required'); await notifyTabs('sync_required');
        return null;
      }
      const next = { accessToken: rotated.accessToken, refreshToken: rotated.refreshToken, expiresAt: rotated.expiresAt, userId: rotated.userId };
      await setSession(next);
      return next;
    } catch {
      return null;
    }
  })();
  refreshInFlight = pending;
  try { return await pending; } finally { if (refreshInFlight === pending) refreshInFlight = null; }
}

async function validSession(): Promise<ExtensionSession | null> {
  if (!saveAllowed(await currentSyncStatus())) return null;
  const session = await currentSession();
  if (!session) return null;
  return session.expiresAt * 1000 > Date.now() + 30_000 ? session : refreshSession();
}

async function performAuthorization(): Promise<boolean> {
  authGeneration++;
  for (const controller of saving.values()) controller.abort();
  const generation = authGeneration;
  const actualCallback = chrome.identity.getRedirectURL('bmc-auth');
  if (!callbackMatches(actualCallback, registeredCallback)) return false;
  const oldSession = await currentSession();
  if (oldSession) {
    const revoked = await revokeSession(oldSession);
    if (!revoked) return false;
  }
  await setSession(null);
  await clearVerifiedProfile();
  const state = randomUrlSafe(32);
  const verifier = randomUrlSafe(32);
  const challenge = await createCodeChallenge(verifier);
  await chrome.storage.session.set({ bmcPendingAuthorization: { state, verifier, redirectUri: actualCallback, expiresAt: Date.now() + 120_000 } });
  await setSyncStatus('synchronizing');
  await notifyTabs('synchronizing');
  try {
    const authorizationUrl = new URL('/extension/authorize', webUrl);
    authorizationUrl.searchParams.set('client_id', clientId);
    authorizationUrl.searchParams.set('redirect_uri', actualCallback);
    authorizationUrl.searchParams.set('state', state);
    authorizationUrl.searchParams.set('code_challenge', challenge);
    const callback = await chrome.identity.launchWebAuthFlow({ url: authorizationUrl.href, interactive: true });
    if (!callback) return false;
    const pending = (await chrome.storage.session.get('bmcPendingAuthorization')).bmcPendingAuthorization as
      { state: string; verifier: string; redirectUri: string; expiresAt: number } | undefined;
    if (!pending || pending.expiresAt <= Date.now() || pending.redirectUri !== actualCallback) return false;
    const callbackUrl = new URL(callback);
    const code = callbackUrl.searchParams.get('code');
    if (!callbackResponseIsValid(callback, actualCallback, pending.state) || !code) return false;
    const response = await fetch(`${apiUrl}/extension-auth/token`, {
      method: 'POST', credentials: 'omit', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, codeVerifier: pending.verifier, state: pending.state, clientId, redirectUri: pending.redirectUri })
    });
    if (!response.ok) return false;
    const tokens = await response.json() as TokenResponse;
    const session = { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, expiresAt: tokens.expiresAt, userId: tokens.userId };
    if (generation !== authGeneration) { await revokeSession(session); return false; }
    await setSession(session);
    const profile = await loadProfile(session);
    if (generation !== authGeneration) { await revokeSession(session); return false; }
    if (!profile || profile.userId !== tokens.userId) {
      await revokeSession(session);
      await setSession(null);
      await setSyncStatus('sync_required');
      await notifyTabs('sync_required');
      return false;
    }
    await chrome.storage.session.set({ [profileKey]: profile });
    await setSyncStatus('ready');
    await notifyTabs('ready');
    return true;
  } catch {
    return false;
  } finally {
    await chrome.storage.session.remove('bmcPendingAuthorization');
    if (await currentSyncStatus() === 'synchronizing') {
      await setSyncStatus('sync_required');
      await notifyTabs('sync_required');
    }
  }
}

function authorizeExtension(): Promise<boolean> {
  if (authFlowInFlight) return authFlowInFlight;
  const pending = performAuthorization();
  authFlowInFlight = pending;
  void pending.then(() => { if (authFlowInFlight === pending) authFlowInFlight = null; }, () => { if (authFlowInFlight === pending) authFlowInFlight = null; });
  return pending;
}

async function authenticatedFetch(path: string, init: RequestInit, expectedGeneration = authGeneration): Promise<Response | null> {
  let session = await validSession();
  if (!session || expectedGeneration !== authGeneration) return null;
  const send = (token: string) => {
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${token}`);
    return fetch(`${apiUrl}${path}`, { ...init, credentials: 'omit', signal: init.signal, headers });
  };
  let response = await send(session.accessToken);
  if (expectedGeneration !== authGeneration) return null;
  if (response.status === 401) {
    session = await refreshSession();
    if (!session || expectedGeneration !== authGeneration) return null;
    response = await send(session.accessToken);
    if (response.status === 401) {
      await setSession(null); await clearVerifiedProfile(); await setSyncStatus('signed_out'); await notifyTabs('signed_out'); return null;
    }
  }
  return expectedGeneration === authGeneration ? response : null;
}

async function loadProfile(session: ExtensionSession): Promise<ExtensionUser | null> {
  let response: Response;
  try {
    response = await fetch(`${apiUrl}/identity/me`, {
      method: 'GET', credentials: 'omit', headers: { Authorization: `Bearer ${session.accessToken}` }
    });
  } catch { return null; }
  if (!response.ok) return null;
  try {
    const profile = await response.json() as ExtensionUser;
    return profileMatchesSession(profile, session.userId) ? profile : null;
  } catch { return null; }
}

async function authStatus(): Promise<ExtensionMessageResponse> {
  const generation = authGeneration;
  const syncStatus = await currentSyncStatus();
  if (syncStatus !== 'ready') return { authenticated: false, syncStatus };
  const session = await validSession();
  if (!session) return { authenticated: false, syncStatus: await currentSyncStatus() };
  const profile = await loadProfile(session);
  if (generation !== authGeneration) return { authenticated: false, syncStatus: await currentSyncStatus() };
  if (!profile || profile.userId !== session.userId) {
    await setSyncStatus('sync_required'); await clearVerifiedProfile(); await notifyTabs('sync_required');
    return { authenticated: false, syncStatus: 'sync_required' };
  }
  await chrome.storage.session.set({ [profileKey]: profile });
  return { authenticated: true, syncStatus: 'ready', user: profile };
}

async function saveProduct(url: string, metadata?: import('../shared/messages').ProductPageMetadata): Promise<ExtensionMessageResponse> {
  const parsed = parseRetailerProductUrl(url);
  if (!parsed.supported) return { ok: false, error: 'invalid_product' };
  if (!saveAllowed(await currentSyncStatus())) return { ok: false, error: 'account_sync_required' };
  const saveKey = `${parsed.retailer}:${parsed.productId}`;
  if (saving.has(saveKey)) return { ok: false, error: 'save_in_progress' };
  const generation = authGeneration;
  const controller = new AbortController();
  saving.set(saveKey, controller);
  try {
    const response = await authenticatedFetch('/saved-products', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url: parsed.originalUrl, metadata }), signal: controller.signal
    }, generation);
    if (generation !== authGeneration) return { ok: false, error: 'account_sync_required' };
    if (!response) return { ok: false, error: 'authentication_required' };
    if (response.status === 409) {
      const error = await response.json() as ApiErrorBody;
      if (error.code === 'duplicate_saved_product') return { ok: true, result: 'duplicate', asin: parsed.asin ?? undefined };
      return { ok: false, error: 'server_error' };
    }
    if (response.status === 401) return { ok: false, error: 'authentication_required' };
    if (!response.ok) return { ok: false, error: 'server_error' };
    return { ok: true, result: 'saved', asin: parsed.asin ?? undefined };
  } catch { return { ok: false, error: controller.signal.aborted ? 'account_sync_required' : 'network_error' }; }
  finally { if (saving.get(saveKey) === controller) saving.delete(saveKey); }
}

async function notifyTabs(syncStatus: SyncStatus): Promise<void> {
  try {
    const tabs = await chrome.tabs.query({ url: ['https://amazon.in/*', 'https://www.amazon.in/*',
      'https://flipkart.com/*', 'https://www.flipkart.com/*', 'https://myntra.com/*', 'https://www.myntra.com/*'] });
    await Promise.all(tabs.flatMap(tab => tab.id === undefined ? [] : [
      chrome.tabs.sendMessage(tab.id, { type: 'AUTH_STATE_CHANGED', authenticated: syncStatus === 'ready', syncStatus }).catch(() => undefined)
    ]));
  } catch { /* Amazon tab may close or not yet have a content script. */ }
}

async function revokeSession(session: ExtensionSession): Promise<boolean> {
  try {
    const response = await fetch(`${apiUrl}/extension-auth/logout`, {
      method: 'POST', credentials: 'omit', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken })
    });
    return response.ok;
  } catch { return false; }
}

async function handleWebsiteAccountEvent(event: 'login' | 'logout'): Promise<void> {
  authGeneration++;
  const generation = authGeneration;
  for (const controller of saving.values()) controller.abort();
  const nextStatus = websiteEventStatus(event);
  await setSyncStatus(nextStatus);
  await clearVerifiedProfile();
  await notifyTabs(nextStatus);
  const session = await currentSession();
  if (session) {
    const revoked = await revokeSession(session);
    if (generation !== authGeneration) return;
    if (revoked) {
      const current = await currentSession();
      if (current?.refreshToken === session.refreshToken) await setSession(null);
    } else {
      await setSyncStatus('sync_required');
      await notifyTabs('sync_required');
    }
  }
  // A failed revocation keeps the opaque refresh credential solely in trusted storage,
  // but syncStatus blocks all use until the user retries/reconnects.
}

async function activeProduct(): Promise<ExtensionMessageResponse> {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const result = tab?.url ? parseRetailerProductUrl(tab.url) : null;
    if (!result?.supported || tab?.id === undefined) return { supported: false };
    let metadata: import('../shared/messages').ProductPageMetadata | undefined;
    try {
      const page = await chrome.tabs.sendMessage(tab.id, { type: 'GET_PAGE_METADATA' }) as { metadata?: import('../shared/messages').ProductPageMetadata };
      metadata = page?.metadata;
    } catch { /* A tab may not yet have the content script; save still works without fallback metadata. */ }
    return { supported: true, retailer: result.retailer, productId: result.productId, asin: result.asin,
      canonicalUrl: result.canonicalUrl, originalUrl: result.originalUrl, metadata };
  } catch { return { supported: false }; }
}

function trustedSender(sender: chrome.runtime.MessageSender): boolean {
  if (sender.id !== chrome.runtime.id) return false;
  if (sender.url?.startsWith(`chrome-extension://${chrome.runtime.id}/`)) return true;
  try {
    const origin = new URL(sender.url ?? '').origin;
    return ['https://amazon.in', 'https://www.amazon.in', 'https://flipkart.com',
      'https://www.flipkart.com', 'https://myntra.com', 'https://www.myntra.com'].includes(origin);
  } catch { return false; }
}

chrome.runtime.onMessage.addListener((message: ExtensionMessage, sender, sendResponse: (response: ExtensionMessageResponse) => void) => {
  if (!trustedSender(sender)) { sendResponse({ ok: false, error: 'chrome_api_error' }); return; }
  if (message?.type === 'AUTH_STATE_CHANGED' || message?.type === 'WEBSITE_ACCOUNT_EVENT') return;
  if (message?.type === 'GET_AUTH_STATUS') { void authStatus().then(sendResponse); return true; }
  if (message?.type === 'GET_ACTIVE_PRODUCT') { void activeProduct().then(sendResponse); return true; }
  if (message?.type === 'START_EXTENSION_AUTH' || message?.type === 'OPEN_BMC_LOGIN') {
    void authorizeExtension().then(success => {
      if (!success) sendResponse({ ok: false, error: registeredCallback === chrome.identity.getRedirectURL('bmc-auth') ? 'authorization_failed' : 'callback_mismatch' });
      else sendResponse({ ok: true });
    }).catch(() => sendResponse({ ok: false, error: 'authorization_failed' }));
    return true;
  }
  if (message?.type === 'SAVE_PRODUCT') { void saveProduct(message.url, message.metadata).then(sendResponse); return true; }
  if (message?.type === 'LOGOUT_EXTENSION') {
    void (async () => {
      authGeneration++;
      for (const controller of saving.values()) controller.abort();
      await setSyncStatus('sync_required');
      await clearVerifiedProfile();
      await notifyTabs('sync_required');
      const session = await currentSession();
      if (session && !await revokeSession(session)) { sendResponse({ ok: false, error: 'network_error' }); return; }
      await setSession(null); await clearVerifiedProfile(); await setSyncStatus('signed_out'); await notifyTabs('signed_out');
      sendResponse({ ok: true });
    })();
    return true;
  }
  sendResponse({ ok: false, error: 'chrome_api_error' });
});

chrome.runtime.onMessageExternal.addListener((message: { type?: string; event?: string }, sender, sendResponse) => {
  let origin: string;
  try { origin = new URL(sender.url ?? '').origin; } catch { sendResponse({ ok: false }); return; }
  if (origin !== webOrigin || message?.type !== 'WEBSITE_ACCOUNT_EVENT'
    || (message.event !== 'login' && message.event !== 'logout')) {
    sendResponse({ ok: false });
    return;
  }
  void handleWebsiteAccountEvent(message.event).then(() => sendResponse({ ok: true }));
  return true;
});
