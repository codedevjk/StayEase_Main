export interface AuditEvent {
  id: number;
  sourceService: string;
  actorId: number;
  actorRole: string;
  eventCategory: string;
  action: string;
  entityId: string;
  description: string;
  timestamp: string;
  beforeJson?: string;
  afterJson?: string;
}

export interface AdminDashboardMetrics {
  reservationCountsByStatus?: { [key: string]: number };
  todayCheckIns?: number;
  todayCheckOuts?: number;
  currentOccupancyRate?: number;
  pendingPaymentsCount?: number;
  totalRevenueCollected?: number;
  cancellationsCount?: number;
  noShowCount?: number;
  partialDataWarning?: string;
}
