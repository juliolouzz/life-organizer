import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../api/api-response';
import { AuthService } from '../auth/auth.service';
import { AuthenticatedUser } from '../auth/authenticated-user';

export interface UpdateProfilePayload {
  displayName: string;
  /** Slice 13: BRL / USD / EUR. */
  currency?: 'BRL' | 'USD' | 'EUR';
  /** Slice 14: anchor day for the user's accounting month (1-31). */
  monthBoundaryDay?: number;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface ChangeEmailPayload {
  newEmail: string;
  currentPassword: string;
}

export interface DeleteAccountPayload {
  password: string;
}

export interface LogoutAllPayload {
  password: string;
}

export interface DeleteAccountResponse {
  deletionScheduledAt: string;
}

/**
 * Self-service /me/* endpoints (Slice 9). All require an authenticated session;
 * the AuthService refresh interceptor handles token renewal transparently.
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = environment.apiBaseUrl;

  updateProfile(payload: UpdateProfilePayload): Observable<AuthenticatedUser> {
    return this.http
      .patch<ApiResponse<AuthenticatedUser>>(`${this.base}/me`, payload)
      .pipe(
        map((res) => this.requireData(res)),
        // PATCH /me returns the new user state; push it straight into the
        // signal so the Money pipe + currency symbol update on the same
        // tick instead of waiting on a follow-up GET /me roundtrip.
        tap((user) => this.auth.setCurrentUser(user))
      );
  }

  changePassword(payload: ChangePasswordPayload): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/me/password`, payload)
      .pipe(map(() => undefined));
  }

  requestEmailChange(payload: ChangeEmailPayload): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/me/email`, payload)
      .pipe(map(() => undefined));
  }

  deleteAccount(payload: DeleteAccountPayload): Observable<DeleteAccountResponse> {
    return this.http
      .post<ApiResponse<DeleteAccountResponse>>(`${this.base}/me/delete`, payload)
      .pipe(map((res) => this.requireData(res)));
  }

  restoreAccount(): Observable<AuthenticatedUser> {
    return this.http
      .post<ApiResponse<AuthenticatedUser>>(`${this.base}/me/restore`, {})
      .pipe(
        map((res) => this.requireData(res)),
        // Same pattern as updateProfile: feed the new user back into the
        // signal so deletionScheduledAt clears immediately.
        tap((user) => this.auth.setCurrentUser(user))
      );
  }

  logoutAllSessions(payload: LogoutAllPayload): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/me/sessions/logout-all`, payload)
      .pipe(map(() => undefined));
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) {
      throw new Error(res.message ?? 'API returned no data');
    }
    return res.data;
  }
}
