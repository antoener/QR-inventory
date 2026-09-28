---
name: frontend-dev
description: 'Best practices for building a React SPA with TypeScript, Vite, and Tailwind CSS that consumes a Spring Boot REST API.'
---

# Frontend Development Best Practices — React SPA

Your goal is to help build a modern, maintainable React frontend that consumes the existing Spring Boot REST API for a Scrap Yard Management system.

## Tech Stack

- **Framework:** React 18+ with TypeScript
- **Build Tool:** Vite
- **Styling:** Tailwind CSS
- **Routing:** React Router v6+
- **Server State:** TanStack Query (React Query) v5
- **Forms:** React Hook Form + Zod validation
- **HTTP Client:** fetch API (native) with a thin wrapper, or axios
- **Icons:** Lucide React
- **Linting:** ESLint + Prettier

## Project Setup

Create the frontend as a **separate project** alongside the Spring Boot backend:

```
management/
  management/          # Spring Boot backend (existing)
  frontend/            # React SPA (new)
    src/
    public/
    index.html
    package.json
    vite.config.ts
    tailwind.config.ts
    tsconfig.json
```

### Vite Proxy Configuration

In `vite.config.ts`, proxy API requests to the Spring Boot backend during development:

```typescript
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

### Spring Boot CORS (if needed)

In the Spring Boot project, configure CORS for development:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
    }
}
```

## Folder Structure

Organize by **feature/domain** to align with the backend structure:

```
src/
  api/                    # API client setup
    client.ts             # Base fetch wrapper / axios instance
    endpoints/            # One file per resource
      materials.ts
      vehicles.ts
      sales.ts
      suppliers.ts
      reports.ts
  components/             # Shared UI components
    ui/                   # Primitive components (Button, Input, Modal, Table)
    layout/               # AppShell, Sidebar, Header, Footer
  features/               # Feature modules
    materials/
      components/         # Feature-specific components
      hooks/              # Feature-specific hooks
      schemas/            # Zod validation schemas
      types.ts            # Feature-specific types
    vehicles/
      ...
    sales/
      ...
    suppliers/
      ...
    dashboard/
      ...
    reports/
      ...
  hooks/                  # Shared custom hooks
  lib/                    # Utility functions, formatters, constants
  pages/                  # Route-level page components
  routes/                 # React Router configuration
  types/                  # Shared TypeScript types (matching backend DTOs)
  App.tsx
  main.tsx
```

## TypeScript Conventions

- **Strict mode enabled** in `tsconfig.json` (`"strict": true`)
- **Interfaces for API responses** — mirror the backend DTOs exactly
- **Type aliases for primitives** — e.g., `type MaterialId = string;`
- **No `any`** — use `unknown` and narrow with type guards if the type is truly unknown
- **Explicit return types** on exported functions

### API Types

Match backend DTO names and field naming. Use camelCase in TypeScript (auto-transform from snake_case if the API returns snake_case):

```typescript
export interface MaterialDTO {
  id: number;
  name: string;
  type: MaterialType;
  weight: number;
  pricePerKg: number;
  entryDate: string;
}

export enum MaterialType {
  FERROUS = 'FERROUS',
  NON_FERROUS = 'NON_FERROUS',
  ELECTRONIC = 'ELECTRONIC',
}
```

## API Client Layer

Create a thin wrapper around fetch to centralize base URL, headers, and error handling:

```typescript
const API_BASE = '/api';

async function apiClient<T>(
  endpoint: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(response.status, error.message || response.statusText);
  }

  return response.json();
}
```

Each endpoint file exports typed functions:

```typescript
export const materialsApi = {
  getAll: () => apiClient<MaterialDTO[]>('/materials'),
  getById: (id: number) => apiClient<MaterialDTO>(`/materials/${id}`),
  create: (data: CreateMaterialDTO) =>
    apiClient<MaterialDTO>('/materials', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: UpdateMaterialDTO) =>
    apiClient<MaterialDTO>(`/materials/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) =>
    apiClient<void>(`/materials/${id}`, { method: 'DELETE' }),
};
```

## Server State with TanStack Query

Use TanStack Query for all server state. Do **not** store API data in React state or context.

### Query Hooks Pattern

```typescript
export function useMaterials() {
  return useQuery({
    queryKey: ['materials'],
    queryFn: materialsApi.getAll,
  });
}

export function useMaterial(id: number) {
  return useQuery({
    queryKey: ['materials', id],
    queryFn: () => materialsApi.getById(id),
    enabled: !!id,
  });
}
```

### Mutation Hooks Pattern

```typescript
export function useCreateMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: materialsApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['materials'] });
    },
  });
}

export function useDeleteMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: materialsApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['materials'] });
    },
  });
}
```

## Forms with React Hook Form + Zod

Define Zod schemas that mirror backend validation:

```typescript
import { z } from 'zod';

export const createMaterialSchema = z.object({
  name: z.string().min(1, 'Name is required').max(100),
  type: z.nativeEnum(MaterialType),
  weight: z.number().positive('Weight must be positive'),
  pricePerKg: z.number().min(0, 'Price cannot be negative'),
  supplierId: z.number().int().positive(),
});

export type CreateMaterialFormData = z.infer<typeof createMaterialSchema>;
```

Use in components:

```typescript
const { register, handleSubmit, formState: { errors } } = useForm<CreateMaterialFormData>({
  resolver: zodResolver(createMaterialSchema),
});
```

## Routing

Define routes in a central configuration file:

```typescript
export const routes = [
  { path: '/', element: <DashboardPage /> },
  { path: '/materials', element: <MaterialsPage /> },
  { path: '/materials/new', element: <CreateMaterialPage /> },
  { path: '/materials/:id', element: <MaterialDetailPage /> },
  { path: '/vehicles', element: <VehiclesPage /> },
  { path: '/sales', element: <SalesPage /> },
  { path: '/suppliers', element: <SuppliersPage /> },
  { path: '/reports', element: <ReportsPage /> },
];
```

Use lazy loading for route components:

```typescript
const MaterialsPage = lazy(() => import('../features/materials/components/MaterialsPage'));
```

## Component Conventions

- **One component per file** — file name matches component name (`MaterialCard.tsx` exports `MaterialCard`)
- **PascalCase** for components and files
- **camelCase** for functions, hooks, variables
- **Use function declarations** for components, not arrow functions:
  ```typescript
  export function MaterialCard({ material }: MaterialCardProps) { ... }
  ```
- **Destructure props** in the function signature
- **Keep components small** — under 150 lines; extract sub-components when needed
- **Colocate** — keep feature-specific components inside the feature folder

### Shared UI Components

Build a small, reusable component library in `components/ui/`:

- `Button` — variants: primary, secondary, danger, ghost; sizes: sm, md, lg
- `Input` — with label, error message support
- `Select` — with options, error message support
- `Modal` — dialog wrapper
- `Table` — sortable columns, pagination
- `Badge` — status indicators
- `Card` — content container
- `LoadingSpinner` — loading state indicator
- `EmptyState` — no-data placeholder

## Tailwind CSS Conventions

- Use **Tailwind utility classes** directly in JSX — no separate CSS files unless absolutely necessary
- Use `@apply` sparingly — prefer composing with component wrappers
- Use the **`cn()` utility** (clsx + tailwind-merge) for conditional classes:
  ```typescript
  import { cn } from '@/lib/utils';
  <div className={cn('rounded-lg p-4', isActive && 'bg-blue-50')} />
  ```
- Define **custom theme tokens** in `tailwind.config.ts` for brand colors and spacing

## Error Handling

- **API errors** — catch in TanStack Query's `onError` callback, show toast notifications
- **Form errors** — display inline below each field via Zod + React Hook Form
- **Unexpected errors** — use an Error Boundary component at the route level
- **Loading states** — always show a loading indicator while data is being fetched
- **Empty states** — always handle the case when a list has no items

## Page Layout Pattern

Every data page should follow this pattern:

1. **Page header** with title and primary action button (e.g., "Add Material")
2. **Filters/search bar** (if applicable)
3. **Data table** with sorting and pagination
4. **Empty state** when no data exists
5. **Loading skeleton** while fetching

## Performance

- **Lazy load** route components with `React.lazy` + `Suspense`
- **Memoize** expensive computations with `useMemo`
- **Debounce** search inputs (300ms)
- **Paginate** data on the server side (pass `page` and `size` query params to match Spring Data pagination)
- **Avoid premature optimization** — profile first

## Testing

- **Vitest** as the test runner (built into Vite ecosystem)
- **React Testing Library** for component tests
- **MSW (Mock Service Worker)** for API mocking in tests
- Test user behavior, not implementation details
- Prioritize integration tests for pages and features over unit tests for individual components

## Accessibility

- Use semantic HTML elements (`<nav>`, `<main>`, `<section>`, `<button>`)
- All images must have `alt` text
- All form inputs must have associated `<label>` elements
- Interactive elements must be keyboard accessible
- Use `aria-*` attributes when semantic HTML is not enough
- Maintain sufficient color contrast (4.5:1 minimum)

## Security

- **Never store tokens in localStorage** — use httpOnly cookies if the backend supports it
- **Sanitize** any user input rendered as HTML (prefer React's default escaping)
- **Validate** all form inputs on both client (Zod) and server side
- **Use environment variables** (`.env` files) for configuration, never hardcode URLs or secrets
