import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CreateReviewRequest, Review, ReviewSummary } from '../models/review.model';
import { PageResponse } from '../models/common.model';

@Injectable({
  providedIn: 'root'
})
export class ReviewService {
  private readonly reviewsUrl = `${environment.apiBaseUrl}/reviews`;
  private readonly hotelsUrl = `${environment.apiBaseUrl}/hotels`;

  constructor(private http: HttpClient) {}

  public getHotelReviews(hotelId: number, page: number = 0, size: number = 10): Observable<PageResponse<Review>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Review>>(`${this.hotelsUrl}/${hotelId}/reviews`, { params });
  }

  public getHotelReviewSummary(hotelId: number): Observable<ReviewSummary> {
    return this.http.get<ReviewSummary>(`${this.hotelsUrl}/${hotelId}/reviews/summary`);
  }

  public createReview(request: CreateReviewRequest): Observable<Review> {
    return this.http.post<Review>(this.reviewsUrl, request);
  }

  public getMyReviews(page: number = 0, size: number = 100): Observable<PageResponse<Review>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Review>>(`${this.reviewsUrl}/my`, { params });
  }

  public getReviewByReservation(reservationId: number): Observable<Review | null> {
    return this.http.get<Review | null>(`${this.reviewsUrl}/reservation/${reservationId}`);
  }
}
