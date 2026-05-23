import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Surfaces non-401 server errors as a snackbar. 401 is consumed by refreshInterceptor.
 * Validation errors (400) are NOT toasted - the calling component reads the field map.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const status = error.status;
        // Don't snackbar 0 (request aborted), 401 (handled elsewhere), or 400 (form-level).
        if (status !== 0 && status !== 401 && status !== 400) {
          const message = pickMessage(error);
          snackBar.open(message, 'Dismiss', { duration: 5000, panelClass: 'snackbar-error' });
        } else if (status === 0) {
          snackBar.open('Cannot reach server', 'Dismiss', {
            duration: 5000,
            panelClass: 'snackbar-error'
          });
        }
      }
      return throwError(() => error);
    })
  );
};

function pickMessage(err: HttpErrorResponse): string {
  const body = err.error as { message?: string } | null;
  if (body?.message) return body.message;
  return `Request failed (${err.status})`;
}
