import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../api/api-response';
import { SKIP_AUTH } from './auth.service';

/** Anonymous Slice 8 endpoints — never carry an Authorization header. */
const skipAuth = () => new HttpContext().set(SKIP_AUTH, true);

@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  forgotPassword(email: string): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/auth/forgot-password`, { email }, { context: skipAuth() })
      .pipe(map(() => undefined));
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/auth/reset-password`, { token, newPassword }, { context: skipAuth() })
      .pipe(map(() => undefined));
  }

  verifyEmail(token: string): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/auth/verify-email`, { token }, { context: skipAuth() })
      .pipe(map(() => undefined));
  }

  resendVerification(email: string): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/auth/resend-verification`, { email }, { context: skipAuth() })
      .pipe(map(() => undefined));
  }
}
