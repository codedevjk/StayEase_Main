import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { HotelService } from '../../services/hotel.service';
import { AuthService } from '../../services/auth.service';
import { Hotel } from '../../models/hotel.model';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  featuredHotels: Hotel[] = [];
  loading: boolean = false;

  // Search parameters (same original fields)
  searchCity: string = '';
  checkInDate: string = '';
  checkOutDate: string = '';
  adults: number = 2;
  children: number = 0;
  minDate: string = '';

  // Validation errors
  checkInError: string | null = null;
  checkOutError: string | null = null;
  guestError: string | null = null;
  formSubmitted: boolean = false;

  get totalGuests(): number {
    return (Number(this.adults) || 0) + (Number(this.children) || 0);
  }

  get requiredRooms(): number {
    // Real-world rule: max 2 adults per room. Children <= 10 stay with parents in adult room.
    const adultCount = Number(this.adults) || 0;
    if (adultCount <= 0) return 0;
    return Math.max(1, Math.ceil(adultCount / 2));
  }

  constructor(
    private hotelService: HotelService,
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const isGuestMode = this.route.snapshot.queryParams['guest'] === 'true';
    if (this.authService.isAdmin() && !isGuestMode) {
      this.router.navigate(['/admin']);
      return;
    }

    const today = new Date();
    this.minDate = today.toISOString().split('T')[0];

    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date(today);
    dayAfter.setDate(dayAfter.getDate() + 3);

    this.checkInDate = tomorrow.toISOString().split('T')[0];
    this.checkOutDate = dayAfter.toISOString().split('T')[0];

    this.validateDates();
    this.validateGuests();
    this.loadFeaturedHotels();
  }

  getDayOfWeek(dateStr: string): string {
    if (!dateStr) return '';
    try {
      const parts = dateStr.split('-');
      if (parts.length === 3) {
        const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
        const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        return days[d.getDay()];
      }
    } catch (e) {}
    return '';
  }

  validateDates(): boolean {
    let valid = true;
    if (!this.checkInDate) {
      this.checkInError = 'Check-in date is required.';
      valid = false;
    } else if (this.checkInDate < this.minDate) {
      this.checkInError = 'Check-in date cannot be in the past.';
      valid = false;
    } else {
      this.checkInError = null;
    }

    if (!this.checkOutDate) {
      this.checkOutError = 'Check-out date is required.';
      valid = false;
    } else if (this.checkOutDate <= this.checkInDate) {
      this.checkOutError = 'Check-out date must be after check-in date.';
      valid = false;
    } else {
      this.checkOutError = null;
    }
    return valid;
  }

  validateGuests(): boolean {
    if (this.adults === undefined || this.adults === null || this.adults < 1) {
      this.guestError = 'At least 1 adult is required to make a booking. Child-only bookings are not allowed.';
      return false;
    }
    this.guestError = null;
    return true;
  }

  loadFeaturedHotels(): void {
    this.loading = true;
    this.hotelService.searchHotels('', undefined, undefined, 4, undefined, 0, 6).subscribe({
      next: (res) => {
        this.loading = false;
        this.featuredHotels = res.content || [];
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onSearch(): void {
    this.formSubmitted = true;
    const datesOk = this.validateDates();
    const guestsOk = this.validateGuests();

    if (!datesOk || !guestsOk) {
      return;
    }

    const safeAdults = Math.max(1, Number(this.adults) || 1);
    const safeChildren = Math.max(0, Number(this.children) || 0);

    this.router.navigate(['/hotels'], {
      queryParams: {
        city: this.searchCity ? this.searchCity.split(',')[0].trim() : undefined,
        checkIn: this.checkInDate || undefined,
        checkOut: this.checkOutDate || undefined,
        adults: safeAdults,
        children: safeChildren,
        guests: safeAdults + safeChildren
      }
    });
  }

  quickCitySearch(city: string): void {
    this.searchCity = city;
    this.onSearch();
  }

  getHotelImage(hotel: Hotel): string {
    if (hotel.imageUrl) return hotel.imageUrl;
    const dest = (hotel.destination || hotel.city || hotel.name || '').toLowerCase();
    
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
