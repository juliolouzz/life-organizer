export type Role = 'ROLE_USER' | 'ROLE_ADMIN';

export interface AuthenticatedUser {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  emailVerified?: boolean;
  /** ISO-8601 instant. Non-null when the account is in the 30-day grace period. */
  deletionScheduledAt?: string | null;
}
