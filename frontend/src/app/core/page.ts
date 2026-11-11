import { Router } from '@angular/router';
import { ApiError } from './api';
import { AuthService } from './auth.service';

/**
 * Turns a failed request into the message to show. On 401 the session is dropped
 * and the visitor is sent back to the login page, in which case nothing is shown.
 */
export async function handleApiFailure(
  e: unknown,
  auth: AuthService,
  router: Router,
): Promise<string | null> {
  if (e instanceof ApiError && e.status === 401) {
    auth.logout();
    await router.navigateByUrl('/login');
    return null;
  }
  return e instanceof Error ? e.message : 'Something went wrong.';
}
