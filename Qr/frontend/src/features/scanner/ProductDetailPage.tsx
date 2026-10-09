import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Minus, Plus, Search } from 'lucide-react';
import { LabelDownloadSection } from '@/features/labels/components/LabelDownloadSection';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { ApiClientError } from '@/api';
import { useProductByCode } from '@/features/products/hooks';
import { useCurrentStock, useRegisterInbound, useRegisterOutbound } from '@/features/movements/hooks';
import { Origin, Reason } from '@/types';

interface MovementFormData {
  quantity: number;
  reason: Reason;
  origin: Origin;
  detail?: string;
}

const inboundReasons: Reason[] = [Reason.PRODUCTION, Reason.MATERIAL_PURCHASE, Reason.CUSTOMER_RETURN];
const outboundReasons: Reason[] = [Reason.SALE, Reason.WASTE, Reason.INTERNAL_USE, Reason.GIFT, Reason.LOSS];

export function ProductDetailPage() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const decodedCode = code ? decodeURIComponent(code) : '';

  const { data: product, isLoading, error } = useProductByCode(decodedCode);
  const { data: stock } = useCurrentStock(product?.id ?? 0);

  if (isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={() => navigate('/scanner')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Volver
        </Button>
        <div className="rounded-lg bg-red-50 p-6 text-center text-red-700">
          El código <strong>{decodedCode}</strong> no corresponde a ningún producto.
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Button variant="ghost" onClick={() => navigate('/scanner')}>
        <ArrowLeft className="mr-2 h-4 w-4" />
        Volver al escáner
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{product.name}</CardTitle>
          <p className="text-sm text-gray-500">{product.code}</p>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="text-center">
            <p className="text-sm text-gray-600">Stock actual</p>
            <p className="text-5xl font-bold text-primary-700">{stock ?? 0}</p>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <Button variant="secondary" onClick={() => navigate(`/p/${encodeURIComponent(decodedCode)}/inbound`)}>
              <Plus className="mr-1 h-4 w-4" />
              Agregar
            </Button>
            <Button variant="secondary" onClick={() => navigate(`/p/${encodeURIComponent(decodedCode)}/outbound`)}>
              <Minus className="mr-1 h-4 w-4" />
              Restar
            </Button>
            <Button variant="ghost" onClick={() => navigate(`/products`)}>
              <Search className="mr-1 h-4 w-4" />
              Buscar
            </Button>
          </div>

          <LabelDownloadSection product={product} />
        </CardContent>
      </Card>
    </div>
  );
}

export function MovementFormPage() {
  const { code, type } = useParams<{ code: string; type: 'inbound' | 'outbound' }>();
  const navigate = useNavigate();
  const decodedCode = code ? decodeURIComponent(code) : '';

  const { data: product } = useProductByCode(decodedCode);
  const isInbound = type === 'inbound';
  const inboundMutation = useRegisterInbound();
  const outboundMutation = useRegisterOutbound();
  const mutation = isInbound ? inboundMutation : outboundMutation;

  const reasons = isInbound ? inboundReasons : outboundReasons;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
    setError,
  } = useForm<MovementFormData>({
    resolver: zodResolver(
      z.object({
        quantity: z.number().int().min(1, 'La cantidad debe ser mayor a 0'),
        reason: z.nativeEnum(Reason),
        origin: z.nativeEnum(Origin),
        detail: z.string().max(255).optional(),
      })
    ),
    defaultValues: {
      reason: reasons[0],
      origin: Origin.MANUAL,
    },
  });

  const selectedReason = watch('reason');
  const selectedOrigin = watch('origin');

  const onSubmit = async (data: MovementFormData) => {
    if (!product) return;

    try {
      await mutation.mutateAsync({
        productId: product.id,
        quantity: data.quantity,
        reason: data.reason,
        origin: isInbound ? data.origin : Origin.MANUAL,
        detail: data.detail?.trim() || undefined,
      });
      navigate('/scanner');
    } catch (error) {
      const message = error instanceof ApiClientError ? error.message : 'Error al registrar el movimiento';
      setError('root', { message });
    }
  };

  if (!product) {
    return null;
  }

  return (
    <div className="space-y-4">
      <Button variant="ghost" onClick={() => navigate(`/p/${encodeURIComponent(decodedCode)}`)}>
        <ArrowLeft className="mr-2 h-4 w-4" />
        Volver
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{isInbound ? 'Agregar stock' : 'Restar stock'}</CardTitle>
          <p className="text-sm text-gray-500">{product.name}</p>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">Motivo</label>
              <div className="grid grid-cols-2 gap-2">
                {reasons.map((reason) => (
                  <Button
                    key={reason}
                    type="button"
                    variant={selectedReason === reason ? 'primary' : 'secondary'}
                    onClick={() => setValue('reason', reason)}
                  >
                    {reason.replace(/_/g, ' ')}
                  </Button>
                ))}
              </div>
              {errors.reason && <p className="mt-1 text-sm text-red-600">{errors.reason.message}</p>}
            </div>

            {isInbound && (
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">Origen</label>
                <div className="grid grid-cols-2 gap-2">
                  {[Origin.MANUAL, Origin.MACHINE].map((o) => (
                    <Button
                      key={o}
                      type="button"
                      variant={selectedOrigin === o ? 'primary' : 'secondary'}
                      onClick={() => setValue('origin', o)}
                    >
                      {o === Origin.MANUAL ? 'Manual' : 'Máquina'}
                    </Button>
                  ))}
                </div>
                {errors.origin && <p className="mt-1 text-sm text-red-600">{errors.origin.message}</p>}
              </div>
            )}

            <Input
              label="Cantidad"
              type="number"
              inputMode="numeric"
              {...register('quantity', { valueAsNumber: true })}
              error={errors.quantity?.message}
            />

            <Input
              label="Detalle (opcional)"
              {...register('detail')}
              error={errors.detail?.message}
            />

            {errors.root && <p className="text-sm text-red-600">{errors.root.message}</p>}

            <Button type="submit" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? 'Guardando...' : 'Confirmar'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
