import { Component, ElementRef, HostListener, ViewChild, inject, signal } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { ClickSoundService } from '../services/click-sound.service';
import { ThemeService } from '../services/theme.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  template: `
    @if (open()) {
      <section id="app-settings-panel" class="settings-panel" aria-label="Settings">
        <h2>Settings</h2>
        <div class="preference">
          <strong id="sound-label">Interface sounds</strong>
          <div class="toggle-control">
            <button type="button" class="toggle" role="switch" data-click-sound="none"
              aria-labelledby="sound-label" [attr.aria-checked]="sound.enabled()"
              (click)="setSound(!sound.enabled())"><span class="toggle-thumb"></span></button>
          </div>
        </div>
        <div class="preference">
          <strong id="theme-label">Dark mode</strong>
          <div class="toggle-control">
            <button type="button" class="toggle" role="switch" aria-labelledby="theme-label"
              [attr.aria-checked]="theme.theme() === 'dark'"
              (click)="theme.setTheme(theme.theme() === 'dark' ? 'light' : 'dark')"><span class="toggle-thumb"></span></button>
          </div>
        </div>
        @if (auth.isLoggedIn()) {
          <div class="account">
            <p class="muted">{{ auth.user()?.email }}</p>
            <button type="button" class="logout" (click)="logout()">Sign out / Logout</button>
          </div>
        }
      </section>
    }
    <button #trigger type="button" class="settings-trigger" aria-controls="app-settings-panel"
            [attr.aria-expanded]="open()" (click)="open.set(!open())">
      <span aria-hidden="true">⚙</span> Settings <span class="chevron" aria-hidden="true">{{ open() ? '⌄' : '⌃' }}</span>
    </button>
  `,
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  auth = inject(AuthService);
  sound = inject(ClickSoundService);
  theme = inject(ThemeService);
  private element = inject(ElementRef<HTMLElement>);
  @ViewChild('trigger') trigger?: ElementRef<HTMLButtonElement>;
  open = signal(false);

  setSound(enabled: boolean): void {
    if (this.sound.enabled() !== enabled) this.sound.toggle();
  }

  async logout(): Promise<void> {
    this.open.set(false);
    await this.auth.logout();
  }

  @HostListener('document:click', ['$event'])
  outside(event: MouseEvent): void {
    if (event.target instanceof Node && !this.element.nativeElement.contains(event.target)) this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  escape(): void {
    if (this.open()) {
      this.open.set(false);
      this.trigger?.nativeElement.focus();
    }
  }
}
