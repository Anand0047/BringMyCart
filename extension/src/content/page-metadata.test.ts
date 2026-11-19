import { describe, expect, it } from 'vitest';
import { extractPageMetadata } from './page-metadata';

describe('retailer page metadata fallback', () => {
  it('extracts only available Open Graph and product metadata', () => {
    document.title = 'Fallback title';
    document.head.innerHTML = `
      <meta property="og:title" content="Phone &amp; case">
      <meta property="og:image" content="https://cdn.example.test/item.jpg">
      <meta property="product:price:amount" content="7,999.50">
      <meta property="product:price:currency" content="INR">
      <meta property="product:availability" content="InStock">
    `;
    expect(extractPageMetadata(document)).toEqual({
      title: 'Phone & case', imageUrl: 'https://cdn.example.test/item.jpg', price: 7999.5,
      currency: 'INR', availability: 'IN_STOCK'
    });
  });

  it('returns undefined values instead of inventing prices or images', () => {
    document.head.innerHTML = '<meta property="product:availability" content="unknown">';
    document.title = 'Title only';
    const metadata = extractPageMetadata(document);
    expect(metadata.title).toBe('Title only');
    expect(metadata.price).toBeUndefined();
    expect(metadata.imageUrl).toBeUndefined();
    expect(metadata.availability).toBe('UNKNOWN');
  });

  it('uses structured metadata only from the requested product identity', () => {
    document.head.innerHTML = `
      <script type="application/ld+json">{"@graph":[
        {"@type":"Product","sku":"OTHER-123","name":"Recommended product","offers":{"price":"100","priceCurrency":"INR","availability":"https://schema.org/InStock"}},
        {"@type":"Product","sku":"319323","name":"Haier television","brand":{"name":"Haier"},"image":"https://images.example.test/tv.jpg","offers":{"price":"5247.00","priceCurrency":"INR","availability":"https://schema.org/OutOfStock"}}
      ]}</script>`;
    const result = extractPageMetadata(document, '319323');
    expect(result).toMatchObject({ title: 'Haier television', brand: 'Haier', imageUrl: 'https://images.example.test/tv.jpg', price: 5247, currency: 'INR', availability: 'OUT_OF_STOCK' });
  });

  it('ignores malformed JSON-LD and non-HTTPS images', () => {
    document.head.innerHTML = '<script type="application/ld+json">{"@type":"Product",</script><meta property="og:image" content="http://insecure.example.test/item.jpg">';
    expect(extractPageMetadata(document, '13784199').imageUrl).toBeUndefined();
  });
});
