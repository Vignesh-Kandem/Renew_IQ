import { Component, Input } from '@angular/core';

/** keyword (lowercase, no spaces) -> Simple Icons slug. Add your own here. */
const ICON_MAP: Record<string, string> = {
    jiohotstar: 'jiohotstar', hotstar: 'jiohotstar', jiosaavn: 'jiosaavn', jio: 'jio',
    netflix: 'netflix', spotify: 'spotify', youtube: 'youtube', primevideo: 'primevideo',
    amazonprime: 'primevideo', prime: 'primevideo', disney: 'disneyplus', sonyliv: 'sonyliv',
    zee5: 'zee5', applemusic: 'applemusic', icloud: 'icloud', apple: 'apple',
    githubcopilot: 'githubcopilot', github: 'github', notion: 'notion', chatgpt: 'openai',
    openai: 'openai', claude: 'anthropic', canva: 'canva', figma: 'figma', adobe: 'adobe',
    microsoft: 'microsoft', google: 'google', dropbox: 'dropbox', slack: 'slack',
    zoom: 'zoom', discord: 'discord', linkedin: 'linkedin', duolingo: 'duolingo',
    zomato: 'zomato', swiggy: 'swiggy', airtel: 'airtel', vercel: 'vercel',
};

// Match longer keywords first so "jiohotstar" wins over "jio"
const KEYS = Object.keys(ICON_MAP).sort((a, b) => b.length - a.length);

@Component({
    selector: 'app-sub-icon',
    standalone: true,
    template: `
    <span class="ico" [class.branded]="url && !failed" [style.width.px]="size" [style.height.px]="size">
      @if (url && !failed) {
        <img [src]="url" [alt]="name" loading="lazy" decoding="async" referrerpolicy="no-referrer" (error)="failed = true" />
      } @else {
        <span class="letter">{{ initial }}</span>
      }
    </span>
  `,
    styles: [`
    .ico {
      display: inline-grid; place-items: center; flex-shrink: 0;
      background: var(--surface-2); border: 1px solid var(--moss-border);
      border-radius: 10px; overflow: hidden;
    }
    /* Native brand colors stay independent of the application theme.
       A neutral light tile also keeps black brand marks legible in dark mode. */
    .ico.branded { background: #ffffff; border-color: #dce1e7; box-shadow: 0 1px 3px rgba(20,24,30,.06); }
    img { display: block; width: 62%; height: 62%; object-fit: contain; }
    .letter { color: var(--neon); font-weight: 700; font-size: 0.95rem; }
  `],
})
export class SubIconComponent {
    @Input() size = 36;
    failed = false;

    private _name = '';
    @Input() set name(v: string) { this._name = v ?? ''; this.failed = false; }
    get name(): string { return this._name; }

    get initial(): string { return (this._name.trim()[0] ?? '?').toUpperCase(); }

    get url(): string | null {
        const n = this._name.toLowerCase().replace(/[^a-z0-9]/g, '');
        const key = KEYS.find((k) => n.includes(k));
        return key ? `https://cdn.simpleicons.org/${ICON_MAP[key]}` : null;
    }
}
