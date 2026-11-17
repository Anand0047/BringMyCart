export type ProductPageResult = { supported: true; asin: string; originalUrl: string; canonicalUrl: string } | { supported: false; reason: 'missing_url' | 'invalid_url' | 'unsupported_domain' | 'unsupported_page' };
const hosts = new Set(['amazon.in', 'www.amazon.in']);

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

export function parseAmazonProductUrl(value?: string): ProductPageResult {
  if (!value) return { supported: false, reason: 'missing_url' };
  let url: URL;
  try { url = new URL(value); } catch { return { supported: false, reason: 'invalid_url' }; }
  if (url.protocol !== 'https:' || !hosts.has(url.hostname.toLowerCase()) || url.username || url.password || url.port) return { supported: false, reason: 'unsupported_domain' };
  const asin = productAsin(url.pathname);
  if (!asin) return { supported: false, reason: 'unsupported_page' };
  return { supported: true, asin, originalUrl: url.href, canonicalUrl: `https://www.amazon.in/dp/${asin}` };
}
