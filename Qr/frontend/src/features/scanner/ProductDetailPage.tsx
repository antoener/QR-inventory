import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Minus, Package, Plus, Search } from 'lucide-react';
import { useState } from 'react';
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

          <ProductionShortcut productId={product.id} />

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
        </CardContent>
      </Card>
    </div>
  );
}

function ProductionShortcut({ productId }: { productId: number }) {
  const navigate = useNavigate();
  const registerInbound = useRegisterInbound();
  const [quantity, setQuantity] = useState<string>('');
  const [detail, setDetail] = useState('');

  const handleSubmit = async () => {
    const qty = parseInt(quantity, 10);
    if (!qty || qty <= 0) return;

    try {
      await registerInbound.mutateAsync({
        productId,
        quantity: qty,
        reason: Reason.PRODUCTION,
        origin: Origin.MACHINE,
        detail: detail.trim() || undefined,
      });
      navigate('/scanner');
    } catch {
      // error handled by mutation state
    }
  };

  return (
    <Card className="border-primary-200 bg-primary-50">
      <CardHeader>
        <CardTitle className="text-base text-primary-900 flex items-center gap-2">
          <Package className="h-4 w-4" />
          Cargar producción
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Input
          label="Cantidad"
          type="number"
          inputMode="numeric"
          placeholder="5000"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
        />
        <Input
          label="Detalle (opcional)"
          placeholder="Turno mañana, lote..."
          value={detail}
          onChange={(e) => setDetail(e.target.value)}
        />
        {registerInbound.isError && (
          <p className="text-sm text-red-600">
            {registerInbound.error instanceof ApiClientError
              ? registerInbound.error.message
              : 'Error al registrar la producción'}
          </p>
        )}
        <Button className="w-full" onClick={handleSubmit} disabled={registerInbound.isPending}>
          {registerInbound.isPending ? 'Guardando...' : 'Confirmar producción'}
        </Button>
      </CardContent>
    </Card>
  );
}

export function MovementFormPage() {
  const { code, type } = useParams<{ code: string; type: 'inbound' | 'outbound' }>();
  const navigate = useNavigate();
  const decodedCode = code ? decodeURIComponent(code) : '';

  const { data: product } = useProductByCode(decodedCode);
  const isInbound = type === 'inbound';
  const mutation = isInbound ? useRegisterInbound() : useRegisterOutbound();

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
        detail: z.string().max(255).optional(),
      })
    ),
    defaultValues: {
      reason: reasons[0],
    },
  });

  const selectedReason = watch('reason');

  const onSubmit = async (data: MovementFormData) => {
    if (!product) return;

    try {
      await mutation.mutateAsync({
        productId: product.id,
        quantity: data.quantity,
        reason: data.reason,
        origin: isInbound ? Origin.MANUAL : Origin.MANUAL,
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
