import { apiClient } from '@/api/client';
import type { DashboardSummaryResponse } from '@/types';

export const dashboardApi = {
  getSummary: () => apiClient<DashboardSummaryResponse>('/dashboard/summary'),
};
