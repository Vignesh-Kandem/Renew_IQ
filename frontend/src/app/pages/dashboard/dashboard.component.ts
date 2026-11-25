import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SubscriptionService } from '../../services/subscription.service';
import { AuthService } from '../../services/auth.service';
import { DashboardSummary, Subscription } from '../../models/subscription.model';
import { SubIconComponent } from '../../shared/sub-icon.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, SubIconComponent],
  template: `
    <div class="page-head">
      <div>
        <span class="eyebrow-label">Your overview</span>
        <h1>{{ greeting }}{{ firstName ? ', ' + firstName : '' }}</h1>
        <p class="muted">{{ today | date:'EEEE, d MMMM' }} · your recurring spend at a glance</p>
      </div>
      <a class="btn" routerLink="/subscriptions">+ Add subscription</a>
    </div>

    <a class="ai-banner" routerLink="/assistant">
      <span class="spark">✦</span>
      <div>
        <strong>Ask the AI assistant</strong>
        <div class="muted">“What will I pay this month?” · “Pause my cheapest subscription”</div>
      </div>
      <span class="go">Open →</span>
    </a>

    @if (summary) {
      <section class="stats">
        <div class="card stat">
          <div class="stat-top"><span class="muted">Monthly spend</span><span class="ico">₹</span></div>
          <h3 class="neon">{{ summary.monthlySpend | currency:'INR':'symbol':'1.0-0' }}</h3><p class="stat-note">Across your active plans</p>
        </div>
        <div class="card stat">
          <div class="stat-top"><span class="muted">Yearly projection</span><span class="ico">↗</span></div>
          <h3>{{ summary.yearlyProjection | currency:'INR':'symbol':'1.0-0' }}</h3><p class="stat-note">Estimated at current rates</p>
        </div>
        <div class="card stat">
          <div class="stat-top"><span class="muted">Active subscriptions</span><span class="ico">●</span></div>
          <h3>{{ summary.activeCount }}</h3><p class="stat-note">Currently active</p>
        </div>
        <div class="card stat">
          <div class="stat-top"><span class="muted">Due in 7 days</span><span class="ico">⏱</span></div>
          <h3>{{ summary.dueThisWeekCount }}
            <small>· {{ summary.dueThisWeekAmount | currency:'INR':'symbol':'1.0-0' }}</small>
          </h3><p class="stat-note">Upcoming renewals this week</p>
        </div>
      </section>
    } @else {
      <section class="stats">
        @for (i of [1,2,3,4]; track i) { <div class="card stat skeleton"></div> }
      </section>
    }

    <section class="grid">
      <div class="card">
        <div class="section-heading"><div><h2>Upcoming payments</h2><p class="muted">Your next 30 days, planned.</p></div><a routerLink="/subscriptions">View all →</a></div>
        @if (upcoming.length === 0) {
          <div class="empty-state">
            <span class="empty-ico">✓</span>
            <strong>All clear</strong>
            <p class="muted">Nothing is due in the next 30 days.</p>
          </div>
        }
        @for (s of upcoming; track s.id) {
          <div class="row">
            <div class="who">
              <app-sub-icon [name]="s.name" />
              <div>
                <strong>{{ s.name }}</strong>
                <div class="muted">{{ s.category }} · {{ s.nextRenewalDate | date:'dd MMM yyyy' }}</div>
              </div>
            </div>
            <div class="right">
              <strong>{{ s.amount | currency:'INR':'symbol':'1.0-0' }}</strong>
              <div class="due" [class.late]="daysLeft(s.nextRenewalDate) < 0"
                   [class.soon]="daysLeft(s.nextRenewalDate) >= 0 && daysLeft(s.nextRenewalDate) <= 3">
                {{ dueLabel(s.nextRenewalDate) }}
              </div>
            </div>
          </div>
        }
      </div>

      <div class="card">
        <div class="section-heading"><div><h2>Spending breakdown</h2><p class="muted">Monthly equivalents by category.</p></div></div>
        @if (summary && categories.length === 0) {
          <div class="empty-state">
            <span class="empty-ico">▦</span>
            <strong>No data yet</strong>
            <p class="muted">Add a subscription to see your breakdown.</p>
          </div>
        }
        @for (c of categories; track c.name) {
          <div class="bar-row">
            <div class="bar-label">
              <span>{{ c.name }}</span>
              <span class="muted">{{ c.value | currency:'INR':'symbol':'1.0-0' }}</span>
            </div>
            <div class="bar"><div class="fill" [style.width.%]="c.percent"></div></div>
          </div>
        }
      </div>
    </section>
  `,
  styles: [`
    .ai-banner {
      display: flex; align-items: center; gap: 14px; padding: 14px 18px; margin-bottom: 20px; text-decoration: none; color: var(--text);
      border-radius: 14px; border: 1px solid var(--moss-border);
      background: linear-gradient(100deg, rgba(var(--accent-rgb), 0.14), var(--surface) 70%); transition: border-color 0.2s;
    }
    .ai-banner:hover { border-color: var(--neon); }
    .ai-banner .spark {
      width: 38px; height: 38px; flex: none; display: grid; place-items: center; border-radius: 12px; font-size: 1.1rem;
      color: var(--neon); background: rgba(var(--accent-rgb), 0.16);
    }
    .ai-banner .muted { font-size: 0.8rem; margin-top: 2px; }
    .ai-banner .go { margin-left: auto; color: var(--neon); font-size: 0.85rem; font-weight: 600; white-space: nowrap; }
    a.btn { text-decoration: none; display: inline-block; }

    .who { display: flex; align-items: center; gap: 12px; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 20px; }
    .stat-top { display: flex; justify-content: space-between; align-items: center; }
    .stat .ico {
      width: 30px; height: 30px; display: grid; place-items: center; border-radius: 9px; font-size: 0.85rem;
      color: var(--neon); background: rgba(var(--accent-rgb), 0.12);
    }
    .stat h3 { font-size: clamp(1.6rem,2.3vw,2rem); letter-spacing:-.04em; font-variant-numeric:tabular-nums; margin-top: 10px; font-weight: 700; }
    .stat h3 small { font-size: 0.8rem; color: var(--muted); font-weight: 400; }
    .stat.skeleton { height: 108px; }
    .grid { display: grid; grid-template-columns: 1.3fr 1fr; gap: 16px; }
    @media (max-width: 950px) { .grid { grid-template-columns: 1fr; } .ai-banner .go { display: none; } }
    .row { display: flex; justify-content: space-between; align-items: center; padding: 14px 0; border-bottom: 1px solid var(--moss-border); }
    .row:last-child { border-bottom: none; }
    .right { text-align: right; }
    .due { font-size: 0.75rem; color: var(--moss); margin-top: 3px; }
    .due.soon { color: var(--warn); }
    .due.late { color: var(--danger); }
    .bar-row { margin-bottom: 16px; }
    .bar-label { display: flex; justify-content: space-between; font-size: 0.88rem; margin-bottom: 6px; }
    .bar { height: 8px; background: var(--surface-2); border-radius: 8px; overflow: hidden; }
    .fill { height: 100%; background: linear-gradient(90deg, var(--steel), var(--coral)); border-radius: 8px; box-shadow: 0 0 10px var(--neon-glow); }
    .stat{position:relative;overflow:hidden;transition:box-shadow .2s,transform .2s}.stat:hover{box-shadow:var(--hover-shadow);transform:translateY(-2px)}.stat:first-child{border-top:3px solid var(--coral)}.stat-note{font-size:.7rem;color:var(--muted);margin-top:8px}.stat-top .muted{font-size:.75rem}.section-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:20px}.section-heading h2{margin-bottom:4px}.section-heading p,.section-heading a{font-size:.72rem}.section-heading a{color:var(--neon);text-decoration:none;white-space:nowrap}.section-heading a:hover{text-decoration:underline}.grid .muted{font-size:.75rem}.row strong{font-size:.85rem}.bar-row{margin-bottom:22px}.bar{height:10px}.ai-banner{padding:18px 22px;border-radius:18px}.ai-banner strong{font-size:.9rem}
    @media(max-width:540px){.stats{grid-template-columns:1fr 1fr;gap:10px}.stat{padding:16px}.stat h3 small{display:block;font-size:.7rem}.ai-banner .muted{font-size:.72rem}.stat .ico{display:none}.ai-banner{padding:16px}.grid{gap:18px}}
  `],
})
export class DashboardComponent implements OnInit {
  private auth = inject(AuthService);

  summary?: DashboardSummary;
  upcoming: Subscription[] = [];
  today = new Date();

  constructor(private api: SubscriptionService) { }

  ngOnInit(): void {
    this.api.getSummary().subscribe((s) => (this.summary = s));
    this.api.getUpcoming(30).subscribe((u) => (this.upcoming = u));
  }

  get firstName(): string {
    return (this.auth.user()?.name ?? '').trim().split(' ')[0];
  }

  get greeting(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  }

  get categories(): { name: string; value: number; percent: number }[] {
    if (!this.summary) return [];
    const entries: [string, number][] = Object.entries(this.summary.categoryBreakdown)
      .map(([k, v]) => [k, v] as [string, number])
      .sort((a, b) => b[1] - a[1]);
    const max: number = entries.length ? entries[0][1] : 1;
    return entries.map(([name, value]) => ({ name, value, percent: (value / max) * 100 }));
  }

  daysLeft(date: string): number {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const due = new Date(date + 'T00:00:00');
    return Math.round((due.getTime() - today.getTime()) / 86400000);
  }

  dueLabel(date: string): string {
    const d = this.daysLeft(date);
    if (d < 0) return `Overdue by ${-d} day${d === -1 ? '' : 's'}`;
    if (d === 0) return 'Due today';
    return `in ${d} day${d === 1 ? '' : 's'}`;
  }
}
