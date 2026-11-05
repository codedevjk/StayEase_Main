import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HotelService } from '../../../services/hotel.service';
import { ReservationService } from '../../../services/reservation.service';
import { AuthService } from '../../../services/auth.service';
import { Hotel, RoomTypeAvailability } from '../../../models/hotel.model';
import { PaymentMethod, PaymentPolicy, PaymentRequest, Reservation, SimulationOutcome } from '../../../models/reservation.model';

@Component({
  selector: 'app-booking',
  templateUrl: './booking.component.html',
  styleUrls: ['./booking.component.css']
})
export class BookingComponent implements OnInit, OnDestroy {
  hotelId!: number;
  roomTypeId!: number;
  checkInDate: string = '';
  checkOutDate: string = '';
  guests: number = 2;
  adults: number = 2;
  children: number = 0;

  hotel: Hotel | null = null;
  selectedRoomType: RoomTypeAvailability | null = null;
  loading: boolean = false;
  errorMessage: string | null = null;

  // Booking Flow Steps: 1: Details & Policy, 2: Payment (if PREPAID), 3: Confirmed
  step: number = 1;
  selectedPolicy: PaymentPolicy = 'PREPAID';
  createdReservation: Reservation | null = null;

  // Payment Form
  submittingBooking: boolean = false;
  processingPayment: boolean = false;
  selectedPaymentMethod: PaymentMethod = 'UPI';
  selectedOutcome: SimulationOutcome = 'SUCCESS';
  paymentError: string | null = null;

  // UPI Input & Validation
  upiId: string = '';
  upiIdTouched: boolean = false;
  upiIdError: string | null = null;

  // Credit / Debit Card Payment Details & Real-World Validation
  cardNumber: string = '';
  cardHolderName: string = '';
  cardExpiry: string = '';
  cardCvv: string = '';
  cardTouched = {
    number: false,
    name: false,
    expiry: false,
    cvv: false
  };
  cardErrors = {
    number: null as string | null,
    name: null as string | null,
    expiry: null as string | null,
    cvv: null as string | null
  };

  // Net Banking Selection & Validation
  selectedBank: string = '';
  bankTouched: boolean = false;
  bankError: string | null = null;

  popularBanks = [
    { name: 'State Bank of India (SBI)', code: 'SBI', icon: 'bi-bank2' },
    { name: 'HDFC Bank', code: 'HDFC', icon: 'bi-bank' },
    { name: 'ICICI Bank', code: 'ICICI', icon: 'bi-building' },
    { name: 'Axis Bank', code: 'AXIS', icon: 'bi-bank' },
    { name: 'Kotak Mahindra Bank', code: 'KOTAK', icon: 'bi-wallet2' },
    { name: 'Punjab National Bank (PNB)', code: 'PNB', icon: 'bi-bank2' }
  ];

  allBanks: string[] = [
    'State Bank of India (SBI)',
    'HDFC Bank',
    'ICICI Bank',
    'Axis Bank',
    'Kotak Mahindra Bank',
    'Punjab National Bank (PNB)',
    'Bank of Baroda',
    'Canara Bank',
    'Union Bank of India',
    'IndusInd Bank',
    'IDBI Bank',
    'Yes Bank',
    'Federal Bank',
    'Central Bank of India',
    'Indian Bank',
    'Bank of India',
    'RBL Bank',
    'Indian Overseas Bank',
    'UCO Bank',
    'South Indian Bank'
  ];

  // 10-Minute Hold Countdown Timer
  holdCountdownSeconds: number = 600; // 10 minutes
  private holdTimerInterval: any = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private hotelService: HotelService,
    private reservationService: ReservationService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }

    this.route.queryParams.subscribe(params => {
      this.hotelId = parseInt(params['hotelId'], 10);
      this.roomTypeId = parseInt(params['roomTypeId'], 10);
      this.checkInDate = params['checkIn'];
      this.checkOutDate = params['checkOut'];
      this.adults = params['adults'] ? Math.max(1, parseInt(params['adults'], 10)) : (params['guests'] ? Math.max(1, parseInt(params['guests'], 10)) : 2);
      this.children = params['children'] ? Math.max(0, parseInt(params['children'], 10)) : 0;
      this.guests = this.adults + this.children;

      if (!this.hotelId || !this.roomTypeId || !this.checkInDate || !this.checkOutDate) {
        this.router.navigate(['/hotels']);
        return;
      }

      this.loadBookingContext();
    });
  }

  ngOnDestroy(): void {
    this.stopHoldTimer();
  }

  loadBookingContext(): void {
    this.loading = true;
    this.hotelService.getHotelById(this.hotelId).subscribe({
      next: (h) => {
        this.hotel = h;
        // Real-world single room adult occupancy limit for checking rate
        const effectiveOccupancy = Math.min(this.adults, 2);
        this.hotelService.checkAvailability(this.hotelId, this.checkInDate, this.checkOutDate, effectiveOccupancy).subscribe({
          next: (avail) => {
            this.loading = false;
            const match = avail.roomTypes.find(r => r.roomTypeId === this.roomTypeId);
            if (match) {
              this.selectedRoomType = match;
            } else {
              this.errorMessage = 'The selected room type is no longer available for these dates.';
            }
          },
          error: (err) => {
            this.loading = false;
            this.errorMessage = err.message || 'Failed to check room rate.';
          }
        });
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to load property details.';
      }
    });
  }

  get hotelLocation(): string {
    if (!this.hotel) return 'India';
    if (this.hotel.destination) return this.hotel.destination;
    const parts = [this.hotel.city, this.hotel.state].filter(Boolean);
    if (parts.length > 0) return parts.join(', ');
    return this.hotel.address || 'India';
  }

  get stayNights(): number {
    if (this.selectedRoomType && this.selectedRoomType.nightlyRates?.length) {
      return this.selectedRoomType.nightlyRates.length;
    }
    if (this.checkInDate && this.checkOutDate) {
      const d1 = new Date(this.checkInDate).getTime();
      const d2 = new Date(this.checkOutDate).getTime();
      const diff = Math.round((d2 - d1) / (1000 * 60 * 60 * 24));
      return diff > 0 ? diff : 1;
    }
    return 1;
  }

  get lodgingSubtotal(): number {
    if (!this.selectedRoomType) return 0;
    if (this.selectedRoomType.lodgingSubtotal !== undefined && this.selectedRoomType.lodgingSubtotal !== null) {
      return Number(this.selectedRoomType.lodgingSubtotal);
    }
    if (this.selectedRoomType.effectiveTotal !== undefined && this.selectedRoomType.effectiveTotal !== null) {
      return Number(this.selectedRoomType.effectiveTotal);
    }
    if (this.selectedRoomType.basePrice) {
      return Number(this.selectedRoomType.basePrice) * this.stayNights;
    }
    return 0;
  }

  get taxAmount(): number {
    if (!this.selectedRoomType) return 0;
    if (this.selectedRoomType.taxAmount !== undefined && this.selectedRoomType.taxAmount !== null) {
      return Number(this.selectedRoomType.taxAmount);
    }
    return Math.round(this.lodgingSubtotal * 0.12 * 100) / 100;
  }

  get totalBookingAmount(): number {
    if (!this.selectedRoomType) return 0;
    if (this.selectedRoomType.totalAmount !== undefined && this.selectedRoomType.totalAmount !== null) {
      return Number(this.selectedRoomType.totalAmount);
    }
    return this.lodgingSubtotal + this.taxAmount;
  }

  get totalGuests(): number {
    return this.adults + this.children;
  }

  get requiredRooms(): number {
    // Real-world rule: max 2 adults per room. Children <= 10 stay with adults without separate room.
    return Math.max(1, Math.ceil(this.adults / 2));
  }

  validateUpiId(): boolean {
    if (!this.upiId || this.upiId.trim() === '') {
      this.upiIdError = 'UPI ID is required to process payment.';
      return false;
    }
    const upiRegex = /^[a-zA-Z0-9.\-_]{2,49}@[a-zA-Z0-9]{2,}$/;
    if (!upiRegex.test(this.upiId.trim())) {
      this.upiIdError = 'Please enter a valid UPI ID (e.g. 9876543210@paytm, user@okhdfcbank).';
      return false;
    }
    this.upiIdError = null;
    return true;
  }

  // Card Brand Detection
  get cardBrand(): 'visa' | 'mastercard' | 'rupay' | 'amex' | 'generic' {
    const clean = this.cardNumber.replace(/\D/g, '');
    if (/^4/.test(clean)) return 'visa';
    if (/^(5[1-5]|2[2-7])/.test(clean)) return 'mastercard';
    if (/^(60|65|35)/.test(clean)) return 'rupay';
    if (/^3[47]/.test(clean)) return 'amex';
    return 'generic';
  }

  formatCardNumber(): void {
    let raw = this.cardNumber.replace(/\D/g, '');
    const isAmex = /^3[47]/.test(raw);
    const maxLen = isAmex ? 15 : 16;
    if (raw.length > maxLen) {
      raw = raw.substring(0, maxLen);
    }
    if (isAmex) {
      // 4-6-5 Amex formatting
      const p1 = raw.substring(0, 4);
      const p2 = raw.substring(4, 10);
      const p3 = raw.substring(10, 15);
      this.cardNumber = [p1, p2, p3].filter(Boolean).join(' ');
    } else {
      // 4-4-4-4 standard formatting
      const parts = raw.match(/[\s\S]{1,4}/g) || [];
      this.cardNumber = parts.join(' ');
    }
    if (this.cardTouched.number) {
      this.validateCardNumber();
    }
  }

  formatCardExpiry(): void {
    let clean = this.cardExpiry.replace(/\D/g, '');
    if (clean.length > 4) {
      clean = clean.substring(0, 4);
    }
    if (clean.length >= 2) {
      this.cardExpiry = clean.substring(0, 2) + '/' + clean.substring(2);
    } else {
      this.cardExpiry = clean;
    }
    if (this.cardTouched.expiry) {
      this.validateCardExpiry();
    }
  }

  validateCardNumber(): boolean {
    const raw = this.cardNumber.replace(/\D/g, '');
    if (!raw) {
      this.cardErrors.number = 'Card number is required.';
      return false;
    }
    const isAmex = /^3[47]/.test(raw);
    const expectedLen = isAmex ? 15 : 16;
    if (raw.length !== expectedLen) {
      this.cardErrors.number = `Card number must be ${expectedLen} digits.`;
      return false;
    }
    this.cardErrors.number = null;
    return true;
  }

  validateCardHolderName(): boolean {
    const clean = this.cardHolderName.trim();
    if (!clean) {
      this.cardErrors.name = 'Cardholder name is required.';
      return false;
    }
    if (clean.length < 3) {
      this.cardErrors.name = 'Name must be at least 3 characters.';
      return false;
    }
    if (!/^[a-zA-Z\s.'-]+$/.test(clean)) {
      this.cardErrors.name = 'Name can only contain alphabets and spaces.';
      return false;
    }
    this.cardErrors.name = null;
    return true;
  }

  validateCardExpiry(): boolean {
    const clean = this.cardExpiry.trim();
    if (!clean) {
      this.cardErrors.expiry = 'Expiry date is required.';
      return false;
    }
    const match = clean.match(/^(0[1-9]|1[0-2])\/([0-9]{2})$/);
    if (!match) {
      this.cardErrors.expiry = 'Enter valid MM/YY format (e.g. 09/28).';
      return false;
    }
    const month = parseInt(match[1], 10);
    const year = parseInt('20' + match[2], 10);
    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth() + 1; // 1-12

    if (year < currentYear || (year === currentYear && month < currentMonth)) {
      this.cardErrors.expiry = 'This card has already expired.';
      return false;
    }
    if (year > currentYear + 25) {
      this.cardErrors.expiry = 'Invalid expiry year.';
      return false;
    }
    this.cardErrors.expiry = null;
    return true;
  }

  validateCardCvv(): boolean {
    const clean = this.cardCvv.trim();
    if (!clean) {
      this.cardErrors.cvv = 'CVV / CVC is required.';
      return false;
    }
    const isAmex = this.cardBrand === 'amex';
    const isValid = isAmex ? /^\d{4}$/.test(clean) : /^\d{3}$/.test(clean);
    if (!isValid) {
      this.cardErrors.cvv = isAmex ? 'Amex requires a 4-digit CVV.' : 'Enter a 3-digit CVV.';
      return false;
    }
    this.cardErrors.cvv = null;
    return true;
  }

  validateCardForm(): boolean {
    this.cardTouched.number = true;
    this.cardTouched.name = true;
    this.cardTouched.expiry = true;
    this.cardTouched.cvv = true;

    const isNumValid = this.validateCardNumber();
    const isNameValid = this.validateCardHolderName();
    const isExpValid = this.validateCardExpiry();
    const isCvvValid = this.validateCardCvv();

    return isNumValid && isNameValid && isExpValid && isCvvValid;
  }

  selectBank(bankName: string): void {
    this.selectedBank = bankName;
    this.bankTouched = true;
    this.validateBank();
  }

  validateBank(): boolean {
    if (!this.selectedBank || this.selectedBank.trim() === '') {
      this.bankError = 'Please select a bank to proceed with Net Banking.';
      return false;
    }
    this.bankError = null;
    return true;
  }

  get isPaymentValid(): boolean {
    if (this.selectedPaymentMethod === 'UPI') {
      return !this.upiIdError && this.upiId.trim().length > 0;
    }
    if (this.selectedPaymentMethod === 'CARD') {
      const raw = this.cardNumber.replace(/\D/g, '');
      const isAmex = /^3[47]/.test(raw);
      const expectedLen = isAmex ? 15 : 16;
      return (
        !this.cardErrors.number &&
        !this.cardErrors.name &&
        !this.cardErrors.expiry &&
        !this.cardErrors.cvv &&
        raw.length === expectedLen &&
        this.cardHolderName.trim().length >= 3 &&
        this.cardExpiry.trim().length === 5 &&
        this.cardCvv.trim().length >= (isAmex ? 4 : 3)
      );
    }
    if (this.selectedPaymentMethod === 'NETBANKING') {
      return !this.bankError && this.selectedBank.trim().length > 0;
    }
    return true;
  }

  onCreateBooking(): void {
    if (!this.adults || this.adults < 1) {
      this.errorMessage = 'At least 1 adult is required to make a booking. Child-only bookings are not allowed.';
      return;
    }

    this.submittingBooking = true;
    this.errorMessage = null;

    // Real-world capacity: adults occupy beds (max 2 adults per room)
    const roomCapacity = this.selectedRoomType?.maxGuests || 2;
    const effectiveOccupancy = Math.min(this.adults, roomCapacity);

    const payload = {
      hotelId: this.hotelId,
      roomTypeId: this.roomTypeId,
      checkInDate: this.checkInDate,
      checkOutDate: this.checkOutDate,
      guestCount: effectiveOccupancy > 0 ? effectiveOccupancy : 1,
      adultCount: this.adults,
      childCount: this.children,
      paymentPolicy: this.selectedPolicy
    };

    this.reservationService.createReservation(payload).subscribe({
      next: (res: any) => {
        this.submittingBooking = false;
        const normalized: Reservation = {
          ...res,
          id: res.id,
          hotelName: this.hotel?.name || res.hotelName || 'StayEase Hotel',
          roomTypeName: this.selectedRoomType?.name || res.roomTypeName || 'Selected Room',
          referenceNumber: res.publicReference || res.referenceNumber || ('RES-' + res.id),
          totalAmount: Number(res.pricingSnapshot?.totalAmount ?? res.totalAmount ?? this.totalBookingAmount),
          lodgingSubtotal: Number(res.pricingSnapshot?.lodgingSubtotal ?? res.lodgingSubtotal ?? this.lodgingSubtotal),
          taxAmount: Number(res.pricingSnapshot?.taxAmount ?? res.taxAmount ?? this.taxAmount),
          status: res.reservationStatus || res.status || 'CONFIRMED',
          paymentStatus: res.paymentStatus || (this.selectedPolicy === 'PREPAID' ? 'PENDING' : 'PENDING'),
          paymentPolicy: res.paymentPolicy || this.selectedPolicy,
          checkInDate: res.checkInDate || this.checkInDate,
          checkOutDate: res.checkOutDate || this.checkOutDate,
          guestCount: res.guestCount || this.totalGuests,
          nights: res.nights || this.stayNights
        };
        this.createdReservation = normalized;

        if (this.selectedPolicy === 'PREPAID') {
          this.step = 2; // Payment Step
          this.startHoldTimer();
        } else {
          this.step = 3; // Confirmed Step (PAY_LATER)
        }
      },
      error: (err) => {
        this.submittingBooking = false;
        this.errorMessage = err.message || 'Failed to create reservation.';
      }
    });
  }

  onProcessPayment(): void {
    if (!this.createdReservation) return;

    if (this.selectedPaymentMethod === 'UPI') {
      this.upiIdTouched = true;
      if (!this.validateUpiId()) {
        return;
      }
    } else if (this.selectedPaymentMethod === 'CARD') {
      if (!this.validateCardForm()) {
        return;
      }
    } else if (this.selectedPaymentMethod === 'NETBANKING') {
      this.bankTouched = true;
      if (!this.validateBank()) {
        return;
      }
    }

    this.processingPayment = true;
    this.paymentError = null;

    const paymentRequest: PaymentRequest = {
      paymentMethod: this.selectedPaymentMethod,
      simulationOutcome: this.selectedOutcome || 'SUCCESS'
    };

    this.reservationService.processPayment(this.createdReservation.id, paymentRequest).subscribe({
      next: (payRes: any) => {
        this.processingPayment = false;
        this.stopHoldTimer();
        const pStatus = payRes.paymentStatus || payRes.status;
        const rStatus = payRes.reservationStatus;
        if (pStatus === 'PAID' || rStatus === 'CONFIRMED' || pStatus === 'SUCCESS') {
          if (this.createdReservation) {
            this.createdReservation.status = 'CONFIRMED';
            this.createdReservation.paymentStatus = 'PAID';
          }
          this.step = 3; // Confirmed
        } else if (pStatus === 'RECONCILIATION_REQUIRED') {
          this.paymentError = 'Payment outcome is being verified by the bank. Your room hold remains protected.';
        } else {
          this.paymentError = 'Payment was declined or timed out. Please retry with another payment method.';
        }
      },
      error: (err) => {
        this.processingPayment = false;
        const msg = err.message || '';
        if (msg.toLowerCase().includes('already fully paid') || msg.toLowerCase().includes('already paid')) {
          this.stopHoldTimer();
          if (this.createdReservation) {
            this.createdReservation.status = 'CONFIRMED';
            this.createdReservation.paymentStatus = 'PAID';
          }
          this.step = 3; // Already paid, show confirmation
          return;
        }
        this.paymentError = msg || 'Payment processing failed. Please try again.';
      }
    });
  }

  startHoldTimer(): void {
    this.holdCountdownSeconds = 600;
    this.holdTimerInterval = setInterval(() => {
      if (this.holdCountdownSeconds > 0) {
        this.holdCountdownSeconds--;
      } else {
        this.stopHoldTimer();
        this.paymentError = 'The 10-minute inventory hold has expired. Please initiate a new booking.';
      }
    }, 1000);
  }

  stopHoldTimer(): void {
    if (this.holdTimerInterval) {
      clearInterval(this.holdTimerInterval);
      this.holdTimerInterval = null;
    }
  }

  get formattedHoldTime(): string {
    const mins = Math.floor(this.holdCountdownSeconds / 60);
    const secs = this.holdCountdownSeconds % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  }
}
