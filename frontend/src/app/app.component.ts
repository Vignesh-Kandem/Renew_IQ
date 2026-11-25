import { Component, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';
import { SettingsComponent } from './shared/settings.component';
import { NotificationsComponent } from './shared/notifications.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, SettingsComponent, NotificationsComponent],
  template: `
    @if (auth.isLoggedIn()) {
      <a class="skip-link" href="#main-content" (click)="skipToContent($event)">Skip to content</a>
      <div class="shell">
        <aside class="sidebar">
          <div class="brand">
            <img class="logo-img" src="logo-mark.svg" alt="RenewIQ logo" />
            <div>
              <strong>Renew<span class="neon">IQ</span></strong>
              <small>Subscription Monitor</small>
            </div>
          </div>
          <app-notifications />
          <nav aria-label="Main navigation">
            <span class="nav-label">WORKSPACE</span>
            <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page"><span class="nav-icon" aria-hidden="true">▦</span> Dashboard</a>
            <a routerLink="/subscriptions" routerLinkActive="active" ariaCurrentWhenActive="page"><span class="nav-icon" aria-hidden="true">↻</span> Subscriptions</a>
            <a routerLink="/history" routerLinkActive="active" ariaCurrentWhenActive="page"><span class="nav-icon" aria-hidden="true">◷</span> History</a>
            <a routerLink="/assistant" routerLinkActive="active" ariaCurrentWhenActive="page"><span class="nav-icon" aria-hidden="true">✦</span> AI Assistant</a>
          </nav>
          <div class="foot">
            <div class="user-info muted"><span class="account-label">YOUR ACCOUNT</span><strong>{{ auth.user()?.name }}</strong></div>
          </div>
        </aside>
        <main id="main-content" class="content" tabindex="-1"><router-outlet /></main>
      </div>
    } @else {
      <router-outlet />
    }
    <app-settings />
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private document = inject(DOCUMENT);
  auth = inject(AuthService);
  skipToContent(event: Event) { event.preventDefault(); this.document.getElementById('main-content')?.focus(); }
}
