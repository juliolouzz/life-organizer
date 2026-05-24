import {
  APP_INITIALIZER,
  ApplicationConfig,
  importProvidersFrom,
  inject,
  provideZoneChangeDetection
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { MatNativeDateModule } from '@angular/material/core';
import { firstValueFrom } from 'rxjs';

import { APP_ROUTES } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { refreshInterceptor } from './core/auth/refresh.interceptor';
import { AuthService } from './core/auth/auth.service';
import { errorInterceptor } from './core/http/error.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(APP_ROUTES, withComponentInputBinding()),
    provideHttpClient(
      // Order matters: auth attaches the token; refresh handles 401 retries;
      // error surfaces remaining failures; loading toggles the global progress bar.
      withInterceptors([authInterceptor, refreshInterceptor, errorInterceptor, loadingInterceptor])
    ),
    provideAnimationsAsync(),
    importProvidersFrom(MatNativeDateModule),
    // Hard reload / direct navigation used to log the user out: the access
    // token lives in memory only, so a full page load lost it and the auth
    // guard immediately bounced to /login even though a valid refresh token
    // was sitting in localStorage. This initializer silently swaps that
    // refresh token for a new access token + /me on app boot so the guard
    // sees an authenticated session by the time it runs.
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const auth = inject(AuthService);
        return () => firstValueFrom(auth.bootstrap()).catch(() => null);
      }
    }
  ]
};
