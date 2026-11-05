import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HotelService } from '../../../services/hotel.service';
import { ReviewService } from '../../../services/review.service';
import { Hotel, RoomTypeAvailability } from '../../../models/hotel.model';
import { Review, ReviewSummary } from '../../../models/review.model';

@Component({
  selector: 'app-hotel-detail',
  templateUrl: './hotel-detail.component.html',
  styleUrls: ['./hotel-detail.component.css']
})
export class HotelDetailComponent implements OnInit {
  hotelId!: number;
  hotel: Hotel | null = null;
  loadingHotel: boolean = false;
  hotelError: string | null = null;

  // Availability state
  checkInDate: string = '';
  checkOutDate: string = '';
  guests: number = 2;
  adults: number = 2;
  children: number = 0;

  get totalGuests(): number {
    return (Number(this.adults) || 1) + (Number(this.children) || 0);
  }

  get requiredRooms(): number {
    // Real-world rule: max 2 adults per room. Children <= 10 stay with parents in adult room.
    const adultCount = Number(this.adults) || 1;
    return Math.max(1, Math.ceil(adultCount / 2));
  }

  minDate: string = '';
  checkingAvailability: boolean = false;
  availableRoomTypes: RoomTypeAvailability[] = [];
  availabilityChecked: boolean = false;
  availabilityError: string | null = null;
  guestError: string | null = null;
  dateError: string | null = null;

  // Reviews state
  reviews: Review[] = [];
  reviewSummary: ReviewSummary | null = null;
  loadingReviews: boolean = false;
  reviewPage: number = 0;
  totalReviews: number = 0;
  totalReviewPages: number = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private hotelService: HotelService,
    private reviewService: ReviewService
  ) {}

  ngOnInit(): void {
    const today = new Date();
    this.minDate = today.toISOString().split('T')[0];

    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date(today);
    dayAfter.setDate(dayAfter.getDate() + 3);

    this.route.params.subscribe(params => {
      this.hotelId = parseInt(params['hotelId'], 10);
      this.loadHotelDetails();
      this.loadReviewSummary();
      this.loadReviews();
    });

    this.route.queryParams.subscribe(queryParams => {
      this.checkInDate = queryParams['checkIn'] || tomorrow.toISOString().split('T')[0];
      this.checkOutDate = queryParams['checkOut'] || dayAfter.toISOString().split('T')[0];
      this.adults = queryParams['adults'] ? Math.max(1, parseInt(queryParams['adults'], 10)) : (queryParams['guests'] ? Math.max(1, parseInt(queryParams['guests'], 10)) : 2);
      this.children = queryParams['children'] ? Math.max(0, parseInt(queryParams['children'], 10)) : 0;
      this.guests = this.adults + this.children;

      if (this.hotelId) {
        this.checkAvailability();
      }
    });
  }

  loadHotelDetails(): void {
    this.loadingHotel = true;
    this.hotelError = null;

    this.hotelService.getHotelById(this.hotelId).subscribe({
      next: (h) => {
        this.loadingHotel = false;
        this.hotel = h;
      },
      error: (err) => {
        this.loadingHotel = false;
        this.hotelError = err.message || 'Failed to load hotel information.';
      }
    });
  }

  get nights(): number {
    if (this.checkInDate && this.checkOutDate) {
      const d1 = new Date(this.checkInDate).getTime();
      const d2 = new Date(this.checkOutDate).getTime();
      const diff = Math.round((d2 - d1) / (1000 * 60 * 60 * 24));
      return diff > 0 ? diff : 1;
    }
    return 1;
  }

  get availableRoomCount(): number {
    return (this.availableRoomTypes || []).filter(rt => (rt.availableRooms || 0) > 0).length;
  }

  get hotelLocation(): string {
    if (!this.hotel) return '';
    if (this.hotel.destination) return this.hotel.destination;
    const parts = [this.hotel.city, this.hotel.state, this.hotel.country].filter(Boolean);
    if (parts.length > 0) return parts.join(', ');
    return this.hotel.address || 'India';
  }

  get hotelPhone(): string {
    if (!this.hotel) return '+91 800 123 4567';
    return (this.hotel as any).contactPhone || this.hotel.phone || '+91 800 123 4567';
  }

  onGuestCountChange(): void {
    if (this.adults < 1) {
      this.guestError = 'At least 1 adult is required to make a booking. Child-only bookings are not allowed.';
      return;
    }
    this.guestError = null;
    this.children = Math.max(0, Number(this.children) || 0);
    this.guests = this.adults + this.children;
    this.checkAvailability();
  }

  checkAvailability(): void {
    if (!this.checkInDate || !this.checkOutDate) return;

    if (this.checkInDate < this.minDate) {
      this.dateError = 'Check-in date cannot be in the past.';
      return;
    }
    if (this.checkOutDate <= this.checkInDate) {
      this.dateError = 'Check-out date must be after check-in date.';
      return;
    }
    this.dateError = null;

    if (this.adults < 1) {
      this.guestError = 'At least 1 adult is required to make a booking. Child-only bookings are not allowed.';
      return;
    }
    this.guestError = null;

    this.checkingAvailability = true;
    this.availabilityError = null;
    this.availabilityChecked = true;

    // Real-world rule: standard rooms host up to 2 adults. Multiple rooms accommodate > 2 adults.
    // Querying availability with Math.min(adults, 2) ensures double/king rooms are returned for multi-room parties.
    const effectiveOccupancy = Math.min(this.adults, 2);

    this.hotelService.checkAvailability(this.hotelId, this.checkInDate, this.checkOutDate, effectiveOccupancy).subscribe({
      next: (res) => {
        this.checkingAvailability = false;
        const rawTypes = res.roomTypes || [];
        const seen = new Set<number>();
        this.availableRoomTypes = rawTypes.filter((rt: any) => {
          const id = rt.roomTypeId || rt.id;
          if (seen.has(id)) return false;
          seen.add(id);
          return true;
        }).map((rt: any) => {
          const nightlyRate = rt.nightlyRates?.[0]?.rate 
            ?? (rt.lodgingSubtotal ? rt.lodgingSubtotal / this.nights : rt.basePrice);
          const maxG = rt.maxGuests || rt.capacity || 2;
          const subtotal = rt.lodgingSubtotal ?? (nightlyRate ? nightlyRate * this.nights : 7000);
          return {
            ...rt,
            roomTypeId: rt.roomTypeId || rt.id,
            capacity: maxG,
            maxGuests: maxG,
            basePrice: Number(nightlyRate || 3500),
            effectiveTotal: Number(subtotal),
            lodgingSubtotal: Number(subtotal),
            taxAmount: rt.taxAmount ?? Math.round(subtotal * 0.12 * 100) / 100,
            totalAmount: rt.totalAmount ?? Math.round(subtotal * 1.12 * 100) / 100
          };
        });
      },
      error: (err) => {
        this.checkingAvailability = false;
        this.availabilityError = err.message || 'Unable to verify room availability for selected dates.';
      }
    });
  }

  loadReviewSummary(): void {
    this.reviewService.getHotelReviewSummary(this.hotelId).subscribe({
      next: (summary) => {
        this.reviewSummary = summary;
      },
      error: () => {}
    });
  }

  loadReviews(page: number = 0): void {
    this.loadingReviews = true;
    this.reviewPage = page;
    this.reviewService.getHotelReviews(this.hotelId, page, 5).subscribe({
      next: (res) => {
        this.loadingReviews = false;
        this.reviews = res.content || [];
        this.totalReviews = res.totalElements || 0;
        this.totalReviewPages = res.totalPages || 0;
      },
      error: () => {
        this.loadingReviews = false;
      }
    });
  }

  bookRoom(roomType: RoomTypeAvailability): void {
    if (this.adults < 1) {
      this.guestError = 'At least 1 adult is required to make a booking. Child-only bookings are not allowed.';
      return;
    }
    this.router.navigate(['/booking'], {
      queryParams: {
        hotelId: this.hotelId,
        roomTypeId: roomType.roomTypeId,
        checkIn: this.checkInDate,
        checkOut: this.checkOutDate,
        adults: this.adults,
        children: this.children,
        guests: this.totalGuests
      }
    });
  }

  getAmenityIcon(name: string): string {
    if (!name) return 'bi-check-circle-fill';
    const lower = name.toLowerCase();
    if (lower.includes('wifi') || lower.includes('internet')) return 'bi-wifi';
    if (lower.includes('pool') || lower.includes('swim')) return 'bi-water';
    if (lower.includes('spa') || lower.includes('wellness') || lower.includes('massage')) return 'bi-flower1';
    if (lower.includes('gym') || lower.includes('fitness')) return 'bi-heart-pulse-fill';
    if (lower.includes('restaurant') || lower.includes('dining') || lower.includes('food') || lower.includes('breakfast')) return 'bi-cup-hot-fill';
    if (lower.includes('bar') || lower.includes('lounge') || lower.includes('drink')) return 'bi-cup-straw';
    if (lower.includes('park') || lower.includes('valet')) return 'bi-p-circle-fill';
    if (lower.includes('ac') || lower.includes('air conditioning') || lower.includes('cooling')) return 'bi-snow';
    if (lower.includes('tv') || lower.includes('television')) return 'bi-tv';
    if (lower.includes('concierge') || lower.includes('service') || lower.includes('desk')) return 'bi-bell-fill';
    if (lower.includes('safe') || lower.includes('security')) return 'bi-shield-check';
    return 'bi-check-circle-fill';
  }

  getHotelImage(hotel: Hotel | null): string {
    if (!hotel) return 'assets/images/default-hotel.jpg';
    if (hotel.imageUrl) return hotel.imageUrl;
    const dest = (hotel.destination || hotel.name || '').toLowerCase();

    // Heritage Stays: Jaipur, Udaipur, Mysore, Hyderabad
    if (dest.includes('jaipur') || dest.includes('palace') || dest.includes('heritage')) return 'assets/images/jaipur.jpg';
    if (dest.includes('udaipur') || dest.includes('lake')) return 'assets/images/udaipur.png';
    if (dest.includes('mysore') || dest.includes('mysuru')) return 'assets/images/mysore.png';
    if (dest.includes('hyderabad')) return 'assets/images/hyderabad.png';

    // Beach Holidays: Goa, Chennai, Daman
    if (dest.includes('goa') || dest.includes('beach')) return 'assets/images/goa.jpg';
    if (dest.includes('chennai') || dest.includes('marina')) return 'assets/images/chennai.png';
    if (dest.includes('daman') || dest.includes('diu')) return 'assets/images/daman.jpg';

    // Temple Tourism: Varanasi, Puri, Madurai
    if (dest.includes('varanasi') || dest.includes('banaras') || dest.includes('kashi') || dest.includes('ghat')) return 'assets/images/varanasi.png';
    if (dest.includes('puri') || dest.includes('jagannath')) return 'assets/images/puri.jpg';
    if (dest.includes('madurai') || dest.includes('meenakshi')) return 'assets/images/madurai.jpg';

    // Business & Cultural Travel: Pune, Bangalore, Gurgaon, Mumbai
    if (dest.includes('pune') || dest.includes('shivajinagar')) return 'assets/images/pune.png';
    if (dest.includes('bangalore') || dest.includes('bengaluru')) return 'assets/images/bangalore.jpg';
    if (dest.includes('gurgaon') || dest.includes('gurugram')) return 'assets/images/gurgaon.jpg';
    if (dest.includes('mumbai') || dest.includes('bombay')) return 'assets/images/mumbai.jpg';

    // Nature Escapes: Manali, Coorg, Munnar
    if (dest.includes('manali') || dest.includes('kullu')) return 'assets/images/manali.jpg';
    if (dest.includes('coorg') || dest.includes('kodagu')) return 'assets/images/coorg.jpg';
    if (dest.includes('munnar')) return 'assets/images/munnar.jpg';

    // Other Escapes: Ooty, Shimla, Kerala, Delhi, Kolkata, Agra
    if (dest.includes('ooty') || dest.includes('hill') || dest.includes('mountain') || dest.includes('retreat')) return 'assets/images/ooty.jpg';
    if (dest.includes('shimla') || dest.includes('himalaya')) return 'assets/images/shimla.jpg';
    if (dest.includes('kerala') || dest.includes('backwater')) return 'assets/images/kerala.jpg';
    if (dest.includes('delhi')) return 'assets/images/delhi.jpg';
    if (dest.includes('kolkata')) return 'assets/images/kolkata.jpg';
    if (dest.includes('agra')) return 'assets/images/agra.jpg';

    return 'assets/images/default-hotel.jpg';
  }
}
