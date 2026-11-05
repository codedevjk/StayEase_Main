import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/auth/login/login.component';
import { RegisterComponent } from './components/auth/register/register.component';
import { ProfileComponent } from './components/user/profile/profile.component';
import { HotelSearchComponent } from './components/hotels/hotel-search/hotel-search.component';
import { HotelDetailComponent } from './components/hotels/hotel-detail/hotel-detail.component';
import { BookingComponent } from './components/reservations/booking/booking.component';
import { MyReservationsComponent } from './components/reservations/my-reservations/my-reservations.component';
import { ReservationDetailComponent } from './components/reservations/reservation-detail/reservation-detail.component';

import { AdminShellComponent } from './components/admin/admin-shell/admin-shell.component';
import { AdminDashboardComponent } from './components/admin/admin-dashboard/admin-dashboard.component';
import { AdminHotelsComponent } from './components/admin/admin-hotels/admin-hotels.component';
import { AdminRoomsComponent } from './components/admin/admin-rooms/admin-rooms.component';
import { AdminReservationsComponent } from './components/admin/admin-reservations/admin-reservations.component';
import { AdminReviewsComponent } from './components/admin/admin-reviews/admin-reviews.component';
import { AdminAuditComponent } from './components/admin/admin-audit/admin-audit.component';

import { AuthGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';

const routes: Routes = [
  // Public Guest Routes
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'hotels', component: HotelSearchComponent },
  { path: 'hotels/:hotelId', component: HotelDetailComponent },

  // Protected Guest Routes
  { path: 'profile', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: 'booking', component: BookingComponent, canActivate: [AuthGuard] },
  { path: 'reservations', component: MyReservationsComponent, canActivate: [AuthGuard] },
  { path: 'reservations/:id', component: ReservationDetailComponent, canActivate: [AuthGuard] },

  // Administrator Operations Portal
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [AdminGuard],
    children: [
      { path: '', component: AdminDashboardComponent },
      { path: 'hotels', component: AdminHotelsComponent },
      { path: 'rooms', component: AdminRoomsComponent },
      { path: 'reservations', component: AdminReservationsComponent },
      { path: 'reviews', component: AdminReviewsComponent },
      { path: 'audit', component: AdminAuditComponent }
    ]
  },

  // Fallback
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { scrollPositionRestoration: 'enabled' })],
  exports: [RouterModule]
})
export class AppRoutingModule {}
