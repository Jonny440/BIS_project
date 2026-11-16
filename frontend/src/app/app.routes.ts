import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'schedule',
    canActivate: [authGuard],
    loadComponent: () => import('./schedule/schedule').then((m) => m.Schedule),
  },
  {
    path: 'register',
    canActivate: [authGuard],
    loadComponent: () => import('./register/register').then((m) => m.Register),
  },
  {
    path: 'grades',
    canActivate: [authGuard],
    loadComponent: () => import('./grades/grades').then((m) => m.Grades),
  },
  {
    path: 'transcript',
    canActivate: [authGuard],
    loadComponent: () => import('./transcript/transcript').then((m) => m.Transcript),
  },
  {
    path: 'attendance',
    canActivate: [authGuard],
    loadComponent: () => import('./attendance/attendance').then((m) => m.Attendance),
  },
  {
    path: 'library',
    canActivate: [authGuard],
    loadComponent: () => import('./library/library').then((m) => m.Library),
  },
  {
    path: 'messages',
    canActivate: [authGuard],
    loadComponent: () => import('./messages/messages').then((m) => m.Messages),
  },
  { path: '**', redirectTo: 'schedule' },
];
