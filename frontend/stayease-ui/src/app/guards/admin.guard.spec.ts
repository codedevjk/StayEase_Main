import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AdminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';

describe('AdminGuard', () => {
  let guard: AdminGuard;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'isAdmin']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        AdminGuard,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
    guard = TestBed.inject(AdminGuard);
  });

  it('should allow activation when user is logged in and has ROLE_ADMIN', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);
    authServiceSpy.isAdmin.and.returnValue(true);

    const result = guard.canActivate({} as any, { url: '/admin' } as any);
    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should redirect non-admin user to home', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);
    authServiceSpy.isAdmin.and.returnValue(false);

    const result = guard.canActivate({} as any, { url: '/admin' } as any);
    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should redirect unauthenticated visitor to login', () => {
    authServiceSpy.isLoggedIn.and.returnValue(false);
    authServiceSpy.isAdmin.and.returnValue(false);

    const result = guard.canActivate({} as any, { url: '/admin' } as any);
    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/admin' } });
  });
});
