import { Component, OnInit } from '@angular/core';
import { AdminService } from '../../../services/admin.service';
import { ReservationService } from '../../../services/reservation.service';
import { Reservation } from '../../../models/reservation.model';

@Component({
  selector: 'app-admin-reservations',
  templateUrl: './admin-reservations.component.html'
})
export class AdminReservationsComponent implements OnInit {
  reservations: Reservation[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  lookupId: number | null = null;
  selectedReservation: Reservation | null = null;
  selectedStatus: string = 'ALL';
  page: number = 0;
  totalPages: number = 1;
  totalElements: number = 0;

  userMap: Map<number, any> = new Map();
  hotelMap: Map<number, string> = new Map();

  // No-Show Modal
  selectedForNoShow: Reservation | null = null;
  adminNotes: string = 'Guest failed to arrive by scheduled check-in window';
  processingNoShow: boolean = false;

  constructor(
    private adminService: AdminService,
    private reservationService: ReservationService
  ) {}

  ngOnInit(): void {
    this.loadMetadata();
    this.loadReservations();
  }

  loadMetadata(): void {
    this.adminService.getAllHotels(0, 50).subscribe({
      next: (res) => {
        (res.content || []).forEach(h => this.hotelMap.set(h.id, h.name));
        this.enrichReservations();
      }
    });

    this.adminService.getUsers().subscribe({
      next: (res) => {
        (res.content || []).forEach((u: any) => this.userMap.set(u.id, u));
        this.enrichReservations();
      }
    });
  }

  enrichReservations(): void {
    if (!this.reservations || this.reservations.length === 0) return;
    this.reservations = this.reservations.map(r => {
      const user = r.userId ? this.userMap.get(r.userId) : null;
      const hotelName = r.hotelId ? this.hotelMap.get(r.hotelId) : null;
      return {
        ...r,
        referenceNumber: r.referenceNumber || r.publicReference || ('RES-' + r.id),
        status: r.status || r.reservationStatus || 'CONFIRMED',
        hotelName: hotelName || r.hotelName || ('Hotel #' + r.hotelId),
        guestName: r.guestName || user?.fullName || ('Guest #' + r.userId),
        guestEmail: r.guestEmail || user?.email || '',
        guestPhone: r.guestPhone || user?.phone || ''
      };
    });
  }

  loadReservations(): void {
    this.loading = true;
    this.errorMessage = null;

    const statusParam = this.selectedStatus === 'ALL' ? undefined : this.selectedStatus;
    this.adminService.getReservations(statusParam, this.page, 20).subscribe({
      next: (res) => {
        this.loading = false;
        this.reservations = res.content || [];
        this.totalPages = res.totalPages || 1;
        this.totalElements = res.totalElements || this.reservations.length;
        this.enrichReservations();
      },
      error: (err) => {
        this.loading = false;
        // Fallback: if backend admin endpoint had issue, try user's reservations
        this.reservationService.getMyReservations(this.page, 20).subscribe({
          next: (myRes) => {
            this.reservations = myRes.content || [];
            this.totalPages = myRes.totalPages || 1;
            this.totalElements = myRes.totalElements || this.reservations.length;
            this.enrichReservations();
          },
          error: (fallbackErr) => {
            this.errorMessage = err.message || 'Failed to load reservations.';
          }
        });
      }
    });
  }

  onStatusChange(): void {
    this.page = 0;
    this.loadReservations();
  }

  onLookup(): void {
    if (!this.lookupId) {
      this.loadReservations();
      return;
    }
    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.reservationService.getReservationById(this.lookupId).subscribe({
      next: (res) => {
        this.loading = false;
        this.selectedReservation = res;
        this.reservations = [res];
        this.enrichReservations();
      },
      error: (err) => {
        this.loading = false;
        this.selectedReservation = null;
        this.reservations = [];
        this.errorMessage = err.message || `Reservation #${this.lookupId} not found or inaccessible.`;
      }
    });
  }

  openNoShowModal(r: Reservation): void {
    this.selectedForNoShow = r;
    this.adminNotes = 'Guest failed to arrive by scheduled check-in window';
  }

  submitNoShow(): void {
    if (!this.selectedForNoShow) return;

    this.processingNoShow = true;
    this.adminService.markNoShow(this.selectedForNoShow.id, this.adminNotes).subscribe({
      next: () => {
        this.processingNoShow = false;
        this.successMessage = `Reservation #${this.selectedForNoShow?.id} marked NO_SHOW and room capacity released.`;
        this.selectedForNoShow = null;
        this.onLookup();
      },
      error: (err) => {
        this.processingNoShow = false;
        this.errorMessage = err.message || 'Failed to execute no-show transition.';
      }
    });
  }

  onReconcile(r: Reservation): void {
    this.adminService.reconcileReservation(r.id, 'SUCCESS').subscribe({
      next: () => {
        this.successMessage = `Reconciliation completed successfully for booking #${r.id}.`;
        this.onLookup();
      },
      error: (err) => {
        this.errorMessage = err.message || 'Reconciliation failed.';
      }
    });
  }
}
