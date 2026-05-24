export type Role = 'ROLE_USER' | 'ROLE_ADMIN';

export type Currency = 'BRL' | 'USD' | 'EUR';

export interface AuthenticatedUser {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  emailVerified?: boolean;
  /** ISO-8601 instant. Non-null when the account is in the 30-day grace period. */
  deletionScheduledAt?: string | null;
  /** Slice 13: display-only preference for amount formatting. */
  currency?: Currency;
  /**
   * Slice 14: day of the calendar month that anchors the user's accounting
   * month (1-31). Defaults to 1 (regular calendar months) for new and legacy
   * users. Day > last-day-of-month is clamped client-side.
   */
  monthBoundaryDay?: number;
}
