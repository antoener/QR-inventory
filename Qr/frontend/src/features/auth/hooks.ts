import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from './AuthContext';
import { authApi } from '@/api';

interface LoginCredentials {
  identifier: string;
  password: string;
}

export function useLogin() {
  const { login } = useAuth();
  return useMutation({
    mutationFn: ({ identifier, password }: LoginCredentials) => login(identifier, password),
  });
}

export function useLogout() {
  const { logout } = useAuth();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear();
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: async (data: { currentPassword: string; newPassword: string }) => {
      return authApi.changePassword(data);
    },
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => authApi.forgotPassword(email),
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      authApi.resetPassword(token, newPassword),
  });
}
