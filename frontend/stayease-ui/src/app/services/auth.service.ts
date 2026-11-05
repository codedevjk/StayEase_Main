import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, User, UpdateProfileRequest, UpdateEmailRequest } from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly USER_KEY = 'stayease_user';
  private readonly TOKEN_KEY = 'stayease_token';
  private readonly authUrl = `${environment.apiBaseUrl}/auth`;
  private readonly userUrl = `${environment.apiBaseUrl}/users`;

  private currentUserSubject = new BehaviorSubject<User | null>(this.loadStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  private loadStoredUser(): User | null {
    const json = localStorage.getItem(this.USER_KEY);
    if (!json) return null;
    try {
      return JSON.parse(json);
    } catch {
      return null;
    }
  }

  public register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authUrl}/register`, request).pipe(
      tap(res => this.handleAuthSuccess(res))
    );
  }

  public login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authUrl}/login`, request).pipe(
      tap(res => this.handleAuthSuccess(res))
    );
  }

  private handleAuthSuccess(res: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, res.token);
    const user: User = {
      id: res.user.id,
      fullName: res.user.fullName,
      email: res.user.email,
      phone: res.user.phone || '',
      role: res.user.role
    };
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  public logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
  }

  public getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  public getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  public isLoggedIn(): boolean {
    return !!this.getToken() && !!this.getCurrentUser();
  }

  public isAdmin(): boolean {
    const user = this.getCurrentUser();
    return !!user && user.role === 'ROLE_ADMIN';
  }

  public getProfile(): Observable<User> {
    return this.http.get<User>(`${this.userUrl}/me`).pipe(
      tap(profile => {
        const current = this.getCurrentUser();
        if (current) {
          const updated = { ...current, ...profile };
          localStorage.setItem(this.USER_KEY, JSON.stringify(updated));
          this.currentUserSubject.next(updated);
        }
      })
    );
  }

  public updateProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>(`${this.userUrl}/me`, request).pipe(
      tap(updated => {
        const current = this.getCurrentUser();
        if (current) {
          const user = { ...current, fullName: updated.fullName, phone: updated.phone };
          localStorage.setItem(this.USER_KEY, JSON.stringify(user));
          this.currentUserSubject.next(user);
        }
      })
    );
  }

  public updateEmail(request: UpdateEmailRequest): Observable<User> {
    return this.http.patch<User>(`${this.userUrl}/me/email`, request).pipe(
      tap(updated => {
        const current = this.getCurrentUser();
        if (current) {
          const user = { ...current, email: updated.email };
          localStorage.setItem(this.USER_KEY, JSON.stringify(user));
          this.currentUserSubject.next(user);
        }
      })
    );
  }
}
