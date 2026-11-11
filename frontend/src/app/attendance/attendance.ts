import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import {
  AttendanceRecordRow,
  AttendanceSummaryRow,
  Contact,
  MarkableSession,
  PortalService,
} from '../core/portal.service';

const DAY_NAMES = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri'];

@Component({
  selector: 'app-attendance',
  templateUrl: './attendance.html',
})
export class Attendance {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly isTeacher = computed(() => this.auth.user()?.role === 'TEACHER');
  protected readonly summary = signal<AttendanceSummaryRow[] | null>(null);
  protected readonly sessions = signal<MarkableSession[]>([]);
  protected readonly records = signal<AttendanceRecordRow[]>([]);
  protected readonly selectedSessionId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly selectedSession = computed(() => {
    const sessions = this.sessions();
    return sessions.find((s) => s.id === this.selectedSessionId()) ?? sessions[0] ?? null;
  });

  constructor() {
    void this.load();
  }

  protected students(): Contact[] {
    return this.selectedSession()?.students ?? [];
  }

  protected dayName(day: number): string {
    return DAY_NAMES[day] ?? '';
  }

  protected percent(present: number, total: number): number {
    return total === 0 ? 0 : Math.round((present / total) * 100);
  }

  protected async mark(date: string, studentId: string, status: string): Promise<void> {
    const session = this.selectedSession();
    if (!session) {
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    try {
      await this.portal.markAttendance(session.id, studentId, date, status);
      this.notice.set(`Attendance saved for ${session.code} on ${date}.`);
      await this.load();
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  private async load(): Promise<void> {
    try {
      const data = await this.portal.attendance();
      this.records.set(data.records ?? []);
      if (data.role === 'TEACHER') {
        const sessions = data.sessions ?? [];
        this.sessions.set(sessions);
        if (this.selectedSessionId() === null && sessions.length) {
          this.selectedSessionId.set(sessions[0].id);
        }
      } else {
        this.summary.set(data.summary ?? []);
      }
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
