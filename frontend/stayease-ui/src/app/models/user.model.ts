export interface User {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  tokenVersion?: number;
  createdAt?: string;
}

export interface UserSummary {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
}

export interface AuthResponse {
  token: string;
  user: UserSummary;
}

export interface LoginRequest {
  email: string;
  password?: string;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  phone: string;
  password?: string;
}

export interface UpdateProfileRequest {
  fullName: string;
  phone: string;
}

export interface UpdateEmailRequest {
  currentPassword: string;
  newEmail: string;
}
