import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { handleApiFailure } from '../core/page';
import { Contact, Message, PortalService } from '../core/portal.service';

@Component({
  selector: 'app-messages',
  templateUrl: './messages.html',
})
export class Messages {
  private readonly auth = inject(AuthService);
  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);

  protected readonly inbox = signal<Message[]>([]);
  protected readonly sent = signal<Message[]>([]);
  protected readonly contacts = signal<Contact[]>([]);
  protected readonly draft = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly loaded = signal(false);

  constructor() {
    void this.load();
  }

  protected async send(recipient: string, body: string): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    try {
      const data = await this.portal.sendMessage(recipient, body);
      this.inbox.set(data.inbox);
      this.sent.set(data.sent);
      this.draft.set('');
      this.notice.set(`Message sent to ${recipient}.`);
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }

  private async load(): Promise<void> {
    try {
      const data = await this.portal.messages();
      this.inbox.set(data.inbox);
      this.sent.set(data.sent);
      this.contacts.set(data.contacts);
      this.loaded.set(true);
    } catch (e) {
      this.error.set(await handleApiFailure(e, this.auth, this.router));
    }
  }
}
