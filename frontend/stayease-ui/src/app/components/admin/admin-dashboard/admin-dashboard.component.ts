import { Component, OnInit } from '@angular/core';
import { AdminService } from '../../../services/admin.service';
import { AdminDashboardMetrics } from '../../../models/audit.model';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html'
})
export class AdminDashboardComponent implements OnInit {
  metrics: AdminDashboardMetrics | null = null;
  loading: boolean = false;
  errorMessage: string | null = null;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadMetrics();
  }

  loadMetrics(): void {
    this.loading = true;
    this.errorMessage = null;

    this.adminService.getDashboardMetrics().subscribe({
      next: (m) => {
        this.loading = false;
        this.metrics = m;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to aggregate dashboard metrics.';
      }
    });
  }
}
