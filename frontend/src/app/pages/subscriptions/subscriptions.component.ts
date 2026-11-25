import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { SubscriptionService } from '../../services/subscription.service';
import { Subscription } from '../../models/subscription.model';
import { SubIconComponent } from '../../shared/sub-icon.component';

@Component({
  selector: 'app-subscriptions',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, SubIconComponent],
  template: `
    <div class="page-head">
      <div>
        <span class="eyebrow-label">Your collection</span><h1>Subscriptions</h1>
        <p class="muted">Manage every recurring payment in one place</p>
      </div>
      <button class="btn" (click)="openForm()">+ Add subscription</button>
    </div>

    <div class="card">
      <div class="toolbar">
        <input class="input" aria-label="Search subscriptions" placeholder="Search by name or category..." [(ngModel)]="search" />
        <select class="input" aria-label="Filter by status" [(ngModel)]="statusFilter">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="PAUSED">Paused</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      <div style="overflow-x:auto">
        <table class="table responsive-table" aria-label="Subscriptions">
          <thead>
            <tr>
              <th>Name</th><th>Category</th><th>Amount</th><th>Cycle</th>
              <th>Next renewal</th><th>Status</th><th><span class="muted">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            @for (s of filtered; track s.id) {
              <tr>
                <td class="primary-cell" data-label="Subscription">
                  <div class="name-cell">
                    <app-sub-icon [name]="s.name" />
                    <strong>{{ s.name }}</strong>
                  </div>
                </td>
                <td class="muted" data-label="Category">{{ s.category }}</td>
                <td data-label="Amount">{{ s.amount | currency:'INR':'symbol':'1.0-2' }}</td>
                <td class="muted" data-label="Cycle">{{ s.billingCycle }}</td>
                <td data-label="Next renewal">{{ s.nextRenewalDate | date:'dd MMM yyyy' }}</td>
                <td data-label="Status"><span class="badge" [ngClass]="s.status">{{ s.status }}</span></td>
                <td class="actions">
                  @if (canMarkPaid(s)) {
                    <button class="btn-ghost" [disabled]="paying.has(s.id!)" (click)="markPaid(s)" title="Record a payment and advance renewal">{{ paying.has(s.id!) ? 'Recording…' : 'Paid ✓' }}</button>
                  }
                  <button class="btn-ghost" data-click-sound="update" (click)="openForm(s)">Edit</button>
                  <button class="btn-ghost danger" data-click-sound="delete" (click)="remove(s)">Delete</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        @if (filtered.length === 0) { <p class="muted empty">No subscriptions found.</p> }
      </div>
    </div>

    @if (showForm) {
      <div class="overlay" (click)="closeForm()">
        <form class="card modal" role="dialog" aria-modal="true" aria-labelledby="subscription-form-title" [formGroup]="form" (ngSubmit)="save()" (click)="$event.stopPropagation()" (keydown.escape)="closeForm()">
          <h2 id="subscription-form-title">{{ editingId ? 'Edit subscription' : 'New subscription' }}</h2>

          <div class="field">
            <label>Name</label>
            <input class="input" formControlName="name" placeholder="e.g. Netflix" />
          </div>
          <div class="two">
            <div class="field">
              <label>Category</label>
              <input class="input" formControlName="category" placeholder="Entertainment" list="cats" />
              <datalist id="cats">
                <option>Entertainment</option><option>Music</option><option>Productivity</option>
                <option>Developer Tools</option><option>Health</option><option>Education</option>
                <option>Utilities</option>
              </datalist>
            </div>
            <div class="field">
              <label>Amount (₹)</label>
              <input class="input" type="number" min="0" formControlName="amount" />
            </div>
          </div>
          <div class="two">
            <div class="field">
              <label>Billing cycle</label>
              <select class="input" formControlName="billingCycle">
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
                <option value="QUARTERLY">Quarterly</option>
                <option value="YEARLY">Yearly</option>
              </select>
            </div>
            <div class="field">
              <label>Next renewal date</label>
              <input class="input" type="date" formControlName="nextRenewalDate" />
            </div>
          </div>
          <div class="field">
            <label>Status</label>
            <select class="input" formControlName="status">
              <option value="ACTIVE">Active</option>
              <option value="PAUSED">Paused</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          <div class="field">
            <label>Notes (optional)</label>
            <input class="input" formControlName="notes" />
          </div>

          <div class="footer">
            <button type="button" class="btn-ghost" data-click-sound="delete" (click)="closeForm()">Cancel</button>
            <button type="submit" class="btn" [attr.data-click-sound]="form.get('status')?.value === 'CANCELLED' ? 'delete' : editingId ? 'update' : 'default'" [disabled]="form.invalid">Save</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: [`
    .name-cell { display: flex; align-items: center; gap: 12px; }
    .toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
    .toolbar .input:first-child { flex: 1; }
    .toolbar select { width: 180px; }
    .actions { white-space: nowrap; text-align: right; }
    .actions button { margin-left: 6px; }
    .empty { text-align: center; padding: 30px; }
    .overlay {
      position: fixed; inset: 0; background: rgba(0,0,0,.75); backdrop-filter: blur(4px);
      display: grid; place-items: center; z-index: 50; padding: 16px;
    }
    .modal { width: 100%; max-width: 520px; max-height: 92vh; overflow-y: auto; border-color: rgba(var(--accent-rgb),.3); }
    .field { margin-bottom: 14px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; align-items: center; }
    .btn:disabled { opacity: .4; cursor: not-allowed; box-shadow: none; }
    input[type=date] { color-scheme: var(--color-scheme); }
    @media(max-width:540px){.toolbar{flex-direction:column}.toolbar select{width:100%}.two{grid-template-columns:1fr}}
  `],
})
export class SubscriptionsComponent implements OnInit {
  items: Subscription[] = [];
  search = '';
  statusFilter = '';
  showForm = false;
  editingId?: number;
  form: FormGroup;

  constructor(private api: SubscriptionService, private fb: FormBuilder) {
    this.form = this.fb.group({
      name: ['', Validators.required],
      category: ['', Validators.required],
      amount: [0, [Validators.required, Validators.min(0)]],
      billingCycle: ['MONTHLY', Validators.required],
      nextRenewalDate: ['', Validators.required],
      status: ['ACTIVE', Validators.required],
      notes: [''],
    });
  }

  ngOnInit(): void { this.load(); }

  load(): void { this.api.getAll().subscribe((d) => (this.items = d)); }

  get filtered(): Subscription[] {
    const q = this.search.toLowerCase().trim();
    return this.items.filter(
      (s) =>
        (!this.statusFilter || s.status === this.statusFilter) &&
        (!q || s.name.toLowerCase().includes(q) || s.category.toLowerCase().includes(q))
    );
  }

  openForm(s?: Subscription): void {
    this.editingId = s?.id;
    if (s) {
      this.form.patchValue({ ...s, notes: s.notes ?? '' });
    } else {
      const today = new Date().toISOString().substring(0, 10);
      this.form.reset({
        name: '', category: '', amount: 0, billingCycle: 'MONTHLY',
        nextRenewalDate: today, status: 'ACTIVE', notes: '',
      });
    }
    this.showForm = true;
  }

  closeForm(): void { this.showForm = false; }

  save(): void {
    if (this.form.invalid) return;
    const payload = this.form.getRawValue() as Subscription;
    const req = this.editingId
      ? this.api.update(this.editingId, payload)
      : this.api.create(payload);
    req.subscribe({
      next: () => { this.closeForm(); this.load(); },
      error: () => alert('Could not save. Please check the values and try again.'),
    });
  }

  canMarkPaid(s: Subscription): boolean {
    if (s.status !== 'ACTIVE') return false;
    // Compare calendar dates so time zones and daylight saving do not shift the window.
    const now = new Date();
    const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
    const renewal = Date.parse(`${s.nextRenewalDate}T00:00:00Z`);
    const daysUntilRenewal = (renewal - today) / 86_400_000;
    return daysUntilRenewal >= 0 && daysUntilRenewal <= 7;
  }

  markPaid(s: Subscription): void {
    if (this.paying.has(s.id!) || !this.canMarkPaid(s)) return;
    if (!confirm(`Record INR ${s.amount} as paid for "${s.name}" and advance its renewal date?`)) return;
    this.paying.add(s.id!);
    this.api.renew(s.id!, s.nextRenewalDate).subscribe({
      next: () => { this.paying.delete(s.id!); this.load(); },
      error: e => { this.paying.delete(s.id!); alert(e.error?.message ?? 'Could not record payment. Please refresh and try again.'); this.load(); },
    });
  }
  paying = new Set<number>();

  remove(s: Subscription): void {
    if (confirm(`Delete "${s.name}"?`)) {
      this.api.remove(s.id!).subscribe(() => this.load());
    }
  }
}
