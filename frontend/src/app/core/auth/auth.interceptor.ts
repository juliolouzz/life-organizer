import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService, SKIP_AUTH } from './auth.service';

/**
 * Attaches the in-memory access token as Authorization: Bearer <token>.
 * Requests issued with skipAuth() context (login, register, refresh) are passed through.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_AUTH)) {
    return next(req);
  }

  const token = inject(AuthService).accessToken();
  if (!token) {
    return next(req);
  }

  const authed = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  return next(authed);
};
