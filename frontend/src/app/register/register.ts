import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import { PortalService, RegistrationData } from '../core/portal.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly data = signal<RegistrationData | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected async enroll(code: string): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    try {
      this.data.set(await this.portal.enroll(code));
      this.notice.set(`Registered for ${code}.`);
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  private async load(): Promise<void> {
    try {
      this.data.set(await this.portal.registration());
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
