import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import { PortalService, TranscriptData } from '../core/portal.service';

@Component({
  selector: 'app-transcript',
  templateUrl: './transcript.html',
})
export class Transcript {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly data = signal<TranscriptData | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.data.set(await this.portal.transcript());
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
