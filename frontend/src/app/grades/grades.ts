import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import { GradeRow, PortalService, TeacherCourse } from '../core/portal.service';

@Component({
  selector: 'app-grades',
  templateUrl: './grades.html',
})
export class Grades {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly isTeacher = computed(() => this.auth.user()?.role === 'TEACHER');
  protected readonly rows = signal<GradeRow[] | null>(null);
  protected readonly teacherCourses = signal<TeacherCourse[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected async save(
    code: string,
    studentId: string,
    att1: number,
    att2: number,
    finalScore: number,
  ): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    try {
      await this.portal.saveMark(code, studentId, att1, att2, finalScore);
      this.notice.set('Marks saved.');
      await this.load();
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  private async load(): Promise<void> {
    try {
      const data = await this.portal.grades();
      if (data.role === 'TEACHER') {
        this.teacherCourses.set(data.courses ?? []);
      } else {
        this.rows.set(data.rows ?? []);
      }
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
