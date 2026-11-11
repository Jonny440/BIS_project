import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { toApiError } from './api';

export type UserRole = 'STUDENT' | 'TEACHER' | 'MANAGER' | 'ADMIN';

export interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  role: UserRole;
  program: string | null;
  year: number | null;
  gpa: number | null;
  department: string | null;
  title: string | null;
}

interface Session {
  token: string;
  user: AuthUser;
}

const STORAGE_KEY = 'uni-portal.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly stored = this.restore();

  private readonly _user = signal<AuthUser | null>(this.stored?.user ?? null);
  private readonly _token = signal<string | null>(this.stored?.token ?? null);

  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);

  get token(): string | null {
    return this._token();
  }

  async login(username: string, password: string): Promise<AuthUser> {
    try {
      const session = await firstValueFrom(
        this.http.post<Session>('/api/auth/login', { username, password }),
      );
      this.save(session);
      return session.user;
    } catch (e) {
      throw toApiError(e, 'Invalid username or password.');
    }
  }

  /** Re-checks the stored token with the backend and drops it if it is no longer valid. */
  async refresh(): Promise<void> {
    const token = this._token();
    if (!token) {
      return;
    }
    try {
      const user = await firstValueFrom(this.http.get<AuthUser>('/api/me', this.authHeaders(token)));
      this.save({ token, user });
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 401) {
        this.logout();
      }
    }
  }

  logout(): void {
    const token = this._token();
    this._user.set(null);
    this._token.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable */
    }
    if (token) {
      firstValueFrom(this.http.post('/api/auth/logout', {}, this.authHeaders(token))).catch(
        () => undefined,
      );
    }
  }

  authHeaders(token: string): { headers: HttpHeaders } {
    return { headers: new HttpHeaders({ Authorization: `Bearer ${token}` }) };
  }

  private save(session: Session): void {
    this._token.set(session.token);
    this._user.set(session.user);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      /* storage unavailable */
    }
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as Session) : null;
    } catch {
      return null;
    }
  }
}
