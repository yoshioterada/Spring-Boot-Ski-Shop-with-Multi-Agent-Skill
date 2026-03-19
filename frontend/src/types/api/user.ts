export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  birthDate?: string;
  gender?: string;
  status: 'ACTIVE' | 'PENDING_VERIFICATION' | 'DEACTIVATED';
  emailVerified: boolean;
  phoneVerified: boolean;
  roleName: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserUpdateRequest {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  birthDate?: string;
  gender?: string;
}
