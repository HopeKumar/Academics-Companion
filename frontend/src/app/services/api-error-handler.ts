import { HttpEvent, HttpHandlerFn, HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

export function apiErrorInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMsg = '';
      if (error.error instanceof ErrorEvent) {
        errorMsg = `Client Error: ${error.error.message}`;
      } else {
        errorMsg = `Server Error Code: ${error.status}, Message: ${error.message}`;
        
        if (error.status === 404 && req.url.includes('/api/v1/podcasts/source/')) {
          // Special handling for podcast not generated yet
          return throwError(() => error);
        }

        if (error.status === 401 || error.status === 403) {
          console.error('Authentication Error:', errorMsg);
        } else if (error.status === 500) {
          console.error('Internal Server Error:', errorMsg);
        } else if (error.status === 502 || error.status === 503) {
          console.error('Service Unavailable:', errorMsg);
        } else {
          console.error('Unhandled API Error:', errorMsg);
        }
      }
      
      console.warn('API Error Intercepted:', errorMsg);
      return throwError(() => error);
    })
  );
}
