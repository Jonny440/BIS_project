import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError, toApiError } from './api';
import { AuthService } from './auth.service';

export interface CourseOption {
  code: string;
  title: string;
  credits: number;
  instructor: string;
  targetMajor: string;
  targetYear: number;
  enrolled: boolean;
  eligible: boolean;
  reason: string | null;
}

export interface RegistrationData {
  credits: number;
  limit: number;
  courses: CourseOption[];
}

export interface GradeRow {
  code: string;
  title: string;
  credits: number;
  att1: number | null;
  att2: number | null;
  finalScore: number | null;
  total: number | null;
  letter: string | null;
}

export interface StudentMark {
  id: string;
  fullName: string;
  att1: number | null;
  att2: number | null;
  finalScore: number | null;
  total: number | null;
  letter: string | null;
}

export interface TeacherCourse {
  code: string;
  title: string;
  students: StudentMark[];
}

export interface GradesData {
  role: 'STUDENT' | 'TEACHER';
  rows?: GradeRow[];
  courses?: TeacherCourse[];
}

export interface TranscriptEntry {
  code: string;
  title: string;
  credits: number;
  letter: string;
  points: number;
  passed: boolean;
}

export interface TranscriptData {
  credits: number;
  gpa: number;
  entries: TranscriptEntry[];
}

export interface AttendanceSummaryRow {
  code: string;
  title: string;
  present: number;
  total: number;
}

export interface AttendanceRecordRow {
  code: string;
  student: string;
  date: string;
  status: string;
}

export interface Contact {
  id: string;
  username: string;
  fullName: string;
}

export interface MarkableSession {
  id: number;
  code: string;
  title: string;
  day: number;
  start: string;
  end: string;
  students: Contact[];
}

export interface AttendanceData {
  role: 'STUDENT' | 'TEACHER';
  summary?: AttendanceSummaryRow[];
  sessions?: MarkableSession[];
  records: AttendanceRecordRow[];
}

export interface Book {
  id: number;
  title: string;
  author: string;
  isbn: string;
  copies: number;
  available: number;
}

export interface Loan {
  id: number;
  title: string;
  borrowedAt: string;
  dueAt: string;
  returnedAt: string | null;
}

export interface LibraryData {
  books: Book[];
  loans: Loan[];
}

export interface Message {
  sender: string;
  recipient: string;
  body: string;
  sentAt: string;
}

export interface MessagesData {
  inbox: Message[];
  sent: Message[];
  contacts: Contact[];
}

/** Everything the portal pages load from the Java API. */
@Injectable({ providedIn: 'root' })
export class PortalService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  registration(): Promise<RegistrationData> {
    return this.request<RegistrationData>('GET', '/registration');
  }

  enroll(code: string): Promise<RegistrationData> {
    return this.request<RegistrationData>('POST', '/registration', { code });
  }

  grades(): Promise<GradesData> {
    return this.request<GradesData>('GET', '/grades');
  }

  saveMark(
    code: string,
    studentId: string,
    att1: number,
    att2: number,
    finalScore: number,
  ): Promise<void> {
    return this.request<void>('POST', '/grades', { code, studentId, att1, att2, finalScore });
  }

  transcript(): Promise<TranscriptData> {
    return this.request<TranscriptData>('GET', '/transcript');
  }

  attendance(): Promise<AttendanceData> {
    return this.request<AttendanceData>('GET', '/attendance');
  }

  markAttendance(
    sessionId: number,
    studentId: string,
    date: string,
    status: string,
  ): Promise<void> {
    return this.request<void>('POST', '/attendance', { sessionId, studentId, date, status });
  }

  library(): Promise<LibraryData> {
    return this.request<LibraryData>('GET', '/library');
  }

  borrow(bookId: number): Promise<LibraryData> {
    return this.request<LibraryData>('POST', '/library/borrow', { bookId });
  }

  giveBack(loanId: number): Promise<LibraryData> {
    return this.request<LibraryData>('POST', '/library/return', { loanId });
  }

  messages(): Promise<MessagesData> {
    return this.request<MessagesData>('GET', '/messages');
  }

  sendMessage(recipient: string, body: string): Promise<MessagesData> {
    return this.request<MessagesData>('POST', '/messages', { recipient, body });
  }

  private async request<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
    const token = this.auth.token;
    if (!token) {
      throw new ApiError(401, 'Please sign in again.');
    }
    try {
      return await firstValueFrom(
        this.http.request<T>(method, `/api${path}`, {
          body,
          headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
        }),
      );
    } catch (e) {
      throw toApiError(e, 'The request failed.');
    }
  }
}
