const RETRYABLE_STATUS = new Set([502, 503, 504]);
const IDEMPOTENT_METHODS = new Set(['GET', 'HEAD', 'DELETE']);
const MAX_DELAY_MS = 8000;
const MAX_RETRY_AFTER_MS = 30000;

/** 429 is retried for every method; 5xx and network errors only for idempotent ones. */
export class RetryPolicy {
  constructor(maxRetries, sleep) {
    this.maxRetries = Math.max(0, maxRetries);
    this.sleep = sleep;
  }

  shouldRetryStatus(status, method, attempt) {
    if (attempt >= this.maxRetries) return false;
    return status === 429 || (IDEMPOTENT_METHODS.has(method) && RETRYABLE_STATUS.has(status));
  }

  shouldRetryConnection(method, attempt) {
    return attempt < this.maxRetries && IDEMPOTENT_METHODS.has(method);
  }

  wait(attempt, retryAfter = null) {
    return this.sleep(RetryPolicy.delay(attempt, retryAfter));
  }

  static delay(attempt, retryAfter) {
    if (retryAfter && /^\d+(\.\d+)?$/.test(retryAfter)) return Math.min(Number(retryAfter) * 1000, MAX_RETRY_AFTER_MS);
    return Math.min(500 * 2 ** attempt, MAX_DELAY_MS);
  }
}
