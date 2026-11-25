import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

interface Payment {
  id: number; subscriptionName: string; category: string; amount: number; currency: string;
  billingCycle: string; renewalDate: string; recordedAt: string;
}
interface History { items: Payment[]; page: number; totalPages: number; totalElements: number; }

@Component({
  selector: 'app-history', standalone: true, imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <div class="page-head"><div><span class="eyebrow-label">Your payments</span><h1>History</h1>
      <p class="muted">Payments you recorded when marking a subscription paid.</p></div></div>
    <div class="card">
      @if (deleteError()) { <p role="alert">{{ deleteError() }}</p> }
      @if (loading()) { <p role="status">Loading payments…</p> }
      @else if (error()) { <p role="alert">Could not load payment history.</p><button class="btn-ghost" (click)="load(page())">Try again</button> }
      @else { @if (history(); as data) {
        @if (data.items.length) {
          <p class="muted">{{ data.totalElements }} recorded {{ data.totalElements === 1 ? 'payment' : 'payments' }} · Most recent first</p>
          <div class="table-wrap"><table class="table responsive-table" aria-label="Payment history">
            <thead><tr><th scope="col">Recorded on</th><th scope="col">Subscription</th><th scope="col">Category</th><th scope="col">Renewal covered</th><th scope="col">Cycle</th><th scope="col">Amount paid</th><th scope="col">Actions</th></tr></thead>
            <tbody>@for (payment of data.items; track payment.id) {
              <tr><td data-label="Recorded on">{{ payment.recordedAt | date:'medium' }}</td><td data-label="Subscription"><strong>{{ payment.subscriptionName }}</strong></td>
                <td class="muted" data-label="Category">{{ payment.category }}</td><td data-label="Renewal covered">{{ payment.renewalDate | date:'dd MMM yyyy' }}</td>
                <td class="muted" data-label="Cycle">{{ payment.billingCycle }}</td><td data-label="Amount paid">{{ payment.amount | currency:payment.currency:'symbol':'1.2-2' }}</td>
                <td class="actions"><button type="button" class="btn-ghost danger" data-click-sound="delete" [disabled]="deleting() !== null"
                  [attr.aria-label]="'Delete payment for ' + payment.subscriptionName" (click)="remove(payment)">{{ deleting() === payment.id ? 'Deleting…' : 'Delete' }}</button></td></tr>
            }</tbody>
          </table></div>
        } @else { <p>No payments recorded yet.</p><p class="muted">After you pay a subscription, use “Paid ✓” on the Subscriptions page to record it here.</p><a routerLink="/subscriptions">Go to subscriptions →</a> }
        @if (data.totalPages > 1) {
          <nav aria-label="Payment history pages"><button class="btn-ghost" [disabled]="data.page === 0" (click)="load(data.page - 1)">Previous</button>
            <span>Page {{ data.page + 1 }} of {{ data.totalPages }}</span>
            <button class="btn-ghost" [disabled]="data.page + 1 >= data.totalPages" (click)="load(data.page + 1)">Next</button></nav>
        }
      } }
    </div>
  `,
  styles: [`
    .table-wrap{overflow-x:auto}nav{display:flex;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:14px;margin-top:18px;font-size:.8rem}a{color:var(--neon)}.card>p{line-height:1.6}
  `],
})
export class HistoryComponent implements OnInit {
  private http = inject(HttpClient);
  loading = signal(true); error = signal(false); page = signal(0); history = signal<History | null>(null);
  deleting = signal<number | null>(null); deleteError = signal('');
  remove(payment: Payment) {
    if (this.deleting() !== null || !confirm(`Permanently delete this payment record for "${payment.subscriptionName}"? This cannot be undone. The subscription's renewal date will stay unchanged.`)) return;
    this.deleteError.set(''); this.deleting.set(payment.id);
    this.http.delete('/api/payments/' + payment.id).subscribe({
      next: () => { this.deleting.set(null); this.load(this.page()); },
      error: () => { this.deleting.set(null); this.deleteError.set('Could not delete the payment. Please try again.'); },
    });
  }
  ngOnInit() { this.load(0); }
  load(page: number) {
    this.page.set(page); this.loading.set(true); this.error.set(false);
    this.http.get<History>('/api/payments', { params: { page } }).subscribe({
      next: history => {
        if (!history.items.length && page > 0) { this.load(Math.max(0, history.totalPages - 1)); return; }
        this.history.set(history); this.loading.set(false);
      },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }
}
