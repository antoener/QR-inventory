import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useLogin } from '@/features/auth/hooks';
import { ApiClientError } from '@/api';

const loginSchema = z.object({
  identifier: z.string().min(1, 'Ingresá tu usuario o email'),
  password: z.string().min(1, 'Ingresá tu contraseña'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useLogin();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setError,
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const from = (location.state as { from?: { pathname: string } } | undefined)?.from?.pathname ?? '/';

  const onSubmit = async (data: LoginFormData) => {
    try {
      await login.mutateAsync(data);
      navigate(from, { replace: true });
    } catch (error) {
      const message = error instanceof ApiClientError ? error.message : 'Error al iniciar sesión';
      setError('root', { message });
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Inventario QR</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input
              label="Usuario o email"
              type="text"
              autoComplete="username"
              {...register('identifier')}
              error={errors.identifier?.message}
            />
            <Input
              label="Contraseña"
              type="password"
              autoComplete="current-password"
              {...register('password')}
              error={errors.password?.message}
            />
            {errors.root && (
              <p className="text-sm text-red-600">{errors.root.message}</p>
            )}
            <Button type="submit" className="w-full" disabled={login.isPending}>
              {login.isPending ? 'Ingresando...' : 'Ingresar'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
