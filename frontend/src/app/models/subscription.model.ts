export interface Subscription {
  id?: number;
  name: string;
  category: string;
  amount: number;
  billingCycle: string;
  nextRenewalDate: string;
  status: string;
  notes?: string;
}

export interface DashboardSummary {
  monthlySpend: number;
  yearlyProjection: number;
  activeCount: number;
  dueThisWeekCount: number;
  dueThisWeekAmount: number;
  categoryBreakdown: { [key: string]: number };
}

