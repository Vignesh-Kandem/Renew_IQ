import { DOCUMENT } from '@angular/common';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';

export type ClickSound = 'default' | 'delete' | 'update';

/** Bundled Pixabay click by DenielCZ; see public/sounds/README.md. */
@Injectable({ providedIn: 'root' })
export class ClickSoundService implements OnDestroy {
  private readonly document = inject(DOCUMENT);
  private readonly storageKey = 'renewiq_click_sound';
  readonly enabled = signal(this.loadPreference());
  private readonly audio = this.document.createElement('audio');
  private lastClick = -Infinity;
  private destroyed = false;
  private readonly onClick = (event: MouseEvent): void => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const control = target.closest<HTMLElement>('button, a[href], [role="button"]');
    if (!control || control.matches(':disabled, [aria-disabled="true"]')) return;
    const kind = control.dataset['clickSound'];
    if (kind === 'none') return;
    // Capture before Angular handlers can remove a control or stop propagation.
    void this.play(kind === 'delete' || kind === 'update' ? kind : 'default');
  };

  constructor() {
    this.audio.src = new URL('sounds/immersivecontrol-button-click.mp3', this.document.baseURI).href;
    this.audio.preload = 'auto';
    this.audio.volume = 0.5;
    this.audio.load();
    this.document.addEventListener('click', this.onClick, true);
  }

  toggle(): void {
    this.enabled.update(value => !value);
    try { localStorage.setItem(this.storageKey, this.enabled() ? 'on' : 'off'); } catch { /* Storage can be blocked. */ }
    if (this.enabled()) {
      void this.play('default');
    } else {
      this.audio.pause();
      this.audio.currentTime = 0;
    }
  }

  private loadPreference(): boolean {
    try { return localStorage.getItem(this.storageKey) !== 'off'; } catch { return true; }
  }

  async play(_kind: ClickSound): Promise<void> {
    if (!this.enabled() || this.destroyed) return;
    const clickedAt = performance.now();
    if (clickedAt - this.lastClick < 45) return;
    this.lastClick = clickedAt;
    try {
      // Reuse one player so rapid clicks never stack into a loud cluster.
      this.audio.currentTime = 0;
      await this.audio.play();
      if (!this.enabled() || this.destroyed) this.audio.pause();
    } catch { /* Audio availability must never prevent the button action. */ }
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.document.removeEventListener('click', this.onClick, true);
    this.audio.pause();
    this.audio.removeAttribute('src');
    this.audio.load();
  }
}
