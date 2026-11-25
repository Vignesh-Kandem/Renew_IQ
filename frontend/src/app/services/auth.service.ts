import { Injectable, computed, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AppUser, SessionState } from './session-state.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private state = inject(SessionState);
  readonly user = this.state.user;
  readonly isLoggedIn = computed(() => this.user() !== null);
  private readonly base = '/api/auth';
  constructor(private http: HttpClient, private router: Router) {
    localStorage.removeItem('renewiq_user');
  }
  async refreshCsrf(): Promise<void> {
    const response = await firstValueFrom(this.http.get<{token: string}>(`${this.base}/csrf`));
    this.state.csrf = response.token;
  }
  async restore(): Promise<void> {
    try {
      await this.refreshCsrf();
      this.user.set(await firstValueFrom(this.http.get<AppUser>(`${this.base}/me`)));
    } catch { this.user.set(null); }
  }
  async login(email: string, password: string): Promise<void> {
    await this.authenticate('login', { email: email.trim(), password });
  }
  async signup(name: string, email: string, password: string): Promise<void> {
    await this.authenticate('signup', { name: name.trim(), email: email.trim(), password });
  }
  private async authenticate(path: string, body: object): Promise<void> {
    await this.refreshCsrf();
    const user = await firstValueFrom(this.http.post<AppUser>(`${this.base}/${path}`, body));
    await this.refreshCsrf();
    this.user.set(user);
  }
  async logout(): Promise<void> {
    try {
      await this.refreshCsrf();
      await firstValueFrom(this.http.post<void>(`${this.base}/logout`, {}));
      this.user.set(null);
      this.state.csrf = '';
      await this.router.navigate(['/login']);
    } catch (e) {
      window.alert(this.errorMessage(e));
    }
  }
  errorMessage(error: unknown): string {
    const e = error as HttpErrorResponse;
    return e.status === 0 ? 'Cannot reach the server. Check your connection and try again.'
      : e.error?.message ?? 'The request failed. Please try again.';
  }
}
