import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created and default to logged out', () => {
    expect(service).toBeTruthy();
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.isAdmin()).toBeFalse();
  });

  it('should store user and token upon successful login', () => {
    const mockAuthResponse = {
      token: 'mock-jwt-token-xyz',
      user: {
        id: 101,
        email: 'guest@example.com',
        fullName: 'Guest User',
        phone: '9876543210',
        role: 'ROLE_USER' as const
      }
    };

    service.login({ email: 'guest@example.com', password: 'password123' }).subscribe(res => {
      expect(res.token).toBe('mock-jwt-token-xyz');
      expect(service.isLoggedIn()).toBeTrue();
      expect(service.getToken()).toBe('mock-jwt-token-xyz');
      expect(service.getCurrentUser()?.email).toBe('guest@example.com');
      expect(service.isAdmin()).toBeFalse();
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(mockAuthResponse);
  });

  it('should identify admin role correctly', () => {
    const mockAdminResponse = {
      token: 'admin-jwt-token-abc',
      user: {
        id: 1,
        email: 'admin@stayease.com',
        fullName: 'System Administrator',
        phone: '9876543210',
        role: 'ROLE_ADMIN' as const
      }
    };

    service.login({ email: 'admin@stayease.com', password: 'adminPassword!' }).subscribe(() => {
      expect(service.isAdmin()).toBeTrue();
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    req.flush(mockAdminResponse);
  });

  it('should clear stored credentials upon logout', () => {
    localStorage.setItem('stayease_token', 'test-token');
    localStorage.setItem('stayease_user', JSON.stringify({ id: 101, role: 'ROLE_USER' }));

    service.logout();

    expect(service.getToken()).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });
});
