import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { Hotel, PhysicalRoom } from '../../../models/hotel.model';

@Component({
  selector: 'app-admin-rooms',
  templateUrl: './admin-rooms.component.html'
})
export class AdminRoomsComponent implements OnInit {
  hotels: Hotel[] = [];
  selectedHotelId: number | null = null;
  rooms: PhysicalRoom[] = [];
  loading: boolean = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  // New Room Type Modal
  showRoomTypeModal: boolean = false;
  roomTypeForm!: FormGroup;
  savingRoomType: boolean = false;

  constructor(private adminService: AdminService, private fb: FormBuilder) {}

  ngOnInit(): void {
    this.initForm();
    this.loadHotels();
  }

  initForm(): void {
    this.roomTypeForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      description: ['', [Validators.maxLength(1000)]],
      maxGuests: [2, [Validators.required, Validators.min(1), Validators.max(20)]],
      baseNightlyRate: [4500, [Validators.required, Validators.min(1)]]
    });
  }

  loadHotels(): void {
    this.adminService.getAllHotels(0, 50).subscribe({
      next: (res) => {
        this.hotels = res.content || [];
        if (this.hotels.length > 0) {
          this.selectedHotelId = this.hotels[0].id;
          this.loadRooms();
        }
      },
      error: (err) => {
        this.errorMessage = err.message || 'Failed to load hotels.';
      }
    });
  }

  loadRooms(): void {
    if (!this.selectedHotelId) return;
    this.loading = true;
    this.errorMessage = null;

    this.adminService.getPhysicalRooms(this.selectedHotelId).subscribe({
      next: (rooms) => {
        this.loading = false;
        this.rooms = rooms || [];
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to retrieve rooms.';
      }
    });
  }

  changeHousekeeping(room: PhysicalRoom, status: 'CLEAN' | 'DIRTY' | 'INSPECTING'): void {
    this.adminService.updateHousekeepingStatus(room.id, status).subscribe({
      next: (updated) => {
        room.housekeepingStatus = updated.housekeepingStatus;
        this.successMessage = `Room ${room.roomNumber} updated to ${status}.`;
      },
      error: (err) => {
        this.errorMessage = err.message || 'Failed to update housekeeping state.';
      }
    });
  }

  changeOperational(room: PhysicalRoom, status: 'OPERATIONAL' | 'OUT_OF_SERVICE'): void {
    this.adminService.updateOperationalStatus(room.id, status).subscribe({
      next: (updated) => {
        room.operationalStatus = updated.operationalStatus;
        this.successMessage = `Room ${room.roomNumber} operational status set to ${status}.`;
      },
      error: (err) => {
        this.errorMessage = err.message || 'Failed to update operational status.';
      }
    });
  }

  onSaveRoomType(): void {
    if (this.roomTypeForm.invalid || !this.selectedHotelId) return;

    this.savingRoomType = true;
    this.adminService.createRoomType(this.selectedHotelId, this.roomTypeForm.value).subscribe({
      next: () => {
        this.savingRoomType = false;
        this.showRoomTypeModal = false;
        this.successMessage = 'Room type created successfully.';
        this.roomTypeForm.reset({ capacity: 2, basePrice: 4500 });
      },
      error: (err) => {
        this.savingRoomType = false;
        this.errorMessage = err.message || 'Failed to create room type.';
      }
    });
  }
}
