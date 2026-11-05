import { Component, OnInit } from '@angular/core';
import { AdminService } from '../../../services/admin.service';
import { AuditEvent } from '../../../models/audit.model';

@Component({
  selector: 'app-admin-audit',
  templateUrl: './admin-audit.component.html'
})
export class AdminAuditComponent implements OnInit {
  auditEvents: AuditEvent[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;

  selectedService: 'users' | 'reservations' | 'reviews' = 'reservations';
  filterCategory: string = '';
  filterActorId: number | null = null;
  fromDate: string = '';
  toDate: string = '';

  currentPage: number = 0;
  pageSize: number = 15;
  totalElements: number = 0;
  totalPages: number = 0;

  selectedSnapshotEvent: AuditEvent | null = null;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadAuditLogs();
  }

  loadAuditLogs(page: number = 0): void {
    this.loading = true;
    this.errorMessage = null;
    this.currentPage = page;

    this.adminService.getAuditEvents(
      this.selectedService,
      this.filterCategory || undefined,
      this.filterActorId !== null ? this.filterActorId : undefined,
      this.fromDate ? new Date(this.fromDate).toISOString() : undefined,
      this.toDate ? new Date(this.toDate).toISOString() : undefined,
      page,
      this.pageSize
    ).subscribe({
      next: (res) => {
        this.loading = false;
        this.auditEvents = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 0;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to retrieve audit trail.';
      }
    });
  }

  onServiceChange(): void {
    this.filterCategory = '';
    this.filterActorId = null;
    this.currentPage = 0;
    this.loadAuditLogs(0);
  }

  viewSnapshot(event: AuditEvent): void {
    this.selectedSnapshotEvent = event;
  }
}
