export interface Amenity {
  id: number;
  name: string;
  iconClass?: string;
}

export interface Hotel {
  id: number;
  name: string;
  destination?: string;
  description: string;
  address: string;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  phone: string;
  email: string;
  starRating: number;
  active: boolean;
  amenities: any[];
  startingPrice?: number;
  startingNightlyRate?: number;
  imageUrl?: string;
  averageRating?: number;
  reviewCount?: number;
}

export interface RoomType {
  id: number;
  hotelId: number;
  name: string;
  description: string;
  capacity: number;
  basePrice: number;
  amenities?: string[];
}

export interface NightlyRate {
  date: string;
  rate?: number;
  effectiveRate?: number;
  rateType: string;
}

export interface RoomTypeAvailability {
  roomTypeId: number;
  name: string;
  capacity?: number;
  maxGuests?: number;
  availableRooms: number;
  basePrice?: number;
  lodgingSubtotal?: number;
  taxPercentage?: number;
  taxAmount?: number;
  totalAmount?: number;
  effectiveTotal?: number;
  nightlyRates: NightlyRate[];
}

export interface HotelAvailabilityResponse {
  hotelId: number;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  roomTypes: RoomTypeAvailability[];
}

export interface PhysicalRoom {
  id: number;
  hotelId: number;
  roomTypeId: number;
  roomNumber: string;
  operationalStatus: 'OPERATIONAL' | 'OUT_OF_SERVICE';
  housekeepingStatus: 'CLEAN' | 'DIRTY' | 'INSPECTING' | 'INSPECTED';
}
