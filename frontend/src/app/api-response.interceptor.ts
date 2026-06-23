import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { map } from 'rxjs/operators';

export const apiResponseInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    map(event => {
      if (event instanceof HttpResponse) {
        // If the backend returned a raw object or array, wrap it in ApiResponse
        // We check if the body exists and is an object, but doesn't have a 'success' property
        // (Assuming the backend never returns an object that happens to have a 'success' property natively
        // unless it's already an ApiResponse)
        
        // Also handle the case where body is an array (which doesn't have 'success')
        
        // Exclude blob responses
        if (event.body && !(event.body instanceof Blob)) {
          const bodyType = typeof event.body;
          if (bodyType === 'object') {
            if (event.body && typeof event.body === 'object' && !('success' in event.body)) {
              return event.clone({
                body: {
                  success: true,
                  data: event.body,
                  message: 'Success',
                  timestamp: Date.now()
                }
              });
            }
          } else {
             // If it's a primitive string/number returned directly, wrap it
              return event.clone({
                body: {
                  success: true,
                  data: event.body,
                  message: 'Success',
                  timestamp: Date.now()
                }
              });
          }
        }
      }
      return event;
    })
  );
};
