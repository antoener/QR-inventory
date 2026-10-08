import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useCreateUser, useUpdateUser } from '@/features/users/hooks';
import { cn } from '@/lib/utils';
import { Role, type CreateUserRequest, type UpdateUserRequest, type UserResponse } from '@/types';

const baseUserSchema = z.object({
  username: z.string().min(1, 'El usuario es obligatorio').max(50, 'Máximo 50 caracteres'),
  email: z.string().min(1, 'El email es obligatorio').email('Email inválido').max(100, 'Máximo 100 caracteres'),
  name: z.string().min(1, 'El nombre es obligatorio').max(100, 'Máximo 100 caracteres'),
  role: z.nativeEnum(Role),
});

const createUserSchema = baseUserSchema.extend({
  password: z
    .string()
    .min(8, 'Mínimo 8 caracteres')
    .max(72, 'Máximo 72 caracteres')
    .regex(/[A-Z]/, 'Al menos una mayúscula')
    .regex(/[a-z]/, 'Al menos una minúscula')
    .regex(/\d/, 'Al menos un número')
    .regex(/[^A-Za-z0-9]/, 'Al menos un carácter especial'),
});

type CreateUserFormData = z.infer<typeof createUserSchema>;
type UpdateUserFormData = z.infer<typeof baseUserSchema>;

interface UserFormModalProps {
  user: UserResponse | null;
  onClose: () => void;
}

export function UserFormModal({ user, onClose }: UserFormModalProps) {
  const isEditing = user !== null;
  const createUser = useCreateUser();
  const updateUser = useUpdateUser();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setError,
  } = useForm<CreateUserFormData | UpdateUserFormData>({
    resolver: zodResolver(isEditing ? baseUserSchema : createUserSchema),
    defaultValues: isEditing
      ? {
          username: user.username,
          email: user.email,
          name: user.name,
          role: user.role,
        }
      : {
          role: Role.ADMIN,
        },
  });

  const onSubmit = async (data: CreateUserFormData | UpdateUserFormData) => {
    try {
      if (isEditing && user) {
        await updateUser.mutateAsync({
          id: user.id,
          data: data as UpdateUserRequest,
        });
      } else {
        await createUser.mutateAsync(data as CreateUserRequest);
      }
      onClose();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Error al guardar el usuario';
      setError('root', { message });
    }
  };

  const isPending = createUser.isPending || updateUser.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>{isEditing ? 'Editar usuario' : 'Nuevo usuario'}</CardTitle>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input
              label="Nombre completo"
              {...register('name')}
              error={errors.name?.message}
            />
            <Input
              label="Nombre de usuario"
              {...register('username')}
              error={errors.username?.message}
            />
            <Input
              label="Email"
              type="email"
              {...register('email')}
              error={errors.email?.message}
            />

            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">Rol</label>
              <select
                {...register('role')}
                className={cn(
                  'w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500',
                  errors.role && 'border-red-500 focus:border-red-500 focus:ring-red-500',
                )}
              >
                <option value={Role.ADMIN}>Administrador</option>
              </select>
              {errors.role && <p className="mt-1 text-sm text-red-600">{errors.role.message}</p>}
            </div>

            {!isEditing && (
              <Input
                label="Contraseña"
                type="password"
                {...register('password' as const)}
                error={(errors as { password?: { message?: string } }).password?.message}
              />
            )}

            {errors.root && <p className="text-sm text-red-600">{errors.root.message}</p>}

            <div className="flex gap-2 pt-2">
              <Button type="button" variant="secondary" className="w-full" onClick={onClose}>
                Cancelar
              </Button>
              <Button type="submit" className="w-full" disabled={isPending}>
                {isPending ? 'Guardando...' : 'Guardar'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
