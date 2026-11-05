import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AdminDashboardMetrics, AuditEvent } from '../models/audit.model';
import { Hotel, PhysicalRoom, RoomType } from '../models/hotel.model';
import { Reservation } from '../models/reservation.model';
import { Review } from '../models/review.model';
import { PageResponse } from '../models/common.model';
import { normalizeReservation } from './reservation.service';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly adminUrl = `${environment.apiBaseUrl}/admin`;

  constructor(private http: HttpClient) {}

  public getDashboardMetrics(): Observable<AdminDashboardMetrics> {
    return this.http.get<AdminDashboardMetrics>(`${this.adminUrl}/dashboard`);
  }

  // ── Hotel Management ────────────────────────────────────────────────────────
  public getAllHotels(page: number = 0, size: number = 10): Observable<PageResponse<Hotel>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    // Property Service exposes public /api/hotels catalog with destination/paging filters
    return this.http.get<PageResponse<Hotel>>(`${environment.apiBaseUrl}/hotels`, { params });
  }

  public createHotel(hotel: Partial<Hotel>): Observable<Hotel> {
    return this.http.post<Hotel>(`${this.adminUrl}/hotels`, hotel);
  }

  public updateHotel(id: number, hotel: Partial<Hotel>): Observable<Hotel> {
    return this.http.put<Hotel>(`${this.adminUrl}/hotels/${id}`, hotel);
  }

  public toggleHotelStatus(id: number, active: boolean): Observable<Hotel> {
    const params = new HttpParams().set('active', active.toString());
    return this.http.patch<Hotel>(`${this.adminUrl}/hotels/${id}/status`, {}, { params });
  }

  // ── Room & Inventory Management ─────────────────────────────────────────────
  public createRoomType(hotelId: number, roomType: any): Observable<RoomType> {
    return this.http.post<RoomType>(`${this.adminUrl}/hotels/${hotelId}/room-types`, roomType);
  }

  public getPhysicalRooms(hotelId: number): Observable<PhysicalRoom[]> {
    // AdminPropertyController returns List<PhysicalRoomDto>
    return this.http.get<PhysicalRoom[]>(`${this.adminUrl}/hotels/${hotelId}/rooms`);
  }

  public updateHousekeepingStatus(roomId: number, status: 'CLEAN' | 'DIRTY' | 'INSPECTING' | 'INSPECTED'): Observable<PhysicalRoom> {
    return this.http.patch<PhysicalRoom>(`${this.adminUrl}/rooms/${roomId}/housekeeping`, { housekeepingStatus: status });
  }

  public updateOperationalStatus(roomId: number, status: 'OPERATIONAL' | 'OUT_OF_SERVICE'): Observable<PhysicalRoom> {
    return this.http.patch<PhysicalRoom>(`${this.adminUrl}/rooms/${roomId}/operational`, { operationalStatus: status });
  }

  // ── Reservation Moderation & Operations ─────────────────────────────────────
  public getReservations(status?: string, page: number = 0, size: number = 20): Observable<PageResponse<Reservation>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<Reservation>>(`${this.adminUrl}/reservations`, { params }).pipe(
      map(res => ({
        ...res,
        content: (res.content || []).map(normalizeReservation)
      }))
    );
  }

  public getUsers(): Observable<PageResponse<any>> {
    return this.http.get<PageResponse<any>>(`${this.adminUrl}/users?size=100`);
  }

  public markNoShow(reservationId: number, adminNotes?: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.adminUrl}/reservations/${reservationId}/no-show`, { adminNotes: adminNotes || 'Guest no-show recorded' });
  }

  public reconcileReservation(reservationId: number, outcome: 'SUCCESS' | 'FAILURE' = 'SUCCESS'): Observable<any> {
    // AdminReservationController requires @RequestParam SimulationOutcome outcome
    const params = new HttpParams().set('outcome', outcome);
    return this.http.post<any>(`${this.adminUrl}/reservations/${reservationId}/reconcile`, {}, { params });
  }

  // ── Review Moderation ───────────────────────────────────────────────────────
  public removeReview(reviewId: number, reason: string): Observable<Review> {
    return this.http.post<Review>(`${this.adminUrl}/reviews/${reviewId}/remove`, { reason });
  }

  // ── Audit Logs ──────────────────────────────────────────────────────────────
  public getAuditEvents(
    service: 'users' | 'reservations' | 'reviews' = 'reservations',
    category?: string,
    actorId?: number,
    from?: string,
    to?: string,
    page: number = 0,
    size: number = 15
  ): Observable<PageResponse<AuditEvent>> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (category) params = params.set('category', category);
    if (actorId) params = params.set('actorId', actorId.toString());
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);

    return this.http.get<PageResponse<AuditEvent>>(`${this.adminUrl}/audit/${service}`, { params });
  }
}
