import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Lets authenticated users through. Anonymous users are sent to /login.
 *
 * The path the user was actually trying to reach is preserved as a
 * `returnUrl` query param (except when the target IS /login itself, to
 * avoid a self-loop). The login page reads that param and navigates
 * there on a successful sign-in.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  const queryParams =
    state.url && state.url !== '/login' ? { returnUrl: state.url } : undefined;
  return router.createUrlTree(['/login'], { queryParams });
};
