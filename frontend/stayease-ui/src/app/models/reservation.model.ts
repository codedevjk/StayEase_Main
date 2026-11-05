export type ReservationStatus = 
  | 'PENDING_PAYMENT' 
  | 'CONFIRMED' 
  | 'CHECKED_IN' 
  | 'CHECKED_OUT' 
  | 'CANCELLED' 
  | 'NO_SHOW' 
  | 'PAYMENT_FAILED';

export type PaymentPolicy = 'PREPAID' | 'PAY_LATER';
export type PaymentStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'FAILED' | 'RECONCILIATION_REQUIRED';
export type PaymentMethod = 'CARD' | 'UPI' | 'NETBANKING';

export interface Reservation {
  id: number;
  referenceNumber: string;
  publicReference?: string;
  hotelId: number;
  hotelName?: string;
  roomTypeId: number;
  roomTypeName?: string;
  userId: number;
  guestName: string;
  guestEmail: string;
  guestPhone: string;
  guestCount: number;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  status: ReservationStatus;
  reservationStatus?: string;
  paymentPolicy: PaymentPolicy;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  lodgingSubtotal: number;
  taxAmount: number;
  amountPaid: number;
  balanceDue: number;
  assignedRoomNumber?: string;
  holdExpiresAt?: string;
  paymentDueAt?: string;
  createdAt: string;
  cancelledAt?: string;
  cancellationPenalty?: number;
  refundAmount?: number;
}

export interface CreateReservationRequest {
  hotelId: number;
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  paymentPolicy: PaymentPolicy;
}

export type SimulationOutcome = 'SUCCESS' | 'FAILURE' | 'TIMEOUT';

export interface PaymentRequest {
  paymentMethod: PaymentMethod;
  simulationOutcome: SimulationOutcome;
}

export interface PaymentResponse {
  transactionId: string;
  reservationId: number;
  status: PaymentStatus;
  amount: number;
  paymentMethod: PaymentMethod;
  idempotencyKey: string;
  message?: string;
}

export interface CancelReservationRequest {
  cancellationReason: string;
}

export interface CancellationSummary {
  reservationId: number;
  status: string;
  cancelledAt: string;
  hoursBeforeCheckIn: number;
  totalPaid: number;
  cancellationPenalty: number;
  refundAmount: number;
  refundStatus: string;
}

export interface AmendReservationRequest {
  newCheckInDate: string;
  newCheckOutDate: string;
  newRoomTypeId: number;
  newGuestCount: number;
}

export interface CheckInResponse {
  reservationId: number;
  reservationStatus: string;
  physicalRoomNumber: string;
  checkedInAt: string;
}

export interface CheckOutResponse {
  reservationId: number;
  reservationStatus: string;
  checkedOutAt: string;
}

export interface InvoiceDto {
  invoiceNumber: string;
  invoiceDate: string;
  reservationReference: string;
  hotel: {
    name: string;
    address: string;
    contactPhone: string;
  };
  guest: {
    fullName: string;
    email: string;
    phone: string;
  };
  stay: {
    roomTypeName: string;
    checkInDate: string;
    checkOutDate: string;
    nights: number;
    roomNumber?: string;
  };
  charges: {
    nightlyRates: { date: string; rate: number }[];
    lodgingSubtotal: number;
    taxPercentage: number;
    taxAmount: number;
    totalAmount: number;
    amountPaid: number;
    balanceDue: number;
  };
  paymentDetails: {
    paymentMethod: PaymentMethod;
    paymentStatus: PaymentStatus;
    transactionId?: string;
  };
}
