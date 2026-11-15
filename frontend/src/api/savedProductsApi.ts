import { apiClient } from './client';
import type { PageResponse, SavedProduct } from './types';

export const savedProductsApi = {
  list: async (page = 0) => (await apiClient.get<PageResponse<SavedProduct>>('/saved-products', { params: { page, size: 20 } })).data,
  save: async (url: string) => (await apiClient.post<SavedProduct>('/saved-products', { url })).data,
  refresh: (id: string) => apiClient.post(`/saved-products/${id}/refresh`),
  remove: (id: string) => apiClient.delete(`/saved-products/${id}`)
};
