import { apiClient } from '@/api/client';
import type { CreateStockMovementRequest, MovementPeriod, MovementType, StockMovementResponse } from '@/types';

export const movementsApi = {
  registerInbound: (data: CreateStockMovementRequest) =>
    apiClient<StockMovementResponse>('/movements/inbound', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  registerOutbound: (data: CreateStockMovementRequest) =>
    apiClient<StockMovementResponse>('/movements/outbound', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getCurrentStock: (productId: number) =>
    apiClient<number>(`/movements/stock/${productId}`),

  findByFilters: (filters?: {
    period?: MovementPeriod;
    productId?: number;
    type?: MovementType;
  }) => {
    const params = new URLSearchParams();
    if (filters?.period) params.set('period', filters.period);
    if (filters?.productId != null) params.set('productId', filters.productId.toString());
    if (filters?.type) params.set('type', filters.type);
    const qs = params.toString();
    return apiClient<StockMovementResponse[]>(`/movements${qs ? `?${qs}` : ''}`);
  },

  recent: () => apiClient<StockMovementResponse[]>('/movements/recent'),
};
