import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useForgotPassword } from '@/features/auth/hooks';

const forgotPasswordSchema = z.object({
  email: z.string().email('Email inválido').min(1, 'El email es obligatorio'),
});

type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>;

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const forgotPassword = useForgotPassword();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitted },
    reset,
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
  });

  const onSubmit = async (data: ForgotPasswordFormData) => {
    try {
      await forgotPassword.mutateAsync(data.email);
      reset();
    } catch {
      // El backend siempre retorna 204 (anti-enumeración)
      // Mostramos el mensaje genérico de éxito aunque falle
    }
  };

  const showSuccessMessage = isSubmitted && !forgotPassword.isPending;

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
          <CardTitle>Recuperar contraseña</CardTitle>
          <p className="text-sm text-gray-600 mt-2">
            Ingresá tu email y te enviaremos un enlace para restablecerla
          </p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              {...register('email')}
              error={errors.email?.message}
            />
            {showSuccessMessage && (
              <div className="rounded-lg bg-green-50 p-3 text-sm text-green-700">
                Si el email existe en nuestro sistema, recibirás un enlace para restablecer tu contraseña.
              </div>
            )}
            <Button
              type="submit"
              className="w-full"
              disabled={forgotPassword.isPending}
            >
              {forgotPassword.isPending ? 'Enviando...' : 'Enviar enlace'}
            </Button>
          </form>
          <div className="mt-4 text-center">
            <p className="text-sm text-gray-600">
              ¿Recordaste tu contraseña?{' '}
              <button
                type="button"
                onClick={() => navigate('/login')}
                className="text-primary-600 hover:underline"
              >
                Iniciar sesión
              </button>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}