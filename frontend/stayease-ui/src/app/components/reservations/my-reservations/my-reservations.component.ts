import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ReservationService } from '../../../services/reservation.service';
import { ReviewService } from '../../../services/review.service';
import { Reservation } from '../../../models/reservation.model';
import { Review } from '../../../models/review.model';

@Component({
  selector: 'app-my-reservations',
  templateUrl: './my-reservations.component.html'
})
export class MyReservationsComponent implements OnInit {
  reservations: Reservation[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;
  actionMessage: string | null = null;

  // Pagination
  currentPage: number = 0;
  pageSize: number = 10;
  totalElements: number = 0;
  totalPages: number = 0;

  // Review Tracking (single review per booking)
  reviewedReservationIds: Set<number> = new Set<number>();
  userReviews: Map<number, Review> = new Map<number, Review>();

  // Modals state
  selectedReservationForCancel: Reservation | null = null;
  cancellationReason: string = 'Change of travel plans';
  cancelling: boolean = false;

  selectedReservationForReview: Reservation | null = null;
  reviewRating: number = 5;
  reviewComment: string = '';
  submittingReview: boolean = false;
  reviewError: string | null = null;

  constructor(
    private reservationService: ReservationService,
    private reviewService: ReviewService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadReservations();
  }

  loadReservations(page: number = 0): void {
    this.loading = true;
    this.errorMessage = null;
    this.currentPage = page;

    this.reservationService.getMyReservations(page, this.pageSize).subscribe({
      next: (res) => {
        this.loading = false;
        this.reservations = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 0;
        this.loadReviewedReservations();
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to retrieve your reservations.';
      }
    });
  }

  loadReviewedReservations(): void {
    this.reviewService.getMyReviews(0, 100).subscribe({
      next: (res) => {
        const set = new Set<number>();
        (res.content || []).forEach(rev => {
          if (rev.reservationId) {
            set.add(rev.reservationId);
            this.userReviews.set(rev.reservationId, rev);
          }
        });
        this.reviewedReservationIds = set;
      },
      error: () => {
        // Fallback: non-blocking if review list is not loaded yet
      }
    });
  }

  onCheckIn(reservation: Reservation): void {
    if (!confirm(`Confirm self check-in for booking ${reservation.referenceNumber}?`)) return;

    this.loading = true;
    this.reservationService.checkIn(reservation.id).subscribe({
      next: (res) => {
        this.loading = false;
        this.actionMessage = `Check-in successful! Assigned Room: ${res.physicalRoomNumber}`;
        this.loadReservations(this.currentPage);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Check-in failed. Please ensure a clean room is available.';
      }
    });
  }

  onCheckOut(reservation: Reservation): void {
    if (!confirm(`Confirm self check-out for booking ${reservation.referenceNumber}?`)) return;

    this.loading = true;
    this.reservationService.checkOut(reservation.id).subscribe({
      next: () => {
        this.loading = false;
        this.actionMessage = 'Check-out successful! Thank you for staying with StayEase.';
        this.loadReservations(this.currentPage);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Check-out failed.';
      }
    });
  }

  openCancelModal(reservation: Reservation): void {
    this.selectedReservationForCancel = reservation;
    this.cancellationReason = 'Change of travel plans';
  }

  submitCancel(): void {
    if (!this.selectedReservationForCancel) return;
    this.cancelling = true;

    this.reservationService.cancelReservation(this.selectedReservationForCancel.id, {
      cancellationReason: this.cancellationReason
    }).subscribe({
      next: (summary) => {
        this.cancelling = false;
        this.selectedReservationForCancel = null;
        this.actionMessage = `Reservation cancelled. Refund issued: ₹${summary.refundAmount} (Penalty: ₹${summary.cancellationPenalty})`;
        this.loadReservations(this.currentPage);
      },
      error: (err) => {
        this.cancelling = false;
        this.errorMessage = err.message || 'Cancellation failed.';
      }
    });
  }

  openReviewModal(reservation: Reservation): void {
    if (this.isReviewed(reservation)) {
      this.errorMessage = 'You have already submitted feedback for this booking.';
      return;
    }
    this.selectedReservationForReview = reservation;
    this.reviewRating = 5;
    this.reviewComment = '';
    this.reviewError = null;
  }

  submitReview(): void {
    if (!this.selectedReservationForReview) return;
    if (this.isReviewed(this.selectedReservationForReview)) {
      this.reviewError = 'Feedback has already been provided for this booking.';
      return;
    }
    if (!this.reviewComment || this.reviewComment.trim().length < 10) {
      this.reviewError = 'Review comment must be at least 10 characters.';
      return;
    }

    this.submittingReview = true;
    this.reviewError = null;

    const resId = this.selectedReservationForReview.id;

    this.reviewService.createReview({
      reservationId: resId,
      rating: this.reviewRating,
      comment: this.reviewComment.trim()
    }).subscribe({
      next: (savedReview) => {
        this.submittingReview = false;
        this.reviewedReservationIds.add(resId);
        if (savedReview) {
          this.userReviews.set(resId, savedReview);
        }
        this.selectedReservationForReview = null;
        this.actionMessage = 'Thank you! Your verified review has been published.';
      },
      error: (err) => {
        this.submittingReview = false;
        if (err.status === 409 || err.message?.includes('already reviewed') || err.error?.code === 'DUPLICATE_REVIEW') {
          this.reviewedReservationIds.add(resId);
          this.reviewError = 'Feedback has already been provided for this booking.';
        } else {
          this.reviewError = err.message || 'Failed to submit review.';
        }
      }
    });
  }

  canReview(r: Reservation): boolean {
    if (r.status !== 'CHECKED_OUT') return false;
    if (this.reviewedReservationIds.has(r.id)) return false;
    // Check if within 30 days
    if (!r.checkOutDate) return false;
    const checkoutDate = new Date(r.checkOutDate);
    const now = new Date();
    const diffDays = Math.floor((now.getTime() - checkoutDate.getTime()) / (1000 * 3600 * 24));
    return diffDays <= 30;
  }

  isReviewed(r: Reservation): boolean {
    return this.reviewedReservationIds.has(r.id);
  }

  viewDetails(reservationId: number): void {
    this.router.navigate(['/reservations', reservationId]);
  }
}
