import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, EMPTY, Observable, filter, switchMap, take, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService, SKIP_AUTH } from './auth.service';

/**
 * On a 401 from a protected endpoint, calls /auth/refresh once and retries the original request.
 * A simple BehaviorSubject-based mutex serialises concurrent 401s so we mint exactly one new
 * access token even when N requests fail in parallel (ADR-F-004).
 *
 * Module-scoped state is fine here because interceptors are singleton functions and there is
 * one auth context per page.
 */
let isRefreshing = false;
const refreshSignal = new BehaviorSubject<string | null>(null);

export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  // Don't try to refresh on the very requests used to perform auth.
  if (req.context.get(SKIP_AUTH)) {
    return next(req);
  }

  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      // 401: attempt a single refresh, then retry.
      return refreshAndRetry(req, next, auth, router) as unknown as Observable<never>;
    })
  );
};

function refreshAndRetry(
  req: Parameters<HttpInterceptorFn>[0],
  next: Parameters<HttpInterceptorFn>[1],
  auth: AuthService,
  router: Router
) {
  if (isRefreshing) {
    // Wait for the in-flight refresh to finish, then retry with the fresh token.
    return refreshSignal.pipe(
      filter((token): token is string => token !== null),
      take(1),
      switchMap((token) =>
        next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
      )
    );
  }

  isRefreshing = true;
  refreshSignal.next(null);

  return auth.refresh().pipe(
    switchMap((newAccess) => {
      isRefreshing = false;
      refreshSignal.next(newAccess);
      return next(req.clone({ setHeaders: { Authorization: `Bearer ${newAccess}` } }));
    }),
    catchError((refreshErr) => {
      isRefreshing = false;
      refreshSignal.next(null);
      router.navigate(['/login']);
      return EMPTY.pipe(switchMap(() => throwError(() => refreshErr)));
    })
  );
}
