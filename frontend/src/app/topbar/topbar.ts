import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, UserRole } from '../core/auth.service';

interface NavItem {
  label: string;
  route: string;
  roles?: UserRole[];
}

@Component({
  selector: 'app-topbar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './topbar.html',
})
export class Topbar {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly items: NavItem[] = [
    { label: 'Schedule', route: '/schedule' },
    { label: 'Register for Course', route: '/register', roles: ['STUDENT'] },
    { label: 'Grades', route: '/grades', roles: ['STUDENT', 'TEACHER'] },
    { label: 'Transcript', route: '/transcript', roles: ['STUDENT'] },
    { label: 'Attendance', route: '/attendance', roles: ['STUDENT', 'TEACHER'] },
    { label: 'Library', route: '/library' },
    { label: 'Messages', route: '/messages' },
  ];

  protected visibleItems(): NavItem[] {
    const role = this.auth.user()?.role;
    return this.items.filter((item) => !item.roles || (role && item.roles.includes(role)));
  }

  protected async logout(): Promise<void> {
    this.auth.logout();
    await this.router.navigateByUrl('/login');
  }
}
