import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiError } from '../core/api';
import { AuthService } from '../core/auth.service';
import { ClassSession, ScheduleData, ScheduleService, Weekday } from '../core/schedule.service';

const FIRST_HOUR = 8;
const LAST_HOUR = 18;

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
}

function startHour(time: string): number {
  return Math.floor(toMinutes(time) / 60);
}

interface Day {
  value: Weekday;
  label: string;
}

@Component({
  selector: 'app-schedule',
  templateUrl: './schedule.html',
})
export class Schedule {
  private readonly auth = inject(AuthService);
  private readonly api = inject(ScheduleService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly data = signal<ScheduleData | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly today = new Date().getDay();

  protected readonly days: Day[] = [
    { value: 1, label: 'Mon' },
    { value: 2, label: 'Tue' },
    { value: 3, label: 'Wed' },
    { value: 4, label: 'Thu' },
    { value: 5, label: 'Fri' },
  ];

  protected readonly hours = computed<number[]>(() => {
    const sessions = this.data()?.sessions ?? [];
    let first = FIRST_HOUR;
    let last = LAST_HOUR;
    for (const session of sessions) {
      first = Math.min(first, startHour(session.start));
      last = Math.max(last, Math.ceil(toMinutes(session.end) / 60));
    }
    return Array.from({ length: Math.max(last - first, 1) }, (_, i) => first + i);
  });

  constructor() {
    void this.load();
  }

  protected todaySessions(): ClassSession[] {
    return (this.data()?.sessions ?? []).filter((s) => s.day === this.today);
  }

  protected cell(day: Weekday, hour: number): ClassSession[] {
    return (this.data()?.sessions ?? []).filter((s) => s.day === day && startHour(s.start) === hour);
  }

  protected hourLabel(hour: number): string {
    return `${String(hour).padStart(2, '0')}:00`;
  }

  private async load(): Promise<void> {
    try {
      this.data.set(await this.api.getSchedule());
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        this.auth.logout();
        await this.router.navigateByUrl('/login');
        return;
      }
      this.error.set(e instanceof Error ? e.message : 'Could not load your schedule.');
    }
  }
}
