import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';

const api = vi.hoisted(() => ({ list: vi.fn(), save: vi.fn(), refresh: vi.fn(), remove: vi.fn() }));
vi.mock('../api/savedProductsApi', () => ({ savedProductsApi: api }));
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ session: { email: 'customer@example.com' }, logout: vi.fn() }) }));

const product = {
  id: 'a6c32718-8494-47e9-846c-b53246108980', asin: 'B0D2MW6L54', originalUrl: 'https://www.amazon.in/dp/B0D2MW6L54', canonicalUrl: 'https://www.amazon.in/dp/B0D2MW6L54',
  parentAsin: null, title: 'Example headphones', brand: 'Example', imageUrl: null, initialPrice: 9999, finalPrice: 7999, currency: 'INR', discountPercentage: 20,
  rating: 4.2, reviewsCount: 52, availability: 'In stock', enrichmentStatus: 'COMPLETED' as const, enrichmentErrorCategory: null,
  metadataFetchedAt: '2026-11-15T10:00:00Z', providerFetchedAt: '2026-11-15T10:00:00Z', createdAt: '2026-11-15T09:00:00Z', updatedAt: '2026-11-15T10:00:00Z'
};

beforeEach(() => {
  api.list.mockResolvedValue({ content: [product], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true });
  api.save.mockResolvedValue(product);
  api.refresh.mockResolvedValue({});
  api.remove.mockResolvedValue({});
  vi.stubGlobal('confirm', vi.fn(() => true));
});

afterEach(cleanup);

test('saves, refreshes, and deletes through the typed API layer', async () => {
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  await screen.findByText('Example headphones');

  await userEvent.type(screen.getByPlaceholderText('Paste a supported product URL'), 'https://www.amazon.in/dp/B0D7SJXCPP');
  await userEvent.click(screen.getByRole('button', { name: /save product/i }));
  await waitFor(() => expect(api.save).toHaveBeenCalledWith('https://www.amazon.in/dp/B0D7SJXCPP'));

  await userEvent.click(screen.getByLabelText('Refresh Example headphones'));
  await waitFor(() => expect(api.refresh).toHaveBeenCalledWith(product.id));

  fireEvent.click(screen.getByLabelText('Delete Example headphones'));
  await waitFor(() => expect(api.remove).toHaveBeenCalledWith(product.id));
});

test('accepts a Myntra product URL through the existing save form', async () => {
  api.list.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  api.save.mockResolvedValue(product);
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  await userEvent.type(screen.getByPlaceholderText('Paste a supported product URL'), 'https://www.myntra.com/shirts/brand/12345678/buy');
  await userEvent.click(screen.getByRole('button', { name: /save product/i }));
  await waitFor(() => expect(api.save).toHaveBeenCalledWith('https://www.myntra.com/shirts/brand/12345678/buy'));
});

test.each([
  'https://www.croma.com/haier-m80f-190-cm-75-inch-3-star-4k-ultra-hd-mini-led-smart-google-tv-with-google-assistant-supported-2025-model-/p/319323',
  'https://www.ajio.com/u-s-polo-assn-men-tailored-fit-shirt-with-logo-embroidery/p/442466815_blue',
])('submits the supplied retailer URL unchanged through the dashboard API: %s', async (url) => {
  api.list.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  const input = screen.getByPlaceholderText('Paste a supported product URL');
  await userEvent.type(input, url);
  await userEvent.click(screen.getByRole('button', { name: /save product/i }));
  await waitFor(() => expect(api.save).toHaveBeenCalledWith(url));
});

test('reports duplicate saves as already saved instead of a generic failure', async () => {
  api.save.mockRejectedValue({ isAxiosError: true, response: { status: 409, data: { code: 'duplicate_saved_product' } } });
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  await userEvent.type(screen.getByPlaceholderText('Paste a supported product URL'), 'https://www.croma.com/tv/p/319323');
  await userEvent.click(screen.getByRole('button', { name: /save product/i }));
  expect(await screen.findByRole('status')).toHaveTextContent(/already in your saved products/i);
});

test('retains the pasted URL after a save request fails so it can be corrected or retried', async () => {
  api.save.mockRejectedValue(new Error('network failed'));
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  const input = screen.getByPlaceholderText('Paste a supported product URL');
  await userEvent.type(input, 'https://www.croma.com/tv/p/319323');
  await userEvent.click(screen.getByRole('button', { name: /save product/i }));
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(input).toHaveValue('https://www.croma.com/tv/p/319323');
});
