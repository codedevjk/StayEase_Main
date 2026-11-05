import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { User } from '../../../models/user.model';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html'
})
export class ProfileComponent implements OnInit {
  user: User | null = null;
  profileForm!: FormGroup;
  emailForm!: FormGroup;
  loading: boolean = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor(private authService: AuthService, private fb: FormBuilder) {}

  ngOnInit(): void {
    this.user = this.authService.getCurrentUser();

    this.profileForm = this.fb.group({
      fullName: [this.user?.fullName || '', [Validators.required, Validators.minLength(2)]],
      phone: [this.user?.phone || '', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]]
    });

    this.emailForm = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newEmail: [this.user?.email || '', [Validators.required, Validators.email]]
    });

    this.fetchProfile();
  }

  fetchProfile(): void {
    this.loading = true;
    this.authService.getProfile().subscribe({
      next: (profile) => {
        this.loading = false;
        this.user = profile;
        this.profileForm.patchValue({
          fullName: profile.fullName,
          phone: profile.phone
        });
        this.emailForm.patchValue({
          newEmail: profile.email
        });
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onUpdateProfile(): void {
    if (this.profileForm.invalid) return;
    this.loading = true;
    this.successMessage = null;
    this.errorMessage = null;

    this.authService.updateProfile(this.profileForm.value).subscribe({
      next: (updated) => {
        this.loading = false;
        this.user = updated;
        this.successMessage = 'Personal details updated successfully.';
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to update profile.';
      }
    });
  }

  onUpdateEmail(): void {
    if (this.emailForm.invalid) return;
    this.loading = true;
    this.successMessage = null;
    this.errorMessage = null;

    this.authService.updateEmail(this.emailForm.value).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Email address updated. For security, your active sessions were reset. Please log in with your new email.';
        setTimeout(() => {
          this.authService.logout();
          window.location.href = '/login';
        }, 2000);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.message || 'Failed to update email. Please verify your current password.';
      }
    });
  }
}
