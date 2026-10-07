import { apiClient } from '@/api/client';
import type { CreateUserRequest, UpdateUserRequest, UserResponse } from '@/types';

export const usersApi = {
  create: (data: CreateUserRequest) =>
    apiClient<UserResponse>('/users/save', { method: 'POST', body: JSON.stringify(data) }),

  findById: (id: number) =>
    apiClient<UserResponse>(`/users/find/${id}`),

  findByUsername: (username: string) =>
    apiClient<UserResponse>(`/users/username/${encodeURIComponent(username)}`),

  findByEmail: (email: string) =>
    apiClient<UserResponse>(`/users/email/${encodeURIComponent(email)}`),

  search: (query?: string) => {
    const params = new URLSearchParams();
    if (query) params.set('q', query);
    const qs = params.toString();
    return apiClient<UserResponse[]>(`/users/search${qs ? `?${qs}` : ''}`);
  },

  findAll: () =>
    apiClient<UserResponse[]>('/users/all'),

  update: (id: number, data: UpdateUserRequest) =>
    apiClient<UserResponse>(`/users/update/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  desactivate: (id: number) =>
    apiClient<void>(`/users/desactivate/${id}`, { method: 'PATCH' }),

  activate: (id: number) =>
    apiClient<void>(`/users/activate/${id}`, { method: 'PATCH' }),
};
