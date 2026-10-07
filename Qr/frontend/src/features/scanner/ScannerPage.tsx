import { ScanLine } from 'lucide-react';
import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Scanner } from './Scanner';

function extractProductCode(value: string): string {
  try {
    const url = new URL(value);
    const match = url.pathname.match(/\/p\/(.+)/);
    return match ? decodeURIComponent(match[1]) : value;
  } catch {
    return value;
  }
}

export function ScannerPage() {
  const navigate = useNavigate();

  const handleCode = useCallback((raw: string) => {
    const code = extractProductCode(raw);
    navigate(`/p/${encodeURIComponent(code)}`);
  }, [navigate]);

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-900">Escanear QR</h1>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ScanLine className="h-5 w-5" />
            Apuntá al código QR
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Scanner onCode={handleCode} />
        </CardContent>
      </Card>
    </div>
  );
}
