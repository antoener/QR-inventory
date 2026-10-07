import { apiClient } from '@/api/client';
import type {
  CreateProductRequest,
  ProductResponse,
  UpdateProductRequest,
  UpdateProductStatusRequest,
} from '@/types';

export const productsApi = {
  create: (data: CreateProductRequest) =>
    apiClient<ProductResponse>('/products/save', { method: 'POST', body: JSON.stringify(data) }),

  findById: (id: number) =>
    apiClient<ProductResponse>(`/products/id/${id}`),

  findByCode: (code: string) =>
    apiClient<ProductResponse>(`/products/code/${encodeURIComponent(code)}`),

  list: (query?: string) => {
    const params = new URLSearchParams();
    if (query) params.set('q', query);
    const qs = params.toString();
    return apiClient<ProductResponse[]>(`/products/list${qs ? `?${qs}` : ''}`);
  },

  update: (id: number, data: UpdateProductRequest) =>
    apiClient<ProductResponse>(`/products/update/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  setStatus: (id: number, active: boolean) =>
    apiClient<void>(`/products/${id}/set-status`, {
      method: 'PATCH',
      body: JSON.stringify({ active } satisfies UpdateProductStatusRequest),
    }),
};
