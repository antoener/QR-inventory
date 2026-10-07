import { apiClient } from '@/api/client';
import type { CreateStockMovementRequest, StockMovementResponse } from '@/types';

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
};
