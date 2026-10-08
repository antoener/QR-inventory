import { Plus, Search } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useToggleUserStatus, useUsers } from '@/features/users/hooks';
import { cn } from '@/lib/utils';
import type { UserResponse } from '@/types';

function UserList({ search, onEdit }: { search: string; onEdit: (user: UserResponse) => void }) {
  const { data: users, isLoading, error } = useUsers();
  const toggleStatus = useToggleUserStatus();

  const filtered = users?.filter((u) => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      u.name.toLowerCase().includes(q) ||
      u.username.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q)
    );
  });

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
        Error al cargar usuarios
      </div>
    );
  }

  if (!filtered || filtered.length === 0) {
    return (
      <div className="rounded-lg bg-gray-100 p-6 text-center text-gray-600">
        {search ? 'No se encontraron usuarios' : 'No hay usuarios cargados'}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {filtered.map((user) => (
        <div
          key={user.id}
          className="flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
        >
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h3 className={cn('font-semibold text-gray-900', !user.active && 'text-gray-500 line-through')}>
                {user.name}
              </h3>
              {!user.active && (
                <span className="rounded-full bg-gray-200 px-2 py-0.5 text-xs font-medium text-gray-700">
                  Inactivo
                </span>
              )}
              {user.mustChangePassword && (
                <span className="rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800">
                  Debe cambiar contraseña
                </span>
              )}
            </div>
            <p className="text-sm text-gray-500">{user.username}</p>
            <p className="text-sm text-gray-500">{user.email}</p>
          </div>
          <div className="ml-4 flex shrink-0 gap-2">
            <Button variant="secondary" size="sm" onClick={() => onEdit(user)}>
              Editar
            </Button>
            <Button
              variant={user.active ? 'ghost' : 'primary'}
              size="sm"
              onClick={() => toggleStatus.mutate({ id: user.id, active: !user.active })}
              disabled={toggleStatus.isPending}
            >
              {user.active ? 'Desactivar' : 'Activar'}
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
}

export function UsersPage() {
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserResponse | null>(null);
  const [search, setSearch] = useState('');

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Usuarios</h1>
        <Button onClick={() => { setEditingUser(null); setIsFormOpen(true); }}>
          <Plus className="mr-1 h-4 w-4" />
          Nuevo usuario
        </Button>
      </div>

      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
        <Input
          placeholder="Buscar por nombre, usuario o email..."
          className="pl-10"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <UserList
        search={search}
        onEdit={(user) => { setEditingUser(user); setIsFormOpen(true); }}
      />

      {isFormOpen && (
        <UserFormModal
          user={editingUser}
          onClose={() => setIsFormOpen(false)}
        />
      )}
    </div>
  );
}

import { UserFormModal } from './UserFormModal';
