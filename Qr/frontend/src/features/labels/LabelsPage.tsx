import { Download, Printer, Tag } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useProducts } from '@/features/products/hooks';
import { generateBatchLabelsPdf, labelSizeOptions, type LabelSize } from '@/features/labels/labelPdf';
import { cn } from '@/lib/utils';

export function LabelsPage() {
  const { data: products, isLoading } = useProducts();
  const [search, setSearch] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [size, setSize] = useState<LabelSize>(50);
  const [isGenerating, setIsGenerating] = useState(false);

  const filteredProducts = useMemo(() => {
    if (!products) return [];
    if (!search.trim()) return products;
    const q = search.toLowerCase();
    return products.filter(
      (p) => p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q),
    );
  }, [products, search]);

  const allFilteredSelected =
    filteredProducts.length > 0 && filteredProducts.every((p) => selectedIds.has(p.id));

  const toggleAll = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allFilteredSelected) {
        filteredProducts.forEach((p) => next.delete(p.id));
      } else {
        filteredProducts.forEach((p) => next.add(p.id));
      }
      return next;
    });
  };

  const toggleProduct = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleGenerate = async () => {
    const selected = (products ?? []).filter((p) => selectedIds.has(p.id));
    if (selected.length === 0) return;
    setIsGenerating(true);
    try {
      await generateBatchLabelsPdf(selected, size);
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-900">Etiquetas QR</h1>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Printer className="h-5 w-5" />
            Generar en lote
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <label className="mb-2 block text-sm font-medium text-gray-700">Tamaño de etiqueta</label>
            <div className="grid grid-cols-3 gap-2">
              {labelSizeOptions.map((opt) => (
                <Button
                  key={opt.value}
                  type="button"
                  variant={size === opt.value ? 'primary' : 'secondary'}
                  onClick={() => setSize(opt.value)}
                >
                  {opt.label}
                </Button>
              ))}
            </div>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-600">
              {selectedIds.size} seleccionado{selectedIds.size !== 1 ? 's' : ''}
            </span>
            <Button
              onClick={handleGenerate}
              disabled={selectedIds.size === 0 || isGenerating}
              className="gap-2"
            >
              <Download className="h-4 w-4" />
              {isGenerating ? 'Generando...' : 'Descargar PDF'}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Seleccionar productos</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Input
            placeholder="Buscar producto..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          {isLoading && (
            <div className="flex justify-center py-6">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
            </div>
          )}

          {!isLoading && filteredProducts.length === 0 && (
            <div className="rounded-lg bg-gray-100 p-4 text-center text-gray-600">
              No se encontraron productos
            </div>
          )}

          {!isLoading && filteredProducts.length > 0 && (
            <>
              <label className="flex items-center gap-2 text-sm font-medium text-gray-700">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-600"
                  checked={allFilteredSelected}
                  onChange={toggleAll}
                />
                Seleccionar todos los visibles
              </label>

              <div className="grid gap-2">
                {filteredProducts.map((product) => (
                  <label
                    key={product.id}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded-lg border border-gray-200 bg-white p-3 shadow-sm transition-colors hover:bg-gray-50',
                      !product.active && 'opacity-60',
                    )}
                  >
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-600"
                      checked={selectedIds.has(product.id)}
                      onChange={() => toggleProduct(product.id)}
                    />
                    <Tag className="h-4 w-4 text-gray-400" />
                    <div className="flex-1">
                      <p className="font-medium text-gray-900">{product.name}</p>
                      <p className="text-sm text-gray-500">{product.code}</p>
                    </div>
                  </label>
                ))}
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
