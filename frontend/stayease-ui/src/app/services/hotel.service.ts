import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Hotel, HotelAvailabilityResponse, RoomType } from '../models/hotel.model';
import { PageResponse } from '../models/common.model';

@Injectable({
  providedIn: 'root'
})
export class HotelService {
  private readonly hotelsUrl = `${environment.apiBaseUrl}/hotels`;
  private readonly reservationsUrl = `${environment.apiBaseUrl}/reservations`;

  constructor(private http: HttpClient) {}

  public searchHotels(
    city?: string,
    minPrice?: number,
    maxPrice?: number,
    minRating?: number,
    amenities?: string[],
    page: number = 0,
    size: number = 10
  ): Observable<PageResponse<Hotel>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (city && city.trim()) {
      params = params.set('destination', city.trim()).set('query', city.trim());
    }
    if (minPrice !== undefined && minPrice !== null) {
      params = params.set('minPrice', minPrice.toString());
    }
    if (maxPrice !== undefined && maxPrice !== null) {
      params = params.set('maxPrice', maxPrice.toString());
    }
    if (minRating !== undefined && minRating !== null) {
      params = params.set('minRating', minRating.toString());
    }
    if (amenities && amenities.length > 0) {
      params = params.set('amenities', amenities.join(','));
    }

    return this.http.get<PageResponse<Hotel>>(this.hotelsUrl, { params });
  }

  public getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.hotelsUrl}/${id}`);
  }

  public getHotelRoomTypes(hotelId: number): Observable<RoomType[]> {
    return this.http.get<RoomType[]>(`${this.hotelsUrl}/${hotelId}/room-types`);
  }

  public checkAvailability(
    hotelId: number,
    checkIn: string,
    checkOut: string,
    guests: number = 1
  ): Observable<HotelAvailabilityResponse> {
    const params = new HttpParams()
      .set('hotelId', hotelId.toString())
      .set('checkIn', checkIn)
      .set('checkOut', checkOut)
      .set('guests', guests.toString());

    return this.http.get<HotelAvailabilityResponse>(`${this.reservationsUrl}/availability`, { params });
  }
}
