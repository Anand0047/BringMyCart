export type ExtensionMessage =
  | { type: 'GET_AUTH_STATUS' }
  | { type: 'GET_ACTIVE_PRODUCT' }
  | { type: 'GET_PAGE_METADATA' }
  | { type: 'START_EXTENSION_AUTH' }
  | { type: 'SAVE_PRODUCT'; url: string; metadata?: ProductPageMetadata }
  | { type: 'OPEN_BMC_LOGIN' }
  | { type: 'LOGOUT_EXTENSION' }
  | { type: 'AUTH_STATE_CHANGED'; authenticated: boolean; syncStatus?: 'ready' | 'signed_out' | 'sync_required' | 'synchronizing'; user?: ExtensionUser }
  | { type: 'WEBSITE_ACCOUNT_EVENT'; event: 'login' | 'logout' };

export interface ExtensionUser {
  userId: string;
  email: string;
  fullName: string | null;
  role: string;
}

export interface ProductPageMetadata {
  title?: string;
  brand?: string;
  imageUrl?: string;
  price?: number;
  currency?: 'INR';
  availability?: 'IN_STOCK' | 'OUT_OF_STOCK' | 'UNKNOWN';
}

export type ExtensionMessageResponse =
  | { authenticated: boolean; syncStatus?: 'ready' | 'signed_out' | 'sync_required' | 'synchronizing'; user?: ExtensionUser }
  | { supported: boolean; retailer?: 'AMAZON' | 'FLIPKART' | 'MYNTRA' | 'MEESHO' | 'AJIO' | 'NYKAA_FASHION' | 'NYKAA' | 'CROMA'; productId?: string; asin?: string | null; variantId?: string; variantLabel?: string; canonicalUrl?: string; originalUrl?: string; metadata?: ProductPageMetadata }
  | { metadata?: ProductPageMetadata }
  | { ok: true; result?: 'saved' | 'duplicate'; asin?: string }
  | { ok: false; error: 'chrome_api_error' | 'authorization_failed' | 'callback_mismatch' | 'authentication_required' | 'account_sync_required' | 'duplicate' | 'invalid_product' | 'server_error' | 'network_error' | 'save_in_progress' };
