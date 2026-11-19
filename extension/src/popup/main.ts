import './style.css';
import { displayUser } from '../auth/account-sync';
import type { ExtensionMessage, ExtensionMessageResponse, ProductPageMetadata, ExtensionUser } from '../shared/messages';

const dashboardUrl = import.meta.env.VITE_BMC_DASHBOARD_URL ?? 'http://localhost:5173/dashboard';
const connection = document.querySelector<HTMLElement>('#connection')!;
const greeting = document.querySelector<HTMLElement>('#greeting')!;
const accountName = document.querySelector<HTMLElement>('#account-name')!;
const avatar = document.querySelector<HTMLElement>('#avatar')!;
const productStatus = document.querySelector<HTMLElement>('#product-status')!;
const details = document.querySelector<HTMLElement>('#product-details')!;
const asinElement = document.querySelector<HTMLElement>('#asin')!;
const urlElement = document.querySelector<HTMLAnchorElement>('#url')!;
const feedback = document.querySelector<HTMLElement>('#feedback')!;
const saveButton = document.querySelector<HTMLButtonElement>('#save')!;
const authButton = document.querySelector<HTMLButtonElement>('#auth')!;
const logoutButton = document.querySelector<HTMLButtonElement>('#logout')!;
let productUrl: string | null = null;
let productMetadata: ProductPageMetadata | undefined;
let currentRetailer = 'Retailer';
let ready = false;
let statusRevision = 0;

function send(message: ExtensionMessage): Promise<ExtensionMessageResponse> {
  return new Promise(resolve => chrome.runtime.sendMessage(message, response => {
    resolve(chrome.runtime.lastError ? { ok: false, error: 'chrome_api_error' } : response);
  }));
}

function showFeedback(message: string) {
  feedback.textContent = message;
  feedback.hidden = false;
}

async function loadProduct() {
  const response = await send({ type: 'GET_ACTIVE_PRODUCT' });
  if (!('supported' in response) || !response.supported) {
    productUrl = null;
    productMetadata = undefined;
    details.hidden = true;
    productStatus.textContent = 'Open a supported retailer product page to save it here.';
    saveButton.disabled = true;
    return;
  }
  currentRetailer = retailerName(response.retailer);
  productUrl = response.originalUrl ?? response.canonicalUrl ?? null;
  productMetadata = response.metadata;
  asinElement.textContent = response.asin ? `Amazon ASIN: ${response.asin}`
    : `${currentRetailer} product: ${response.productId ?? ''}${response.variantLabel ? ` · ${response.variantLabel}` : ''}`;
  urlElement.href = response.canonicalUrl ?? '#';
  urlElement.textContent = 'View product page';
  details.hidden = false;
  productStatus.textContent = `This ${currentRetailer} product page is ready to add to your collection.`;
  saveButton.disabled = !ready || !productUrl;
}

async function refreshStatus() {
  const revision = statusRevision;
  connection.className = 'connection pending';
  connection.lastElementChild!.textContent = 'Checking';
  greeting.textContent = 'Welcome';
  accountName.textContent = 'Checking your account…';
  avatar.hidden = true;
  const response = await send({ type: 'GET_AUTH_STATUS' });
  if (revision !== statusRevision) return;
  const authenticated = 'authenticated' in response && response.authenticated;
  const syncStatus = 'syncStatus' in response ? response.syncStatus : undefined;
  const user = 'user' in response ? response.user : undefined;
  ready = authenticated && syncStatus === 'ready';
  connection.className = `connection ${ready ? 'connected' : 'disconnected'}`;
  connection.lastElementChild!.textContent = ready ? 'Connected'
    : syncStatus === 'sync_required' ? 'Sync needed'
    : syncStatus === 'synchronizing' ? 'Synchronizing' : 'Not connected';
  authButton.hidden = ready || syncStatus === 'synchronizing';
  authButton.textContent = syncStatus === 'sync_required' ? 'Connect current account' : 'Sign in to BringMyCart';
  logoutButton.hidden = !ready && syncStatus !== 'sync_required';
  if (ready && user) {
    const identity = displayUser(user);
    greeting.textContent = identity.greeting;
    accountName.textContent = `Connected account: ${identity.account}`;
    avatar.textContent = identity.initials;
    avatar.hidden = false;
  } else if (syncStatus === 'sync_required') {
    greeting.textContent = 'Account changed';
    accountName.textContent = 'Reconnect to use the current website account.';
  } else if (syncStatus === 'synchronizing') {
    greeting.textContent = 'Connecting your account';
    accountName.textContent = 'Complete approval in the BringMyCart window.';
  } else {
    greeting.textContent = 'Welcome to BringMyCart';
    accountName.textContent = 'Sign in to connect your collection.';
  }
  saveButton.disabled = !ready || !productUrl;
  await loadProduct();
}

authButton.addEventListener('click', async () => {
  authButton.disabled = true;
  showFeedback('Continue in the BringMyCart authorization page to connect this account.');
  const result = await send({ type: 'START_EXTENSION_AUTH' });
  if ('ok' in result && !result.ok) showFeedback(result.error === 'callback_mismatch'
    ? 'This extension ID does not match the registered development callback.'
    : 'Authorization was not completed. Your previous account remains blocked.');
  await refreshStatus();
  authButton.disabled = false;
});

logoutButton.addEventListener('click', async () => {
  logoutButton.disabled = true;
  const result = await send({ type: 'LOGOUT_EXTENSION' });
  if ('ok' in result && !result.ok) showFeedback('Could not revoke the extension session. Saving remains blocked; please retry.');
  else { showFeedback('Extension disconnected.'); await refreshStatus(); }
  logoutButton.disabled = false;
});

saveButton.addEventListener('click', async () => {
  if (!productUrl || !ready || saveButton.disabled) return;
  saveButton.disabled = true;
  saveButton.textContent = 'Saving…';
  feedback.hidden = true;
  const result = await send({ type: 'SAVE_PRODUCT', url: productUrl, metadata: productMetadata });
  if ('ok' in result && result.ok) showFeedback(result.result === 'duplicate' ? 'Already in your collection.' : 'Product saved to your collection.');
  else {
    const error = 'error' in result ? result.error : 'server_error';
    showFeedback(error === 'authentication_required' || error === 'account_sync_required'
      ? 'Your account needs to reconnect before saving.'
      : error === 'network_error' ? 'Could not reach BringMyCart. Please try again.'
      : 'BringMyCart could not save this product. Please try again.');
    if (error === 'authentication_required' || error === 'account_sync_required') await refreshStatus();
  }
  saveButton.textContent = 'Save Item';
  saveButton.disabled = !ready || !productUrl;
});

document.querySelector<HTMLButtonElement>('#dashboard')!.addEventListener('click', () => chrome.tabs.create({ url: dashboardUrl }));

function retailerName(retailer?: string): string {
  const names: Record<string, string> = {
    AMAZON: 'Amazon India', FLIPKART: 'Flipkart', MYNTRA: 'Myntra', MEESHO: 'Meesho',
    AJIO: 'AJIO', NYKAA_FASHION: 'Nykaa Fashion', NYKAA: 'Nykaa', CROMA: 'Croma'
  };
  return retailer ? names[retailer] ?? 'Retailer' : 'Retailer';
}

chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName === 'session' && changes['bmc-extension-sync-status']) {
    statusRevision++;
    void refreshStatus();
  }
});
void refreshStatus();
