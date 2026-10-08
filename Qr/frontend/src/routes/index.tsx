import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { AuthGuard, PublicOnlyGuard } from '@/features/auth/AuthGuard';
import { ChangePasswordPage } from '@/pages/ChangePasswordPage';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';
import { LoginPage } from '@/pages/LoginPage';
import { ResetPasswordPage } from '@/pages/ResetPasswordPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { ProductsPage } from '@/features/products/ProductList';
import { ScannerPage } from '@/features/scanner/ScannerPage';
import { ProductDetailPage, MovementFormPage } from '@/features/scanner/ProductDetailPage';
import { MovementsPage } from '@/features/movements/MovementsPage';
import { LabelsPage } from '@/features/labels/LabelsPage';
import { UsersPage } from '@/features/users/UsersPage';

export const router = createBrowserRouter([
  {
    element: <PublicOnlyGuard />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
    ],
  },
  {
    element: <AuthGuard />,
    children: [
      { path: '/change-password', element: <ChangePasswordPage /> },
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/products', element: <ProductsPage /> },
          { path: '/movements', element: <MovementsPage /> },
          { path: '/etiquetas', element: <LabelsPage /> },
          { path: '/users', element: <UsersPage /> },
          { path: '/scanner', element: <ScannerPage /> },
          { path: '/p/:code', element: <ProductDetailPage /> },
          { path: '/p/:code/:type', element: <MovementFormPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
