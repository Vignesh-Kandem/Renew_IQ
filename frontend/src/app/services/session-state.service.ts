import { Injectable, signal } from '@angular/core';
export interface AppUser { id: number; name: string; email: string; }
@Injectable({ providedIn: 'root' })
export class SessionState {
  readonly user = signal<AppUser | null>(null);
  csrf = '';
}
