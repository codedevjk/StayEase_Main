import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HotelService } from '../../../services/hotel.service';
import { Hotel } from '../../../models/hotel.model';

@Component({
  selector: 'app-hotel-search',
  templateUrl: './hotel-search.component.html'
})
export class HotelSearchComponent implements OnInit {
  hotels: Hotel[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;

  // Filter models
  filterCity: string = '';
  filterMinPrice: number | null = null;
  filterMaxPrice: number | null = null;
  filterMinRating: number | null = null;

  // Date models passed through to booking
  checkInDate: string = '';
  checkOutDate: string = '';
  guests: number = 2;
  adults: number = 2;
  children: number = 0;

  // Pagination
  currentPage: number = 0;
  pageSize: number = 9;
  totalElements: number = 0;
  totalPages: number = 0;

  constructor(
    private hotelService: HotelService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.filterCity = params['city'] || '';
      this.checkInDate = params['checkIn'] || '';
      this.checkOutDate = params['checkOut'] || '';
      this.adults = params['adults'] ? Math.max(1, parseInt(params['adults'], 10)) : (params['guests'] ? Math.max(1, parseInt(params['guests'], 10)) : 2);
      this.children = params['children'] ? Math.max(0, parseInt(params['children'], 10)) : 0;
      this.guests = this.adults + this.children;
      this.filterMinPrice = params['minPrice'] ? parseFloat(params['minPrice']) : null;
      this.filterMaxPrice = params['maxPrice'] ? parseFloat(params['maxPrice']) : null;
      this.filterMinRating = params['minRating'] ? parseInt(params['minRating'], 10) : null;
      this.currentPage = params['page'] ? parseInt(params['page'], 10) : 0;

      this.executeSearch();
    });
  }

  executeSearch(): void {
    this.loading = true;
    this.errorMessage = null;

    this.hotelService.searchHotels(
      this.filterCity,
      this.filterMinPrice !== null ? this.filterMinPrice : undefined,
      this.filterMaxPrice !== null ? this.filterMaxPrice : undefined,
      this.filterMinRating !== null ? this.filterMinRating : undefined,
      undefined,
      this.currentPage,
      this.pageSize
    ).subscribe({
      next: (res) => {
        this.loading = false;
        this.hotels = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 0;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to load hotels.';
      }
    });
  }

  onFilterSubmit(): void {
    this.currentPage = 0;
    this.updateQueryParams();
  }

  onPageChange(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.updateQueryParams();
    }
  }

  resetFilters(): void {
    this.filterCity = '';
    this.filterMinPrice = null;
    this.filterMaxPrice = null;
    this.filterMinRating = null;
    this.currentPage = 0;
    this.updateQueryParams();
  }

  private updateQueryParams(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        city: this.filterCity || null,
        checkIn: this.checkInDate || null,
        checkOut: this.checkOutDate || null,
        adults: this.adults,
        children: this.children,
        guests: this.guests,
        minPrice: this.filterMinPrice,
        maxPrice: this.filterMaxPrice,
        minRating: this.filterMinRating,
        page: this.currentPage > 0 ? this.currentPage : null
      },
      queryParamsHandling: 'merge'
    });
  }

  getHotelImage(hotel: Hotel): string {
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
