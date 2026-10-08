import { ArrowDownLeft, ArrowUpRight, Package, ScanLine } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { useDashboardSummary } from '@/features/dashboard/hooks';
import { cn } from '@/lib/utils';
import { MovementType } from '@/types';

function SummaryCard({
  title,
  value,
  subtitle,
  icon: Icon,
  variant,
}: {
  title: string;
  value: number;
  subtitle?: string;
  icon: React.ElementType;
  variant: 'blue' | 'green' | 'red' | 'gray';
}) {
  const variantClasses = {
    blue: 'bg-blue-50 text-blue-700',
    green: 'bg-green-50 text-green-700',
    red: 'bg-red-50 text-red-700',
    gray: 'bg-gray-50 text-gray-700',
  };

  return (
    <Card>
      <CardContent className="flex items-center gap-4 p-4">
        <div className={cn('rounded-lg p-3', variantClasses[variant])}>
          <Icon className="h-6 w-6" />
        </div>
        <div>
          <p className="text-sm text-gray-500">{title}</p>
          <p className="text-2xl font-bold text-gray-900">{value}</p>
          {subtitle && <p className="text-xs text-gray-500">{subtitle}</p>}
        </div>
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const navigate = useNavigate();
  const { data: summary, isLoading, error } = useDashboardSummary();

  if (isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
      </div>
    );
  }

  if (error || !summary) {
    return (
      <div className="rounded-lg bg-red-50 p-6 text-center text-red-700">
        Error al cargar el dashboard
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Inicio</h1>

      <Button size="lg" className="h-24 w-full flex-col gap-2" onClick={() => navigate('/scanner')}>
        <ScanLine className="h-8 w-8" />
        Escanear QR
      </Button>

      <div className="grid grid-cols-2 gap-3">
        <SummaryCard
          title="Productos"
          value={summary.totalProducts}
          subtitle={`${summary.activeProducts} activos`}
          icon={Package}
          variant="blue"
        />
        <SummaryCard
          title="Stock total"
          value={summary.totalStock}
          icon={Package}
          variant="gray"
        />
        <SummaryCard
          title="Entradas hoy"
          value={summary.inboundTodayCount}
          subtitle={`${summary.inboundTodayUnits} unidades`}
          icon={ArrowDownLeft}
          variant="green"
        />
        <SummaryCard
          title="Salidas hoy"
          value={summary.outboundTodayCount}
          subtitle={`${summary.outboundTodayUnits} unidades`}
          icon={ArrowUpRight}
          variant="red"
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Últimos movimientos</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {summary.recentMovements.length === 0 && (
            <div className="rounded-lg bg-gray-100 p-4 text-center text-gray-600">
              No hay movimientos recientes
            </div>
          )}
          {summary.recentMovements.map((m) => (
            <div
              key={m.id}
              className="flex items-center justify-between rounded-lg bg-gray-50 p-3 text-sm"
            >
              <div>
                <p className="font-medium text-gray-900">{m.productName}</p>
                <p className="text-gray-500">
                  {m.reason.replace(/_/g, ' ')} · {m.userName} · {new Date(m.createdAt).toLocaleString()}
                </p>
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
    </div>
  );
}
