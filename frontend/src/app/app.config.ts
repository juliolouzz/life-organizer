import { ApplicationConfig, importProvidersFrom, provideZoneChangeDetection } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { MatNativeDateModule } from '@angular/material/core';

import { APP_ROUTES } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { refreshInterceptor } from './core/auth/refresh.interceptor';
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
    importProvidersFrom(MatNativeDateModule)
  ]
};
