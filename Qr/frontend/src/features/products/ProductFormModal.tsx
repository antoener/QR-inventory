import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useCreateProduct, useUpdateProduct } from '@/features/products/hooks';
import { ApiClientError } from '@/api';
import type { ProductResponse } from '@/types';

const productSchema = z.object({
  code: z.string().min(1, 'El código es obligatorio').max(30, 'Máximo 30 caracteres'),
  name: z.string().min(1, 'El nombre es obligatorio').max(120, 'Máximo 120 caracteres'),
  description: z.string().max(500, 'Máximo 500 caracteres').optional(),
});

type ProductFormData = z.infer<typeof productSchema>;

interface ProductFormModalProps {
  product: ProductResponse | null;
  onClose: () => void;
}

export function ProductFormModal({ product, onClose }: ProductFormModalProps) {
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const isEditing = product !== null;

  const {
    register,
    handleSubmit,
    formState: { errors },
    setError,
  } = useForm<ProductFormData>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      code: product?.code ?? '',
      name: product?.name ?? '',
      description: product?.description ?? '',
    },
  });

  const onSubmit = async (data: ProductFormData) => {
    try {
      if (isEditing) {
        await updateProduct.mutateAsync({ id: product.id, data });
      } else {
        await createProduct.mutateAsync(data);
      }
      onClose();
    } catch (error) {
      const message = error instanceof ApiClientError ? error.message : 'Error al guardar el producto';
      setError('root', { message });
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-lg">
        <h2 className="mb-4 text-xl font-bold text-gray-900">
          {isEditing ? 'Editar producto' : 'Nuevo producto'}
        </h2>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <Input
            label="Código"
            disabled={isEditing}
            {...register('code')}
            error={errors.code?.message}
          />
          <Input
            label="Nombre"
            {...register('name')}
            error={errors.name?.message}
          />
          <div className="w-full">
            <label className="mb-1 block text-sm font-medium text-gray-700">Descripción</label>
            <textarea
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 disabled:bg-gray-100"
              rows={3}
              {...register('description')}
            />
            {errors.description && <p className="mt-1 text-sm text-red-600">{errors.description.message}</p>}
          </div>
          {errors.root && <p className="text-sm text-red-600">{errors.root.message}</p>}
          <div className="flex gap-2 pt-2">
            <Button type="button" variant="secondary" className="w-full" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" className="w-full" disabled={createProduct.isPending || updateProduct.isPending}>
              {createProduct.isPending || updateProduct.isPending ? 'Guardando...' : 'Guardar'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
