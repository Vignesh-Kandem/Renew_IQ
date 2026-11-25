import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { SessionState } from './session-state.service';
export const sessionInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) return next(req);
  const state = inject(SessionState);
  const router = inject(Router);
  const mutation = !['GET', 'HEAD', 'OPTIONS'].includes(req.method);
  const secured = req.clone({ withCredentials: true,
    ...(mutation && state.csrf ? { setHeaders: { 'X-CSRF-TOKEN': state.csrf } } : {}) });
  return next(secured).pipe(catchError((error: HttpErrorResponse) => {
    if (error.status === 401 && !req.url.includes('/auth/')) {
      state.user.set(null);
      void router.navigate(['/login']);
    }
    return throwError(() => error);
  }));
};
