# StayEase Comprehensive API Specification & Data Contracts

**Document Version:** 1.1 (Phase 0 Contract Freeze)  
**Status:** Code-Ready API Contract  
**Base URL:** All external routes are versioned under `/api` via Spring Cloud Gateway (`http://localhost:8080`)  

---

## 1. Global Standards & Protocol Envelopes

### 1.1 HTTP Headers & Correlation ID
- `Authorization: Bearer <JWT_ACCESS_TOKEN>`: Required for all protected endpoints.
- `X-Correlation-ID: <UUID>`: Gateway assigns or propagates correlation ID across every inbound request, internal service call, and MDC logging context. Returned in all API responses.
- `Idempotency-Key: <UUID>`: Required for non-safe mutating operations (payments, amendments, cancellations).

### 1.2 Pagination Envelope: `PageResponse<T>`
All paginated list endpoints return data wrapped in this normalized envelope:
```json
{
  "content": [],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false,
  "empty": false
}
```
Query parameters for pagination:
- `page`: 0-indexed page number (default: `0`).
- `size`: Items per page (default: `20`, max: `100`).

### 1.3 Normalized Error Envelope: `ErrorResponse`
All microservices implement centralized exception handling (`@RestControllerAdvice`) returning:
```json
{
  "timestamp": "2026-10-18T16:45:00.123Z",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_INVENTORY",
  "message": "Selected room type has no available capacity for the requested date range",
  "path": "/api/reservations",
  "correlationId": "c4b9e28f-7c1a-4d2a-89a3-53d9e8b7c012",
  "fieldErrors": [
    {
      "field": "roomTypeId",
      "rejectedValue": 4,
      "message": "Capacity exceeded for date 2026-11-02"
    }
  ]
}
```

### 1.4 Standard HTTP Status Codes
| HTTP Status | Semantics in StayEase |
|---|---|
| `200 OK` | Successful query or idempotent update |
| `201 Created` | Successful resource creation (Register, Booking, Review) |
| `204 No Content` | Successful operation with no return payload |
| `400 Bad Request` | Malformed JSON or syntax failure |
| `401 Unauthorized` | Missing, invalid, expired, or revoked JWT token |
| `403 Forbidden` | Access denied (e.g. USER calling ADMIN endpoint, or accessing another user's reservation) |
| `404 Not Found` | Entity ID not found in database |
| `409 Conflict` | Domain conflict (overbooking, dirty room at check-in, duplicate email, duplicate review) |
| `422 Unprocessable Entity`| Bean validation failure on request DTO |
| `500 Internal Server Error`| Unexpected runtime server exception |
| `503 Service Unavailable`| Downstream microservice unreachable during critical fail-closed operation |

---

## 2. User Service APIs (Port 8081)

### 2.1 `POST /api/auth/register`
Creates a new guest account.
- **Access:** Public
- **Validation:**
  - `fullName`: `@NotBlank`, `@Size(min = 2, max = 100)`
  - `email`: `@NotBlank`, `@Email`, unique across database
  - `phone`: `@NotBlank`, `@Pattern(regexp = "^[0-9]{10,15}$")`
  - `address`: Optional string, `@Size(max = 255)`
  - `password`: `@NotBlank`, `@Size(min = 8, max = 100)`
- **Request Body (`RegisterRequest`):**
  ```json
  {
    "fullName": "Jane Doe",
    "email": "jane.doe@example.com",
    "phone": "9876543210",
    "address": "123 Main Street, Bangalore",
    "password": "Password@123"
  }
  ```
- **Response (`201 Created` - `AuthResponse`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsIn...",
    "user": {
      "id": 101,
      "fullName": "Jane Doe",
      "email": "jane.doe@example.com",
      "phone": "9876543210",
      "role": "ROLE_USER"
    }
  }
  ```
- **Error Cases:** `409 Conflict` if email already registered.

### 2.2 `POST /api/auth/login`
Authenticates a user and issues a 15-minute JWT access token.
- **Access:** Public
- **Request Body (`LoginRequest`):**
  ```json
  {
    "email": "jane.doe@example.com",
    "password": "Password@123"
  }
  ```
- **Response (`200 OK` - `AuthResponse`):** Same as Register.
- **Error Cases:** `401 Unauthorized` with generic message: `"Invalid email or password"`.

### 2.3 `GET /api/users/me`
Retrieves the authenticated user's profile.
- **Access:** Authenticated (`ROLE_USER`, `ROLE_ADMIN`)
- **Response (`200 OK` - `UserProfileDto`):**
  ```json
  {
    "id": 101,
    "fullName": "Jane Doe",
    "email": "jane.doe@example.com",
    "phone": "9876543210",
    "address": "123 Main Street, Bangalore",
    "role": "ROLE_USER",
    "tokenVersion": 1,
    "createdAt": "2026-10-18T10:00:00Z"
  }
  ```

### 2.4 `PUT /api/users/me`
Updates guest profile fields (name, phone, address).
- **Access:** Authenticated (`ROLE_USER`, `ROLE_ADMIN`)
- **Request Body (`UpdateProfileRequest`):**
  ```json
  {
    "fullName": "Jane Doe",
    "phone": "9876543219",
    "address": "456 Residency Road, Bangalore"
  }
  ```
- **Response (`200 OK` - `UserProfileDto`)**

### 2.5 `PATCH /api/users/me/email`
Updates user email with password verification, increments `tokenVersion`, and invalidates existing sessions.
- **Access:** Authenticated (`ROLE_USER`)
- **Request Body (`UpdateEmailRequest`):**
  ```json
  {
    "currentPassword": "Password@123",
    "newEmail": "jane.new@example.com"
  }
  ```
- **Response (`200 OK` - `UserProfileDto`)**: User must log in again with new email.
- **Error Cases:** `401 Unauthorized` on invalid password; `409 Conflict` on duplicate email.

### 2.6 `GET /api/admin/users`
Searches guest accounts for customer support.
- **Access:** `ROLE_ADMIN`
- **Query Parameters:** `query` (name or email), `page`, `size`
- **Response (`200 OK` - `PageResponse<AdminUserSummaryDto>`):**
  ```json
  {
    "content": [
      {
        "id": 101,
        "fullName": "Jane Doe",
        "email": "jane.doe@example.com",
        "phone": "9876543210",
        "role": "ROLE_USER",
        "tokenVersion": 2,
        "createdAt": "2026-10-18T10:00:00Z"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

### 2.7 `POST /api/admin/users/{userId}/password-reset`
Admin-assisted password reset. Generates a secure temporary password and increments `tokenVersion`.
- **Access:** `ROLE_ADMIN`
- **Response (`200 OK` - `AdminPasswordResetResponse`):**
  ```json
  {
    "userId": 101,
    "temporaryPassword": "TempPassword!8472",
    "message": "Temporary password generated. Disclose securely to user; will not be displayed again."
  }
  ```

### 2.8 Internal Endpoint: `GET /internal/users/{userId}/token-version`
- **Access:** Internal Service-to-Service only
- **Response (`200 OK`):**
  ```json
  {
    "userId": 101,
    "tokenVersion": 2
  }
  ```

---

## 3. Property Service APIs (Port 8082)

### 3.1 `GET /api/hotels`
Searches and filters hotels catalog.
- **Access:** Public
- **Query Parameters:** `destination`, `query` (name), `minPrice`, `maxPrice`, `amenities` (comma-separated IDs), `minRating`, `page`, `size`
- **Response (`200 OK` - `PageResponse<HotelSummaryDto>`):**
  ```json
  {
    "content": [
      {
        "id": 1,
        "name": "StayEase Grand Palace",
        "destination": "Goa",
        "address": "100 Beach Road, Calangute, Goa",
        "starRating": 5,
        "publishedRating": 4.8,
        "reviewCount": 124,
        "startingNightlyRate": 3500.00,
        "imageUrl": "/assets/hotels/hotel1.jpg",
        "amenities": ["Wi-Fi", "Pool", "Breakfast", "Spa"]
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

### 3.2 `GET /api/hotels/{hotelId}`
Retrieves detailed hotel information with configured room types and amenities.
- **Access:** Public
- **Response (`200 OK` - `HotelDetailDto`):**
  ```json
  {
    "id": 1,
    "name": "StayEase Grand Palace",
    "destination": "Goa",
    "address": "100 Beach Road, Calangute, Goa",
    "contactEmail": "palace.goa@stayease.com",
    "contactPhone": "0832223344",
    "description": "Luxury beachfront hotel with world-class amenities.",
    "starRating": 5,
    "publishedRating": 4.8,
    "reviewCount": 124,
    "amenities": [
      {"id": 1, "name": "Free High-Speed Wi-Fi"},
      {"id": 2, "name": "Swimming Pool"}
    ],
    "roomTypes": [
      {
        "id": 10,
        "name": "Deluxe Ocean View",
        "description": "King bed with ocean balcony",
        "maxGuests": 2,
        "baseNightlyRate": 4500.00,
        "totalRooms": 20
      }
    ]
  }
  ```

### 3.3 Admin Property Management Endpoints (`ROLE_ADMIN`)
- `POST /api/admin/hotels`: Creates a hotel.
- `PUT /api/admin/hotels/{hotelId}`: Updates hotel information.
- `POST /api/admin/hotels/{hotelId}/room-types`: Adds room type.
- `PUT /api/admin/room-types/{roomTypeId}`: Updates room type attributes.
- `POST /api/admin/room-types/{roomTypeId}/rate-overrides`:
  - **Request Body (`CreateRateOverrideRequest`):**
    ```json
    {
      "startDate": "2026-12-20",
      "endDate": "2027-01-05",
      "overrideType": "SEASONAL",
      "nightlyRate": 7500.00
    }
    ```
- `POST /api/admin/hotels/{hotelId}/rooms`: Registers physical room with room number.
- `GET /api/admin/hotels/{hotelId}/rooms`: Lists physical rooms with housekeeping and operational status.
- `PATCH /api/admin/rooms/{roomId}/housekeeping`:
  - Request: `{"housekeepingStatus": "CLEAN"}` (`CLEAN`, `DIRTY`, `INSPECTING`)
- `PATCH /api/admin/rooms/{roomId}/operational`:
  - Request: `{"operationalStatus": "OUT_OF_SERVICE"}` (`OPERATIONAL`, `OUT_OF_SERVICE`)

### 3.4 Internal Endpoints for Reservation Service
- `GET /internal/properties/room-types/{roomTypeId}/capacity`:
  - Returns `{"roomTypeId": 10, "sellableCapacity": 18}` (total physical rooms minus `OUT_OF_SERVICE`).
- `GET /internal/properties/room-types/{roomTypeId}/pricing?checkIn=2026-11-01&checkOut=2026-11-03`:
  - Returns calculated nightly rates applying seasonal/weekend precedence:
    ```json
    {
      "roomTypeId": 10,
      "baseNightlyRate": 4500.00,
      "nightlyRates": [
        {"date": "2026-11-01", "effectiveRate": 4500.00, "rateType": "BASE"},
        {"date": "2026-11-02", "effectiveRate": 5200.00, "rateType": "WEEKEND"}
      ]
    }
    ```
- `POST /internal/properties/rooms/assign-clean`:
  - Request: `{"hotelId": 1, "roomTypeId": 10, "reservationId": 5001}`
  - Returns: `{"physicalRoomId": 302, "roomNumber": "104-A"}` or `409 Conflict` if no clean room.
- `POST /internal/properties/rooms/{roomId}/mark-dirty`:
  - Invoked upon checkout.

---

## 4. Reservation Service APIs (Port 8083)

### 4.1 `GET /api/reservations/availability`
Authoritative real-time availability search across hotel room types for requested dates.
- **Access:** Public
- **Query Parameters:** `hotelId` (mandatory), `checkIn` (`YYYY-MM-DD`), `checkOut` (`YYYY-MM-DD`), `guests` (int)
- **Response (`200 OK` - `HotelAvailabilityResponse`):**
  ```json
  {
    "hotelId": 1,
    "checkInDate": "2026-11-01",
    "checkOutDate": "2026-11-03",
    "nights": 2,
    "roomTypes": [
      {
        "roomTypeId": 10,
        "name": "Deluxe Ocean View",
        "maxGuests": 2,
        "availableRooms": 5,
        "nightlyRates": [
          {"date": "2026-11-01", "rate": 4500.00},
          {"date": "2026-11-02", "rate": 5200.00}
        ],
        "lodgingSubtotal": 9700.00,
        "taxPercentage": 12.00,
        "taxAmount": 1164.00,
        "totalAmount": 10864.00
      }
    ]
  }
  ```

### 4.2 `POST /api/reservations`
Initiates a reservation. For `PREPAID`, creates a 10-minute hold. For `PAY_LATER`, reserves inventory and sets `paymentDueAt`.
- **Access:** Authenticated (`ROLE_USER`)
- **Request Body (`CreateReservationRequest`):**
  ```json
  {
    "hotelId": 1,
    "roomTypeId": 10,
    "checkInDate": "2026-11-01",
    "checkOutDate": "2026-11-03",
    "guestCount": 2,
    "paymentPolicy": "PREPAID"
  }
  ```
- **Response (`201 Created` - `ReservationDetailDto`):**
  ```json
  {
    "id": 5001,
    "publicReference": "SE-2026-5001",
    "userId": 101,
    "hotelId": 1,
    "roomTypeId": 10,
    "checkInDate": "2026-11-01",
    "checkOutDate": "2026-11-03",
    "nights": 2,
    "guestCount": 2,
    "reservationStatus": "PENDING_PAYMENT",
    "paymentPolicy": "PREPAID",
    "paymentStatus": "PENDING",
    "holdExpiresAt": "2026-10-18T17:10:00Z",
    "paymentDueAt": null,
    "pricingSnapshot": {
      "lodgingSubtotal": 9700.00,
      "taxPercentage": 12.00,
      "taxAmount": 1164.00,
      "totalAmount": 10864.00,
      "nightlyRates": [
        {"date": "2026-11-01", "rate": 4500.00, "rateType": "BASE"},
        {"date": "2026-11-02", "rate": 5200.00, "rateType": "WEEKEND"}
      ]
    },
    "createdAt": "2026-10-18T17:00:00Z"
  }
  ```
- **Error Cases:** `409 Conflict` (`INSUFFICIENT_INVENTORY`) if capacity exhausted on any night.

### 4.3 `POST /api/reservations/{reservationId}/payment`
Simulates a payment attempt.
- **Access:** Authenticated (`ROLE_USER` owning reservation)
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`PaymentSimulationRequest`):**
  ```json
  {
    "paymentMethod": "UPI",
    "simulationOutcome": "SUCCESS"
  }
  ```
- **Response (`200 OK` - `PaymentResponseDto`):**
  ```json
  {
    "transactionId": "TXN-902183",
    "reservationId": 5001,
    "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "amount": 10864.00,
    "paymentMethod": "UPI",
    "paymentStatus": "PAID",
    "reservationStatus": "CONFIRMED",
    "completedAt": "2026-10-18T17:02:15Z"
  }
  ```

### 4.4 `GET /api/reservations/my`
Lists reservations belonging to the authenticated guest.
- **Access:** Authenticated (`ROLE_USER`)
- **Query Parameters:** `page`, `size`, `status`
- **Response (`200 OK` - `PageResponse<ReservationSummaryDto>`)**

### 4.5 `GET /api/reservations/{reservationId}`
Retrieves complete reservation details, price snapshot, and assignment status.
- **Access:** Authenticated (Guest owner or `ROLE_ADMIN`)
- **Response (`200 OK` - `ReservationDetailDto`)**

### 4.6 `POST /api/reservations/{reservationId}/cancel`
Cancels an eligible reservation and calculates policy penalties/refunds.
- **Access:** Authenticated (Guest owner or `ROLE_ADMIN`)
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`CancelReservationRequest`):**
  ```json
  {
    "cancellationReason": "Change of travel plans"
  }
  ```
- **Response (`200 OK` - `CancellationSummaryDto`):**
  ```json
  {
    "reservationId": 5001,
    "status": "CANCELLED",
    "cancelledAt": "2026-10-25T12:00:00Z",
    "hoursBeforeCheckIn": 170.0,
    "totalPaid": 10864.00,
    "cancellationPenalty": 0.00,
    "refundAmount": 10864.00,
    "refundStatus": "REFUNDED"
  }
  ```

### 4.7 `POST /api/reservations/{reservationId}/amend`
Modifies reservation dates or room type.
- **Access:** Authenticated (Guest owner or `ROLE_ADMIN`)
- **Request Body (`AmendReservationRequest`):**
  ```json
  {
    "newCheckInDate": "2026-11-05",
    "newCheckOutDate": "2026-11-08",
    "newRoomTypeId": 10,
    "newGuestCount": 2
  }
  ```
- **Response (`200 OK` - `ReservationDetailDto`):** Shows updated price snapshot and financial reconciliation.

### 4.8 `POST /api/reservations/{reservationId}/check-in`
Performs guest self-service check-in.
- **Access:** Authenticated (Guest owner)
- **Response (`200 OK` - `CheckInResponseDto`):**
  ```json
  {
    "reservationId": 5001,
    "reservationStatus": "CHECKED_IN",
    "physicalRoomNumber": "104-A",
    "checkedInAt": "2026-11-01T14:15:00Z"
  }
  ```
- **Error Cases:** `409 Conflict` if no clean room is available (`"No clean room currently ready. Front desk has been notified."`).

### 4.9 `POST /api/reservations/{reservationId}/check-out`
Performs guest self-service check-out.
- **Access:** Authenticated (Guest owner)
- **Response (`200 OK` - `CheckOutResponseDto`):**
  ```json
  {
    "reservationId": 5001,
    "reservationStatus": "CHECKED_OUT",
    "checkedOutAt": "2026-11-03T10:30:00Z"
  }
  ```

### 4.10 `POST /api/admin/reservations/{reservationId}/no-show`
Admin marks unfulfilled reservation as NO_SHOW.
- **Access:** `ROLE_ADMIN`
- **Request Body (`NoShowRequest`):**
  ```json
  {
    "adminNotes": "Guest failed to arrive by check-in date"
  }
  ```
- **Response (`200 OK` - `ReservationDetailDto`)**

### 4.11 `GET /api/reservations/{reservationId}/invoice`
Retrieves printable invoice data backed by the immutable booking price snapshot.
- **Access:** Authenticated (Guest owner or `ROLE_ADMIN`)
- **Response (`200 OK` - `InvoiceDto`):**
  ```json
  {
    "invoiceNumber": "INV-2026-5001",
    "invoiceDate": "2026-11-03T10:30:00Z",
    "reservationReference": "SE-2026-5001",
    "hotel": {
      "name": "StayEase Grand Palace",
      "address": "100 Beach Road, Calangute, Goa",
      "contactPhone": "0832223344"
    },
    "guest": {
      "fullName": "Jane Doe",
      "email": "jane.doe@example.com",
      "phone": "9876543210"
    },
    "stay": {
      "roomTypeName": "Deluxe Ocean View",
      "checkInDate": "2026-11-01",
      "checkOutDate": "2026-11-03",
      "nights": 2,
      "roomNumber": "104-A"
    },
    "charges": {
      "nightlyRates": [
        {"date": "2026-11-01", "rate": 4500.00},
        {"date": "2026-11-02", "rate": 5200.00}
      ],
      "lodgingSubtotal": 9700.00,
      "taxPercentage": 12.00,
      "taxAmount": 1164.00,
      "totalAmount": 10864.00,
      "amountPaid": 10864.00,
      "balanceDue": 0.00
    },
    "paymentDetails": {
      "paymentMethod": "UPI",
      "paymentStatus": "PAID",
      "transactionId": "TXN-902183"
    }
  }
  ```

### 4.12 `GET /api/admin/dashboard`
Operational administrative metrics.
- **Access:** `ROLE_ADMIN`
- **Response (`200 OK` - `AdminDashboardDto`):**
  ```json
  {
    "reservationCountsByStatus": {
      "CONFIRMED": 45,
      "CHECKED_IN": 12,
      "CHECKED_OUT": 8,
      "CANCELLED": 4,
      "NO_SHOW": 1,
      "PENDING_PAYMENT": 3
    },
    "todayCheckIns": 14,
    "todayCheckOuts": 9,
    "currentOccupancyRate": 78.5,
    "pendingPaymentsCount": 3,
    "totalRevenueCollected": 542000.00,
    "cancellationsCount": 4,
    "noShowCount": 1,
    "partialDataWarning": null
  }
  ```

---

## 5. Review Service APIs (Port 8084)

### 5.1 `POST /api/reviews`
Submits a verified review.
- **Access:** Authenticated (`ROLE_USER`)
- **Validation:**
  - `reservationId`: Mandatory
  - `rating`: `@NotNull`, `@Min(1)`, `@Max(5)`
  - `comment`: `@NotBlank`, `@Size(min = 10, max = 1000)`
- **Request Body (`CreateReviewRequest`):**
  ```json
  {
    "reservationId": 5001,
    "rating": 5,
    "comment": "Exceptional stay! The ocean balcony and morning breakfast were wonderful."
  }
  ```
- **Response (`201 Created` - `ReviewDto`):**
  ```json
  {
    "id": 901,
    "reservationId": 5001,
    "hotelId": 1,
    "guestName": "Jane D.",
    "rating": 5,
    "comment": "Exceptional stay! The ocean balcony and morning breakfast were wonderful.",
    "status": "PUBLISHED",
    "createdAt": "2026-11-04T09:00:00Z"
  }
  ```
- **Error Cases:** `409 Conflict` if review already submitted; `403 Forbidden` if reservation not checked out or 30-day window expired.

### 5.2 `GET /api/hotels/{hotelId}/reviews`
Lists published reviews for a hotel.
- **Access:** Public
- **Query Parameters:** `page`, `size`
- **Response (`200 OK` - `PageResponse<ReviewDto>`)**

### 5.3 `POST /api/admin/reviews/{reviewId}/remove`
Admin moderation to remove inappropriate reviews.
- **Access:** `ROLE_ADMIN`
- **Request Body (`RemoveReviewRequest`):**
  ```json
  {
    "reason": "Violates community content guidelines"
  }
  ```
- **Response (`200 OK` - `ReviewDto`):** Updates status to `REMOVED`.

---

## 6. Audit Trail Contract (All Services)

Every business service implements:
`GET /api/admin/audit`
- **Access:** `ROLE_ADMIN`
- **Query Parameters:**
  - `from`: ISO Instant (e.g. `2026-10-01T00:00:00Z`)
  - `to`: ISO Instant
  - `category`: String (e.g. `AUTH`, `RESERVATION`, `PAYMENT`, `HOUSEKEEPING`)
  - `actorId`: Long
  - `entityId`: String
  - `page`: int
  - `size`: int
- **Response (`200 OK` - `PageResponse<AuditEventDto>`):**
  ```json
  {
    "content": [
      {
        "id": 1204,
        "sourceService": "RESERVATION_SERVICE",
        "actorId": 101,
        "actorRole": "USER",
        "eventCategory": "RESERVATION",
        "action": "RESERVATION_CONFIRMED",
        "entityId": "5001",
        "description": "Prepaid reservation 5001 confirmed after successful UPI payment",
        "timestamp": "2026-10-18T17:02:15Z",
        "beforeJson": "{\"status\":\"PENDING_PAYMENT\"}",
        "afterJson": "{\"status\":\"CONFIRMED\"}"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```
