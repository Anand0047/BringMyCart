import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { ProductCard } from './ProductCard';
import type { SavedProduct } from '../api/types';

const product: SavedProduct = {
  id: 'a6c32718-8494-47e9-846c-b53246108980', asin: 'B0D2MW6L54', originalUrl: 'https://www.amazon.in/dp/B0D2MW6L54', canonicalUrl: 'https://www.amazon.in/dp/B0D2MW6L54',
  parentAsin: null, title: 'Example headphones', brand: 'Example', imageUrl: null, initialPrice: 9999, finalPrice: 7999, currency: 'INR', discountPercentage: 20,
  rating: 4.2, reviewsCount: 52, availability: 'In stock', enrichmentStatus: 'COMPLETED', enrichmentErrorCategory: null,
  metadataFetchedAt: '2026-11-15T10:00:00Z', providerFetchedAt: '2026-11-15T10:00:00Z', createdAt: '2026-11-15T09:00:00Z', updatedAt: '2026-11-15T10:00:00Z'
};

afterEach(cleanup);

test('renders persisted metadata and exposes manual actions', async () => {
  const onRefresh = vi.fn();
  const onDelete = vi.fn();
  render(<ProductCard product={product} onRefresh={onRefresh} onDelete={onDelete} />);

  expect(screen.getByText('Example headphones')).toBeInTheDocument();
  expect(screen.getByText('20% OFF')).toBeInTheDocument();
  expect(screen.getByText('52 ratings')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Refresh Example headphones'));
  expect(onRefresh).toHaveBeenCalledWith(product);
});

test('keeps unavailable metadata honest while product enrichment is pending', () => {
  const pendingProduct: SavedProduct = {
    ...product,
    title: null,
    brand: null,
    imageUrl: null,
    initialPrice: null,
    finalPrice: null,
    discountPercentage: null,
    rating: null,
    reviewsCount: null,
    availability: null,
    enrichmentStatus: 'PENDING',
    metadataFetchedAt: null,
    providerFetchedAt: null
  };
  render(<ProductCard product={pendingProduct} onRefresh={vi.fn()} onDelete={vi.fn()} />);
  expect(screen.getByText(/retrieving permitted product metadata/i)).toBeInTheDocument();
  expect(screen.getByText('Price unavailable')).toBeInTheDocument();
});

test('shows a retailer-specific action and opens the original URL when metadata is missing', () => {
  const flipkartProduct: SavedProduct = {
    ...product,
    retailer: 'FLIPKART',
    retailerProductId: 'MOBABCDE1234',
    asin: null,
    title: null,
    brand: null,
    imageUrl: null,
    initialPrice: null,
    finalPrice: null,
    discountPercentage: null,
    enrichmentStatus: 'FAILED',
    originalUrl: 'https://www.flipkart.com/phone/p/item123456?pid=MOBABCDE1234&tracking=x',
    canonicalUrl: 'https://www.flipkart.com/phone/p/item123456?pid=MOBABCDE1234'
  };
  render(<ProductCard product={flipkartProduct} onRefresh={vi.fn()} onDelete={vi.fn()} />);
  const buyLink = screen.getByRole('link', { name: /buy on flipkart/i });
  expect(buyLink).toHaveAttribute('href', flipkartProduct.originalUrl);
  expect(screen.getByText(/product information unavailable/i)).toBeInTheDocument();
  expect(screen.getByText('Price unavailable')).toBeInTheDocument();
  expect(screen.getByText(/metadata retrieval failed/i)).toBeInTheDocument();
});

test('keeps long titles and failed-enrichment actions usable', async () => {
  const failedProduct: SavedProduct = {
    ...product,
    title: 'A very long product title that should remain constrained inside the card without pushing status, prices, timestamps, or actions over one another',
    enrichmentStatus: 'FAILED',
    enrichmentErrorCategory: 'TIMEOUT',
    metadataFetchedAt: null,
    providerFetchedAt: null
  };
  const onRefresh = vi.fn();
  render(<ProductCard product={failedProduct} onRefresh={onRefresh} onDelete={vi.fn()} />);
  expect(screen.getByText(/metadata retrieval failed/i)).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText(/retry a very long product title/i));
  expect(onRefresh).toHaveBeenCalledWith(failedProduct);
});
