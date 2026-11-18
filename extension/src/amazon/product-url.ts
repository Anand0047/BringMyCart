export type RetailerId = 'AMAZON' | 'FLIPKART' | 'MYNTRA';
export type ProductPageResult =
  | { supported: true; retailer: RetailerId; productId: string; asin: string | null; originalUrl: string; canonicalUrl: string }
  | { supported: false; reason: 'missing_url' | 'invalid_url' | 'unsupported_domain' | 'unsupported_page' };

const hosts = new Set(['amazon.in', 'www.amazon.in', 'flipkart.com', 'www.flipkart.com', 'myntra.com', 'www.myntra.com']);

export function parseRetailerProductUrl(value?: string): ProductPageResult {
  if (!value) return { supported: false, reason: 'missing_url' };
  let url: URL;
  try { url = new URL(value); } catch { return { supported: false, reason: 'invalid_url' }; }
  const host = url.hostname.toLowerCase();
  if (url.protocol !== 'https:' || !hosts.has(host) || url.username || url.password || url.port) {
    return { supported: false, reason: 'unsupported_domain' };
  }

  if (host === 'amazon.in' || host === 'www.amazon.in') {
    const asin = productAsin(url.pathname);
    if (!asin) return { supported: false, reason: 'unsupported_page' };
    return { supported: true, retailer: 'AMAZON', productId: asin, asin, originalUrl: url.href,
      canonicalUrl: `https://www.amazon.in/dp/${asin}` };
  }
  if (host === 'flipkart.com' || host === 'www.flipkart.com') {
    const path = url.pathname.split('/').filter(Boolean);
    const pIndex = path.findIndex(part => part.toLowerCase() === 'p');
    if (pIndex < 0 || !path[pIndex + 1]) return { supported: false, reason: 'unsupported_page' };
    const pathId = path[pIndex + 1];
    const pids = url.searchParams.getAll('pid');
    if (pids.length > 1 || pids.length === 1 && !/^[A-Za-z0-9_-]{5,80}$/.test(pids[0])) {
      return { supported: false, reason: 'unsupported_page' };
    }
    const pid = pids[0];
    const productId = pid ?? pathId;
    if (!/^[A-Za-z0-9_-]{5,80}$/.test(productId)) return { supported: false, reason: 'unsupported_page' };
    const canonical = new URL(`https://www.flipkart.com${url.pathname}`);
    if (pid) canonical.searchParams.set('pid', pid);
    return { supported: true, retailer: 'FLIPKART', productId, asin: null, originalUrl: url.href, canonicalUrl: canonical.href };
  }
  const segments = url.pathname.split('/').filter(Boolean);
  let productId: string | undefined;
  for (let i = segments.length - 1; i >= 0; i--) {
    if (/^\d{4,20}$/.test(segments[i]) && (i === segments.length - 1 || segments[i + 1].toLowerCase() === 'buy')) {
      productId = segments[i]; break;
    }
  }
  if (!productId) return { supported: false, reason: 'unsupported_page' };
  return { supported: true, retailer: 'MYNTRA', productId, asin: null, originalUrl: url.href,
    canonicalUrl: `https://www.myntra.com/${segments.join('/')}` };
}

export function parseAmazonProductUrl(value?: string): ProductPageResult {
  const result = parseRetailerProductUrl(value);
  return result.supported && result.retailer === 'AMAZON' ? result
    : { supported: false, reason: result.supported ? 'unsupported_domain' : result.reason };
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
