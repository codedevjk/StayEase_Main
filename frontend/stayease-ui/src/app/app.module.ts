import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

// Interceptors
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { ErrorInterceptor } from './interceptors/error.interceptor';

// Shell & Shared Components
import { NavbarComponent } from './components/shell/navbar/navbar.component';
import { FooterComponent } from './components/shell/footer/footer.component';
import { LoadingSpinnerComponent } from './components/shared/loading-spinner/loading-spinner.component';
import { EmptyStateComponent } from './components/shared/empty-state/empty-state.component';
import { ErrorStateComponent } from './components/shared/error-state/error-state.component';

// Auth Components
import { LoginComponent } from './components/auth/login/login.component';
import { RegisterComponent } from './components/auth/register/register.component';

// User & Home Components
import { HomeComponent } from './components/home/home.component';
import { ProfileComponent } from './components/user/profile/profile.component';

// Hotel Components
import { HotelSearchComponent } from './components/hotels/hotel-search/hotel-search.component';
import { HotelDetailComponent } from './components/hotels/hotel-detail/hotel-detail.component';

// Reservation Components
import { BookingComponent } from './components/reservations/booking/booking.component';
import { MyReservationsComponent } from './components/reservations/my-reservations/my-reservations.component';
import { ReservationDetailComponent } from './components/reservations/reservation-detail/reservation-detail.component';

// Admin Components
import { AdminShellComponent } from './components/admin/admin-shell/admin-shell.component';
import { AdminDashboardComponent } from './components/admin/admin-dashboard/admin-dashboard.component';
import { AdminHotelsComponent } from './components/admin/admin-hotels/admin-hotels.component';
import { AdminRoomsComponent } from './components/admin/admin-rooms/admin-rooms.component';
import { AdminReservationsComponent } from './components/admin/admin-reservations/admin-reservations.component';
import { AdminReviewsComponent } from './components/admin/admin-reviews/admin-reviews.component';
import { AdminAuditComponent } from './components/admin/admin-audit/admin-audit.component';

@NgModule({
  declarations: [
    AppComponent,
    NavbarComponent,
    FooterComponent,
    LoadingSpinnerComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoginComponent,
    RegisterComponent,
    HomeComponent,
    ProfileComponent,
    HotelSearchComponent,
    HotelDetailComponent,
    BookingComponent,
    MyReservationsComponent,
    ReservationDetailComponent,
    AdminShellComponent,
    AdminDashboardComponent,
    AdminHotelsComponent,
    AdminRoomsComponent,
    AdminReservationsComponent,
    AdminReviewsComponent,
    AdminAuditComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
