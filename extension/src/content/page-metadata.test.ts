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
});
