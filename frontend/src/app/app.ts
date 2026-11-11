import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { Topbar } from './topbar/topbar';

@Component({
  imports: [RouterOutlet, Topbar],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);

  constructor() {
    void this.auth.refresh();
  }
}
