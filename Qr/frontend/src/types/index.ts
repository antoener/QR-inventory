export enum Role {
  ADMIN = 'ADMIN',
}

export enum MovementType {
  INBOUND = 'INBOUND',
  OUTBOUND = 'OUTBOUND',
}

export enum Origin {
  MANUAL = 'MANUAL',
  MACHINE = 'MACHINE',
}

export enum Reason {
  PRODUCTION = 'PRODUCTION',
  MATERIAL_PURCHASE = 'MATERIAL_PURCHASE',
  CUSTOMER_RETURN = 'CUSTOMER_RETURN',
  SALE = 'SALE',
  WASTE = 'WASTE',
  INTERNAL_USE = 'INTERNAL_USE',
  GIFT = 'GIFT',
  LOSS = 'LOSS',
}

export enum MovementPeriod {
  TODAY = 'TODAY',
  THIS_WEEK = 'THIS_WEEK',
  THIS_MONTH = 'THIS_MONTH',
}

export interface ApiError {
  status: number;
  message: string;
  field: string | null;
  timestamp: string;
  retryAfter?: number;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  name: string;
  active: boolean;
  mustChangePassword: boolean;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: UserResponse;
}

export interface ProductResponse {
  id: number;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
}

export interface StockMovementResponse {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  type: MovementType;
  quantity: number;
  reason: Reason;
  detail: string | null;
  origin: Origin;
  userId: number;
  userName: string;
  createdAt: string;
}

export interface DashboardSummaryResponse {
  totalProducts: number;
  activeProducts: number;
  totalStock: number;
  inboundTodayCount: number;
  inboundTodayUnits: number;
  outboundTodayCount: number;
  outboundTodayUnits: number;
  recentMovements: StockMovementResponse[];
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface LoginRequest {
  identifier: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface CreateProductRequest {
  code: string;
  name: string;
  description?: string | null;
}

export interface UpdateProductRequest {
  name: string;
  description?: string | null;
}

export interface UpdateProductStatusRequest {
  active: boolean;
}

export interface CreateStockMovementRequest {
  productId: number;
  quantity: number;
  reason: Reason;
  detail?: string | null;
  origin: Origin;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  name: string;
  role: Role;
}

export interface UpdateUserRequest {
  username: string;
  email: string;
  name: string;
}
