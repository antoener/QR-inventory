import type { ApiError } from '@/types';

export class ApiClientError extends Error {
  status: number;
  data: ApiError | null;

  constructor(status: number, data: ApiError | null) {
    super(data?.message ?? `Request failed with status ${status}`);
    this.status = status;
    this.data = data;
  }
}

let accessToken: string | null = null;
let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

function onRefreshed(token: string): void {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

function subscribeTokenRefresh(callback: (token: string) => void): void {
  refreshSubscribers.push(callback);
}

async function refreshAccessToken(): Promise<string> {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? '/api'}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new ApiClientError(response.status, await parseError(response));
  }

  const data = (await response.json()) as { token: string };
  setAccessToken(data.token);
  return data.token;
}

async function parseError(response: Response): Promise<ApiError | null> {
  try {
    return (await response.json()) as ApiError;
  } catch {
    return null;
  }
}

export async function apiClient<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api';
  const url = `${baseUrl}${endpoint}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) ?? {}),
  };

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
    credentials: 'include',
  });

  if (response.status === 401 && accessToken && endpoint !== '/auth/refresh') {
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        subscribeTokenRefresh(async (token) => {
          try {
            const retry = await apiClient<T>(endpoint, {
              ...options,
              headers: {
                ...headers,
                Authorization: `Bearer ${token}`,
              },
            });
            resolve(retry);
          } catch (error) {
            reject(error);
          }
        });
      });
    }

    isRefreshing = true;
    try {
      const newToken = await refreshAccessToken();
      onRefreshed(newToken);
      return apiClient<T>(endpoint, {
        ...options,
        headers: {
          ...headers,
          Authorization: `Bearer ${newToken}`,
        },
      });
    } catch (error) {
      setAccessToken(null);
      throw error;
    } finally {
      isRefreshing = false;
    }
  }

  if (!response.ok) {
    throw new ApiClientError(response.status, await parseError(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
