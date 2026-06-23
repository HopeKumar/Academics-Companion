import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('accessToken');
  
  // Only intercept requests to our backend (relative or matching localhost:8080)
  if (token && (req.url.startsWith('/api/') || req.url.startsWith('http://localhost:8080') || req.url.startsWith('/auth/'))) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(cloned);
  } else if (!token && (req.url.startsWith('/api/') || req.url.startsWith('http://localhost:8080'))) {
    console.warn('Missing auth token for API request:', req.url);
  }
  
  return next(req);
};
