import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { movementsApi } from '@/api';
import type { StockMovementResponse } from '@/types';

const movementKeys = {
  all: ['movements'] as const,
  stock: (productId: number) => [...movementKeys.all, 'stock', productId] as const,
};

export function useRegisterInbound() {
  const queryClient = useQueryClient();
  return useMutation<StockMovementResponse, Error, Parameters<typeof movementsApi.registerInbound>[0]>({
    mutationFn: movementsApi.registerInbound,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: movementKeys.stock(data.productId) });
    },
  });
}

export function useRegisterOutbound() {
  const queryClient = useQueryClient();
  return useMutation<StockMovementResponse, Error, Parameters<typeof movementsApi.registerOutbound>[0]>({
    mutationFn: movementsApi.registerOutbound,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: movementKeys.stock(data.productId) });
    },
  });
}

export function useCurrentStock(productId: number) {
  return useQuery({
    queryKey: movementKeys.stock(productId),
    queryFn: () => movementsApi.getCurrentStock(productId),
    enabled: productId > 0,
  });
}
