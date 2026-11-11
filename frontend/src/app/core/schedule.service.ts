import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError, toApiError } from './api';
import { AuthService } from './auth.service';

export type Weekday = 1 | 2 | 3 | 4 | 5; // Mon..Fri

export interface ClassSession {
  code: string;
  title: string;
  kind: 'LECTURE' | 'PRACTICE' | 'LAB';
  day: Weekday;
  start: string; // "HH:mm"
  end: string; // "HH:mm"
  room: string;
  instructor: string;
}

export interface CourseSummary {
  code: string;
  title: string;
  credits: number;
  instructor: string;
  score: number | null;
  grade: string | null;
}

export interface ScheduleData {
  courses: CourseSummary[];
  sessions: ClassSession[];
}

@Injectable({ providedIn: 'root' })
export class ScheduleService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  async getSchedule(): Promise<ScheduleData> {
    const token = this.auth.token;
    if (!token) {
      throw new ApiError(401, 'Please sign in again.');
    }
    try {
      return await firstValueFrom(
        this.http.get<ScheduleData>('/api/schedule', this.auth.authHeaders(token)),
      );
    } catch (e) {
      throw toApiError(e, 'Could not load your schedule.');
    }
  }
}
