from typing import Callable, Optional

RETRYABLE_STATUS = {502, 503, 504}
IDEMPOTENT_METHODS = {"GET", "HEAD", "DELETE"}
MAX_DELAY_SECONDS = 8.0
MAX_RETRY_AFTER_SECONDS = 30.0


class RetryPolicy:
    """429 is retried for every method; 5xx and network errors only for idempotent ones."""

    def __init__(self, max_retries: int, sleep: Callable[[float], None]):
        self.max_retries = max(0, max_retries)
        self._sleep = sleep

    def should_retry_status(self, status: int, method: str, attempt: int) -> bool:
        if attempt >= self.max_retries:
            return False
        return status == 429 or (method in IDEMPOTENT_METHODS and status in RETRYABLE_STATUS)

    def should_retry_connection(self, method: str, attempt: int) -> bool:
        return attempt < self.max_retries and method in IDEMPOTENT_METHODS

    def wait(self, attempt: int, retry_after: Optional[str] = None) -> None:
        self._sleep(self._delay(attempt, retry_after))

    @staticmethod
    def _delay(attempt: int, retry_after: Optional[str]) -> float:
        if retry_after and retry_after.replace(".", "", 1).isdigit():
            return min(float(retry_after), MAX_RETRY_AFTER_SECONDS)
        return min(0.5 * (2 ** attempt), MAX_DELAY_SECONDS)
