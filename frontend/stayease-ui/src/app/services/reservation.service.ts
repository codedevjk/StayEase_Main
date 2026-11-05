import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import {
  AmendReservationRequest,
  CancellationSummary,
  CancelReservationRequest,
  CheckInResponse,
  CheckOutResponse,
  CreateReservationRequest,
  InvoiceDto,
  PaymentRequest,
  PaymentResponse,
  Reservation
} from '../models/reservation.model';
import { PageResponse } from '../models/common.model';

export function normalizeReservation(raw: any): Reservation {
  if (!raw) return raw;
  const snapshot = raw.pricingSnapshot || {};
  const lodgingSubtotal = Number(snapshot.lodgingSubtotal ?? raw.lodgingSubtotal ?? 0);
  const taxAmount = Number(snapshot.taxAmount ?? raw.taxAmount ?? Math.round(lodgingSubtotal * 0.12 * 100) / 100);
  const totalAmount = Number(snapshot.totalAmount ?? raw.totalAmount ?? (lodgingSubtotal + taxAmount));
  const status = raw.reservationStatus || raw.status || 'CONFIRMED';
  const paymentStatus = raw.paymentStatus || 'PENDING';
  const isPaid = (paymentStatus === 'PAID' || status === 'CHECKED_IN' || status === 'CHECKED_OUT');
  const amountPaid = Number(raw.amountPaid ?? (isPaid ? totalAmount : 0));
  const balanceDue = Number(raw.balanceDue ?? Math.max(0, totalAmount - amountPaid));

  return {
    ...raw,
    id: raw.id,
    referenceNumber: raw.publicReference || raw.referenceNumber || ('RES-' + raw.id),
    publicReference: raw.publicReference || raw.referenceNumber || ('RES-' + raw.id),
    status: status,
    reservationStatus: status,
    paymentStatus: paymentStatus,
    paymentPolicy: raw.paymentPolicy || 'PREPAID',
    lodgingSubtotal: lodgingSubtotal,
    taxAmount: taxAmount,
    totalAmount: totalAmount,
    amountPaid: amountPaid,
    balanceDue: balanceDue,
    hotelName: raw.hotelName || raw.hotel?.name || 'StayEase Hotel',
    roomTypeName: raw.roomTypeName || raw.stay?.roomTypeName || ('Room Type ' + raw.roomTypeId),
    assignedRoomNumber: raw.physicalRoomNumber || raw.assignedRoomNumber || raw.roomNumber,
    checkInDate: raw.checkInDate,
    checkOutDate: raw.checkOutDate,
    guestCount: raw.guestCount || 1,
    nights: raw.nights || 1
  };
}

@Injectable({
  providedIn: 'root'
})
export class ReservationService {
  private readonly reservationsUrl = `${environment.apiBaseUrl}/reservations`;

  constructor(private http: HttpClient) {}

  public createReservation(request: CreateReservationRequest): Observable<Reservation> {
    return this.http.post<Reservation>(this.reservationsUrl, request).pipe(
      map(normalizeReservation)
    );
  }

  public getMyReservations(page: number = 0, size: number = 10): Observable<PageResponse<Reservation>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Reservation>>(`${this.reservationsUrl}/my`, { params }).pipe(
      map(res => ({
        ...res,
        content: (res.content || []).map(normalizeReservation)
      }))
    );
  }

  public getReservationById(id: number): Observable<Reservation> {
    return this.http.get<Reservation>(`${this.reservationsUrl}/${id}`).pipe(
      map(normalizeReservation)
    );
  }

  public processPayment(
    reservationId: number,
    request: PaymentRequest,
    idempotencyKey?: string
  ): Observable<PaymentResponse> {
    const key = idempotencyKey || this.generateUUID();
    const headers = new HttpHeaders().set('Idempotency-Key', key);
    return this.http.post<PaymentResponse>(
      `${this.reservationsUrl}/${reservationId}/payment`,
      request,
      { headers }
    );
  }

  public cancelReservation(
    reservationId: number,
    request: CancelReservationRequest,
    idempotencyKey?: string
  ): Observable<CancellationSummary> {
    const key = idempotencyKey || this.generateUUID();
    const headers = new HttpHeaders().set('Idempotency-Key', key);
    return this.http.post<CancellationSummary>(
      `${this.reservationsUrl}/${reservationId}/cancel`,
      request,
      { headers }
    );
  }

  public amendReservation(
    reservationId: number,
    request: AmendReservationRequest
  ): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.reservationsUrl}/${reservationId}/amend`, request).pipe(
      map(normalizeReservation)
    );
  }

  public checkIn(reservationId: number): Observable<CheckInResponse> {
    return this.http.post<CheckInResponse>(`${this.reservationsUrl}/${reservationId}/check-in`, {});
  }

  public checkOut(reservationId: number): Observable<CheckOutResponse> {
    return this.http.post<CheckOutResponse>(`${this.reservationsUrl}/${reservationId}/check-out`, {});
  }

  public getInvoice(reservationId: number): Observable<InvoiceDto> {
    return this.http.get<InvoiceDto>(`${this.reservationsUrl}/${reservationId}/invoice`);
  }

  private generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}
