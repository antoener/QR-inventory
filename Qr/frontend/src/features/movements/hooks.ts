import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { movementsApi } from '@/api';
import type { MovementPeriod, MovementType, StockMovementResponse } from '@/types';

const movementKeys = {
  all: ['movements'] as const,
  stock: (productId: number) => [...movementKeys.all, 'stock', productId] as const,
  list: (filters?: { period?: MovementPeriod; type?: MovementType }) =>
    [...movementKeys.all, 'list', filters] as const,
  recent: () => [...movementKeys.all, 'recent'] as const,
};

export function useRegisterInbound() {
  const queryClient = useQueryClient();
  return useMutation<StockMovementResponse, Error, Parameters<typeof movementsApi.registerInbound>[0]>({
    mutationFn: movementsApi.registerInbound,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: movementKeys.stock(data.productId) });
      queryClient.invalidateQueries({ queryKey: movementKeys.all });
    },
  });
}

export function useRegisterOutbound() {
  const queryClient = useQueryClient();
  return useMutation<StockMovementResponse, Error, Parameters<typeof movementsApi.registerOutbound>[0]>({
    mutationFn: movementsApi.registerOutbound,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: movementKeys.stock(data.productId) });
      queryClient.invalidateQueries({ queryKey: movementKeys.all });
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

export function useMovements(filters?: { period?: MovementPeriod; type?: MovementType }) {
  return useQuery({
    queryKey: movementKeys.list(filters),
    queryFn: () => movementsApi.findByFilters(filters),
    enabled: Boolean(filters?.period || filters?.type),
  });
}

export function useRecentMovements() {
  return useQuery({
    queryKey: movementKeys.recent(),
    queryFn: movementsApi.recent,
  });
}
