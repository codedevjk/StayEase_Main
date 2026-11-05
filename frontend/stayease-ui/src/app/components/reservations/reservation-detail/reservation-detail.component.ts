import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ReservationService } from '../../../services/reservation.service';
import { InvoiceDto, Reservation } from '../../../models/reservation.model';

@Component({
  selector: 'app-reservation-detail',
  templateUrl: './reservation-detail.component.html',
  styleUrls: ['./reservation-detail.component.css']
})
export class ReservationDetailComponent implements OnInit {
  reservationId!: number;
  reservation: Reservation | null = null;
  invoice: InvoiceDto | null = null;
  loading: boolean = false;
  errorMessage: string | null = null;

  // Amendment Form
  showAmendModal: boolean = false;
  newCheckInDate: string = '';
  newCheckOutDate: string = '';
  newGuestCount: number = 2;
  amending: boolean = false;
  amendError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private reservationService: ReservationService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.reservationId = parseInt(params['id'], 10);
      if (this.reservationId) {
        this.loadDetails();
        this.loadInvoice();
      }
    });
  }

  loadDetails(): void {
    this.loading = true;
    this.reservationService.getReservationById(this.reservationId).subscribe({
      next: (res) => {
        this.loading = false;
        this.reservation = res;
        this.newCheckInDate = res.checkInDate;
        this.newCheckOutDate = res.checkOutDate;
        this.newGuestCount = res.guestCount;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to load reservation details.';
      }
    });
  }

  loadInvoice(): void {
    this.reservationService.getInvoice(this.reservationId).subscribe({
      next: (inv) => {
        this.invoice = inv;
      },
      error: () => {}
    });
  }

  printInvoice(): void {
    window.print();
  }

  openAmendModal(): void {
    this.showAmendModal = true;
    this.amendError = null;
  }

  submitAmendment(): void {
    if (!this.reservation) return;

    this.amending = true;
    this.amendError = null;

    const payload = {
      newCheckInDate: this.newCheckInDate,
      newCheckOutDate: this.newCheckOutDate,
      newRoomTypeId: this.reservation.roomTypeId,
      newGuestCount: this.newGuestCount
    };

    this.reservationService.amendReservation(this.reservation.id, payload).subscribe({
      next: (updated) => {
        this.amending = false;
        this.showAmendModal = false;
        this.reservation = updated;
        this.loadInvoice();
      },
      error: (err) => {
        this.amending = false;
        this.amendError = err.message || 'Amendment failed. Please check room availability.';
      }
    });
  }
}
