import type { ProductPageMetadata } from '../shared/messages';

interface ProductNode {
  '@type'?: string | string[];
  name?: string;
  brand?: string | { name?: string };
  image?: string | string[] | { url?: string };
  sku?: string;
  productID?: string;
  productId?: string;
  offers?: OfferNode | OfferNode[];
}
interface OfferNode { price?: string | number; priceCurrency?: string; availability?: string }

export function extractPageMetadata(page: Document, productId?: string): ProductPageMetadata {
  const structured = productId ? matchingProduct(page, productId) : undefined;
  const read = (selector: string) => page.querySelector<HTMLMetaElement>(selector)?.content?.trim() || undefined;
  const jsonOffer = structured?.offers ? (Array.isArray(structured.offers) ? structured.offers[0] : structured.offers) : undefined;
  const rawPrice = (typeof jsonOffer?.price === 'number' ? String(jsonOffer.price) : asText(jsonOffer?.price))
    ?? read('meta[property="product:price:amount"]');
  const parsedPrice = parsePrice(rawPrice);
  const rawAvailability = (jsonOffer?.availability ?? read('meta[property="product:availability"]') ?? '').toLowerCase();
  const structuredBrand = typeof structured?.brand === 'string' ? structured.brand : structured?.brand?.name;
  const structuredImage = Array.isArray(structured?.image) ? structured.image[0]
    : typeof structured?.image === 'string' ? structured.image : structured?.image?.url;
  const structuredImageUrl = httpsUrl(structuredImage);
  const metaImageUrl = httpsUrl(read('meta[property="og:image"]'));
  const structuredCurrency = jsonOffer?.priceCurrency?.toUpperCase();
  const metaCurrency = read('meta[property="product:price:currency"]')?.toUpperCase();
  return {
    title: asText(structured?.name) ?? read('meta[property="og:title"]') ?? (page.title.trim() || undefined),
    brand: structuredBrand?.trim() || read('meta[property="product:brand"]'),
    imageUrl: structuredImageUrl ?? metaImageUrl,
    price: parsedPrice,
    currency: (structuredCurrency ?? metaCurrency ?? 'INR') === 'INR' ? 'INR' : undefined,
    availability: rawAvailability.includes('outofstock') || rawAvailability.includes('out of stock') ? 'OUT_OF_STOCK'
      : rawAvailability.includes('instock') || rawAvailability.includes('in stock') ? 'IN_STOCK' : 'UNKNOWN'
  };
}

function matchingProduct(page: Document, requestedId: string): ProductNode | undefined {
  const products: ProductNode[] = [];
  page.querySelectorAll('script[type="application/ld+json"]').forEach(script => {
    try {
      const parsed: unknown = JSON.parse(script.textContent ?? '');
      collectProducts(parsed, products);
    } catch { /* Malformed or unrelated structured data is ignored safely. */ }
  });
  const normalized = requestedId.toLowerCase();
  return products.find(node => [node.sku, node.productID, node.productId].some(id => id?.toLowerCase() === normalized));
}

function collectProducts(value: unknown, result: ProductNode[]): void {
  if (Array.isArray(value)) { value.forEach(item => collectProducts(item, result)); return; }
  if (!value || typeof value !== 'object') return;
  const node = value as Record<string, unknown>;
  const type = node['@type'];
  if (type === 'Product' || Array.isArray(type) && type.includes('Product')) result.push(node as ProductNode);
  if (Array.isArray(node['@graph'])) collectProducts(node['@graph'], result);
  if (Array.isArray(node.itemListElement)) collectProducts(node.itemListElement, result);
}

function asText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function parsePrice(value?: string): number | undefined {
  if (!value) return undefined;
  const cleaned = value.replace(/[₹\s,]/g, '');
  if (!/^\d+(?:\.\d{1,2})?$/.test(cleaned)) return undefined;
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function httpsUrl(value?: string): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value, location.href);
    return url.protocol === 'https:' ? url.href : undefined;
  } catch { return undefined; }
}
