import { HttpErrorResponse } from '@angular/common/http';

/** Error raised by the API services, with the HTTP status kept for guards and redirects. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function toApiError(e: unknown, fallback: string): ApiError {
  if (e instanceof HttpErrorResponse) {
    const message = e.error && typeof e.error.error === 'string' ? e.error.error : null;
    if (message) {
      return new ApiError(e.status, message);
    }
    if (e.status === 0) {
      return new ApiError(0, 'Cannot reach the server. Is the backend running?');
    }
    return new ApiError(e.status, fallback);
  }
  return new ApiError(0, fallback);
}
