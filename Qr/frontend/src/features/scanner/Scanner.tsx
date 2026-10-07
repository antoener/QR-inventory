import { useEffect, useRef, useState } from 'react';
import { BrowserQRCodeReader } from '@zxing/browser';
import { Camera, Keyboard } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

interface ScannerProps {
  onCode: (code: string) => void;
}

export function Scanner({ onCode }: ScannerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [isScanning, setIsScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [manualCode, setManualCode] = useState('');
  const [showManual, setShowManual] = useState(false);
  const controlsRef = useRef<{ stop: () => void } | null>(null);

  useEffect(() => {
    let mounted = true;

    const start = async () => {
      try {
        setIsScanning(true);
        setError(null);
        const reader = new BrowserQRCodeReader();
        const result = await reader.decodeFromVideoDevice(
          undefined,
          videoRef.current ?? undefined,
          (result, err) => {
            if (!mounted) return;
            if (result) {
              onCode(result.getText());
            }
            if (err && err.name !== 'NotFoundException') {
              setError('Error al acceder a la cámara');
            }
          }
        );
        controlsRef.current = result;
      } catch {
        if (mounted) {
          setError('No se pudo iniciar la cámara. Usá el ingreso manual.');
          setIsScanning(false);
        }
      }
    };

    if (!showManual) {
      start();
    }

    return () => {
      mounted = false;
      controlsRef.current?.stop();
      controlsRef.current = null;
    };
  }, [showManual, onCode]);

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (manualCode.trim()) {
      onCode(manualCode.trim());
    }
  };

  if (showManual) {
    return (
      <div className="space-y-4">
        <form onSubmit={handleManualSubmit} className="space-y-3">
          <Input
            label="Código del producto"
            placeholder="Ej: BOL-001"
            value={manualCode}
            onChange={(e) => setManualCode(e.target.value)}
            autoFocus
          />
          <Button type="submit" className="w-full">
            Buscar producto
          </Button>
        </form>
        <Button variant="ghost" className="w-full" onClick={() => setShowManual(false)}>
          <Camera className="mr-2 h-4 w-4" />
          Volver a la cámara
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="relative aspect-square w-full overflow-hidden rounded-xl bg-black">
        <video ref={videoRef} className="h-full w-full object-cover" muted playsInline />
        {!isScanning && !error && (
          <div className="absolute inset-0 flex items-center justify-center text-white">
            Iniciando cámara...
          </div>
        )}
        {error && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/80 p-4 text-center text-white">
            <p className="mb-4">{error}</p>
            <Button onClick={() => setShowManual(true)}>Ingresar código manual</Button>
          </div>
        )}
      </div>
      <Button variant="secondary" className="w-full" onClick={() => setShowManual(true)}>
        <Keyboard className="mr-2 h-4 w-4" />
        ¿Tenés el código? Ingresalo a mano
      </Button>
    </div>
  );
}
