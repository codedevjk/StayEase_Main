import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {

  constructor(private authService: AuthService, private router: Router) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        let errorMessage = 'An unexpected error occurred. Please try again.';

        if (error.status === 401) {
          // Token expired or invalid
          this.authService.logout();
          this.router.navigate(['/login'], { queryParams: { sessionExpired: 'true' } });
          errorMessage = 'Session expired or unauthorized. Please log in again.';
        } else if (error.error && typeof error.error === 'object') {
          if (error.error.message) {
            errorMessage = error.error.message;
          } else if (error.error.error) {
            errorMessage = error.error.error;
          }
        } else if (error.status === 0) {
          errorMessage = 'Unable to connect to StayEase services. Please check network connection.';
        } else if (error.status === 503) {
          errorMessage = 'Service temporarily unavailable. Please retry in a few moments.';
        }

        return throwError(() => new Error(errorMessage));
      })
    );
  }
}
