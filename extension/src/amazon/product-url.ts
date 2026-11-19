export type RetailerId = 'AMAZON' | 'FLIPKART' | 'MYNTRA' | 'MEESHO' | 'AJIO' | 'NYKAA_FASHION' | 'NYKAA' | 'CROMA';
export type ProductPageResult =
  | { supported: true; retailer: RetailerId; productId: string; asin: string | null; originalUrl: string; canonicalUrl: string; variantId?: string; variantLabel?: string }
  | { supported: false; reason: 'missing_url' | 'invalid_url' | 'unsupported_domain' | 'unsupported_page' };

const hosts: Record<string, RetailerId> = {
  'amazon.in': 'AMAZON', 'www.amazon.in': 'AMAZON',
  'flipkart.com': 'FLIPKART', 'www.flipkart.com': 'FLIPKART',
  'myntra.com': 'MYNTRA', 'www.myntra.com': 'MYNTRA',
  'meesho.com': 'MEESHO', 'www.meesho.com': 'MEESHO',
  'ajio.com': 'AJIO', 'www.ajio.com': 'AJIO',
  'nykaafashion.com': 'NYKAA_FASHION', 'www.nykaafashion.com': 'NYKAA_FASHION',
  'nykaa.com': 'NYKAA', 'www.nykaa.com': 'NYKAA',
  'croma.com': 'CROMA', 'www.croma.com': 'CROMA'
};

export function parseRetailerProductUrl(value?: string): ProductPageResult {
  if (!value) return { supported: false, reason: 'missing_url' };
  let url: URL;
  try { url = new URL(value); } catch { return { supported: false, reason: 'invalid_url' }; }
  const host = url.hostname.toLowerCase();
  if (url.protocol !== 'https:' || !hosts[host] || url.username || url.password || url.port) {
    return { supported: false, reason: 'unsupported_domain' };
  }
  const retailer = hosts[host];
  const originalUrl = url.href;

  if (retailer === 'AMAZON') {
    const asin = productAsin(url.pathname);
    if (!asin) return { supported: false, reason: 'unsupported_page' };
    return supported(retailer, asin, asin, originalUrl, `https://www.amazon.in/dp/${asin}`);
  }
  if (retailer === 'FLIPKART') {
    const path = url.pathname.split('/').filter(Boolean);
    const pIndex = path.findIndex(part => part.toLowerCase() === 'p');
    if (pIndex < 0 || !path[pIndex + 1]) return { supported: false, reason: 'unsupported_page' };
    const pids = url.searchParams.getAll('pid');
    if (pids.length > 1 || pids.length === 1 && !/^[A-Za-z0-9_-]{5,80}$/.test(pids[0])) return { supported: false, reason: 'unsupported_page' };
    const productId = pids[0] ?? path[pIndex + 1];
    if (!/^[A-Za-z0-9_-]{5,80}$/.test(productId)) return { supported: false, reason: 'unsupported_page' };
    const canonical = new URL(`https://www.flipkart.com${url.pathname}`);
    if (pids[0]) canonical.searchParams.set('pid', pids[0]);
    return supported(retailer, productId, null, originalUrl, canonical.href);
  }

  const segments = url.pathname.split('/').filter(Boolean);
  if (retailer === 'MEESHO') {
    const pIndex = segments.findIndex(part => part.toLowerCase() === 'p');
    const id = pIndex >= 0 ? segments[pIndex + 1] : undefined;
    if (pIndex < 1 || pIndex !== segments.length - 2 || !id || !/^[a-z0-9]{4,40}$/i.test(id)) return { supported: false, reason: 'unsupported_page' };
    return supported(retailer, id.toLowerCase(), null, originalUrl, `https://www.meesho.com/${segments.slice(0, pIndex).join('/')}/p/${id.toLowerCase()}`);
  }

  if (retailer === 'AJIO') {
    const pIndex = segments.findIndex(part => part.toLowerCase() === 'p');
    const rawId = pIndex === segments.length - 2 ? segments[pIndex + 1] : undefined;
    const match = rawId?.match(/^(\d{4,24})(?:_([a-z0-9-]{1,40}))?$/i);
    if (!match) return { supported: false, reason: 'unsupported_page' };
    const productId = match[1];
    const variantId = match[2]?.toLowerCase();
    const variantLabel = variantId ? readableVariant(variantId) : undefined;
    const canonical = `https://www.ajio.com/${segments.slice(0, pIndex).join('/')}/p/${productId}${variantId ? `_${variantId}` : ''}`;
    return { ...supported(retailer, productId, null, originalUrl, canonical), ...(variantId ? { variantId, variantLabel } : {}) };
  }

  if (retailer === 'NYKAA' || retailer === 'NYKAA_FASHION' || retailer === 'CROMA') {
    const pIndex = segments.findIndex(part => part.toLowerCase() === 'p');
    const id = pIndex === segments.length - 2 ? segments[pIndex + 1] : undefined;
    if (!id || !/^\d{4,24}$/.test(id)) return { supported: false, reason: 'unsupported_page' };
    if (retailer === 'NYKAA') {
      const queryIds = url.searchParams.getAll('productId');
      if (queryIds.length > 1 || queryIds.length === 1 && queryIds[0] !== id) return { supported: false, reason: 'unsupported_page' };
    }
    const base = retailer === 'NYKAA' ? 'nykaa.com' : retailer === 'NYKAA_FASHION' ? 'nykaafashion.com' : 'croma.com';
    return supported(retailer, id, null, originalUrl, `https://www.${base}/${segments.slice(0, pIndex).join('/')}/p/${id}`);
  }

  if (retailer === 'MYNTRA') {
    let productId: string | undefined;
    for (let i = segments.length - 1; i >= 0; i--) {
      if (/^\d{4,20}$/.test(segments[i]) && (i === segments.length - 1 || segments[i + 1].toLowerCase() === 'buy')) {
        productId = segments[i]; break;
      }
    }
    if (!productId) return { supported: false, reason: 'unsupported_page' };
    return supported(retailer, productId, null, originalUrl, `https://www.myntra.com/${segments.join('/')}`);
  }
  return { supported: false, reason: 'unsupported_page' };
}

export function parseAmazonProductUrl(value?: string): ProductPageResult {
  const result = parseRetailerProductUrl(value);
  return result.supported && result.retailer === 'AMAZON' ? result
    : { supported: false, reason: result.supported ? 'unsupported_domain' : result.reason };
}

function supported(retailer: RetailerId, productId: string, asin: string | null, originalUrl: string, canonicalUrl: string): ProductPageResult {
  return { supported: true, retailer, productId, asin, originalUrl, canonicalUrl };
}

function readableVariant(value: string): string {
  return value.split(/[-_]/).filter(Boolean).map(part => part[0].toUpperCase() + part.slice(1)).join(' ');
}

function productAsin(pathname: string): string | undefined {
  const parts = pathname.split('/').filter(Boolean);
  const lower = parts.map(part => part.toLowerCase());
  let asin: string | undefined;
  const dpIndex = lower.findIndex((part, index) => part === 'dp' && index < parts.length - 1);
  if (dpIndex >= 0) {
    const suffix = parts.slice(dpIndex + 2);
    if (suffix.length > 0 && !suffix[0].toLowerCase().startsWith('ref=')) return undefined;
    asin = parts[dpIndex + 1];
  } else if (lower[0] === 'gp' && lower[1] === 'product' && parts.length >= 3) {
    const suffix = parts.slice(3);
    if (suffix.length > 0 && !suffix[0].toLowerCase().startsWith('ref=')) return undefined;
    asin = parts[2];
  } else if (lower[0] === 'gp' && lower[1] === 'aw' && lower[2] === 'd' && parts.length >= 4) {
    const suffix = parts.slice(4);
    if (suffix.length > 0 && !suffix[0].toLowerCase().startsWith('ref=')) return undefined;
    asin = parts[3];
  }
  const normalized = asin?.toUpperCase();
  return normalized && /^[A-Z0-9]{10}$/.test(normalized) ? normalized : undefined;
}
