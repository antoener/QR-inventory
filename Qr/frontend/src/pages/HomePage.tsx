import { ScanLine } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent } from '@/components/ui/Card';

export function HomePage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Bienvenido</h1>
      <Card>
        <CardContent className="flex flex-col items-center gap-4 py-10">
          <ScanLine className="h-16 w-16 text-primary-600" />
          <p className="text-center text-gray-600">
            Escaneá un código QR o buscá un producto para ver su stock y registrar movimientos.
          </p>
          <Button size="lg" onClick={() => window.location.href = '/scanner'}>
            Escanear QR
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
