import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { HotelService } from '../../../services/hotel.service';
import { Hotel } from '../../../models/hotel.model';

@Component({
  selector: 'app-admin-hotels',
  templateUrl: './admin-hotels.component.html'
})
export class AdminHotelsComponent implements OnInit {
  hotels: Hotel[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  showHotelModal: boolean = false;
  hotelForm!: FormGroup;
  saving: boolean = false;
  editingHotelId: number | null = null;

  availableAssetImages = [
    // Heritage Stays
    { label: '[Heritage] Jaipur - Royal Palace Hotel', path: 'assets/images/jaipur.jpg' },
    { label: '[Heritage] Udaipur - Lake Palace Villa (HD)', path: 'assets/images/udaipur.png' },
    { label: '[Heritage] Mysore - Royal Heritage Palace (HD)', path: 'assets/images/mysore.png' },
    { label: '[Heritage] Hyderabad - Nizami Palace Hotel (HD)', path: 'assets/images/hyderabad.png' },

    // Beach Holidays
    { label: '[Beach] Goa - Tropical Beach Resort', path: 'assets/images/goa.jpg' },
    { label: '[Beach] Chennai - Coastal Boutique Resort (HD)', path: 'assets/images/chennai.png' },
    { label: '[Beach] Daman - Seaside Beachfront Stay', path: 'assets/images/daman.jpg' },

    // Temple Tourism
    { label: '[Temple] Varanasi - Spiritual Heritage Stay (HD)', path: 'assets/images/varanasi.png' },
    { label: '[Temple] Puri - Golden Beach Temple Resort', path: 'assets/images/puri.jpg' },
    { label: '[Temple] Madurai - Cultural Heritage Stay', path: 'assets/images/madurai.jpg' },

    // Business & Cultural Travel
    { label: '[Business/Culture] Pune - Oxford Heritage Retreat (HD)', path: 'assets/images/pune.png' },
    { label: '[Business] Bangalore - Modern Tech Corridor Hotel', path: 'assets/images/bangalore.jpg' },
    { label: '[Business] Gurgaon - Cyber City Luxury Stay', path: 'assets/images/gurgaon.jpg' },
    { label: '[Business] Mumbai - Financial Capital Luxury Hotel', path: 'assets/images/mumbai.jpg' },

    // Nature Escapes
    { label: '[Nature] Manali - Alpine Snow Valley Resort', path: 'assets/images/manali.jpg' },
    { label: '[Nature] Coorg - Coffee Estate Plantation Stay', path: 'assets/images/coorg.jpg' },
    { label: '[Nature] Munnar - Tea Garden Mountain Retreat', path: 'assets/images/munnar.jpg' },

    // Other Destinations & Rooms
    { label: '[Escape] Ooty - Nilgiri Mountain Retreat', path: 'assets/images/ooty.jpg' },
    { label: '[Escape] Shimla - Himalayan Pine Resort', path: 'assets/images/shimla.jpg' },
    { label: '[Escape] Kerala - Backwaters Ayurvedic Stay', path: 'assets/images/kerala.jpg' },
    { label: '[City] Delhi - Capital Luxury Hotel', path: 'assets/images/delhi.jpg' },
    { label: '[City] Kolkata - Grand Colonial Hotel', path: 'assets/images/kolkata.jpg' },
    { label: '[City] Agra - Heritage Mughal Stay', path: 'assets/images/agra.jpg' },
    { label: '[Property] Boutique Villa - Private Villa', path: 'assets/images/boutique-villa.jpg' },
    { label: '[Property] Resort Pool - Tropical Lagoon', path: 'assets/images/resort-pool.jpg' },
    { label: '[Room] Luxury Suite - Executive Bedroom', path: 'assets/images/luxury-suite.jpg' },
    { label: '[Room] Deluxe Room - Modern Bedroom', path: 'assets/images/deluxe-room.jpg' },
    { label: '[General] Default - Premier Hotel Fallback', path: 'assets/images/default-hotel.jpg' }
  ];

  constructor(
    private adminService: AdminService,
    private hotelService: HotelService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadHotels();
  }

  initForm(): void {
    this.hotelForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
      destination: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      address: ['', [Validators.required, Validators.maxLength(255)]],
      contactEmail: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      contactPhone: ['', [Validators.required, Validators.pattern(/^[0-9]{10,15}$/)]],
      description: ['', [Validators.maxLength(2000)]],
      starRating: [4, [Validators.required, Validators.min(1), Validators.max(5)]],
      imageUrl: [''],
      active: [true]
    });
  }

  loadHotels(): void {
    this.loading = true;
    this.errorMessage = null;

    this.adminService.getAllHotels(0, 50).subscribe({
      next: (res) => {
        this.loading = false;
        this.hotels = (res.content || []).map(h => ({
          ...h,
          active: h.active !== false
        }));
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to retrieve hotels.';
      }
    });
  }

  onToggleStatus(hotel: Hotel, event: Event): void {
    const input = event.target as HTMLInputElement;
    const newStatus = input.checked;
    this.adminService.toggleHotelStatus(hotel.id, newStatus).subscribe({
      next: () => {
        hotel.active = newStatus;
        this.successMessage = `Property "${hotel.name}" is now ${newStatus ? 'ACTIVE (Bookable online)' : 'INACTIVE (Delisted from Search)'}.`;
        setTimeout(() => {
          if (this.successMessage?.includes(hotel.name)) {
            this.successMessage = null;
          }
        }, 4000);
      },
      error: (err) => {
        input.checked = !newStatus;
        hotel.active = !newStatus;
        this.errorMessage = err.message || `Failed to update status for ${hotel.name}.`;
      }
    });
  }

  openCreateModal(): void {
    this.editingHotelId = null;
    this.hotelForm.reset({
      starRating: 4,
      active: true
    });
    this.showHotelModal = true;
  }

  openEditModal(hotel: any): void {
    this.editingHotelId = hotel.id;
    this.hotelForm.patchValue({
      name: hotel.name,
      destination: hotel.destination || hotel.city || '',
      address: hotel.address,
      contactEmail: hotel.contactEmail || hotel.email || '',
      contactPhone: hotel.contactPhone || hotel.phone || '',
      description: hotel.description || '',
      starRating: hotel.starRating,
      imageUrl: hotel.imageUrl || '',
      active: hotel.active !== false
    });
    this.showHotelModal = true;

    // Fetch full details (contactEmail, contactPhone, description)
    this.hotelService.getHotelById(hotel.id).subscribe({
      next: (full: any) => {
        if (this.editingHotelId === hotel.id) {
          this.hotelForm.patchValue({
            name: full.name || hotel.name,
            destination: full.destination || full.city || hotel.destination || '',
            address: full.address || hotel.address,
            contactEmail: full.contactEmail || full.email || '',
            contactPhone: full.contactPhone || full.phone || '',
            description: full.description || hotel.description || '',
            starRating: full.starRating || hotel.starRating,
            imageUrl: full.imageUrl || hotel.imageUrl || '',
            active: full.active !== false
          });
        }
      }
    });
  }

  onSaveHotel(): void {
    if (this.hotelForm.invalid) {
      this.hotelForm.markAllAsTouched();
      return;
    }

    this.saving = true;
    this.errorMessage = null;
    this.successMessage = null;

    if (this.editingHotelId) {
      this.adminService.updateHotel(this.editingHotelId, this.hotelForm.value).subscribe({
        next: () => {
          this.saving = false;
          this.showHotelModal = false;
          this.successMessage = 'Hotel property updated successfully.';
          this.loadHotels();
        },
        error: (err) => {
          this.saving = false;
          this.errorMessage = err.message || 'Failed to update hotel.';
        }
      });
    } else {
      this.adminService.createHotel(this.hotelForm.value).subscribe({
        next: () => {
          this.saving = false;
          this.showHotelModal = false;
          this.successMessage = 'New hotel property onboarded successfully.';
          this.loadHotels();
        },
        error: (err) => {
          this.saving = false;
          this.errorMessage = err.message || 'Failed to create hotel.';
        }
      });
    }
  }
}
