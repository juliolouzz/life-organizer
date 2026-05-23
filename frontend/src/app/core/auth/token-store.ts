import { Injectable } from '@angular/core';

/**
 * localStorage wrapper for the refresh token only. The access token is held in
 * memory by AuthService; persisting it would widen the XSS exposure window for
 * a short-TTL credential we can cheaply re-mint.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private static readonly KEY = 'life-organizer.refresh-token';

  readRefresh(): string | null {
    try {
      return window.localStorage.getItem(TokenStore.KEY);
    } catch {
      return null;
    }
  }

  writeRefresh(token: string): void {
    try {
      window.localStorage.setItem(TokenStore.KEY, token);
    } catch {
      /* localStorage unavailable - ignore; the user will need to re-login next session. */
    }
  }

  clear(): void {
    try {
      window.localStorage.removeItem(TokenStore.KEY);
    } catch {
      /* nothing we can do */
    }
  }
}
