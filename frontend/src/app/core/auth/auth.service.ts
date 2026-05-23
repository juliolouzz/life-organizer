import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of, tap, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../api/api-response';
import { AuthenticatedUser } from './authenticated-user';
import { TokenStore } from './token-store';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  displayName: string;
  /** Slice 13: BRL (default) / USD / EUR. */
  currency?: 'BRL' | 'USD' | 'EUR';
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

/** Marker context flag used by the auth interceptor to skip token attachment. */
export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);
export const skipAuth = (): HttpContext => new HttpContext().set(SKIP_AUTH, true);

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenStore = inject(TokenStore);
  private readonly base = environment.apiBaseUrl;

  // accessToken kept in memory only (ADR-F-003); refreshToken mirrored to localStorage.
  private readonly accessTokenSig = signal<string | null>(null);
  private readonly refreshTokenSig = signal<string | null>(this.tokenStore.readRefresh());
  private readonly currentUserSig = signal<AuthenticatedUser | null>(null);

  readonly accessToken = this.accessTokenSig.asReadonly();
  readonly currentUser = this.currentUserSig.asReadonly();
  readonly isAuthenticated = computed(() => this.accessTokenSig() !== null);

  hasRefreshToken(): boolean {
    return this.refreshTokenSig() !== null;
  }

  login(payload: LoginPayload): Observable<AuthenticatedUser> {
    return this.http
      .post<ApiResponse<AuthTokens>>(`${this.base}/auth/login`, payload, { context: skipAuth() })
      .pipe(
        map((res) => this.requireData(res)),
        tap((tokens) => this.storeTokens(tokens)),
        switchMap(() => this.fetchMe())
      );
  }

  register(payload: RegisterPayload): Observable<AuthenticatedUser> {
    return this.http
      .post<ApiResponse<AuthenticatedUser>>(`${this.base}/auth/register`, payload, {
        context: skipAuth()
      })
      .pipe(
        map((res) => this.requireData(res)),
        // Auto-login: backend doesn't return tokens from register.
        switchMap(() => this.login({ email: payload.email, password: payload.password }))
      );
  }

  refresh(): Observable<string> {
    const refreshToken = this.refreshTokenSig();
    if (!refreshToken) {
      return throwError(() => new Error('no refresh token available'));
    }
    return this.http
      .post<ApiResponse<AccessTokenResponse>>(
        `${this.base}/auth/refresh`,
        { refreshToken },
        { context: skipAuth() }
      )
      .pipe(
        map((res) => this.requireData(res).accessToken),
        tap((access) => this.accessTokenSig.set(access)),
        catchError((err) => {
          this.clearTokens();
          return throwError(() => err);
        })
      );
  }

  fetchMe(): Observable<AuthenticatedUser> {
    return this.http
      .get<ApiResponse<AuthenticatedUser>>(`${this.base}/me`)
      .pipe(
        map((res) => this.requireData(res)),
        tap((user) => this.currentUserSig.set(user))
      );
  }

  /** Called on app boot. If we have a refresh token, silently mint a new access + load /me. */
  bootstrap(): Observable<AuthenticatedUser | null> {
    if (!this.hasRefreshToken()) {
      return of(null);
    }
    return this.refresh().pipe(
      switchMap(() => this.fetchMe()),
      catchError(() => of(null))
    );
  }

  logout(): void {
    this.clearTokens();
  }

  private storeTokens(tokens: AuthTokens): void {
    this.accessTokenSig.set(tokens.accessToken);
    this.refreshTokenSig.set(tokens.refreshToken);
    this.tokenStore.writeRefresh(tokens.refreshToken);
  }

  private clearTokens(): void {
    this.accessTokenSig.set(null);
    this.refreshTokenSig.set(null);
    this.currentUserSig.set(null);
    this.tokenStore.clear();
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) {
      throw new Error(res.message ?? 'API returned no data');
    }
    return res.data;
  }
}
