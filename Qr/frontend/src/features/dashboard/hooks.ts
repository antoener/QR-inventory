import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '@/api';

const dashboardKeys = {
  summary: ['dashboard', 'summary'] as const,
};

export function useDashboardSummary() {
  return useQuery({
    queryKey: dashboardKeys.summary,
    queryFn: dashboardApi.getSummary,
  });
}
