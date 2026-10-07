import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from './AuthContext';

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
      const { authApi } = await import('@/api');
      return authApi.changePassword(data);
    },
  });
}
