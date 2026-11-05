import { Component, OnInit } from '@angular/core';
import { AdminService } from '../../../services/admin.service';
import { ReviewService } from '../../../services/review.service';
import { Hotel } from '../../../models/hotel.model';
import { Review } from '../../../models/review.model';

@Component({
  selector: 'app-admin-reviews',
  templateUrl: './admin-reviews.component.html'
})
export class AdminReviewsComponent implements OnInit {
  hotels: Hotel[] = [];
  selectedHotelId: number | null = null;
  reviews: Review[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  // Moderation Removal Modal
  selectedReviewForRemoval: Review | null = null;
  removalReason: string = 'Violates community content guidelines';
  removing: boolean = false;

  constructor(
    private adminService: AdminService,
    private reviewService: ReviewService
  ) {}

  ngOnInit(): void {
    this.loadHotels();
  }

  loadHotels(): void {
    this.adminService.getAllHotels(0, 50).subscribe({
      next: (res) => {
        this.hotels = res.content || [];
        if (this.hotels.length > 0) {
          this.selectedHotelId = this.hotels[0].id;
          this.loadReviews();
        }
      },
      error: (err) => {
        this.errorMessage = err.message || 'Failed to load hotels.';
      }
    });
  }

  loadReviews(): void {
    if (!this.selectedHotelId) return;
    this.loading = true;
    this.errorMessage = null;

    this.reviewService.getHotelReviews(this.selectedHotelId, 0, 50).subscribe({
      next: (res) => {
        this.loading = false;
        this.reviews = res.content || [];
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to load reviews.';
      }
    });
  }

  openRemoveModal(review: Review): void {
    this.selectedReviewForRemoval = review;
    this.removalReason = 'Violates community content guidelines';
  }

  submitRemoval(): void {
    if (!this.selectedReviewForRemoval || !this.removalReason.trim()) return;

    this.removing = true;
    this.adminService.removeReview(this.selectedReviewForRemoval.id, this.removalReason.trim()).subscribe({
      next: () => {
        this.removing = false;
        this.successMessage = `Review #${this.selectedReviewForRemoval?.id} has been moderated and removed from public listings.`;
        this.selectedReviewForRemoval = null;
        this.loadReviews();
      },
      error: (err) => {
        this.removing = false;
        this.errorMessage = err.message || 'Failed to moderate review.';
      }
    });
  }
}
