import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import { PortalService, PortalTask, Priority } from '../core/portal.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly tasks = signal<PortalTask[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly title = signal('');
  protected readonly deadline = signal('');
  protected readonly priority = signal<Priority>('MEDIUM');

  constructor() {
    void this.load();
  }

  protected async add(): Promise<void> {
    const title = this.title().trim();
    if (!title || !this.deadline()) {
      this.error.set('A task and a deadline are required.');
      return;
    }

    this.busy.set(true);
    this.error.set(null);
    try {
      this.tasks.set((await this.portal.addTask(title, this.deadline(), this.priority())).tasks);
      this.title.set('');
      this.deadline.set('');
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    } finally {
      this.busy.set(false);
    }
  }

  protected async toggle(task: PortalTask): Promise<void> {
    this.error.set(null);
    try {
      this.tasks.set((await this.portal.setTaskDone(task.id, !task.done)).tasks);
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  private async load(): Promise<void> {
    try {
      this.tasks.set((await this.portal.tasks()).tasks);
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
