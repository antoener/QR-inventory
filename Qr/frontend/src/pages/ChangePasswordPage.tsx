import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useAuth } from '@/features/auth/AuthContext';
import { useChangePassword } from '@/features/auth/hooks';
import { ApiClientError } from '@/api';

const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Ingresá tu contraseña actual'),
    newPassword: z
      .string()
      .min(8, 'Mínimo 8 caracteres')
      .max(72, 'Máximo 72 caracteres'),
    confirmPassword: z.string().min(1, 'Confirmá la nueva contraseña'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Las contraseñas no coinciden',
    path: ['confirmPassword'],
  });

type ChangePasswordFormData = z.infer<typeof changePasswordSchema>;

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { refetchMe } = useAuth();
  const changePassword = useChangePassword();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setError,
  } = useForm<ChangePasswordFormData>({
    resolver: zodResolver(changePasswordSchema),
  });

  const onSubmit = async (data: ChangePasswordFormData) => {
    try {
      await changePassword.mutateAsync({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      await refetchMe();
      navigate('/', { replace: true });
    } catch (error) {
      const message = error instanceof ApiClientError ? error.message : 'Error al cambiar la contraseña';
      setError('root', { message });
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Cambiar contraseña</CardTitle>
          <p className="text-sm text-gray-600">
            Tenés que cambiar tu contraseña temporal antes de continuar.
          </p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input
              label="Contraseña actual"
              type="password"
              autoComplete="current-password"
              {...register('currentPassword')}
              error={errors.currentPassword?.message}
            />
            <Input
              label="Nueva contraseña"
              type="password"
              autoComplete="new-password"
              {...register('newPassword')}
              error={errors.newPassword?.message}
            />
            <Input
              label="Repetir nueva contraseña"
              type="password"
              autoComplete="new-password"
              {...register('confirmPassword')}
              error={errors.confirmPassword?.message}
            />
            {errors.root && (
              <p className="text-sm text-red-600">{errors.root.message}</p>
            )}
            <Button type="submit" className="w-full" disabled={changePassword.isPending}>
              {changePassword.isPending ? 'Guardando...' : 'Guardar y continuar'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
