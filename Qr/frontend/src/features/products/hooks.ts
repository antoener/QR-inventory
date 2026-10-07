import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { productsApi } from '@/api';
import type { UpdateProductRequest } from '@/types';

const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (query: string | undefined) => [...productKeys.lists(), { query }] as const,
  detail: (id: number) => [...productKeys.all, 'detail', id] as const,
  code: (code: string) => [...productKeys.all, 'code', code] as const,
};

export function useProducts(query?: string) {
  return useQuery({
    queryKey: productKeys.list(query),
    queryFn: () => productsApi.list(query),
  });
}

export function useProduct(id: number) {
  return useQuery({
    queryKey: productKeys.detail(id),
    queryFn: () => productsApi.findById(id),
    enabled: id > 0,
  });
}

export function useProductByCode(code: string) {
  return useQuery({
    queryKey: productKeys.code(code),
    queryFn: () => productsApi.findByCode(code),
    enabled: code.length > 0,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: productsApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.lists() });
    },
  });
}

export function useUpdateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateProductRequest }) =>
      productsApi.update(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: productKeys.detail(variables.id) });
      queryClient.invalidateQueries({ queryKey: productKeys.lists() });
    },
  });
}

export function useUpdateProductStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      productsApi.setStatus(id, active),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: productKeys.detail(variables.id) });
      queryClient.invalidateQueries({ queryKey: productKeys.lists() });
    },
  });
}
