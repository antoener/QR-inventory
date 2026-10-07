import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { AuthGuard, PublicOnlyGuard } from '@/features/auth/AuthGuard';
import { ChangePasswordPage } from '@/pages/ChangePasswordPage';
import { HomePage } from '@/pages/HomePage';
import { LoginPage } from '@/pages/LoginPage';
import { ProductsPage } from '@/features/products/ProductList';
import { ScannerPage } from '@/features/scanner/ScannerPage';
import { ProductDetailPage, MovementFormPage } from '@/features/scanner/ProductDetailPage';
import { MovementsPage } from '@/features/movements/MovementsPage';

export const router = createBrowserRouter([
  {
    element: <PublicOnlyGuard />,
    children: [
      { path: '/login', element: <LoginPage /> },
    ],
  },
  {
    element: <AuthGuard />,
    children: [
      { path: '/change-password', element: <ChangePasswordPage /> },
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/products', element: <ProductsPage /> },
          { path: '/movements', element: <MovementsPage /> },
          { path: '/scanner', element: <ScannerPage /> },
          { path: '/p/:code', element: <ProductDetailPage /> },
          { path: '/p/:code/:type', element: <MovementFormPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
