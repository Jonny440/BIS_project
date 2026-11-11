import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import { LibraryData, PortalService } from '../core/portal.service';

@Component({
  selector: 'app-library',
  templateUrl: './library.html',
})
export class Library {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly data = signal<LibraryData | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected async borrow(bookId: number, title: string): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    try {
      this.data.set(await this.portal.borrow(bookId));
      this.notice.set(`Borrowed "${title}". Due in two weeks.`);
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  protected async giveBack(loanId: number): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    try {
      this.data.set(await this.portal.giveBack(loanId));
      this.notice.set('Book returned.');
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  private async load(): Promise<void> {
    try {
      this.data.set(await this.portal.library());
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
