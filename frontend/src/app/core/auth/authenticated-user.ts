export type Role = 'ROLE_USER' | 'ROLE_ADMIN';

export interface AuthenticatedUser {
  id: number;
  email: string;
  displayName: string;
  role: Role;
}
