export interface Review {
  id: number;
  reservationId: number;
  hotelId: number;
  guestName: string;
  rating: number;
  comment: string;
  status: 'PUBLISHED' | 'REMOVED';
  createdAt: string;
  removedBy?: number;
  removedAt?: string;
  removalReason?: string;
}

export interface CreateReviewRequest {
  reservationId: number;
  rating: number;
  comment: string;
}

export interface ReviewSummary {
  hotelId: number;
  totalReviews: number;
  averageRating: number;
}

export interface RemoveReviewRequest {
  reason: string;
}
