import type { ProductPageMetadata } from '../shared/messages';

export function extractPageMetadata(page: Document): ProductPageMetadata {
  const read = (selector: string) => page.querySelector<HTMLMetaElement>(selector)?.content?.trim() || undefined;
  const rawPrice = read('meta[property="product:price:amount"]');
  const parsedPrice = rawPrice ? Number(rawPrice.replace(/,/g, '')) : undefined;
  const rawAvailability = (read('meta[property="product:availability"]') ?? '').toLowerCase();
  return {
    title: read('meta[property="og:title"]') ?? (page.title.trim() || undefined),
    brand: read('meta[property="product:brand"]'),
    imageUrl: read('meta[property="og:image"]'),
    price: parsedPrice !== undefined && Number.isFinite(parsedPrice) && parsedPrice > 0 ? parsedPrice : undefined,
    currency: (read('meta[property="product:price:currency"]') ?? 'INR').toUpperCase() === 'INR' ? 'INR' : undefined,
    availability: rawAvailability.includes('outofstock') || rawAvailability.includes('out of stock') ? 'OUT_OF_STOCK'
      : rawAvailability.includes('instock') || rawAvailability.includes('in stock') ? 'IN_STOCK' : 'UNKNOWN'
  };
}
