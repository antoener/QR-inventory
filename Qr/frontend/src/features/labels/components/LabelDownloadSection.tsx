import { Download, Tag } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { generateIndividualLabelPdf, labelSizeOptions, type LabelSize } from '@/features/labels/labelPdf';
import type { ProductResponse } from '@/types';

interface LabelDownloadSectionProps {
  product: ProductResponse;
}

export function LabelDownloadSection({ product }: LabelDownloadSectionProps) {
  const [size, setSize] = useState<LabelSize>(50);
  const [isGenerating, setIsGenerating] = useState(false);

  const handleDownload = async () => {
    setIsGenerating(true);
    try {
      await generateIndividualLabelPdf(product, size);
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base flex items-center gap-2">
          <Tag className="h-4 w-4" />
          Etiqueta QR
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <label className="mb-2 block text-sm font-medium text-gray-700">Tamaño</label>
          <div className="grid grid-cols-3 gap-2">
            {labelSizeOptions.map((opt) => (
              <Button
                key={opt.value}
                type="button"
                variant={size === opt.value ? 'primary' : 'secondary'}
                onClick={() => setSize(opt.value)}
                size="sm"
              >
                {opt.label}
              </Button>
            ))}
          </div>
        </div>
        <Button onClick={handleDownload} disabled={isGenerating} className="w-full gap-2">
          <Download className="h-4 w-4" />
          {isGenerating ? 'Generando...' : 'Descargar etiqueta'}
        </Button>
      </CardContent>
    </Card>
  );
}
