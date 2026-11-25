import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, tap } from 'rxjs';
import { DashboardSummary, Subscription } from '../models/subscription.model';

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly changed = new Subject<void>();
  readonly changes = this.changed.asObservable();
  private readonly base = '/api';

  constructor(private http: HttpClient) { }

  getAll(): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.base}/subscriptions`);
  }
  getUpcoming(days = 30): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.base}/subscriptions/upcoming?days=${days}`);
  }
  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/dashboard/summary`);
  }
  create(s: Subscription): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.base}/subscriptions`, s).pipe(tap(() => this.changed.next()));
  }
  update(id: number, s: Subscription): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.base}/subscriptions/${id}`, s).pipe(tap(() => this.changed.next()));
  }
  renew(id: number, expectedRenewalDate: string): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.base}/subscriptions/${id}/renew`, { expectedRenewalDate }).pipe(tap(() => this.changed.next()));
  }
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/subscriptions/${id}`).pipe(tap(() => this.changed.next()));
  }
}
