import { HttpInterceptorFn, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { tap, finalize } from 'rxjs/operators';
import { environment } from '../environments/environment'; // Assuming standard angular environment

export const diagnosticsInterceptor: HttpInterceptorFn = (req, next) => {
  const startTime = Date.now();
  let status: 'SUCCESS' | 'ERROR' = 'SUCCESS';
  let responseBody: any = null;
  let errorObj: any = null;

  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          responseBody = event.body;
        }
      },
      error: (error: HttpErrorResponse) => {
        status = 'ERROR';
        errorObj = error;
      }
    }),
    finalize(() => {
      const duration = Date.now() - startTime;
      const isApi = req.url.includes('/api/v1/');
      
      // We log even if environment.debug might not exist, but let's try to be safe.
      // We will check for window['__debug__'] or similar if environment fails, but usually environment.debug is better.
      // We log unconditionally here as per prompt "Add logging. For every feature".
      if (isApi) {
        console.groupCollapsed(`[Feature Request] ${req.method} ${req.url}`);
        console.log('Endpoint:', req.url);
        if (req.method === 'POST' || req.method === 'PUT') {
          console.log('Payload:', req.body);
        }
        if (status === 'SUCCESS') {
          console.log('Response:', responseBody);
        } else {
          console.error('Errors:', errorObj);
        }
        console.log('Duration:', `${duration}ms`);
        console.groupEnd();
      }
    })
  );
};
