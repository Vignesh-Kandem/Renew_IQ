import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private document = inject(DOCUMENT);
  readonly theme = signal<Theme>(this.load());

  constructor() { this.apply(); }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    this.apply();
    try { localStorage.setItem('renewiq_theme', theme); } catch { /* Preference still works for this visit. */ }
  }

  private load(): Theme {
    try { return localStorage.getItem('renewiq_theme') === 'light' ? 'light' : 'dark'; }
    catch { return 'dark'; }
  }

  private apply(): void {
    this.document.documentElement.dataset['theme'] = this.theme();
    this.document.documentElement.style.colorScheme = this.theme();
  }
}
