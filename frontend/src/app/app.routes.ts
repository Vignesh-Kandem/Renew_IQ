import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { SubscriptionsComponent } from './pages/subscriptions/subscriptions.component';
import { LoginComponent } from './pages/login/login.component';
import { SignupComponent } from './pages/signup/signup.component';
import { AssistantComponent } from './pages/assistant/assistant.component';
import { authGuard, guestGuard } from './guards/auth.guard';
import { HistoryComponent } from './pages/history/history.component';

export const routes: Routes = [
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
    { path: 'signup', component: SignupComponent, canActivate: [guestGuard] },
    { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
    { path: 'subscriptions', component: SubscriptionsComponent, canActivate: [authGuard] },
    { path: 'history', component: HistoryComponent, canActivate: [authGuard] },
    { path: 'assistant', component: AssistantComponent, canActivate: [authGuard] },
    { path: '**', redirectTo: 'dashboard' },
];
