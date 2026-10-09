import { Search } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useProducts, useUpdateProductStatus } from '@/features/products/hooks';
import { cn } from '@/lib/utils';
import type { ProductResponse } from '@/types';

interface ProductListProps {
  search: string;
  onEdit: (product: ProductResponse) => void;
  onSelect?: (product: ProductResponse) => void;
}

function ProductList({ search, onEdit, onSelect }: ProductListProps) {
  const { data: products, isLoading, error } = useProducts(search || undefined);
  const toggleStatus = useUpdateProductStatus();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div className="flex justify-center py-10">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg bg-red-50 p-4 text-center text-red-700">
        Error al cargar productos
      </div>
    );
  }

  if (!products || products.length === 0) {
    return (
      <div className="rounded-lg bg-gray-100 p-6 text-center text-gray-600">
        {search ? 'No se encontraron productos' : 'No hay productos cargados'}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {products.map((product) => (
        <div
          key={product.id}
          onClick={() => onSelect?.(product)}
          className={cn(
            'flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4 shadow-sm',
            onSelect && 'cursor-pointer active:bg-gray-50'
          )}
        >
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h3 className={cn('font-semibold text-gray-900', !product.active && 'line-through text-gray-500')}>
                {product.name}
              </h3>
              {!product.active && (
                <span className="rounded-full bg-gray-200 px-2 py-0.5 text-xs font-medium text-gray-700">
                  Inactivo
                </span>
              )}
            </div>
            <p className="text-sm text-gray-500">{product.code}</p>
            {product.description && (
              <p className="mt-1 truncate text-sm text-gray-600">{product.description}</p>
            )}
          </div>
          <div className="ml-4 flex shrink-0 gap-2">
            <Button variant="secondary" size="sm" onClick={(e) => { e.stopPropagation(); onEdit(product); }}>
              Editar
            </Button>
            <Button variant="secondary" size="sm" onClick={(e) => { e.stopPropagation(); navigate(`/p/${encodeURIComponent(product.code)}`); }}>
              Stock
            </Button>
            <Button
              variant={product.active ? 'ghost' : 'primary'}
              size="sm"
              onClick={(e) => { e.stopPropagation(); toggleStatus.mutate({ id: product.id, active: !product.active }); }}
              disabled={toggleStatus.isPending}
            >
              {product.active ? 'Desactivar' : 'Activar'}
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
}

export function ProductsPage() {
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductResponse | null>(null);
  const [search, setSearch] = useState('');

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Productos</h1>
        <Button onClick={() => { setEditingProduct(null); setIsFormOpen(true); }}>
          Nuevo producto
        </Button>
      </div>

      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
        <Input
          placeholder="Buscar por código o nombre..."
          className="pl-10"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <ProductList
        search={search}
        onEdit={(product) => { setEditingProduct(product); setIsFormOpen(true); }}
      />

      {isFormOpen && (
        <ProductFormModal
          product={editingProduct}
          onClose={() => setIsFormOpen(false)}
        />
      )}
    </div>
  );
}

// Import dinámico para evitar referencia circular; se define abajo.
import { ProductFormModal } from './ProductFormModal';
