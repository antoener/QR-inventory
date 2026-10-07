import { apiClient } from '@/api/client';
import type {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  UserResponse,
} from '@/types';

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),

  refresh: () =>
    apiClient<AuthResponse>('/auth/refresh', { method: 'POST' }),

  logout: () =>
    apiClient<void>('/auth/logout', { method: 'POST' }),

  me: () =>
    apiClient<UserResponse>('/auth/me'),

  changePassword: (data: ChangePasswordRequest) =>
    apiClient<void>('/auth/change-password', { method: 'POST', body: JSON.stringify(data) }),
};
