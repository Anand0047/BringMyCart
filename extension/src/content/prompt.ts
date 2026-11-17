import { parseAmazonProductUrl } from '../amazon/product-url';
import type { ExtensionMessage, ExtensionMessageResponse } from '../shared/messages';

const host = document.createElement('div');
host.id = 'bringmycart-prompt-host';
const shadow = host.attachShadow({ mode: 'closed' });
const root = document.createElement('section');
root.setAttribute('role', 'dialog');
root.setAttribute('aria-label', 'BringMyCart product prompt');
root.innerHTML = `<style>
  :host{all:initial}section{position:fixed;z-index:2147483647;right:20px;bottom:20px;width:320px;box-sizing:border-box;padding:18px;border:1px solid #d9e3de;border-radius:14px;background:#fff;color:#1b2924;box-shadow:0 12px 36px #10251e30;font:14px/1.45 system-ui,sans-serif}header{display:flex;align-items:center;gap:10px;margin-bottom:12px}.mark{display:grid;place-items:center;width:34px;height:34px;border-radius:11px;background:#176b55;color:white;font-weight:800}strong{font-size:15px}p{margin:8px 0 14px;color:#52615b}small{display:block;overflow-wrap:anywhere;color:#708079;font-size:11px}footer{display:flex;gap:8px}button{min-height:36px;padding:0 12px;border-radius:8px;font:600 13px system-ui;cursor:pointer}.primary{border:0;background:#176b55;color:white}.secondary{border:1px solid #d5dfda;background:white;color:#31423b}.close{margin-left:auto;border:0;background:transparent;color:#62736b;font-size:20px}
</style><header><span class="mark">B</span><strong>BringMyCart</strong><button class="close" aria-label="Dismiss">×</button></header><p id="message"></p><small id="asin"></small><footer><button class="primary" id="action"></button><button class="secondary" id="dismiss"></button></footer>`;
shadow.append(root);
let currentAsin: string | null = null;
let lastUrl = location.href;
let actionBusy = false;
let authRevision = 0;

function send(message: ExtensionMessage): Promise<ExtensionMessageResponse> {
  return new Promise(resolve => chrome.runtime.sendMessage(message, response => {
    resolve(chrome.runtime.lastError ? { ok: false, error: 'chrome_api_error' } : response);
  }));
}

function dismiss(asin: string) {
  try { sessionStorage.setItem(`bmc-prompt-dismissed:${asin}`, '1'); } catch { /* session storage may be unavailable */ }
  host.remove(); currentAsin = null;
}

async function showForCurrentUrl() {
  const product = parseAmazonProductUrl(location.href);
  if (!product.supported) { host.remove(); currentAsin = null; return; }
  if (currentAsin === product.asin && host.isConnected) { await updatePrompt(product.asin, product.originalUrl); return; }
  host.remove(); currentAsin = product.asin;
  actionBusy = false;
  try { if (sessionStorage.getItem(`bmc-prompt-dismissed:${product.asin}`)) return; } catch { /* show prompt without persistence */ }

  if (!host.isConnected) document.documentElement.append(host);
  root.querySelector('#asin')!.textContent = `Amazon ASIN: ${product.asin}`;
  await updatePrompt(product.asin, product.originalUrl);
}

async function updatePrompt(asin: string, originalUrl: string) {
  const revision = authRevision;
  const authenticated = await send({ type: 'GET_AUTH_STATUS' });
  if (currentAsin !== asin || revision !== authRevision) return;
  const isAuthenticated = 'authenticated' in authenticated && authenticated.authenticated;
  const syncStatus = 'syncStatus' in authenticated ? authenticated.syncStatus : undefined;
  root.querySelector('#message')!.textContent = isAuthenticated
    ? 'Do you want to save this product to BringMyCart?'
    : syncStatus === 'sync_required' ? 'Your website account changed. Connect to BringMyCart before saving.'
    : syncStatus === 'synchronizing' ? 'Synchronizing your BringMyCart account…'
    : 'Log in to BringMyCart to save this product.';
  const action = root.querySelector<HTMLButtonElement>('#action')!;
  const dismissButton = root.querySelector<HTMLButtonElement>('#dismiss')!;
  action.textContent = actionBusy ? 'Saving…' : isAuthenticated ? 'Save Item'
    : syncStatus === 'sync_required' ? 'Connect account'
    : syncStatus === 'synchronizing' ? 'Synchronizing…' : 'Log in to BMC';
  action.disabled = actionBusy || syncStatus === 'synchronizing';
  action.title = '';
  dismissButton.textContent = isAuthenticated ? 'No, Thanks' : 'Not Now';
  dismissButton.onclick = () => dismiss(asin);
  root.querySelector<HTMLButtonElement>('.close')!.onclick = () => dismiss(asin);
  action.onclick = async () => {
    if (!isAuthenticated) {
      const auth = await send({ type: 'START_EXTENSION_AUTH' });
      if (!('ok' in auth) || !auth.ok) root.querySelector('#message')!.textContent =
        'BringMyCart sign-in was not completed. You can try again when ready.';
      else await updatePrompt(asin, originalUrl);
      return;
    }
    if (actionBusy) return;
    actionBusy = true;
    action.textContent = 'Saving…'; action.disabled = true;
    const result = await send({ type: 'SAVE_PRODUCT', url: originalUrl });
    actionBusy = false;
    if ('ok' in result && result.ok && result.result === 'saved') {
      root.querySelector('#message')!.textContent = 'Product saved to your BringMyCart collection.';
      action.textContent = 'Saved'; action.disabled = true;
    } else if ('ok' in result && result.ok && result.result === 'duplicate') {
      root.querySelector('#message')!.textContent = 'This product is already in your BringMyCart collection.';
      action.textContent = 'Already saved'; action.disabled = true;
    } else {
      const error = 'error' in result ? result.error : 'server_error';
      if (error === 'account_sync_required') {
        authRevision++;
        await updatePrompt(asin, originalUrl);
        return;
      }
      root.querySelector('#message')!.textContent = error === 'authentication_required'
        ? 'Your BringMyCart extension session expired. Please log in again.'
        : error === 'network_error' ? 'Unable to connect to BringMyCart. Please try again.'
        : error === 'save_in_progress' ? 'This product is already being saved.'
        : 'BringMyCart could not save this product. Please try again.';
      action.textContent = error === 'authentication_required' ? 'Log in to BMC' : 'Save Item';
      action.disabled = false;
      if (error === 'authentication_required') action.onclick = () => { void send({ type: 'START_EXTENSION_AUTH' }); };
    }
  };
}

chrome.runtime.onMessage.addListener((message: ExtensionMessage) => {
  if (message?.type === 'AUTH_STATE_CHANGED') { authRevision++; actionBusy = false; void showForCurrentUrl(); }
});

function onNavigation() {
  if (lastUrl === location.href) return;
  lastUrl = location.href;
  void showForCurrentUrl();
}

window.addEventListener('popstate', onNavigation);
window.addEventListener('hashchange', onNavigation);
const originalPushState = history.pushState.bind(history);
history.pushState = ((...args: Parameters<History['pushState']>) => { originalPushState(...args); onNavigation(); }) as History['pushState'];
const originalReplaceState = history.replaceState.bind(history);
history.replaceState = ((...args: Parameters<History['replaceState']>) => { originalReplaceState(...args); onNavigation(); }) as History['replaceState'];
void showForCurrentUrl();
