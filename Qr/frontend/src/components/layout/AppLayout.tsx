import { Link, NavLink, Outlet } from 'react-router-dom';
import { LayoutDashboard, Package, ArrowLeftRight, Tag, Users, ScanLine, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/features/auth/AuthContext';
import { useLogout } from '@/features/auth/hooks';
import { cn } from '@/lib/utils';

export function AppLayout() {
  const { user } = useAuth();
  const logout = useLogout();

  const navItems = [
    { to: '/', icon: LayoutDashboard, label: 'Inicio' },
    { to: '/products', icon: Package, label: 'Productos' },
    { to: '/movements', icon: ArrowLeftRight, label: 'Movimientos' },
    { to: '/etiquetas', icon: Tag, label: 'Etiquetas' },
    { to: '/users', icon: Users, label: 'Usuarios' },
  ];

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white px-4 py-3 shadow-sm">
        <div className="mx-auto flex max-w-3xl items-center justify-between">
          <Link to="/" className="flex items-center gap-2 text-primary-700">
            <ScanLine className="h-6 w-6" />
            <span className="text-lg font-bold">Inventario QR</span>
          </Link>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-gray-600 sm:inline">{user?.name}</span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => logout.mutate()}
              disabled={logout.isPending}
            >
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </header>

      <main className="flex-1 p-4">
        <div className="mx-auto max-w-3xl">
          <Outlet />
        </div>
      </main>

      <nav className="sticky bottom-0 border-t border-gray-200 bg-white px-4 py-2">
        <div className="mx-auto flex max-w-3xl justify-around">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex flex-col items-center gap-1 rounded-lg px-3 py-2 text-xs font-medium transition-colors',
                  isActive ? 'text-primary-700 bg-primary-50' : 'text-gray-600 hover:bg-gray-100'
                )
              }
            >
              <Icon className="h-5 w-5" />
              {label}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  );
}
