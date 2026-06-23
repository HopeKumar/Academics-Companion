import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './auth.interceptor';
import { apiErrorInterceptor } from './services/api-error-handler';
import { diagnosticsInterceptor } from './diagnostics.interceptor';
import { apiResponseInterceptor } from './api-response.interceptor';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiResponseInterceptor, diagnosticsInterceptor, authInterceptor, apiErrorInterceptor]))
  ]
};
