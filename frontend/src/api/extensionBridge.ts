type WebsiteAccountEvent = 'login' | 'logout';

interface ChromeRuntimeBridge {
  runtime?: {
    sendMessage(extensionId: string, message: { type: 'WEBSITE_ACCOUNT_EVENT'; event: WebsiteAccountEvent }, callback?: () => void): void;
  };
}

const extensionId = import.meta.env.VITE_BMC_EXTENSION_ID;

/** Sends a non-authoritative lifecycle signal. It never includes credentials or identity claims. */
export function notifyExtensionAccountEvent(event: WebsiteAccountEvent): Promise<void> {
  if (!extensionId) return Promise.resolve();
  try {
    const runtime = (globalThis as typeof globalThis & { chrome?: ChromeRuntimeBridge }).chrome?.runtime;
    if (!runtime) return Promise.resolve();
    return new Promise(resolve => {
      let settled = false;
      const finish = () => { if (!settled) { settled = true; resolve(); } };
      const timeout = window.setTimeout(finish, 1_500);
      runtime.sendMessage(extensionId, { type: 'WEBSITE_ACCOUNT_EVENT', event }, () => {
        window.clearTimeout(timeout);
        finish();
      });
    });
  } catch {
    // The extension may be unavailable, not installed, or not externally connected.
    return Promise.resolve();
  }
}
