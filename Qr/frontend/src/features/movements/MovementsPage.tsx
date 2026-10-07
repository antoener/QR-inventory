import { ArrowDownLeft, ArrowUpRight, RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useProducts } from '@/features/products/hooks';
import { useRegisterInbound, useRegisterOutbound } from '@/features/movements/hooks';
import { ApiClientError } from '@/api';
import { cn } from '@/lib/utils';
import { MovementType, Origin, Reason, type ProductResponse, type StockMovementResponse } from '@/types';

const inboundReasons: Reason[] = [Reason.PRODUCTION, Reason.MATERIAL_PURCHASE, Reason.CUSTOMER_RETURN];
const outboundReasons: Reason[] = [Reason.SALE, Reason.WASTE, Reason.INTERNAL_USE, Reason.GIFT, Reason.LOSS];

interface MovementFormProps {
  product: ProductResponse;
  type: MovementType;
  onSuccess: (movement: StockMovementResponse) => void;
  onCancel: () => void;
}

function MovementForm({ product, type, onSuccess, onCancel }: MovementFormProps) {
  const isInbound = type === MovementType.INBOUND;
  const reasons = isInbound ? inboundReasons : outboundReasons;
  const inbound = useRegisterInbound();
  const outbound = useRegisterOutbound();
  const mutation = isInbound ? inbound : outbound;

  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState<Reason>(reasons[0]);
  const [detail, setDetail] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isPending = mutation.isPending;

  const handleSubmit = async () => {
    const qty = parseInt(quantity, 10);
    if (!qty || qty <= 0) {
      setSubmitError('La cantidad debe ser mayor a 0');
      return;
    }

    setSubmitError(null);

    try {
      const result = await mutation.mutateAsync({
        productId: product.id,
        quantity: qty,
        reason,
        origin: Origin.MANUAL,
        detail: detail.trim() || undefined,
      });
      onSuccess(result);
    } catch (error) {
      const message = error instanceof ApiClientError ? error.message : 'Error al registrar el movimiento';
      setSubmitError(message);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          {isInbound ? <ArrowDownLeft className="h-5 w-5 text-green-600" /> : <ArrowUpRight className="h-5 w-5 text-red-600" />}
          {isInbound ? 'Entrada de stock' : 'Salida de stock'}
        </CardTitle>
        <p className="text-sm text-gray-500">{product.name} ({product.code})</p>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <label className="mb-2 block text-sm font-medium text-gray-700">Motivo</label>
          <div className="grid grid-cols-2 gap-2">
            {reasons.map((r) => (
              <Button
                key={r}
                type="button"
                variant={reason === r ? 'primary' : 'secondary'}
                onClick={() => setReason(r)}
              >
                {r.replace(/_/g, ' ')}
              </Button>
            ))}
          </div>
        </div>

        <Input
          label="Cantidad"
          type="number"
          inputMode="numeric"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
        />

        <Input
          label="Detalle (opcional)"
          value={detail}
          onChange={(e) => setDetail(e.target.value)}
        />

        {submitError && (
          <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
            <p>{submitError}</p>
            {submitError.toLowerCase().includes('stock changed') && (
              <Button
                variant="ghost"
                size="sm"
                className="mt-2"
                onClick={handleSubmit}
                disabled={isPending}
              >
                <RotateCcw className="mr-1 h-4 w-4" />
                Reintentar
              </Button>
            )}
          </div>
        )}

        <div className="flex gap-2">
          <Button variant="secondary" className="w-full" onClick={onCancel}>
            Cancelar
          </Button>
          <Button className="w-full" onClick={handleSubmit} disabled={isPending}>
            {isPending ? 'Guardando...' : 'Confirmar'}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function ProductSelector({ onSelect }: { onSelect: (product: ProductResponse) => void }) {
  const [search, setSearch] = useState('');
  const { data: products, isLoading } = useProducts(search || undefined);

  return (
    <div className="space-y-3">
      <Input
        placeholder="Buscar producto por código o nombre..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      {isLoading && (
        <div className="flex justify-center py-6">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
        </div>
      )}
      {!isLoading && products && products.length === 0 && (
        <div className="rounded-lg bg-gray-100 p-4 text-center text-gray-600">
          No se encontraron productos
        </div>
      )}
      <div className="space-y-2">
        {products?.map((product) => (
          <button
            key={product.id}
            onClick={() => onSelect(product)}
            className={cn(
              'w-full rounded-lg border border-gray-200 bg-white p-3 text-left shadow-sm transition-colors hover:bg-gray-50',
              !product.active && 'opacity-60'
            )}
          >
            <p className="font-medium text-gray-900">{product.name}</p>
            <p className="text-sm text-gray-500">{product.code}</p>
          </button>
        ))}
      </div>
    </div>
  );
}

export function MovementsPage() {
  const [step, setStep] = useState<'menu' | 'select-product' | 'form'>('menu');
  const [selectedType, setSelectedType] = useState<MovementType>(MovementType.INBOUND);
  const [selectedProduct, setSelectedProduct] = useState<ProductResponse | null>(null);
  const [recentMovements, setRecentMovements] = useState<StockMovementResponse[]>([]);

  const handleSuccess = (movement: StockMovementResponse) => {
    setRecentMovements((prev) => [movement, ...prev].slice(0, 10));
    setStep('menu');
    setSelectedProduct(null);
  };

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-900">Movimientos</h1>

      {step === 'menu' && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Button
              size="lg"
              className="h-24 flex-col gap-2 bg-green-600 hover:bg-green-700"
              onClick={() => { setSelectedType(MovementType.INBOUND); setStep('select-product'); }}
            >
              <ArrowDownLeft className="h-6 w-6" />
              Entrada
            </Button>
            <Button
              size="lg"
              className="h-24 flex-col gap-2 bg-red-600 hover:bg-red-700"
              onClick={() => { setSelectedType(MovementType.OUTBOUND); setStep('select-product'); }}
            >
              <ArrowUpRight className="h-6 w-6" />
              Salida
            </Button>
          </div>

          {recentMovements.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Últimos movimientos</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {recentMovements.map((m, idx) => (
                  <div key={`${m.id}-${idx}`} className="flex items-center justify-between rounded-lg bg-gray-50 p-3 text-sm">
                    <div>
                      <p className="font-medium text-gray-900">{m.productName}</p>
                      <p className="text-gray-500">{m.reason.replace(/_/g, ' ')}</p>
                    </div>
                    <span className={cn(
                      'font-bold',
                      m.type === MovementType.INBOUND ? 'text-green-600' : 'text-red-600'
                    )}>
                      {m.type === MovementType.INBOUND ? '+' : '-'}{m.quantity}
                    </span>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {step === 'select-product' && (
        <div className="space-y-3">
          <Button variant="ghost" onClick={() => setStep('menu')}>← Volver</Button>
          <Card>
            <CardHeader>
              <CardTitle>Seleccionar producto</CardTitle>
            </CardHeader>
            <CardContent>
              <ProductSelector onSelect={(product) => { setSelectedProduct(product); setStep('form'); }} />
            </CardContent>
          </Card>
        </div>
      )}

      {step === 'form' && selectedProduct && (
        <MovementForm
          product={selectedProduct}
          type={selectedType}
          onSuccess={handleSuccess}
          onCancel={() => setStep('select-product')}
        />
      )}
    </div>
  );
}
