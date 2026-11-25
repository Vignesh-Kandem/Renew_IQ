import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card card">
        <div class="brand">
          <span class="logo">R</span>
          <strong>Renew<span class="neon">IQ</span></strong>
        </div>
        <h2>Welcome back</h2>
        <p class="muted">Sign in to manage your subscriptions</p>

        @if (error) { <div class="err">{{ error }}</div> }

        <form (ngSubmit)="onSubmit()">
          <div class="field">
            <label for="login-email">Email</label>
            <input id="login-email" autocomplete="email" class="input" type="email" [(ngModel)]="email" name="email"
                   placeholder="you@example.com" required />
          </div>
          <div class="field">
            <label for="login-password">Password</label>
            <input id="login-password" autocomplete="current-password" class="input" type="password" [(ngModel)]="password" name="password"
                   placeholder="••••••••" required />
          </div>
          <button class="btn full" type="submit" [disabled]="busy || !email || !password">{{ busy ? 'Signing in…' : 'Sign in' }}</button>
        </form>

        <p class="alt muted">Don't have an account? <a routerLink="/signup" class="link">Sign up</a></p>
      </div>
    </div>
  `,
  styles: [`
    .auth-page {
      display: grid; place-items: center; min-height: 100vh;
      background: var(--jet);
      background-image: radial-gradient(900px 500px at 50% 0%, rgba(var(--accent-rgb),.07), transparent 60%);
      padding: 20px;
    }
    .auth-card { width: 100%; max-width: 420px; text-align: center; padding: 36px; }
    .brand { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 24px; }
    .brand strong { font-size: 1.3rem; }
    .logo {
      width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center;
      background: var(--button-bg); color: var(--on-accent); font-weight: 800; box-shadow: 0 0 14px var(--neon-glow);
    }
    h2 { font-size: 1.3rem; margin-bottom: 4px; }
    .field { margin-top: 16px; text-align: left; }
    .full { width: 100%; margin-top: 20px; }
    .alt { margin-top: 18px; font-size: 0.85rem; }
    .link { color: var(--neon); text-decoration: none; }
    .link:hover { text-decoration: underline; }
    .err { background: rgba(255,92,108,.1); color: var(--danger); padding: 10px; border-radius: 10px; margin-top: 14px; font-size: 0.85rem; }
  `],
})
export class LoginComponent {
  email = '';
  password = '';
  error = '';
  busy = false;

  constructor(private auth: AuthService, private router: Router) {}

  async onSubmit(): Promise<void> {
    if (this.busy || !this.email || !this.password) return;
    this.busy = true; this.error = '';
    try {
      await this.auth.login(this.email, this.password);
      await this.router.navigate(['/dashboard']);
    } catch (e) { this.error = this.auth.errorMessage(e); }
    finally { this.busy = false; }
  }
}

