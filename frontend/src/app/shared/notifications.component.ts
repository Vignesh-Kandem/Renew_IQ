import { Component, ElementRef, HostListener, OnDestroy, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Subscription, timer, switchMap, catchError, of, merge } from 'rxjs';
import { SubscriptionService } from '../services/subscription.service';

interface RenewalAlert {
  subscriptionId: number; name: string; amount: number; renewalDate: string; daysUntil: number;
}

@Component({
  selector: 'app-notifications', standalone: true, imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <button type="button" class="bell" aria-controls="renewal-alerts" [attr.aria-expanded]="open()"
      [attr.aria-label]="'Renewal alerts: ' + alerts().length + ' upcoming'" (click)="toggle()">
      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
      <span>Renewal alerts</span><strong aria-live="polite" aria-atomic="true">{{ alerts().length }}</strong>
    </button>
    @if (open()) {
      <section id="renewal-alerts" aria-label="Upcoming renewal alerts">
        <h2>Renewals this week</h2>
        @if (error()) {
          <p role="status">Could not load alerts.</p><button type="button" (click)="refresh()">Try again</button>
        } @else if (loading()) {
          <p role="status">Loading alerts…</p>
        } @else {
          @for (alert of alerts(); track alert.subscriptionId) {
            <a routerLink="/subscriptions" (click)="open.set(false)">
              <strong>{{ alert.name }}</strong>
              <span>{{ alert.daysUntil === 0 ? 'Renews today' : alert.daysUntil === 1 ? 'Renews tomorrow' : 'Renews in ' + alert.daysUntil + ' days' }}</span>
              <small>{{ alert.renewalDate | date:'mediumDate' }} · {{ alert.amount | currency:'INR' }}</small>
            </a>
          } @empty { <p>No active subscriptions renew in the next 7 days.</p> }
        }
      </section>
    }
  `,
  styles: [`
    :host{display:block;position:relative;margin-bottom:16px}
    button{cursor:pointer;background:var(--surface);color:var(--text);border:1px solid var(--moss-border);border-radius:10px;padding:10px 12px}
    .bell{display:flex;align-items:center;gap:9px;width:100%;font:inherit;font-size:.8rem}
    svg{width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
    .bell strong{margin-left:auto;color:var(--neon)}
    section{position:absolute;top:calc(100% + 8px);left:0;width:min(340px,calc(100vw - 32px));max-height:65vh;overflow:auto;z-index:40;padding:16px;background:var(--surface);color:var(--text);border:1px solid var(--moss-border);border-radius:14px;box-shadow:0 12px 32px #0003}
    h2{font-size:1rem;margin:0 0 12px}p{font-size:.8rem;line-height:1.5;color:var(--muted)}
    a{display:flex;flex-direction:column;gap:5px;text-decoration:none;color:var(--text);padding:12px 0;border-top:1px solid var(--moss-border)}
    a span{font-size:.8rem;color:var(--neon)}small{color:var(--muted);font-size:.72rem}
    @media(max-width:1024px){:host{margin:0}section{left:auto;right:0}.bell{width:44px;height:44px;padding:10px;position:relative}.bell>span{display:none}.bell strong{position:absolute;right:-5px;top:-5px;min-width:20px;border-radius:20px;background:var(--surface-2);font-size:.65rem}}
  `],
})
export class NotificationsComponent implements OnDestroy {
  private http = inject(HttpClient);
  private subscriptions = inject(SubscriptionService);
  private element = inject(ElementRef<HTMLElement>);
  open = signal(false);
  alerts = signal<RenewalAlert[]>([]);
  error = signal(false);
  loading = signal(true);
  private refreshRequest?: Subscription;
  private polling = merge(timer(0, 60000), this.subscriptions.changes).pipe(switchMap(() => this.request())).subscribe(result => this.apply(result));
  private request() {
    return this.http.get<RenewalAlert[]>('/api/notifications').pipe(catchError(() => of(null)));
  }
  private apply(result: RenewalAlert[] | null) {
    this.loading.set(false); this.error.set(result === null);
    this.alerts.set(result ?? []);
  }
  refresh() {
    this.refreshRequest?.unsubscribe();
    this.refreshRequest = this.request().subscribe(result => this.apply(result));
  }
  toggle() { this.open.update(value => !value); if (this.open()) this.refresh(); }
  @HostListener('document:keydown.escape') close() { this.open.set(false); }
  @HostListener('document:click', ['$event']) outside(event: MouseEvent) {
    if (!this.element.nativeElement.contains(event.target as Node)) this.open.set(false);
  }
  ngOnDestroy() { this.polling.unsubscribe(); this.refreshRequest?.unsubscribe(); }
}
